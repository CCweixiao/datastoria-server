# P4 实施报告 — AgentScope Java 最小 Harness

> Stage: P4（AgentScope 最小 Harness）
> 本次交付：**P4.1 + P4.2 + P4.3 + P4.4（已通过 review）+ P4.5（本次）**
> 分支：`codex/phase-p4`（worktree `/Users/jielongping/OpenProjects/datastoria-server-p4`）
> 基线 master：`a540e8b`
> 状态：P4.1–P4.4 review 已通过；**P4.5 已实现，待 review**；P4.6–P4.8 未开始。

---

# P4.1 — AgentScope 兼容性 Spike（已通过 review，冻结）

## 0. 范围说明（P4.1）

按指令“只交付 P4.1”，该阶段只完成 **P4.1：AgentScope 兼容性 Spike**，并产出第一轮验收物：

- AgentScope Java 版本兼容性 ADR（`docs/adr/0004-agentscope-java-baseline.md`）
- Maven 依赖锁定（`pom.xml`）
- fake model spike（`FakeStreamModel`）
- 流事件与取消测试（`AgentScopeSpikeTest`）
- 明确的阻断项（见 §8）

**P4.2–P4.8 未实现**：内部 Harness 模型、Run/Checkpoint DDL 与 repository、AI SDK encoder、
A01 Java chat API、前端 gateway、端到端验证均未开始。任何“完成”字样仅指 P4.1。

## 1. AgentScope 锁定版本

| 项 | 值 |
| --- | --- |
| 仓库 | `https://github.com/agentscope-ai/agentscope-java` |
| 标签 | `v2.0.0` GA（2026-07-10） |
| commit | `44c304ec84d5fbd8588c1af8bc71b1edb9663380` |
| Maven | `io.agentscope:agentscope-core:2.0.0`、`io.agentscope:agentscope-harness:2.0.0` |
| JDK | 17（`--release 17`，无 toolchains，与 Spring Boot 3.5.16 共存） |

不使用 snapshot；`main` 的 `2.0.1-SNAPSHOT` 不纳入。详见 ADR-0004 §2。

## 2. P4.1 完成范围

| 子任务 | 交付物 | 状态 |
| --- | --- | --- |
| 版本兼容性 ADR | `docs/adr/0004-agentscope-java-baseline.md` | ✅ |
| Maven 依赖锁定 | `pom.xml`（`agentscope.version=2.0.0`，core+harness，不导入 BOM） | ✅ |
| fake model spike | `src/test/.../agent/spike/FakeStreamModel.java` | ✅ |
| 流事件测试 | `AgentScopeSpikeTest#streamEventsProducesExpectedEventSequence`、`#harnessAgentBuildsAndStreams` | ✅ |
| 取消测试 | `AgentScopeSpikeTest#disposingSubscriptionCancelsModelFlux`、`#modelErrorPropagatesAsOnError` | ✅ |
| 阻断项 | 本报告 §8 + ADR-0004 §8 | ✅ |

## 3. 关键实测结论

1. **兼容性达成**：AgentScope `2.0.0` 在 Spring Boot 管理的 `reactor-core 3.7.19`（非其自带的
   `3.8.2`）下流式 / 错误 / 取消全部正常。`jackson`/`sqlite-jdbc`/`snakeyaml` 同样由 SB 管理，
   无冲突（`mvn dependency:tree` 已核对，见 ADR-0004 §3.1）。
2. **流式事件序列固定**（见 ADR-0004 §3.2）：`AGENT_START → MODEL_CALL_START →
   THINKING_BLOCK_{START,DELTA*,END} → TEXT_BLOCK_{START,DELTA*,END} → MODEL_CALL_END(usage) →
   AGENT_RESULT → AGENT_END`。同类型连续帧合并为单逻辑块；usage 在 `ModelCallEndEvent`。
3. **取消**：取消 `streamEvents` 订阅会向上游传播并取消 provider flux（token 即停）；`interrupt()`
   为协作式、仅在多步步间生效，单步纯文本响应中不会中止 mid-stream（ADR-0004 §3.3）。
   → DataStoria 取消策略：dispose 订阅（省 token）+ `interrupt()`（协作收尾）。
4. **错误传播**：`Flux.error` 经 `streamEvents` 以 `onError` 透出且根因保留 → 映射为 `RunFailed`。
5. **唯一 runtime**：P4 固定使用 `HarnessAgent`。最小 builder 显式关闭 filesystem、shell、
   memory、skill 和 subagent 等 P5+ 能力，测试断言传给 fake model 的 tool schema 数量为 0。

## 4. 测试命令与结果

```
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -B -ntp spotless:apply clean verify
```

- 全量：`Tests run: 173, Failures: 0, Errors: 0, Skipped: 0`（P3 基线 169 + P4.1 新增 4）。
- Spike 专项：`./mvnw test -Dtest=AgentScopeSpikeTest` → 4/4 通过。
- 无真实网络、无 API key、无真实 provider。

## 5. 安全检查（P4.1 范围）

- Spike 无 HTTP、无凭据；`FakeStreamModel` 不读 `GenerateOptions.getApiKey()`。
- `pom.xml` 与文档无 API key / OAuth token / 系统提示词 / checkpoint 明文。
- AgentScope 被钉在 test+compile classpath，但 P4.1 未写任何 Controller；无新增对外端点。
- 真实 provider smoke 未执行（P4.1 范围内不执行）。

## 6. MySQL / 真实 provider smoke 执行情况

- **MySQL contract：本机未执行**（无 Docker，Testcontainers 自动跳过，CI `mysql-contract` 兜底）。
  P4.1 未新增任何数据库变更（无 DDL、无 repository），不产生新 MySQL 风险。
- **真实 provider smoke：未执行**（P4.1 不在范围；留待 P4.6 且仅在显式环境变量开关下运行）。

## 7. 回滚方式

- P4.1 只新增依赖与 test-only spike，未改任何生产行为。
- 回滚 = 移除 `pom.xml` 中 `agentscope.version` 及两个 dependency、删除
  `src/test/.../agent/spike/`、撤销 ADR/报告。无数据迁移、无端点变更。

## 8. 已知后续风险（不阻断 P4.2）

1. reactor 降级（3.8.2→3.7.19）当前无异常；P4.2 起若用更复杂特性需复跑全量回归。
2. 真实 provider smoke 与 MySQL contract 均未在本机执行（见 §6），属后续阶段事项。

## 9. 修改文件

```
pom.xml                                                  (agentscope 依赖锁定)
docs/adr/0004-agentscope-java-baseline.md                (新增)
docs/delivery/p4-implementation-report.md                (新增，P4.1 only)
src/test/java/io/datastoria/server/agent/spike/FakeStreamModel.java   (新增)
src/test/java/io/datastoria/server/agent/spike/AgentScopeSpikeTest.java (新增)
```

## 10. 后续阶段建议（不在 P4.1 范围）

- P4.2：把 spike 的 `Model` 边界与事件映射提升为 `io.datastoria.server.agent.*` 生产抽象
  （`HarnessAgentFactory`/`AgentRunService`/`ModelAdapter`/`AgentEvent`/`AgentEventMapper`/
  `CancellationRegistry`/`RunContext`），controller 不直接依赖 `io.agentscope.*`。
- P4.3–P4.4：`V5__agent_run_and_checkpoint.sql`（SQLite/MySQL 双方言）+ repository + 状态机。
- P4.5：捕获 Node A01 golden stream，实现 `AiSdkStreamEncoder`。
- P4.6：A01 Java chat endpoint（凭据服务端注入、client_request_id 幂等、JDBC 不阻塞 event loop）。
- P4.7：前端 `NEXT_PUBLIC_DATASTORIA_CHAT_BACKEND=node|java` 网关。
- P4.8：fake model 端到端验证 + 可选真实 provider smoke。

每子阶段独立提交、可运行、可测试；P4.1 review 通过后再继续。

---

# P4.2 — 内部 Agent 抽象层（已通过 review）

## P4.2-0. 范围

把 P4.1 spike 证明的 AgentScope 边界提升为位于 `io.datastoria.server.agent` 的生产抽象层，使
controller / repository / 内部 event model **不依赖任何 `io.agentscope.*` 类型**（ADR-0004 §7 隔离
边界）。交付物：

- 内部 Agent event model（`domain`，AgentScope-free）
- `ModelAdapter`（模型边界）
- `HarnessAgentFactory`（最小权限，tool schema 为空）
- `AgentEventMapper`（AgentScope 事件 → 内部事件）
- `CancellationRegistry`（取消传播 + 租户隔离）
- `RunContext` / `AgentRunService` 骨架（`start` / `cancel`）
- 对应 fake-model 单元测试（20 个）

**明确非目标（未实现，留给后续阶段）**：Run/Checkpoint DDL + repository（P4.3/4）、AI SDK
encoder（P4.5）、A01 Java chat API（P4.6）、前端 gateway（P4.7）、端到端验证（P4.8）。**不涉及
P5+ 的 Skill、业务工具、sandbox、多 Agent**。

## P4.2-1. 分层

```text
controller (P4.6, 未实现)
  -> AgentRunService          (application, AgentScope-free)
       -> HarnessAgentFactory (runtime) -> ModelAdapter (runtime) -> AgentScope Model
       -> AgentEventMapper    (runtime): AgentScope AgentEvent -> AgentRunEvent (domain)
       -> CancellationRegistry(runtime): dispose 订阅 + interrupt(), 校验 owner
domain: AgentRunEvent(sealed, 11 变体) / RunContext / TokenUsage / RunFailureCode  (无 AgentScope)
```

`AgentRunService` 在类型层面不引用 AgentScope：它只见 `RunnableAgent`（接口仅暴露
`Flux<AgentRunEvent> / interrupt() / close()`），AgentScope 实现隐藏在 runtime 层包私有类
`HarnessRunnableAgent`。

## P4.2-2. 完成范围

| 子任务 | 交付物 | 状态 |
| --- | --- | --- |
| 内部 event model | `domain/AgentRunEvent.java`（sealed：RunStarted / TextBlock{Started,Delta,Ended} / ReasoningBlock{Started,Delta,Ended} / UsageReported / RunCompleted / RunFailed / RunCancelled）、`TokenUsage`、`RunContext`、`RunFailureCode` | ✅ |
| ModelAdapter | `runtime/ModelAdapter.java`（fake 与真实 provider 共用边界，凭据服务端注入） | ✅ |
| HarnessAgentFactory | `runtime/HarnessAgentFactory.java` + `HarnessRunnableAgent.java`（全 P5+ capability 关闭、移除 `wait_async_results`、关闭 trace log） | ✅ |
| AgentEventMapper | `runtime/AgentEventMapper.java`（ADR-0004 §3.2 映射，sequence 仅计已发射事件） | ✅ |
| CancellationRegistry | `runtime/CancellationRegistry.java`（dispose + interrupt，owner 校验） | ✅ |
| RunContext / AgentRunService 骨架 | `application/{RunRequest,AgentRunService}.java` | ✅ |
| fake-model 单元测试 | 4 个测试类，20 测试 | ✅ |

## P4.2-3. 关键设计决策

1. **隔离边界**：`io.agentscope.*` 只出现在 `agent.runtime`；`domain`/`application` 不引用。未来换
   runtime 只重写 `ModelAdapter` + `AgentEventMapper`，上层与（未来的）encoder/repository 不变。
2. **事件模型**：sealed `AgentRunEvent`（11 个 P4 变体）。tool 相关事件（tool-input/output 等）
   留到 P5 引入真实工具时再加 —— P4 模型边界 tool schema 为空，不产生 tool 事件。
3. **错误显式消费**：`HarnessRunnableAgent.streamEvents()` 末端 `.onErrorResume(error ->
   Flux.just(mapper.failure(error)))` 把任何流错误映射为 `RunFailed`（固定 `safeMessage`，永不透传
   raw）。订阅者只见 `onNext(RunFailed)` + `onComplete`，永不 `onError`；不安装全局
   `Hooks.onErrorDropped`。
4. **取消（ADR-0004 §3.3）**：客户端断开 / 服务端 cancel = **cancel Reactor Subscription**
   （可靠，停 provider token，向上游传播）+ **`agent.interrupt()`**（协作、步边界）。
   `AgentRunService` 在 `doOnSubscribe` 自动绑定 subscription，WebFlux controller 无需也不得手工
   subscribe/bind。`CancellationRegistry` 按 `runId` 原子注册；重复活动 runId 被拒绝，晚于 cancel
   到达的 subscription 会立即 cancel，且仅 owner 可以取消。
5. **最小权限**：所有 P5+ capability `disable*` + `removeTool("wait_async_results")` +
   `enableAgentTracingLog(false)`（AgentScope `AgentTraceMiddleware` 默认 INFO 打印模型输出与异常
   `toString()`，可能泄露 prompt/context/凭据 → 关闭）。测试断言 `lastToolCount()==0`。
6. **无文件系统**：实测 HarnessAgent 2.0.0 在全 disable 下 **不需要 workspace** 即可构建并流式
   （probe 证实 `BUILD_OK_WITHOUT_WORKSPACE`）。P4.2 无任何 per-run FS I/O；测试运行未创建
   `.agentscope/`。
7. **生命周期/线程模型**：返回的 `Flux` 为 single-use，agent 创建与注册通过 `Flux.defer` 延迟到
   订阅时，未订阅不分配资源，重复订阅被拒绝。唯一阻塞的 AgentScope `close()` 经专用 daemon
   executor 执行，不阻塞 Netty event loop。取消后 `RunCancelled` 通过独立 lifecycle observer
   交给 P4.3 持久化；不会尝试向已经取消的 subscriber 继续 `onNext`。

## P4.2-4. 测试命令与结果

```
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -B -ntp spotless:apply clean verify
```

- 全量：`Tests run: 193, Failures: 0, Errors: 0, Skipped: 0`
  （P3 基线 169 + P4.1 spike 4 + **P4.2 新增 20**）。
- P4.2 专项：`./mvnw test -Dtest='AgentEventMapperTest,HarnessAgentFactoryTest,CancellationRegistryTest,AgentRunServiceTest'` → 20/20。
- P4.1 spike 仍 4/4：`FakeStreamModel` 提升到 `agent.testing`（共享测试基础设施），spike 测试
  import 同步更新，**行为不变**（class 体未改逻辑，仅迁包 + 新增可选 `error(Throwable)` builder）。
- 无真实网络、无 API key、无真实 provider。

P4.2 新增测试覆盖：

| 测试类 | 用例 | 覆盖不变量 |
| --- | --- | --- |
| `AgentEventMapperTest` | reasoning+text 序列 / text-only 序列 | ADR-0004 §3.2 精确事件序列、delta 拼接、usage 值、runId/sequence 单调、`lastToolCount==0` |
| `HarnessAgentFactoryTest` | 流式+无工具 / 独立 run / interrupt+close | 最小权限、run-scoped、清理不抛 |
| `CancellationRegistryTest` | owner / 错 owner / 未知 run / 晚绑定 / 重复 runId / unregister | cancel+interrupt、竞态安全、**租户隔离** |
| `AgentRunServiceTest` | 完成 / 脱敏 / cancel / owner / single-use / 重复 run / 取消 observer | 延迟分配、端到端、终态与 cancel 传播 |

## P4.2-5. 安全检查

- **API key**：`ModelAdapter` 是唯一模型边界；`FakeStreamModel`/`FakeModelAdapter` 不读
  `GenerateOptions.getApiKey()`。`RunContext` 不携带任何 secret（凭据在 P4.6 provider adapter 内服务端注入）。
- **错误脱敏**：`RunFailed.message` 恒为 `RunFailureCode.safeMessage()`（固定串）。测试用
  `RuntimeException("leak sk-SECRET-123 raw prompt fragment")` 驱动，断言 event 中**不含**
  `sk-SECRET-123` / `raw prompt`；且 `collectList().block()` 不抛（证明 `onError` 被显式消费）。
- **trace 日志**：`enableAgentTracingLog(false)` 关闭后，专项测试确认 raw 异常文本不再出现在日志
  （grep `sk-SECRET`/`AgentTraceMiddleware` 无命中）。
- **租户隔离**：`CancellationRegistry.cancel` 仅 owner 成功；错 (tenant,user) 返回 false 且不
  dispose / 不 interrupt（6 个 registry 测试覆盖）。
- **取消需 owner**：`AgentRunServiceTest#serverCancelRejectsNonOwner` 覆盖。
- P4.2 未新增 HTTP 端点、未新增 DB 变更、未接触真实 provider、未改任何 P3 行为。

## P4.2-6. MySQL / 真实 provider smoke

- **MySQL contract：本机未执行**（无 Docker，Testcontainers 自动跳过，CI `mysql-contract` 兜底）。
  P4.2 **无任何数据库变更**（无 DDL、无 repository），不产生新 MySQL 风险。
- **真实 provider smoke：未执行**（P4.6 范围；仅在显式环境变量开关下运行）。

## P4.2-7. 回滚

- P4.2 为**纯新增**（`agent` 包）+ `FakeStreamModel` 提升（test-only）。无 DB 迁移、无端点、无配置。
- 回滚 = 删除 `src/main/.../agent/`、`src/test/.../agent/{application,runtime,testing}`、
  恢复 `agent/spike/FakeStreamModel.java`（或保留 testing 版供 spike import）、撤销本报告 P4.2 段。
- 对线上无影响：P4.2 未引入任何 Spring bean 装配或 endpoint，不改变运行时行为。

## P4.2-8. 已知风险（不阻断 P4.3）

1. **默认 workspace 路径**：AgentScope 即使全 disable 仍把 workspace 默认指向 CWD 相对路径
   `.agentscope/workspace`（仅 WARN，不创建、不写）。P4.6+ 若启用 FS/skill 必须显式配置 run-scoped
   workspace（docs/design/harness-agent.md §5）。
2. **真实 provider 错误 redact**：event 层已脱敏、trace log 已关；但 P4.6 的 provider `ModelAdapter`
   仍应在 `stream()` 内捕获 provider 原始异常并 re-throw redacted 版本，确保任何上游 logger 都拿不到
   含 prompt/key 的 raw 文本。
3. **未实现**：idempotency / 重放 / run 状态持久化 / HITL（P4.3/4/6/8）。
4. **reactor 降级**（3.8.2→3.7.19）：本次全量 193 测试通过，无异常。

## P4.2-9. 修改文件

```
src/main/java/io/datastoria/server/agent/domain/AgentRunEvent.java          (新增)
src/main/java/io/datastoria/server/agent/domain/TokenUsage.java            (新增)
src/main/java/io/datastoria/server/agent/domain/RunContext.java            (新增)
src/main/java/io/datastoria/server/agent/domain/RunFailureCode.java        (新增)
src/main/java/io/datastoria/server/agent/runtime/ModelAdapter.java         (新增)
src/main/java/io/datastoria/server/agent/runtime/AgentRuntimeConfig.java   (新增)
src/main/java/io/datastoria/server/agent/runtime/RunnableAgent.java        (新增)
src/main/java/io/datastoria/server/agent/runtime/AgentEventMapper.java     (新增)
src/main/java/io/datastoria/server/agent/runtime/HarnessAgentFactory.java  (新增)
src/main/java/io/datastoria/server/agent/runtime/HarnessRunnableAgent.java (新增)
src/main/java/io/datastoria/server/agent/runtime/CancellationRegistry.java (新增)
src/main/java/io/datastoria/server/agent/application/RunRequest.java       (新增)
src/main/java/io/datastoria/server/agent/application/AgentRunService.java  (新增)
src/test/java/io/datastoria/server/agent/testing/FakeStreamModel.java      (新增，从 spike 提升)
src/test/java/io/datastoria/server/agent/testing/FakeModelAdapter.java     (新增)
src/test/java/io/datastoria/server/agent/runtime/AgentEventMapperTest.java (新增)
src/test/java/io/datastoria/server/agent/runtime/HarnessAgentFactoryTest.java (新增)
src/test/java/io/datastoria/server/agent/runtime/CancellationRegistryTest.java (新增)
src/test/java/io/datastoria/server/agent/application/AgentRunServiceTest.java (新增)
src/test/java/io/datastoria/server/agent/spike/FakeStreamModel.java        (删除，提升到 testing)
src/test/java/io/datastoria/server/agent/spike/AgentScopeSpikeTest.java    (import 更新)
docs/delivery/p4-implementation-report.md                                  (追加 P4.2)
```

## P4.2-10. 下一阶段（P4.3）计划

- `V5__agent_run_and_checkpoint.sql`（SQLite + MySQL 双言）：`ds_agent_run`、`ds_agent_checkpoint`，
  版本号与业务语义一致，由 Flyway 管理。
- `AgentRunRepository` / `AgentCheckpointRepository`（Spring `JdbcClient`，显式 SQL，不阻塞 event
  loop；跨租户测试为必选项）。
- run 状态机：`running / completed / failed / cancelled / waiting_input`。
- 复用 P4.2 的 `AgentRunEvent` 作为持久化与重放事件源；`(runId, sequence)` 单调唯一。
- 验证：默认 SQLite repository contract + MySQL Testcontainers（CI），dialect parity。

**停在 P4.2 review，不自动开始 P4.3。**

---

# P4.3 — Agent Run / Checkpoint 数据模型（已通过 review）

## P4.3-0. 范围

落地 Agent run 生命周期与 checkpoint 的双方言持久化层（`ds_agent_run` / `ds_agent_checkpoint`），
完全遵循 `docs/design/database-data-model.md §8`。**AgentScope 内部状态绝不写入
`ds_chat_message`**——产品消息与 run 状态严格分离，AgentScope state 只以 DataStoria 自有 adapter
产出的 opaque `state_json` 存入 `ds_agent_checkpoint`，repository 不暴露 AgentScope `State` 类型。

**明确非目标（未实现）**：AI SDK encoder（P4.5）、A01 Java chat API（P4.6）、前端 gateway（P4.7）、
端到端验证（P4.8）、真实 AgentScope state 序列化 adapter（P4.4/P4.8）。本阶段的 checkpoint 只存
opaque JSON payload，验证存储/读取/覆盖/顺序语义。**不涉及 P5+ 的 Skill、业务工具、sandbox。**

## P4.3-1. 完成范围

| 子任务 | 交付物 | 状态 |
| --- | --- | --- |
| V5 双方言 migration | `db/migration/{sqlite,mysql}/V5__agent_run_and_checkpoint.sql` | ✅ |
| ds_agent_run | 状态列 + CHECK、`UNIQUE(tenant,user,idempotency_key)`、`UNIQUE(tenant,id)`、tenant/session 索引、FK→session CASCADE | ✅ |
| ds_agent_checkpoint | `UNIQUE(tenant,run,sequence)`、`state_json` json_valid CHECK、FK→run CASCADE | ✅ |
| Domain + 状态机 | `agent.domain`：`AgentRunStatus`（7 态 + 迁移表）、`AgentRun`、`AgentCheckpoint`、`CheckpointType`、`RunTransition`、`IllegalRunTransitionException`（均 AgentScope-free） | ✅ |
| Repository | `AgentRunRepository` / `AgentCheckpointRepository` 接口 + `JdbcAgentRunRepository` / `JdbcAgentCheckpointRepository`（`JdbcClient`，全查询带 `tenant_id`） | ✅ |
| 取消 observer 持久化 | `agent.application.RunCancellationPersister`（`Consumer<RunCancelled>`，复用 P4.2 observer 缝隙，JDBC 在专用 executor 执行不阻塞 Netty） | ✅ |
| 测试 | V5SchemaSmokeTest(8) + SqliteAgentRunRepositoryTest(13) + SqliteAgentCheckpointRepositoryTest(5) + RunCancellationPersisterTest(2) = 28；+ MysqlRepositoryIT 新增 2（MySQL） | ✅ |

## P4.3-2. 关键设计决策

1. **状态集合以 doc §8 为准**：`queued/running/waiting_input/succeeded/failed/cancelled/expired`。
   P4.3 实现 `QUEUED→RUNNING`、`RUNNING→{SUCCEEDED,FAILED,CANCELLED}` 等迁移；`WAITING_INPUT`(HITL) 与
   `EXPIRED`(timeout) 状态已定义，留给 P4.6/P4.8。（用户指令中的 “COMPLETED” 对应 doc 的 `succeeded`，
   P4.2 的 `RunCompleted` 事件映射为持久化 `status=succeeded`。）
2. **终态不可逆**：`AgentRunStatus.canTransitionTo` —— 终态（succeeded/failed/cancelled/expired）自迁移
   幂等成功，迁移到任何**别的**状态均非法；尤其不能回到 `RUNNING`。`IllegalRunTransitionException`
   消息只含 runId + 状态，无 prompt/凭据。
3. **乐观锁避免终态互相覆盖**：`transition` / `applyCancellation` 用
   `UPDATE ... WHERE id AND tenant_id AND revision = :expected`。0 行更新后 **re-read**：若已落在目标态
   视为幂等成功，若落在别的状态则抛 `IllegalRunTransitionException`——并发终态竞争必有一个赢、另一个
   被拒，绝不互相覆盖。测试 `concurrentTerminalTransitionsDoNotOverwrite` 验证（两线程 succeeded vs
   cancelled → 恰好一个落地、revision==1）。
4. **幂等**：`succeeded→succeeded`、`cancelled→cancelled` 为 no-op 成功，不 bump revision（测试覆盖）。
5. **租户边界在 repository**：`find/transition/findByIdempotencyKey/findBySession` 及 checkpoint 全部读写
   都带 `tenant_id`；`applyCancellation`（observer 入口，只有 runId）先按全局唯一 ULID 解析行得到
   tenant，再走 tenant-scoped `UPDATE`。**不依赖 controller 做隔离**（跨租户负例测试覆盖）。
6. **cancel lifecycle observer**：`RunCancellationPersister` 接收 P4.2 `AgentRunService` 在 CANCEL 信号
   发出的 `RunCancelled`，经专用 daemon executor 调 `applyCancellation` 持久化 `cancelled`（JDBC 不阻塞
   event loop）；**不向已取消的 Flux 回推事件**。late cancel 到达已终态 run 为安全 no-op（return false，
   不抛）。AgentRunService 未改动（冻结）。
7. **checkpoint 语义**：按 `(tenant,run,sequence)` **原子 upsert**——同 sequence 覆盖
   `checkpoint_type/state_json/codec_version/checksum`（保留 `created_at`、bump `updated_at`），新 sequence
   追加；latest = max(sequence)。实现采用跨方言 update-then-insert，若并发 INSERT 冲突则重试 UPDATE，
   不用方言专属 `ON CONFLICT`/`ON DUPLICATE KEY`。
8. **checkpoint 隔离边界**：`state_json` 为 opaque 字符串（DataStoria adapter 产出），repository 永不引用
   AgentScope `State`；adapter 必须排除 prompt / API key / provider credential（约束写在接口与 DDL 注释）。
9. **checkpoint 完整性**：双方言 DDL 均约束 `state_json NOT NULL`、`checkpoint_type IN
   ('run_state','pending_action')`；领域对象同步 fail-fast，防止产生不可恢复的空 checkpoint。
10. **无 `@Transactional`**：遵循项目既有约定（main 代码无 `@Transactional`），靠条件 UPDATE + DB
   约束 + 原子 upsert 保证正确性；时间戳统一走 `SqlTimestamps`（SQLite ISO-8601 / MySQL datetime(6)）。

## P4.3-3. 测试命令与结果

```
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -B -ntp spotless:apply clean verify
```

- 全量：`Tests run: 221, Failures: 0, Errors: 0, Skipped: 0`。
- P4.3 新增 SQLite/persister 测试 **28 个**（V5SchemaSmokeTest 8 + SqliteAgentRunRepositoryTest 13 +
  SqliteAgentCheckpointRepositoryTest 5 + RunCancellationPersisterTest 2），专项：
  `./mvnw test -Dtest='V5SchemaSmokeTest,SqliteAgentRunRepositoryTest,SqliteAgentCheckpointRepositoryTest,RunCancellationPersisterTest'` → 28/28。
- P4.1/P4.2 全部测试仍通过（生命周期未改动）。
- 无真实网络、无 API key、无真实 provider。

P4.3 测试覆盖矩阵：

| 测试 | 不变量 |
| --- | --- |
| V5SchemaSmokeTest | status CHECK、idempotency 唯一、checkpoint sequence 唯一、`json_valid`、非空 state/type CHECK、FK CASCADE（删 run→checkpoint、删 session→run+checkpoint） |
| SqliteAgentRunRepositoryTest | create/find 往返、**租户隔离**、idempotency-key 跨租户/用户、session 排序、**合法迁移**、**非法终态迁移被拒**、**终态幂等**、NotFound/cross-tenant、**并发终态不互相覆盖**、observer cancel、late-cancel 不复活终态 |
| SqliteAgentCheckpointRepositoryTest | append、**并发同 sequence 原子收敛**、overwrite 保留 created_at、latest=max(seq)、**租户隔离** |
| RunCancellationPersisterTest | observer→cancelled、幂等、未知 run 安全 |

## P4.3-4. 安全检查

- **租户隔离**：所有 repository 读写带 `tenant_id`；跨租户 `find`/`transition`/`findByIdempotencyKey`/
  checkpoint 读均返回空或抛 NotFound（测试覆盖）。`applyCancellation` 解析 tenant 后走 tenant-scoped UPDATE。
- **prompt / key / credential 不入库**：`ds_agent_run` 只存 `input_snapshot_json`（pin 的 revision id 等非敏感
  元数据）、`usage_json`（token 计数）、`error_code`（`RunFailureCode` 名）+ 固定 `safe_message`；
  `ds_agent_checkpoint.state_json` 由 DataStoria adapter 产出且约束排除敏感数据。错误文本以
  `RunFailureCode.safeMessage()` 固定串持久化，永不透传 raw provider 异常。
- **AgentScope 隔离边界**：`agent.domain` / `repository` / `repository.jdbc` 均不引用 `io.agentscope.*`；
  AgentScope 类型仍只在 `agent.runtime`。
- **JDBC 不阻塞 Netty**：observer 的 JDBC 写在专用 daemon executor；repository 为命令式 JDBC（由 P4.6
  controller 在 bounded scheduler 调度，不在 event loop）。
- P4.3 未新增 HTTP 端点、未改任何 P3/P4.2 运行时行为、未改 `AgentRunService`（冻结）。

## P4.3-5. MySQL / dialect parity 执行情况

- **本机未执行 MySQL**：`docker info` 不可用 → `SchemaParityTest`（@BeforeAll `Assumptions.abort`，Tests run: 0）
  与 `MysqlRepositoryIT`（fallback SQLite + `assumeTrue` 跳过）均未实际跑。与 P3/P4.1/P4.2 一致。
- V5 已同时提供 SQLite + MySQL 两份 migration，列名 / PK / FK / 唯一键一致（`SchemaParityTest` 在 CI
  Docker 环境自动覆盖 `ds_agent_run` / `ds_agent_checkpoint`）；`MysqlRepositoryIT` 新增 2 个用例
  （run 状态机 + checkpoint upsert）由 CI `mysql-contract` 兜底。
- 真实 provider smoke：未执行（P4.6 范围）。

## P4.3-6. 回滚

- P4.3 为**纯新增**：2 份 V5 migration + `agent.domain` 新类型 + 2 个 repository 接口/实现 +
  `RunCancellationPersister` + 测试 + `TestDbHelper` 增 2 表。无 P4.2 运行时改动、无端点、无配置。
- 回滚 = 删除上述文件 + 撤销 `MysqlRepositoryIT`/`TestDbHelper` 的新增行 + 撤销报告 P4.3 段。Flyway V5
  回滚采用备份恢复/前向修复（不写破坏性 down migration，符合 doc §10）。
- 对线上无影响：未引入 Spring bean 装配以外的运行时行为变化（repository/persister 为新增 `@Repository`/普通类）。

## P4.3-7. 已知风险（不阻断 P4.4）

1. **乐观锁 0-rows 重检路径**：本地 SQLite 单连接（test `maximum-pool-size=1`）下并发测试为串行化执行，
   验证的是“终态不互相覆盖”的不变量；真正的多连接并发竞争（条件 UPDATE 返回 0 行后 re-read）由 CI
   `MysqlRepositoryIT`（多连接 Testcontainer）覆盖。本地未跑 MySQL（见 §5）。
2. **run→session / checkpoint→run CASCADE 删除**：删 session 会级联删 run+checkpoint。若将来需要 run 审计
   保留，需调整 FK 策略或加 retention（doc §8 提及 TTL，留给后续）。
3. **idempotency_key 可空**：`UNIQUE(tenant,user,idempotency_key)` 允许多个 NULL（无 key 的 run 不去重）；
   幂等去重由 P4.6 controller 用 `findByIdempotencyKey` lookup-then-create 实现。
4. **AgentScope state adapter 未实现**：checkpoint 当前存 opaque JSON；真实 AgentScope `State` ↔ `state_json`
   的序列化 adapter 在 P4.4/P4.8，届时须保证不含 prompt/key。
5. **`applyCancellation` 按 runId 解析 tenant**：runId 为全局唯一 ULID，且该入口仅服务端 observer 调用
   （非客户端请求），无租户混淆面；客户端 cancel 端点（P4.6）将额外 tenant-scoped 校验。

## P4.3-8. 修改文件

```
src/main/resources/db/migration/sqlite/V5__agent_run_and_checkpoint.sql          (新增)
src/main/resources/db/migration/mysql/V5__agent_run_and_checkpoint.sql           (新增)
src/main/java/io/datastoria/server/agent/domain/AgentRunStatus.java             (新增，状态机)
src/main/java/io/datastoria/server/agent/domain/AgentRun.java                   (新增)
src/main/java/io/datastoria/server/agent/domain/AgentCheckpoint.java            (新增)
src/main/java/io/datastoria/server/agent/domain/CheckpointType.java             (新增)
src/main/java/io/datastoria/server/agent/domain/RunTransition.java              (新增)
src/main/java/io/datastoria/server/agent/domain/IllegalRunTransitionException.java (新增)
src/main/java/io/datastoria/server/repository/AgentRunRepository.java           (新增)
src/main/java/io/datastoria/server/repository/AgentCheckpointRepository.java    (新增)
src/main/java/io/datastoria/server/repository/jdbc/JdbcAgentRunRepository.java  (新增)
src/main/java/io/datastoria/server/repository/jdbc/JdbcAgentCheckpointRepository.java (新增)
src/main/java/io/datastoria/server/agent/application/RunCancellationPersister.java (新增)
src/test/java/io/datastoria/server/repository/V5SchemaSmokeTest.java            (新增)
src/test/java/io/datastoria/server/repository/SqliteAgentRunRepositoryTest.java (新增)
src/test/java/io/datastoria/server/repository/SqliteAgentCheckpointRepositoryTest.java (新增)
src/test/java/io/datastoria/server/agent/application/RunCancellationPersisterTest.java (新增)
src/test/java/io/datastoria/server/MysqlRepositoryIT.java                        (新增 run/checkpoint 用例)
src/test/java/io/datastoria/server/TestDbHelper.java                            (增 ds_agent_checkpoint / ds_agent_run)
docs/delivery/p4-implementation-report.md                                       (追加 P4.3)
```

## P4.3-9. 下一阶段（P4.4）计划

- run/checkpoint 与 `AgentRunService`/事件流的持久化接线（run 创建于请求、completed/failed 落库、
  RunCancelled 经 observer 落库）——属 P4.6 chat endpoint 范畴，P4.4 视 review 决定是否先做状态机
  service 封装。
- 真实 AgentScope `State` ↔ `state_json` 的 DataStoria adapter（序列化、checksum、codec_version）。
- run 事件持久化（`ds_agent_event`，doc §8 可选表）用于断线续传 / `Last-Event-ID` 重放。

**P4.3 review 已通过；不自动开始 P4.4。**

---

# P4.4 — Checkpoint state adapter（已通过 review）

## P4.4-0. 范围

实现 DataStoria 自有的 Agent checkpoint state adapter：把 AgentScope `State` 与持久化的
`state_json` 隔离开，定义稳定的 `codec_version` / checksum，支持编码、解码、版本拒绝、checksum
不匹配检测，并保证序列化结果不含 prompt / API key / provider credential。接入 P4.3 的
`AgentCheckpointRepository`，但**不实现 HTTP chat endpoint**（P4.6）。

**明确非目标（未实现）**：AI SDK encoder（P4.5）、A01 Java chat API（P4.6）、前端 gateway（P4.7）、
端到端 run resume（P4.8）。P4.4 的 checkpoint 只持久化 agent 的安全控制字段，**不含对话 context**
（prompt）；resume 时由 `ds_chat_message` 重建消息（P4.8）。不涉及 P5+ Skill / 工具状态。

## P4.4-1. 分层

```text
agent.domain (AgentScope-free):
  CheckpointContent(codecVersion, stateJson, checksum)
  CheckpointState(sessionId, userId, replyId, currentIteration, shutdownInterrupted)
  CheckpointCodec interface + CURRENT_VERSION="ds-checkpoint-v1"
  UnsupportedCodecVersionException / ChecksumMismatchException
agent.runtime (AgentScope 只能在此):
  JsonCheckpointCodec        — closed-schema canonical JSON + SHA-256（AgentScope-free，用 Jackson）
  CheckpointStateAdapter     — State <-> CheckpointContent 接口（引用 io.agentscope State）
  AgentScopeCheckpointAdapter— 仅抽取 AgentState 的安全标量，排除 context（prompt）
agent.application (AgentScope-free):
  CheckpointStore            — CheckpointContent -> AgentCheckpointRepository 写入 / 读回
```

`domain` / `application` / `repository` 均不引用 `io.agentscope.*`；AgentScope `State` 仅出现在
`agent.runtime` 的 adapter（满足 ADR-0004 §7 隔离边界）。

## P4.4-2. 完成范围

| 子任务 | 交付物 | 状态 |
| --- | --- | --- |
| Codec domain | `CheckpointContent`、`CheckpointState`、`CheckpointCodec`(+`CURRENT_VERSION`)、两个异常 | ✅ |
| Codec 实现 | `JsonCheckpointCodec`：closed-schema canonical JSON、SHA-256 checksum、版本拒绝、checksum 校验 | ✅ |
| AgentScope 隔离 adapter | `CheckpointStateAdapter` 接口 + `AgentScopeCheckpointAdapter`（AgentState 安全标量 ↔ content，排除 context） | ✅ |
| 接入 repository | `CheckpointStore`（application，content ↔ `AgentCheckpointRepository`，tenant-scoped，复用 P4.3 原子 upsert） | ✅ |
| 测试 | 17 个：codec 9 + adapter 3 + DB wiring 4 + V5 checksum constraint 1 | ✅ |

## P4.4-3. 关键设计决策

1. **隔离边界**：AgentScope `State`/`AgentState` 只在 `agent.runtime.AgentScopeCheckpointAdapter`；
   `CheckpointContent`/`CheckpointState` 是 DataStoria 自有 domain 类型，repository 与 controller 不感知
   AgentScope。换 runtime 只重写 adapter。
2. **codec_version**：`"ds-checkpoint-v1"`（`CheckpointCodec.CURRENT_VERSION`）。decode 遇未知版本抛
   `UnsupportedCodecVersionException`，绝不静默误解析旧/不兼容 blob。
3. **checksum**：SHA-256 over `"<codecVersion>\n<canonicalStateJson>"`（hex，64 字符），绑定 payload 与
   codec 版本。**canonical = 经同一 ObjectMapper 的 record 声明序 + `ORDER_MAP_ENTRIES_BY_KEYS` 排序的
   Map 键**，故即使 MySQL `json` 列读回时重排格式，decode 重新 canonicalize 后 checksum 仍一致（dialect
   无关）。decode 重算 checksum 与存储值不等 → `ChecksumMismatchException`（篡改/损坏）。
4. **prompt / 凭据结构性排除（核心安全属性）**：`CheckpointState` 是闭合 schema，只承载
   session/user/reply id、迭代计数与 shutdown 标志。`context`、`summary`、任意 metadata 均为自由文本或
   容器，都可能携带 prompt、工具输出或凭据，因此全部不进入 v1 schema；不依赖不完备的关键词脱敏。
   测试同时把 secret 放进 context 与 summary，断言落库 JSON 均不含。
5. **restore 不重建 context/summary**：`restore()` 只回填安全标量，context 与 summary 留空；run
   resume（P4.8）从 `ds_chat_message` 重建消息后再调用模型。若未来需要持久化 compaction 摘要，必须
   另行设计可验证的脱敏 schema 并升级 codec version，不能塞回 v1。
6. **checksum 强约束**：checksum 为 64 位小写 SHA-256 hex；`CheckpointContent`、`AgentCheckpoint` 及
   SQLite/MySQL V5 DDL 三层均 fail-fast/拒绝 NULL 或非法格式。比较使用 `MessageDigest.isEqual`。
7. **接入 repository 不改 P4.3 语义**：`CheckpointStore.save` 复用 P4.3 race-safe upsert（同 sequence
   覆盖、新 sequence 追加）；`loadLatest` tenant-scoped。未新增端点、未改 `AgentRunService`（冻结）。

## P4.4-4. 测试命令与结果

```
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -B -ntp spotless:apply clean verify
./mvnw -o test
```

- 全量：`Tests run: 238, Failures: 0, Errors: 0, Skipped: 0`。
- P4.4 新增 16 个 codec/adapter/store 测试：`JsonCheckpointCodecTest`(9) +
  `AgentScopeCheckpointAdapterTest`(3) + `CheckpointStoreTest`(4)，另有 V5 checksum 约束回归 1 个。专项：
  `./mvnw test -Dtest='JsonCheckpointCodecTest,AgentScopeCheckpointAdapterTest,CheckpointStoreTest'` → 16/16。
- P4.1/P4.2/P4.3 全部仍通过。
- 无真实网络、无 API key、无真实 provider。

P4.4 测试覆盖矩阵：

| 测试 | 不变量 |
| --- | --- |
| JsonCheckpointCodecTest | 正常往返、确定性 canonical、版本拒绝、**stateJson 篡改→ChecksumMismatch**、**checksum 篡改→ChecksumMismatch**、checksum 格式拒绝、闭合 schema 无自由文本字段 |
| AgentScopeCheckpointAdapterTest | **源 context 与 summary 均含 prompt/key 但序列化排除**、restore 安全标量往返且 context/summary 为空、未知 State 类型拒绝 |
| CheckpointStoreTest | 经 repository 往返（stateJson/checksum/codecVersion 一致）、**持久化 blob 不含 prompt/secret**、同 sequence 覆盖、**跨租户 loadLatest 为空**、restore 重建安全状态 |

## P4.4-5. 安全检查

- **prompt / API key / credential 不入 checkpoint**：`CheckpointState` 无 context、summary 或 metadata；
  adapter 不读取这些自由内容。源 state 的 context 与 summary 均含 secret/prompt 时，落库
  `state_json` 不含（测试覆盖）。
- **完整性**：checksum SHA-256 绑定 codec_version + canonical payload；篡改 stateJson 或 checksum 均
  被 `ChecksumMismatchException` 拒绝（2 个测试）。
- **版本安全**：未知 `codec_version` 抛 `UnsupportedCodecVersionException`，不静默解析。
- **隔离边界**：`agent.domain` / `agent.application` / `repository` 不引用 `io.agentscope.*`（编译期保证）。
- **租户隔离**：`CheckpointStore.loadLatest` 走 tenant-scoped repository；跨租户返回空（测试覆盖）。
- **JDBC 不阻塞 Netty**：`CheckpointStore` 为命令式 JDBC，由 P4.6 controller 在 bounded scheduler 调度；
  checkpoint 写非 hot path。
- 未新增 HTTP 端点、未改 P4.2/P4.3 运行时行为；review 收紧 V5 checksum 为双方言 NOT NULL + 格式 CHECK。

## P4.4-6. MySQL / dialect 兼容

- **本机未执行 MySQL**：`docker info` 不可用 → `SchemaParityTest`（Tests run: 0，@BeforeAll abort）、
  `MysqlRepositoryIT`（fallback + assumeTrue 跳过）。与 P3/P4.1–P4.3 一致，不声称 MySQL 通过。
- 双方言兼容设计：checksum 基于 canonical form（decode 重新 canonicalize），吸收 MySQL `json` 列读回时
  的格式重排；`state_json` 在 SQLite 为 TEXT、MySQL 为 `json`，均由 P4.3 V5 migration 承载。CI
  `mysql-contract` + `SchemaParityTest`（Docker）兜底。

## P4.4-7. 回滚

- P4.4 新增 5 个 domain 类 + 2 个 runtime 类（codec impl + adapter）+ adapter 接口 +
  `CheckpointStore` + 3 个测试，并在尚未发布的 V5 中收紧 checksum 约束。无端点。
- 回滚 = 删除上述新增文件、撤销报告 P4.4 段，并恢复 V5 checksum nullable 定义；无已发布数据迁移。
- 对线上无影响：`CheckpointStore` 虽为 `@Component`，但当前无调用方（P4.6 chat 流程才接入）。

## P4.4-8. 已知风险（不阻断 P4.5）

1. **resume 未实现**：P4.4 的 restore 只回填控制标量，context 留空；真正 run resume（重建消息、恢复
   AgentScope 运行态）在 P4.8。
2. **permission/tool/task context 不持久化**：P4 minimal checkpoint 只存控制标量；HITL pending action、
   工具上下文等留给后续阶段（`checkpoint_type='pending_action'` 已在 V5 预留）。
3. **checksum 基于 canonical 而非原始字节**：纯空白/键序差异（语义等价）不会被判定篡改；语义变更必被
   检出（设计如此，避免 MySQL 重排误报）。
4. **MySQL 本机未验证**（见 §6），CI 兜底。

## P4.4-9. 修改文件

```
src/main/java/io/datastoria/server/agent/domain/CheckpointContent.java              (新增)
src/main/java/io/datastoria/server/agent/domain/CheckpointState.java                (新增)
src/main/java/io/datastoria/server/agent/domain/CheckpointCodec.java                (新增)
src/main/java/io/datastoria/server/agent/domain/UnsupportedCodecVersionException.java (新增)
src/main/java/io/datastoria/server/agent/domain/ChecksumMismatchException.java      (新增)
src/main/java/io/datastoria/server/agent/runtime/JsonCheckpointCodec.java           (新增)
src/main/java/io/datastoria/server/agent/runtime/CheckpointStateAdapter.java        (新增)
src/main/java/io/datastoria/server/agent/runtime/AgentScopeCheckpointAdapter.java   (新增)
src/main/java/io/datastoria/server/agent/application/CheckpointStore.java           (新增)
src/main/resources/db/migration/sqlite/V5__agent_run_and_checkpoint.sql             (收紧 checksum)
src/main/resources/db/migration/mysql/V5__agent_run_and_checkpoint.sql              (收紧 checksum)
src/test/java/io/datastoria/server/agent/runtime/JsonCheckpointCodecTest.java       (新增)
src/test/java/io/datastoria/server/agent/runtime/AgentScopeCheckpointAdapterTest.java (新增)
src/test/java/io/datastoria/server/agent/application/CheckpointStoreTest.java       (新增)
src/test/java/io/datastoria/server/repository/V5SchemaSmokeTest.java                 (checksum 约束)
src/test/java/io/datastoria/server/repository/SqliteAgentCheckpointRepositoryTest.java (合法 checksum)
src/test/java/io/datastoria/server/MysqlRepositoryIT.java                            (合法 checksum)
docs/delivery/p4-implementation-report.md                                           (追加 P4.4)
```

## P4.4-10. 下一阶段（P4.5）计划

- 捕获 Node A01 golden stream（`docs/api/stream-protocol.md` fixture），实现
  `AiSdkStreamEncoder`：把内部 `AgentRunEvent` 编码为 AI SDK UI Message Stream 字节。
- 与 P4.2 事件模型对齐：`RunStarted`→`start`/`start-step`、`TextDelta`→`text-delta`、
  `ReasoningDelta`→`reasoning-delta`、`UsageReported`→`finish` 的 messageMetadata.usage、
  `RunFailed`→`error`、`RunCancelled`→`abort`。
- 语义 diff 规则按 stream-protocol §6；先纯文本/reasoning/usage/error/cancel fixture。

**P4.4 review 已通过；不自动开始 P4.5。**

---

# P4.5 — AI SDK UI Message Stream Encoder（本次交付，待 review）

## P4.5-0. 范围

实现 AgentScope-free 的 `AiSdkStreamEncoder`，把 P4.2 的 `AgentRunEvent` **增量**编码为当前前端
`@ai-sdk/react` 可消费的 AI SDK v6 UI Message Stream 字节帧。**不新增 Java chat HTTP endpoint、不接真实
provider、不改前端 gateway**（P4.6/P4.7/P4.8）。encoder/controller/测试均不引用 `io.agentscope.*`。

## P4.5-1. golden 来源

- **事实依据**：`frontend/src/app/api/ai/agent/route.ts`（`result.toUIMessageStream(...)` →
  `createUIMessageStreamResponse(...)`）+ `frontend/src/lib/ai/token-usage-utils.ts`（`normalizeUsage`/
  `sumTokenUsage`）。
- **fixture**：`docs/fixtures/stream/*.jsonl` + `schema.json`（chunk JSON Schema）。这些 fixture 是
  **手工构造的规范样本**（`MANIFEST.md` 标注真实字节捕获 TBD）。P4.5 encoder 对齐它们的 **事件类型序列**，
  并以 **实际前端行为** 为准处理字段差异（见 §4 差异记录）。

## P4.5-2. 分层与组件

```text
agent.application.AiSdkStreamEncoder   (AgentScope-free)
  encode(AgentRunEvent) -> List<"data: {json}\n\n">   (增量，按事件)
  encode(Flux<AgentRunEvent>) -> Flux<String>          (concatMap 逐事件 + [DONE])
  done() -> "data: [DONE]\n\n"
```

## P4.5-3. 事件 → 帧映射（冻结规则）

| AgentRunEvent | AI SDK chunk | 备注 |
| --- | --- | --- |
| RunStarted | `start`{messageId} + `start-step` | 一次 run 一次 start+step |
| ReasoningBlockStarted/Delta/Ended | `reasoning-start`/`-delta`/`-end` | 同 block 共享一个 part id（`rsn-<n>`） |
| TextBlockStarted/Delta/Ended | `text-start`/`-delta`/`-end` | 同 block 共享 part id（`txt-<n>`） |
| UsageReported | （缓冲，不立即出帧） | usage 进 `finish` 的 messageMetadata |
| RunCompleted | `finish-step` + `finish`{finishReason="stop", messageMetadata.usage} | |
| RunFailed | `error`{errorText = 固定 safe message} | 永不透传 provider/prompt/credential |
| RunCancelled | `abort`{reason="client_disconnect"} | 见 §5 取消语义 |
| 终止 | `data: [DONE]\n\n` | 流始终以此终止 |

- **帧格式**：`"data: " + 紧凑 JSON + "\n\n"`；JSON 由 Jackson 序列化（正确转义 `"` `\` `\n` `\t` 与控制字符）。
- **part id**：`txt-<n>` / `rsn-<n>`，block 内 start/delta/end 共享；值不参与语义 diff（协议 §6 忽略）。
- **增量**：`encode(event)` 只返回当前事件的帧，无 lookahead；`encode(Flux)` 用 `concatMap` 逐事件出帧，
  最后 `concatWith([DONE])`。UsageReported 的 usage 先缓冲、在 RunCompleted 的 `finish` 帧上携带（跨事件状态）。

## P4.5-4. 关键差异记录（不静默选择）

**`docs/fixtures/stream` 的 usage 字段名与实际 Node 行为不一致**：

- fixture（`text-only.jsonl`/`reasoning.jsonl`/`usage-title.jsonl`）用 **已废弃** 的 `promptTokens`/
  `completionTokens`/`totalTokens`。
- 实际 Node A01（`token-usage-utils.ts` 的 `sumTokenUsage` 返回 AI SDK v6 `LanguageModelUsage`）输出
  **`inputTokens`/`outputTokens`/`totalTokens` + `inputTokenDetails{...}` + `outputTokenDetails{...}`**。
- 前端 `@ai-sdk/react` 消费的是 `inputTokens`/`outputTokens`。若 encoder 输出 `promptTokens`，前端无法识别。
- **处理**：encoder 按 **实际前端行为** 输出 `inputTokens`/`outputTokens`/`totalTokens`（+ details）。
  `goldenFixtureUsesDeprecatedUsageNaming` 测试显式断言 fixture 用 `promptTokens` 而 encoder 用 `inputTokens`，
  锁定该差异。建议后续 contract runner 抓取真实字节后刷新 fixture（`MANIFEST.md` 已标注 TBD）。
- usage 数值：`TokenUsage(inputTokens, outputTokens, cachedTokens, ...)` → `inputTokens`/`outputTokens`
  直映，`cacheReadTokens=cachedTokens`，`noCacheTokens=max(0, input-cached)`，`totalTokens=input+output`。

## P4.5-5. 取消语义（满足冻结约束）

- `RunCancelled → abort{reason="client_disconnect"}`（匹配 `cancel.jsonl`）。
- **不向已取消订阅强行写帧**：encoder 只在 `RunCancelled` **到达它时** 才产出 abort 帧。客户端断开时，
  `AgentRunService` 的 Flux 已 dispose，encoder 的订阅终止、不再收到事件，故不会向已断开的客户端写帧
  （reactive dispose 自然丢弃下游）。`RunCancelled` 经独立 cancellation observer 落库（P4.3），
  不依赖 encoder。encoder 不实现 force-write 逻辑。

## P4.5-6. 测试命令与结果

```
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -B -ntp spotless:apply clean verify
```

- 全量：`Tests run: 250, Failures: 0, Errors: 0, Skipped: 0`。
- P4.5 专项（`AiSdkStreamEncoderTest`，12 用例）：
  `./mvnw test -Dtest=AiSdkStreamEncoderTest` → 12/12。
- P4.1–P4.4 全部仍通过；未改任何前端文件、未改 `AgentRunService`/事件模型（冻结）。
- 无真实网络、无 API key、无真实 provider。

P4.5 测试覆盖：

| 测试 | 不变量 |
| --- | --- |
| textOnly / reasoning 场景 | 精确帧序列 + 与 golden fixture 的 **type 序列一致**（语义 diff） |
| reasoningPartIdSharedWithinBlock | block 内 start/delta/end 共享同一 id |
| usageEmittedOnFinish | `finish.messageMetadata.usage` 为 AI SDK v6 `LanguageModelUsage` 形状（inputTokens/outputTokens/totalTokens/details） |
| goldenFixtureUsesDeprecatedUsageNaming | **记录 fixture 的 promptTokens vs encoder 的 inputTokens 差异** |
| error scenario | `start→start-step→error`（无 finish），errorText 为固定 safe 文本、不含 `sk-`/`apiKey` |
| cancel scenario | `start→...→abort{client_disconnect}`，与 `cancel.jsonl` 一致 |
| jsonSpecialCharactersEscaped | `"` `\` `\n` `\t` 经 Jackson 正确转义并可往返 |
| eachTextDeltaItsOwnFrame | 增量：每事件只出自身帧、无 lookahead；usage 缓冲后在 finish 携带 |
| encodeFlux + done | reactive `encode(Flux)` 逐事件出帧并以 `[DONE]` 终止 |

## P4.5-7. 安全检查

- **AgentScope 隔离**：encoder/controller/测试不引用 `io.agentscope.*`（仅消费 `AgentRunEvent`）。
- **错误脱敏**：`error` 帧的 `errorText` 取自 `RunFailed.message`（P4.2 已固定 safeMessage）；测试断言不含
  `sk-`/`apiKey`。encoder 不接触 provider 原始异常/prompt/credential。
- **取消安全**：不向已取消订阅写帧（§5）。
- 未新增端点、未改 DDL、未改前端、未改 P4.2–P4.4 运行时行为。

## P4.5-8. MySQL / 前端测试

- **MySQL 本机未执行**（无 Docker；`SchemaParityTest` Tests run: 0）。P4.5 无 DB 变更，不产生 MySQL 风险。
- **前端**：P4.5 **未修改任何前端文件**（fixture 只读引用），故未运行前端测试。usage 字段名差异已记录，
  待 contract runner 抓取真实字节后刷新 fixture。

## P4.5-9. 回滚

- 纯新增：`AiSdkStreamEncoder` + 其测试。无 DDL、无端点、无 bean 装配（encoder 为普通类，P4.6 才接入）。
- 回滚 = 删除这两个文件 + 撤销报告 P4.5 段。无数据迁移、无前端影响。

## P4.5-10. 已知风险（不阻断 P4.6）

1. **fixture 为手工样本，非真实捕获**：`MANIFEST.md` 标注真实 SSE 字节捕获 TBD。P4.5 对齐了 type 序列与
   usage 实际形状；待 contract runner（受控 provider 环境）抓字节后做最终 byte-for-byte 校验（仅协议要求的
   `[DONE]`/`type` 顺序/`errorText` 存在性等做 byte 断言，其余语义 diff）。
2. **`data:` vs `data: `（空格）**：protocol doc §3 写 `data: {json}`（带空格），AI SDK 实际可能用 `data:`。
   encoder 按 doc 用 `data: `（带空格，标准 SSE）。语义 diff 解析 JSON 前剥离前缀，不受影响；真实字节
   校验时确认。
3. **title 注入未实现**：`usage-title.jsonl` 的 `messageMetadata.title` 由 Node route 的 TransformStream 注入
   （独立 `SessionTitleGenerator`）；P4.5 encoder 无 title 事件源，`finish` 只带 usage。title 注入留 P4.6。
4. **tool 事件未覆盖**：P4 model-boundary tool schema 为 0，不产生 tool 事件（tool-success/tool-error fixture
   的 type 序列不在 P4.5 范围，留 P5+ 工具阶段）。
5. **part id 格式**：`txt-<n>`/`rsn-<n>` 为 DataStoria 生成，值被语义 diff 忽略；AI SDK 实际 id 格式不同但等价。

## P4.5-11. 修改文件

```
src/main/java/io/datastoria/server/agent/application/AiSdkStreamEncoder.java          (新增)
src/test/java/io/datastoria/server/agent/application/AiSdkStreamEncoderTest.java      (新增)
docs/delivery/p4-implementation-report.md                                             (追加 P4.5)
```

## P4.5-12. 下一阶段（P4.6）计划

- A01 Java chat endpoint：`POST /api/ai/agent`，凭据服务端注入、`client_request_id` 幂等、JDBC 不阻塞 event loop。
- 接线：`AgentRunService.start(...)` 的 `Flux<AgentRunEvent>` → `AiSdkStreamEncoder.encode(...)` → SSE 响应；
  保留 P4.2 single-use/deferred/自动 binding；`X-Vercel-AI-UI-Message-Stream: v1` 等响应头按 stream-protocol §2。
- run 创建于请求、completed/failed 落库（P4.3）、RunCancelled 经 observer 落库；`finish` 注入 title（独立 service）。

**停在 P4.5 review，不自动开始 P4.6。**

