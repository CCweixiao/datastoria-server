# V3 P2 Spike:ClickHouse 集群状态采集设计

> 本文件是 [`ddl-approval-v3-plan-centric.md`](ddl-approval-v3-plan-centric.md) §7.3（真集群节点采集）的 **spike 研究与设计产物**，对应 §11「P2 先 spike」。它解决执行引擎重建前最大的未知：**如何把一条 `ON CLUSTER` DDL 的执行结果映射到逐节点状态**。代码实现需在真实 ClickHouse 上验证开放问题（§4）后再落地。

## 1. Spike 目标

当前 `executeOnEndpoint`（`ApprovalCommandService:802`）只解析 `connection.url()` 的单一 host，永远写一条 `ds_approval_node_execution`。P2 要实现真集群逐节点采集，必须先搞清：

1. 一条 `ON CLUSTER` DDL 发出后，如何拿到**每个节点**的成功/失败/耗时？
2. 不同 ClickHouse 版本的系统表差异如何兜底？
3. 如何判定「部分节点未确认」并按失败处理（V1 §9.3）？

## 2. 关键发现：`system.distributed_ddl_queue`

[官方文档](https://clickhouse.com/docs/reference/system-tables/distributed_ddl_queue) 确认该表**天然按节点分行**——这是设计的基石：

| 列 | 类型 | 用途 |
| ---- | ---- | ---- |
| `entry` | String | DDL 队列条目 id（文档标注为 Query ID） |
| `cluster` | String | 集群名 |
| `query` | String | 执行的 SQL |
| `host` | Nullable(String) | **节点主机名** |
| `port` | Nullable(UInt16) | 节点端口 |
| `status` | Nullable(Enum8) | `Inactive(0)`/`Active(1)`/`Finished(2)`/`Removing(3)`/`Unknown(4)` |
| `exception_code` | Nullable(UInt16) | ClickHouse 错误码（0 = 成功） |
| `exception_text` | Nullable(String) | 错误信息 |
| `query_finish_time` | Nullable(DateTime) | 完成时间 |
| `query_duration_ms` | Nullable(UInt64) | 耗时 |

**核心语义**：同一条 DDL（同 `entry`）在该表产生 **N 行（每节点一行）**，每行独立携带 `status`/`exception`/`duration`。这正是 `ds_approval_node_execution` 想要的逐节点视图。

> 注意：文档把 `entry` 标为 "Query ID"，但分布式 DDL 队列条目 id 与执行器设置的 `query_id` 是否严格相等，**需在真实 CH 上验证**（见 §4）。这是实现的前提。

## 3. 配套表：`system.clusters`

[官方文档](https://clickhouse.com/docs/reference/system-tables/clusters) 列出集群拓扑，用于「期望节点集合」比对，检测缺失/部分节点：

| 列 | 类型 | 用途 |
| ---- | ---- | ---- |
| `cluster` | String | 集群名 |
| `shard_num` | UInt32 | 分片号 |
| `replica_num` | UInt32 | 副本号 |
| `host_name` | String | 主机名 |
| `port` | UInt16 | 端口 |
| `is_local` | UInt8 | 是否本机 |

期望节点集 = `SELECT host_name, port FROM system.clusters WHERE cluster = :cluster`。执行后用 `distributed_ddl_queue` 的实际行与此集合比对：缺节点 → `UNKNOWN`/`PARTIAL_FAILED`，按失败处理。

## 4. 采集设计（待真实 CH 验证后实现）

```text
执行器发 ON CLUSTER DDL（设唯一 queryId，沿用现有 connections.query）
  ↓ 同步返回后（CH 已对该连接的协调节点完成调度）
查询 SELECT host, port, status, exception_code, exception_text, query_duration_ms
       FROM system.distributed_ddl_queue
       WHERE entry = :queryId        -- §4-Q1：验证 entry == queryId
  ↓ 按行映射到 ds_approval_node_execution
对每个期望节点（system.clusters）：
  - 有 Finished 行            → 节点 SUCCEEDED（带 duration）
  - 有非 Finished 且 exception → 节点 FAILED（带 code/text）
  - 无行 / Unknown            → 节点 UNKNOWN（工单按失败，V1 §9.3）
工单状态：任一节点非 Finished → 工单 FAILED，不继续下一条 SQL
```

**版本探测 + 兜底**（`SELECT version()`）：

- 现代 CH（≥ ~21.8）：`system.distributed_ddl_queue`。
- 旧版 CH：表名曾为 `system.cluster_ddl_queue`（文档未给出确切改名版本，**需验证**）。
- 探测失败 / 表不存在 / 查询异常 → **回退当前单节点行为**（解析 connection.url() 单 host），不阻断执行，但日志标注「未采集到集群节点」。

## 5. 开放验证问题（需真实 CH 环境）

这些是 spike 的「实现前置」，必须在真实 ClickHouse 上确认后才能写采集代码：

1. **Q1 `entry` ↔ `queryId` 关联**：执行器发送 DDL 时设置的 `query_id` 是否等于 `distributed_ddl_queue.entry`？若不等，需另找关联键（如 `query` 文本匹配 + `query_create_time` 窗口）。
2. **Q2 读取时机**：同步 `connections.query` 返回后，各节点行是否已全部 `Finished`？还是需要短轮询（带超时）等 lag 节点落定？
3. **Q3 版本边界**：`distributed_ddl_queue` 何时从 `cluster_ddl_queue` 改名？项目目标 CH 版本支持哪个？是否两个都要兼容？
4. **Q4 权限**：执行身份能否读 `system.distributed_ddl_queue` / `system.clusters`？（可能需额外 GRANT。）
5. **Q5 ON CLUSTER cluster 校验**：SQL 中 cluster 是否与冻结 plan 的 `clusterName` 一致（V1 §9.3），由 AST 安全注入还是信任 SQL 文本？

## 6. 对 P2 工作分解的影响

spike 把「节点采集」从「未知」降为「待验证的实现」。P2 剩余工作可清晰分解为**两条相对独立的线**，互不阻塞：

| 线 | 内容 | 依赖 |
| ---- | ---- | ---- |
| **A 节点采集** | 实现 §4 采集逻辑 + 版本兜底 + 映射到 node_execution | 真实 CH 验证 §5 |
| **B 异步执行引擎** | QUEUED + scheduler/worker + 租约 + AUTO_AFTER_APPROVAL | 系统身份 / 跨租户轮询 / 并发的设计决策（无需 CH） |

两条线可并行推进：B 不依赖 CH，A 的设计已就绪待验证。当前 `execute()`/`retry()`（同步、MANUAL_TRIGGER）已是可用基线，A 与 B 在其上增量替换，不破坏现有闭环。

## 7. 当前状态

- ✅ spike 研究/设计完成（本文件）。
- ⏳ A 节点采集：待真实 CH 验证 §5 后实现。
- ⏳ B 异步执行引擎：待系统身份/并发设计决策后实现（独立的下一阶段）。

## 8. 异步 worker 身份决断（已解阻，2026-08-07）

原担心 worker 无人類身份无法执行 DDL。核查 `ClickHouseConnectionService` 后**解除**：

- `query(id, sql, params, identity)`（`executeOnEndpoint` 所用，:220）只做 `require(id, identity)`（**租户隔离**：`identity.tenantId()` 必须匹配连接租户）+ `remoteClient.execute(...)`，**无 admin 门禁**。
- 只有 `queryStream`（:239）才按 `identity.isAdmin()` 把非 admin 限为只读；worker 走 `query`，不受此限。

因此 worker 对每个被领取的工单，按其租户构造系统身份即可执行：

```java
Identity system = new Identity(request.tenantId(), "system", Set.of("ROLE_ADMIN"));
```

`require()` 因租户匹配通过，`query` 无 admin 门禁故 DDL 可发。worker 实现路径已无身份阻断，剩余仅为：

- `findClaimableQueuedRequests(limit)`（QUEUED 且租约过期/未持有，跨租户）；
- `claimQueued`（CAS QUEUED→RUNNING + 写 `execution_lease_until`，租约防并发重复领取）；
- `drainOnce()`：领取 → 构造系统身份 → 复用 `revalidate` + `verifyEnvNotDrifted` + 语句执行 + `finishExecutionRequest`（释放租约）；
- `@EnableScheduling` + 薄 `@Scheduled` 包装调 `drainOnce()`；
- AUTO 可达性：`review()` 对 `executionMode = AUTO_AFTER_APPROVAL` 转 QUEUED（SUBMITTED→QUEUED），`executionMode` 默认仍 MANUAL_TRIGGER。

并发安全靠 CAS 租约（claim 的 `WHERE revision=:expected AND status='QUEUED' AND (lease 过期/未持有)`），多 worker 实例只有一个领取成功。
