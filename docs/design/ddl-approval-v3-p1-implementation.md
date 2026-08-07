# DDL 审批 V3 — P1 实施清单（升 Plan）

> 本文件是 [`ddl-approval-v3-plan-centric.md`](ddl-approval-v3-plan-centric.md) 的 P1 落地规格，不是新设计。凡设计原理引用 V3 文档，本文只写「怎么建」。

## 0. 范围

**P1 目标**：把 Plan 从 `content_json` 里的一坨提升为带 `plan_version` / `plan_hash` 的版本化一等公民，打通 change classifier 与结构化 prepare 返回。

**P1 包含**：

- `ds_approval_request` 加 4 列（plan_version / plan_hash / env_snapshot_json / policy_version_ref）；
- `plan_hash` 规范化与计算；
- change classifier（P1 简化版：哈希驱动，见 §3）；
- `env_snapshot` 捕获与漂移检测（仅 ALTER 类型；CREATE_TABLE 走分支）；
- `Plan` 领域 record + `prepare` / `createDraft` 改造 + 返回结构升级；
- 一条真实集成测试路径。

**P1 不含**（P2/P3）：执行引擎换芯、scheduler/worker/租约、真集群节点采集、scope allowlist、`EXPIRED` 状态机落地、声明式 branches 被执行器消费、`ApprovalProvider`。P1 阶段 `env` 漂移检测先**只计算并记录**，不触发状态转换（`EXPIRED` 留 P2）。

**前置决策（已定）**：Plan 表**并入 `ds_approval_request`**，不建 `ds_approval_plan`。升级触发条件见 V3 §4.1。

---

## 1. 数据模型改动（migration）

新建 Flyway：`V25__approval_plan_versioning.sql`（接 V24）。

```sql
ALTER TABLE ds_approval_request
  ADD COLUMN plan_version       INT          NOT NULL DEFAULT 1 COMMENT 'Plan 语义版本，随 plan_hash 变化递增',
  ADD COLUMN plan_hash          CHAR(64)     NULL     DEFAULT NULL COMMENT 'Plan 语义内容 SHA-256；DRAFT 创建时计算',
  ADD COLUMN env_snapshot_json  LONGTEXT     NULL     DEFAULT NULL COMMENT '审批时冻结的环境指纹（cluster 拓扑/对象/schema fingerprint）',
  ADD COLUMN policy_version_ref VARCHAR(128) NULL     DEFAULT NULL COMMENT '策略版本引用：type_definition_revision:routing_checksum';

-- 辅助索引：按 request 查当前 plan 版本
CREATE INDEX idx_approval_request_plan
  ON ds_approval_request (tenant_id, request_id, plan_version);
```

> **类型核对**：`env_snapshot_json` 类型对齐既有 `content_json`（V22，预期 LONGTEXT）；落地前 `SHOW CREATE TABLE ds_approval_request` 确认 `content_json` 实际类型并保持一致。

**既有数据回填**（idempotent，开发库通常 0 行）：

```sql
-- 若存在历史 DRAFT 且 plan_hash 为空，由代码侧 ReindexJob 按 content_json 重算；
-- SQL 层只保证列存在与默认值。干净库无需运行。
```

**复用既有**：`content_json`（持有结构化 Plan 快照，权威内容）、`content_digest`（执行前逐字节匹配，保留）、`revision`（乐观锁，与 plan_version 正交）。

---

## 2. `plan_hash` 规范化规范（P1 核心）

`plan_hash` 是 Plan **语义内容**的 SHA-256。定义正确，则 change classifier 自动成立（见 §3）。

### 2.1 进入哈希的语义内容

```text
plan_hash = sha256( canonicalJson({
  "intent":     <semantic intent fields, 见 §2.3>,
  "steps":      [ <per-step canonical object>, ... ],   // 按 ordinal 有序
  "branches":   [ <branch object>, ... ],               // 按 id 排序
  "compensationPolicy": <object>,
  "impactScope": { "cluster":..., "database":..., "tables":[sorted],
                   "columnScope":[sorted], "nodeScope":... }
}) )
```

**per-step canonical object**（固定键序）：

```json
{ "o": <ordinal>,
  "k": "<operationKind>",
  "d": "<normalizedSqlDigest>",
  "obj": [<sorted objectRefs>],
  "rd":  [<sorted readDeps>],
  "pc":  <precondition | null>,
  "idem": "<idempotencyStrategy>",
  "dep": [<sorted dependsOn>] }
```

### 2.2 排除项（绝不进哈希）

| 排除 | 理由 |
| ---- | ---- |
| `sqlText` 字面量 | 用 `normalizedSqlDigest` 代替——SQL 重新格式化不应使审批失效 |
| 步骤内部 id / createdAt / retryCount / lastAttemptStatus | 运行时元数据 |
| `riskSummary` | 由内容派生，是结果不是原因 |
| `envSnapshot` | 独立指纹，单独比对（§4） |
| `policyVersionRef` | 独立锚定，不折进 plan_hash |
| `generationRuleChecksum` / `ruleSummaries` | 派生自类型定义，由 policy_version_ref 独立锚定 |

### 2.3 各 operation type 的 semantic intent 字段（进哈希）

| type | semantic intent 字段 |
| ---- | ---- |
| `CLICKHOUSE_CREATE_TABLE` | database, table, cluster, columns(name+type), orderBy, shardingKey, engine |
| `CLICKHOUSE_ADD_COLUMN` | database, table, column, type, after |
| `CLICKHOUSE_MODIFY_COLUMN` | database, table, column, newType |
| `CLICKHOUSE_DROP_COLUMN` | database, table, column |
| `CLICKHOUSE_ADD_INDEX` | database, table, indexName, type, expression, granularity, materialize |

> 上述字段**全部是 semantic**——任意变化都改变 `plan_hash`。这是 P1 的关键性质：当前 5 种类型**没有「语义不变但需重审」的灰色字段**，因此 classifier 不需要逐字段规则。

### 2.4 canonical JSON 规则

- UTF-8，紧凑（无 insignificant whitespace）；
- 键用**声明顺序**（非字母序），与 §2.1 / per-step object 列出的顺序一致；
- `steps` 按 `ordinal`；`objectRefs`/`readDeps`/`dependsOn` 字母序；`branches` 按 `id`；
- `null` 字段省略（除 per-step 的 `pc` 显式保留以区分「无前置」与「未声明」）；
- 字符串原样（标识符不强制小写，但 `trim` 前后空白）。

### 2.5 计算伪代码

```java
String planHash = sha256Hex(canonicalJson(
    "intent",     canonicalIntent(intent, semanticFields(typeKey)),
    "steps",      plan.steps().stream()
                       .sorted(byOrdinal)
                       .map(this::canonicalStep).toList(),
    "branches",   plan.branches().stream()
                       .sorted(byId).map(this::canonicalBranch).toList(),
    "compensationPolicy", plan.compensationPolicy(),
    "impactScope", canonicalImpactScope(plan.impactScope())
));
```

`canonicalJson` 必须用固定键序的有序 map 实现，**禁用** `HashMap`（无序）——复用 V1 既有 `ApprovalCommandService` 里组装 content_json 用的有序 `ObjectNode` 模式。

---

## 3. change classifier（P1 简化版：哈希驱动）

### 3.1 关键洞察

**P1 不需要规则引擎。** 若 §2 的 `plan_hash` 定义正确（排除运行时元数据），则：

```text
INVALIDATES_APPROVAL  ⟺  plan_hash(edit后) ≠ plan_hash(已冻结/已审批)
RUNTIME_OK            ⟺  plan_hash 不变（仅运行时元数据变化，如重试/超时/跳过）
EXPIRED               ⟺  env_fingerprint(执行时) ≠ env_snapshot(审批时) 超阈值   [P1 只记录，P2 触发]
```

「逐 operation type 边界表」（V3 §4.4）在 P1 **坍缩为 §2.3 的 semantic 字段表**：进哈希的字段变化即 `INVALIDATES`，运行时字段变化即 `RUNTIME_OK`。

### 3.2 调用时机

| 时机 | 动作 |
| ---- | ---- |
| 创建 DRAFT | `plan_version = 1`，计算 `plan_hash` |
| 更新 DRAFT（CAS） | 重算 `plan_hash`；**变化 → `plan_version++`**；不变 → 保持 |
| SUBMITTED 后编辑（需回 DRAFT） | `plan_hash` 必变 → `plan_version++` → 旧 Approval 失效（标 `SUPERSEDED`） |
| submit | 冻结当前 `(plan_version, plan_hash, env_snapshot, policy_version_ref)` 进 Approval 锚定 |
| 执行前（P2） | 比对 `plan_hash`（不变才执行）+ env 漂移（超阈值 → `EXPIRED`） |

`plan_version` 与 `revision` 正交：`revision` 每次更新都涨（乐观锁）；`plan_version` 仅在语义变化时涨。

### 3.3 接口

```java
public interface ChangeClassifier {
  // 输入旧/新 Plan，输出是否使审批失效
  Classification classify(Plan before, Plan after);
  enum Outcome { INVALIDATES_APPROVAL, RUNTIME_OK }
  record Classification(Outcome outcome, String newPlanHash, int newPlanVersion) {}
}
```

P1 实现体就是 `before.planHash().equals(after.planHash())` + env 指针（env 比对见 §4）。

---

## 4. `env_snapshot` 与漂移阈值

### 4.1 捕获（submit 时冻结）

| 类型 | env_snapshot 内容 | 来源 |
| ---- | ---- | ---- |
| CREATE_TABLE | cluster 拓扑 + 目标表「不存在」 | `system.clusters`（V3 新增探测）+ schema 短路 |
| ADD/MODIFY/DROP_COLUMN、ADD_INDEX | 目标表 schema fingerprint（列集 + 列类型 + sorting/primary/partition/sampling key） | `DdlSchemaInspector` 已有，封装成 fingerprint |

CREATE_TABLE 在 P1 暂不查 `system.clusters`（P2 执行芯才需要拓扑）——P1 的 env_snapshot 对 CREATE_TABLE 记录 `{tableExists: false}` 即可，漂移交给 same-name-table 分支（V3 §4.5，P3 消费）。

### 4.2 漂移阈值（P1 只记录，P2 触发 EXPIRED）

- **ALTER 类型**：目标表 schema fingerprint 任一变化（列集/列类型/四类 key）→ 判定漂移。
- **P1 行为**：执行前（P2 才有执行前钩子）或在 revalidate 路径里**计算并记录** drift 布尔，**不**触发状态转换。日志/审计可见。
- **P2 行为**：drift=true → `EXPIRED`。
- **未来精化**：仅「目标列 / 受保护 key 变化」才算漂移（忽略并发无关加列），降低误报。P1 取保守版（任一变化即漂移），因为 DDL 目标表并发无关变更少见，且假 `EXPIRED` 代价仅是「重新确认」，非数据损失。

---

## 5. 领域模型与 prepare 改造点

### 5.1 `Plan` record（从 request 列 + content_json 重建）

```java
public record Plan(
    RequestId requestId,
    int planVersion,
    String planHash,
    PlanStatus status,                 // DRAFT / FROZEN / SUPERSEDED（P1 用 DRAFT/FROZEN）
    DdlIntent intent,
    EnvSnapshot envSnapshot,
    String policyVersionRef,
    List<PlanStep> steps,
    List<PlanBranch> branches,         // P1 可为空 list（branches 消费在 P3）
    CompensationPolicy compensationPolicy,
    ImpactScope impactScope,
    RiskSummary riskSummary
) {}
```

`Plan` 是从 `ApprovalRequest` 的列 + `content_json` 反序列化重建的**视图对象**，不单独持久化。

### 5.2 `ApprovalCommandService.prepare` / `createDraft` 改造

在现有 6 步（V3 §4.6）基础上插入：

```text
步 4（compile 后）：
  + 从 CompiledDdlPlan 组装 Plan 视图（steps/branches/impactScope）
  + 计算 plan_hash（§2.5）
  + 捕获 env_snapshot（§4.1）

步 5（createDraft/updateDraft）：
  + 新建 DRAFT：plan_version=1, plan_hash, env_snapshot_json, policy_version_ref
       policy_version_ref = typeDefinition.revision() + ":v1"   // routing 在 P1 是常量 SINGLE+禁自批
  + 更新 DRAFT：调 ChangeClassifier；plan_hash 变 → plan_version++；CAS revision 不变
  + content_json 结构不变（仍含 statements/ruleSummaries），新增 plan_hash 不替代 content_digest

步 6（返回）：
  DdlApprovalPrepareResponse 增加 planVersion, planHash, envSnapshotSummary, impactScope
```

### 5.3 `prepare` 返回结构（升级）

```text
prepare_ddl_approval(...)
  -> draftId, requestNo, revision, contentDigest,
     planVersion, planHash, envSnapshotSummary, impactScope,
     orderedItems: [{ordinal, operationKind, sql, riskLevel, warnings}, ...],
     appliedRuleSummary, submittable
```

`planHash` / `envSnapshotSummary` / `impactScope` 是新增字段；`orderedItems` 保持精简安全视图（V3 §4.7c）。

### 5.4 submit 锚定

`submit`（`ApprovalCommandService:111`）在现有 revalidate 后，把 `(plan_version, plan_hash, env_snapshot_json, policy_version_ref)` 作为 Approval 锚定写入（首期写入 request 主表字段 + `ds_approval_event` 快照）。`revalidate`（`:887`）增加 `plan_hash` 比对：执行/提交前 `plan_hash` 必须等于冻结值。

---

## 6. 测试策略（补真实集成测试）

现状：`ApprovalCommandServiceTest` 全程 mock repository，无真实 JDBC/ClickHouse e2e（V3 §1 探索结论）。P1 改的是 prepare/compile/hash/classifier——**mock 测不到 hash 稳定性与 classifier 正确性**。

新增集成测试 `ApprovalPlanHashIntegrationTest`（用真实测试库，ClickHouse 侧用 CREATE_TABLE 短路 + mock `DdlSchemaInspector`）：

| # | 用例 | 断言 |
|---|------|------|
| 1 | prepare CREATE_TABLE → 再 prepare 同 intent | `plan_hash` 稳定不变；幂等命中不新建 |
| 2 | 改 intent（列类型 String→UInt64）再 prepare | `plan_hash` 变化；`plan_version` 递增；classifier 返回 `INVALIDATES_APPROVAL` |
| 3 | 改运行时元数据（重试次数，若暴露） | `plan_hash` 不变；classifier 返回 `RUNTIME_OK` |
| 4 | submit 冻结 `plan_hash`；篡改 content_json 后 revalidate | `plan_hash` 比对失败，拒绝 |
| 5 | submit 捕获 `env_snapshot_json` 非空 | env_snapshot 落库 |
| 6 | 改列类型为受保护 key 列（MODIFY_COLUMN） | descriptor 抛 `DDL_RULE_VIOLATION`（既有行为回归） |

测试库账号用 root 覆盖（见 memory `build-and-test-execution`）；`./mvnw` 需 `JAVA_HOME` 指向 JDK 17。

---

## 7. P1 验收

1. `ds_approval_request` 新增 4 列，migration 在干净库与有存量 DRAFT 库均通过。
2. `plan_hash` 满足 §2 规范：同语义稳定、SQL 重格式化不变、改任一 semantic 字段必变。
3. change classifier 输出与 §3.1 等价（hash 变→INVALIDATES，不变→RUNTIME_OK）。
4. DRAFT 编辑使 `plan_hash` 变化时 `plan_version` 递增；不变时保持。
5. submit 锚定 `(plan_version, plan_hash, env_snapshot, policy_version_ref)`；revalidate 含 `plan_hash` 比对。
6. `prepare` 返回含 `planVersion/planHash/envSnapshotSummary/impactScope`。
7. ALTER 类型 `env_snapshot` 含目标表 schema fingerprint；漂移检测在 P1 计算并记录（不触发状态转换）。
8. §6 的 6 个集成测试全绿。

---

## 8. 不在 P1（边界）

- 执行引擎、scheduler/worker/租约、真集群节点采集、scope allowlist → **P2**（先做 ClickHouse `system.clusters`/`distributed_ddl_queue` 探针 spike）。
- `EXPIRED` 状态机、声明式 branches 被执行器消费、`ApprovalProvider`、路由矩阵 → **P3**。
- `ds_approval_plan` 独立表 → 仅当出现「批量查历史版本/版本对比 UI/按版本回滚」需求时升级（V3 §4.1）。
