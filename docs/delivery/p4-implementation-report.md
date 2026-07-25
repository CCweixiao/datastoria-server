# P4 实施报告 — AgentScope Java 最小 Harness

> Stage: P4（AgentScope 最小 Harness）
> 本次交付：**P4.1–P4.8（均已通过 review）**
> 分支：`codex/backend-only-migration`
> 基线 master：`a540e8b`
> 状态：P4.1–P4.8 已实现并完成本地 review；可在开始 P5 前进行前后端联调。

---

# P4.1 — AgentScope 兼容性 Spike（已通过 review，冻结）

## 0. 范围说明（P4.1）

按指令“只交付 P4.1”，该阶段只完成 **P4.1：AgentScope 兼容性 Spike**，并产出第一轮验收物：

- AgentScope Java 版本兼容性 ADR（`docs/adr/0004-agentscope-java-baseline.md`）
- Maven 依赖锁定（`pom.xml`）
- fake model spike（`FakeStreamModel`）
- 流事件与取消测试（`AgentScopeSpikeTest`）
- 明确的阻断项（见 §8）

**历史切片说明**：内部 Harness 模型、Run/Checkpoint DDL 与 repository、AI SDK encoder、
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

# P4.5 — AI SDK UI Message Stream Encoder（已通过 review）

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
| UsageReported | （累加，不立即出帧） | 多次模型调用 usage 求和后进入 `finish` 的 messageMetadata |
| RunCompleted | `finish-step` + `finish`{finishReason="stop", messageMetadata.usage} | |
| RunFailed | `error`{errorText = code 对应固定 safe message} | 不信任 event.message，未知 code 降级 AGENT_INTERNAL |
| RunCancelled | `abort`{reason="client_disconnect"} | 见 §5 取消语义 |
| 终止 | `data: [DONE]\n\n` | 流始终以此终止 |

- **帧格式**：`"data: " + 紧凑 JSON + "\n\n"`；JSON 由 encoder 私有 copy 的 Jackson mapper
  序列化并强制关闭 pretty-print（正确转义 `"` `\` `\n` `\t` 与控制字符，不修改共享 mapper）。
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
- usage 数值：每个 `TokenUsage(inputTokens, outputTokens, cachedTokens, ...)` 累加，匹配 Node
  `sumTokenUsage`；`cacheReadTokens=ΣcachedTokens`，`noCacheTokens=max(0, Σinput-Σcached)`，
  `totalTokens=Σinput+Σoutput`。内部以 long 累加，避免多 step 的 int 溢出。
- error fixture 只参与 type 序列 diff。Java `errorText` 始终由 `RunFailureCode` 重新推导，不信任
  `RunFailed.message`；未知 code 固定降级 `"The agent run failed. Please retry."`。

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

- 全量：`Tests run: 253, Failures: 0, Errors: 0, Skipped: 0`。
- P4.5 专项（`AiSdkStreamEncoderTest`，15 用例）：
  `./mvnw test -Dtest=AiSdkStreamEncoderTest` → 15/15。
- P4.1–P4.4 全部仍通过；未改任何前端文件、未改 `AgentRunService`/事件模型（冻结）。
- 无真实网络、无 API key、无真实 provider。

P4.5 测试覆盖：

| 测试 | 不变量 |
| --- | --- |
| textOnly / reasoning 场景 | 精确帧序列 + 与 golden fixture 的 **type 序列一致**（语义 diff） |
| reasoningPartIdSharedWithinBlock | block 内 start/delta/end 共享同一 id |
| usageEmittedOnFinish / usageIsAccumulatedAcrossModelCalls | `finish.messageMetadata.usage` 为 AI SDK v6 shape；多次模型调用按 Node `sumTokenUsage` 累加 |
| goldenFixtureUsesDeprecatedUsageNaming | **记录 fixture 的 promptTokens vs encoder 的 inputTokens 差异** |
| error scenario / malicious event text | `start→start-step→error`（无 finish）；仅按 code 选择固定 safe 文本，恶意 event.message 被忽略 |
| cancel scenario | `start→...→abort{client_disconnect}`，与 `cancel.jsonl` 一致 |
| jsonSpecialCharactersEscaped / pretty mapper | 特殊字符正确转义；外部 mapper 即使启用缩进也无法制造多行 SSE 或被 encoder 修改 |
| eachTextDeltaItsOwnFrame | 增量：每事件只出自身帧、无 lookahead；usage 缓冲后在 finish 携带 |
| encodeFlux + done | reactive `encode(Flux)` 逐事件出帧并以 `[DONE]` 终止 |

## P4.5-7. 安全检查

- **AgentScope 隔离**：encoder/controller/测试不引用 `io.agentscope.*`（仅消费 `AgentRunEvent`）。
- **错误脱敏（纵深防御）**：`error` 帧不使用 `RunFailed.message`，而是重新按 `RunFailureCode` 选择固定
  safe message；未知/非法 code 降级 `AGENT_INTERNAL`。即便上游误构造含 key/prompt 的 event 也不会泄露。
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

**P4.5 review 已通过；不自动开始 P4.6。**

---

# P4.6 — A01 Java Chat API（review 已通过）

## P4.6-0. 范围

实现与 Node A01 兼容的 Java `POST /api/ai/agent`：服务端解析 tenant/user/session/agent/model，
创建/复用 `AgentRun`，`AgentRunService.start` → `AiSdkStreamEncoder` → AI SDK UI Message Stream SSE。
**不接真实 provider（fake model 测试）、不改前端 gateway（P4.7）、不做 run resume（P4.8）。**

## P4.6-1. 请求 / 响应契约

**请求**（`POST /api/ai/agent`，compat 家族，`@RequestBody JsonNode` 手解析，对齐
`frontend/.../remote-chat-request.ts` 的 `validateRemoteChatRequest`）：

| 字段 | P4.6 支持 | 说明 |
| --- | --- | --- |
| `sessionId` | ✅ 必填 | 校验属当前 (tenant,user)，否则 404 |
| `connectionId` | ✅ | 存入 run；ClickHouse 工具未启用（P5） |
| `message{id,role,parts}` | ✅ | 从 `text` parts 抽取用户文本；`role` 非 user 时由 service 校验 |
| `modelConfigId` | ✅ 首选 | 服务端按 tenant 解析模型配置 |
| `model{provider,modelId}` | ✅ 兼容 | 按 `modelKey` best-effort 匹配 tenant 的 enabled 模型 |
| `model.apiKey` / `connection.password` / 顶层 `apiKey` | ❌ **拒绝** | 400 `CLIENT_SECRET_NOT_ALLOWED`（处理前拒绝，不建 run） |
| `agentId` | ✅ 可选 | 解析 published revision（tenant 校验）；缺省走内置 prompt |
| `Idempotency-Key` 头 / `clientRequestId` | ✅ | 幂等键 (tenant,user,key) |
| `continuation:true` | ❌ | 400（无工具/HITL，P5+） |
| `generateTitle` / `ephemeral` / `agentContext` | ⚪ 接受未用 | title 生成本阶段未实现（见 §7） |

**响应**：200，固定头（stream-protocol §2）：`Content-Type: text/event-stream`、`Cache-Control: no-cache`、
`Connection: keep-alive`、`X-Vercel-AI-UI-Message-Stream: v1`、`X-Accel-Buffering: no`；body 为
encoder 产出的 `data: {json}\n\n` 帧，以 `data: [DONE]\n\n` 终止。

**关键实现**：controller 用 `response.writeWith(frames.map(UTF-8 buffer))` 直接写原始字节——
若返回 `Flux<String>` + `text/event-stream`，WebFlux 会把每个 String 再当 SSE 事件二次编码（`data:data:`），
破坏字节契约；直接写 buffer 保留 encoder 的精确帧。

## P4.6-2. 分层

```text
api.compat.AiAgentController            (AgentScope-free: JsonNode 校验、reject secrets、写 SSE 帧+头)
  -> agent.application.ChatRunService   (AgentScope-free: 解析/幂等/建 run/接线)
       -> AgentRunService.start (P4.2)  -> Flux<AgentRunEvent>
       -> RunLifecycleRecorder          -> tap 事件，终态落库（succeeded/failed）
       -> AiSdkStreamEncoder (P4.5)     -> Flux<String> 帧
  agent.runtime.ModelAdapterProvider     (凭据服务端注入缝；NoOp 默认，fake 测试覆盖)
  agent.runtime.HarnessAgentFactory      (唯一 runtime，P5+ 全关)
config: AgentRunConfiguration 装配 AgentRunService(=factory+registry+cleanupExecutor+RunCancellationPersister)
```

## P4.6-3. 幂等行为（client_request_id）

- 范围 `(tenant_id, user_id, idempotency_key)`。**非 lookup-then-create 竞态**：先 lookup 快路径，
  再 `INSERT`；`UNIQUE(tenant,user,idempotency_key)`（V5）是原子裁决——并发同 key 时第二个 INSERT
  触发约束，catch 后 re-lookup 找到 winner 并拒。
- 已有 run 的响应：`RUNNING/QUEUED/WAITING_INPUT` → 409 `RESOURCE_IN_USE`（已有一个 active agent）；
  `SUCCEEDED/FAILED/CANCELLED` → 409（终态不可重放，事件重放在 P4.8）。
- 测试：串行（首请求完成后第二请求 409）+ **并发**（两线程同 key → 恰好一个 started、一个 conflict）。

## P4.6-4. Run 状态接线

- 请求接受 → 建 `RUNNING` run（`AgentRunRepository.create`，`agent_revision_id` 非空：无 DB revision 时用
  sentinel `"builtin-default"`，列无 FK 为逻辑引用）。
- `RunCompleted` → `RunLifecycleRecorder` 累加 usage（跨 UsageReported）→ `transition(SUCCEEDED, usageJson)`，
  fire-and-forget 在 jdbc scheduler。
- `RunFailed` → `transition(FAILED, code+safeMessage)`（`RunFailureCode` 固定串，不含 raw）。
- cancel/client disconnect → `AgentRunService` doFinally(CANCEL) → `RunCancellationPersister`（observer）
  → `applyCancellation` → `CANCELLED`。
- 终态竞争：复用 P4.3 revision 乐观锁（`transition` 条件 UPDATE + re-read），不覆盖其他终态；late cancel
  到达已终态 run 为安全 no-op。

## P4.6-5. 线程模型

- **JDBC 不阻塞 Netty**：`ChatRunService.stream` 用 `Mono.fromCallable(prepareRun).subscribeOn(JdbcSchedulerConfig.JDBC_SCHEDULER)`
  跑解析+建 run；`RunLifecycleRecorder` 在同一 bounded scheduler fire-and-forget 终态写；AgentScope `close()`
  在专用 daemon executor（P4.2）。`RunLifecycleRecorderTest` 断言 transition 线程名 `test-jdbc` ≠ 调用线程。
- **不手工 subscribe**：controller 返回 `Mono<Void>`，WebFlux 订阅 SSE body（单次），保持 P4.2 single-use/deferred/自动 binding。

## P4.6-6. 测试命令与结果

```
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -B -ntp spotless:apply clean verify
```

- review 修复后全量：`Tests run: 272, Failures: 0, Errors: 0, Skipped: 0`。
- P4.6 新增 **19 测试**（`AiAgentControllerTest` 17 + `RunLifecycleRecorderTest` 2）。
- P4.1–P4.5 + P3 全部仍通过；`DatastoriaServerApplicationTests` context 加载通过。
- 无真实网络、无 API key、无真实 provider（`FakeModelAdapterProvider` 注入）。

P4.6 测试覆盖（对齐需求 15）：

| 测试 | 不变量 |
| --- | --- |
| happyTextStreamReturnsSseWithFixedHeaders | SSE 帧 + 5 头 + `[DONE]` + `\n\n` 分帧 |
| reasoningAndAccumulatedUsageStream | reasoning 序列 + usage 累加落 finish |
| clientModelApiKeyRejectedBeforeAnyRun | `model.apiKey` → 400 且**不建 run** |
| connectionPasswordRejected | `connection.password` → 400 |
| missingSessionReturnsNotFound / missingModelReturnsNotFound | 404 |
| crossTenantSessionReturnsNotFound | 跨租户 session 不可见（404，不泄漏） |
| connectionMustMatchOwnedSession | 请求 connection 必须与 tenant/user 所属 session 的固定 connection 一致 |
| assistantInitialMessageIsRejectedBeforeRunCreation | 非 continuation 初始请求只允许 user message |
| conflictingIdempotencyKeysAreRejected | header/body 幂等键不一致时在建 run 前拒绝 |
| providerErrorIsSanitizedInTheStream | error 帧只含固定 safe message，不含 `sk-SECRET`/raw |
| adapterInitializationFailureIsSanitizedAndCreatesNoRun | adapter/凭据初始化失败不泄密、不遗留 RUNNING run |
| serialIdempotencyKeyRejectsSecondRequest | 串行同 key → 第二请求 409 |
| concurrentSameIdempotencyKeyStartsExactlyOneRun | 并发同 key → 恰好一个 run |
| completedRunWithUsagePersisted / failedRunPersistedWithSafeCodeOnly | 终态落库（succeeded+usage / failed+code，无 raw） |
| clientDisconnectCancelsRunAndPersistsCancelled | 取消订阅 → provider flux 取消 + run `CANCELLED` |
| RunLifecycleRecorderTest | usage 累加 + 终态写运行在 jdbc scheduler（非调用线程） |

## P4.6-7. 安全检查

- **客户端凭据不进边界**：`model.apiKey`/`connection.password`/顶层 `apiKey` 在解析前拒绝（`ClientSecretNotAllowedException` 400），
  不建 run、不日志、不落库。`apiKey 被拒不建 run` 测试断言。
- **provider 错误脱敏**：`RunFailed` 经 encoder 的 `safeFailureMessage(code)`（不信任 event.message，P4.5 冻结）→
  固定 `RunFailureCode.safeMessage()`；run 落 `safe_message` 同值；测试断言不含 `sk-`/raw。AgentScope trace log 已关（P4.2）。
- **凭据服务端注入**：`ModelAdapterProvider` 是唯一缝，NoOp 默认（fail-fast），真实 provider 读 `SecretService.decrypt` 在 P4.8。
- **租户隔离**：session/model/agent 全 tenant-scoped 校验；跨租户 session → 404。
- **连接隔离**：请求 `connectionId` 必须等于已通过 tenant/user 校验的 session 固定
  `connectionId`；不接受调用方用任意连接重标记 run。
- **adapter 初始化失败安全**：服务端 adapter 在建 run 前解析；失败统一映射为无 cause 的安全
  `503 PROVIDER_UNAVAILABLE`，原始凭据异常既不进入全局日志，也不留下永久 `RUNNING` run。
- **隔离边界**：controller/service/encoder/repository 不引用 `io.agentscope.*`（仅 `agent.runtime`）。
- **取消 owner isolation**：P4.2/4.3 不变；disconnect → cancel 上游传播 + owner 校验保留。

## P4.6-8. MySQL / 真实 provider / 前端

- **MySQL 本机未执行**（无 Docker；`SchemaParityTest` Tests run: 0）。P4.6 无 DB schema 变更（用 V5 既有表），
  CI `mysql-contract` 兜底。
- **真实 provider smoke 未执行**（P4.8，显式开关下）。本阶段 `NoOpModelAdapterProvider` 默认，真实请求会 fail-fast。
- **前端**：P4.6 **未改前端文件/fixture**（route + fixture 只读），故未运行前端测试。usage 形状沿用 P4.5 结论（`inputTokens`）。

## P4.6-9. 回滚

- P4.6 新增：controller + DTO + service + recorder + provider/NoOp + AgentRunConfiguration + 3 测试类 + fake provider。
  无 DDL、无前端改动。
- 回滚 = 删除上述文件 + 撤销报告 P4.6 段。`NoOpModelAdapterProvider` 为 `@Component`（context 仍加载，真实请求 fail-fast）。
- 对线上无影响：前端仍走 Node A01（P4.7 才切 Java）。

## P4.6-10. 已知风险 / 未覆盖（不阻断 P4.7）

1. **title 生成未实现**：`finish` 只带 usage，无 `messageMetadata.title`（Node route 的 TransformStream + `SessionTitleGenerator`）。
   独立可超时、失败不影响主回答的要求留给后续（建议 P4.8 或独立切片）。
2. **assistant 消息未持久化到 `ds_chat_message`**：P4.6 只持久化 run 记录；刷新回放 Java run 的助手消息需消息持久化
   （P4.8 或后续切片）。当前前端从流组装消息。
3. **idempotent 终态重放未实现**：终态 run 重复提交返回 409（非同流重放）；事件重放（`ds_agent_event` / `Last-Event-ID`）在 P4.8。
4. **`{provider,modelId}` 解析 best-effort**：按 `modelKey` 匹配 enabled 模型，未做 provider key 反查；`modelConfigId` 为首选。
5. **`Connection: keep-alive` 头**：controller 设置，但 reactor-netty 对 hop-by-hop 头可能不下发；测试不断言该头（断言其余 4 个）。
6. **真实 provider**：NoOp fail-fast；P4.8 接入。

## P4.6-11. 修改文件

```
src/main/java/io/datastoria/server/api/compat/AiAgentController.java            (新增)
src/main/java/io/datastoria/server/api/compat/AgentChatRequest.java             (新增)
src/main/java/io/datastoria/server/agent/application/ChatRunService.java        (新增)
src/main/java/io/datastoria/server/agent/application/RunLifecycleRecorder.java  (新增)
src/main/java/io/datastoria/server/agent/application/AgentRunConfiguration.java (新增)
src/main/java/io/datastoria/server/agent/runtime/ModelAdapterProvider.java      (新增)
src/main/java/io/datastoria/server/agent/runtime/NoOpModelAdapterProvider.java  (新增)
src/test/java/io/datastoria/server/api/compat/AiAgentControllerTest.java        (新增)
src/test/java/io/datastoria/server/agent/application/RunLifecycleRecorderTest.java (新增)
src/test/java/io/datastoria/server/agent/testing/FakeModelAdapterProvider.java  (新增)
docs/delivery/p4-implementation-report.md                                       (追加 P4.6)
```

## P4.6-12. 下一阶段（P4.7）计划

- 前端 `NEXT_PUBLIC_DATASTORIA_CHAT_BACKEND=node|java` 网关：把 `POST /api/ai/agent` 按开关打到 Java 后端。
- 确认 `Idempotency-Key` 由前端生成；确认 Java 返回的 SSE 能被未修改的 `DefaultChatTransport` 消费。
- 跑 Node/Java diff + Playwright（不改前端渲染逻辑）。

**P4.6 review 已通过；不自动开始 P4.7。**

## P4.6-13. Review 修复结论

review 发现并修复 4 个边界问题：

1. 原实现只校验 session ownership，未约束请求 `connectionId`；现要求与 session 的固定 connection
   一致，防止跨连接混淆。
2. 原实现先创建 `RUNNING` run、后解析 provider adapter；初始化失败会留下悬挂 run。现改为 adapter
   解析成功后才创建 run，并将初始化异常映射为不携带原 cause 的安全 503。
3. 补齐初始 turn 的 `message.role=user` 与非空 text 校验，拒绝 unsupported assistant 初始消息。
4. 同时提供 `Idempotency-Key` 和 `clientRequestId` 时必须一致，避免两个来源产生歧义。

验证：JDK 17 执行 `./mvnw -B -ntp spotless:apply clean verify`，272 tests 全通过；本机无 Docker，
`SchemaParityTest` 仍为 0 tests，由 CI `mysql-contract` 覆盖。**P4.6 review 通过，可开始 P4.7。**

---

# P4.7 — 前端 Node/Java Chat Gateway（review 已通过）

## P4.7-0. 范围

在前端 `POST /api/ai/agent` 路由加 `NEXT_PUBLIC_DATASTORIA_CHAT_BACKEND=node|java` 开关（默认 `node`）。
`node` 模式行为/响应字节**完全不变**；`java` 模式把请求**原样代理**到 Java `POST /api/ai/agent` 并流式回传
AI SDK UI Message Stream。**不接真实 provider、不做 run resume/事件重放（P4.8）。**

## P4.7-1. 设计

```text
POST /api/ai/agent (frontend/src/app/api/ai/agent/route.ts)
  if (isJavaChatBackend()) return proxyChatToJava(req);   // 顶部短路，node 逻辑一字不改
  // ...既有 Node streamText 逻辑...
```

- `frontend/src/lib/ai/chat/chat-backend.ts`：开关 + Java base URL 解析（镜像 P3 `session-api-base.ts`）。
  `NEXT_PUBLIC_DATASTORIA_CHAT_BACKEND==="java"` 选 java；Java URL 取自 `NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL`
  （与 session 后端共用），未配置时 fail-fast。
- `frontend/src/lib/ai/chat/java-chat-proxy.ts`：`proxyChatToJava(req)` 代理。
- `/api/ai/chat/v2` 复用 A01 的 `POST`，短路随之生效。

## P4.7-2. 关键不变量

1. **node 模式零改动**：短路在 `try` 之前；既有 `streamText`/`createUIMessageStreamResponse` 字节不变。
2. **客户端凭据不发往 Java**：gateway 递归识别 `apiKey/api_key/password/token/accessToken/
   refreshToken/authorization/secret/clientSecret`（含嵌套 `model.apiKey`/`connection.password`），在 fetch 前
   返回 `400 CLIENT_SECRET_NOT_ALLOWED`；保留递归 sanitizer 作为防御纵深。不得静默剥离后继续执行，否则会
   掩盖尚未完成凭据迁移的调用方。
3. **身份转发**：`getAuthenticatedUserEmail(req)`（`proxy.ts` 中间件从 next-auth session 注入到请求头）→ 作为
   `x-datastoria-user-email` 转发。**不**用客户端可控的公开 env 伪造身份。
4. **稳定 Idempotency-Key**：优先透传客户端 `Idempotency-Key` 头，其次复用 body
   `clientRequestId`；否则由 `sessionId + message.id`（客户端 `uuidv7`，跨重试稳定）派生
   `ds-<sha256>`，再否则 `sessionId+text`。同一请求（含网络重试）复用同一键。
5. **SSE 原样流式**：上游 `Response.body` 经一个 `ReadableStream` 包装逐 chunk `enqueue` 转发（**不缓冲、不解析、
   不重新编码**）；透传 `content-type`/`cache-control`/`connection`/`x-vercel-ai-ui-message-stream`/
   `x-accel-buffering` 头与状态码。Java 非 2xx → 透传状态 + 兼容错误体（不合成内部异常）。
6. **取消传播**：入站 `req.signal` 与 downstream `ReadableStream.cancel()` 均绑定
   `AbortController`；断开发生在等待响应头期间或流式读取期间，都会 abort 上游 fetch、cancel upstream
   reader，使 Java 后端取消 run、停止 provider token。
7. **边界保持**：Java 模式与 Node A01 一样限制请求体为 10 MiB；缺少中间件注入的认证 email 时在 gateway
   返回 401，不依赖 Java local profile 的 anonymous 默认值。
8. **未修改前端 chat 客户端**：`DefaultChatTransport` 仍打 `/api/ai/agent`；java 模式下网关透明代理，AI SDK 消费
   Java SSE（P4.6 已验证 `data: {json}\n\n` + `[DONE]`）。

## P4.7-3. 测试命令与结果

```
cd frontend
npm ci                              # 1306 包
npx vitest run src/lib/ai/chat/     # P4.7 专项
npx vitest run                      # 全量
npx tsc -p tsconfig.typecheck.json --noEmit   # typecheck
npx eslint src/lib/ai/chat/ src/app/api/ai/agent/route.ts
npx prettier --check "src/lib/ai/chat/*.ts" src/app/api/ai/agent/route.ts
```

- review 修复后 P4.7 专项：`chat-backend.test.ts`(4) + `java-chat-proxy.test.ts`(13) =
  **17/17 通过**。
- 前端全量：**82 files / 436 tests 全通过**。
- typecheck / eslint / prettier：**clean**（修复了一个 TS `Transformer.cancel` 类型缺失 → 改用 `ReadableStream`
  包装，及一个 unused eslint-disable）。
- Java 回归：`./mvnw test -Dtest=AiAgentControllerTest` → **17/17 通过**（P4.6 未受影响，P4.7 不改 Java）。

P4.7 测试覆盖（对齐需求 9）：

| 测试 | 不变量 |
| --- | --- |
| chat-backend | 默认 node；仅 `===\"java\"` 选 java；Java URL 去尾斜杠；未配置 fail-fast |
| proxy forwards endpoint+identity+idempotency | 转发到 `${JAVA_BASE}/api/ai/agent`，带 `x-datastoria-user-email` + `idempotency-key` |
| proxy rejects client credentials | `model.apiKey`/`connection.password`/`accessToken` → 400，且不 fetch |
| resolveIdempotencyKey stable | header/body clientRequestId 优先；派生键使用 SHA-256；同请求同键 |
| proxy streams SSE verbatim + headers | 状态 200 + 5 头 + body 逐字节相等 |
| proxy passes through non-2xx | 409 状态 + body 透传，不合成内部异常 |
| proxy aborts upstream on disconnect | downstream reader cancel 与等待响应头时 inbound abort 均取消上游 |
| proxy fails closed without identity / oversized body | 缺认证 email → 401；超过 10 MiB → 413；均不 fetch |
| proxy 502 when URL unset | 未配置返回 502，不 fetch |
| stripClientSecrets | 嵌套凭据键删除、安全字段保留 |

## P4.7-4. 安全检查

- **客户端凭据**：`apiKey/password/token/authorization/secret` 在 gateway 显式拒绝并返回
  `CLIENT_SECRET_NOT_ALLOWED`，绝不发往 Java。
- **身份**：服务端从请求头解析（中间件已剥离客户端伪造的 `x-datastoria-user-email`），不以公开 env 信任客户端身份。
- **错误透传**：Java 非 2xx 状态+body 透传，gateway 不合成/不放大后端内部异常（P4.6 已脱敏）。
- 未改 Java、未改前端 chat 客户端、未改协议 fixture（route/fixture 只读）。

## P4.7-5. 回滚

- P4.7 纯前端：新增 `frontend/src/lib/ai/chat/{chat-backend,java-chat-proxy}.ts` + 2 测试 + `route.ts` 顶部 3 行短路。
  无 Java 改动、无 DDL、无协议变更。
- 回滚 = 删除 `lib/ai/chat/` + 撤销 `route.ts` 短路与 import。默认 `node` 模式下行为与 P4.6 完全一致。
- 开关默认 `node`：即便合并，前端仍走 Node A01，需显式 `NEXT_PUBLIC_DATASTORIA_CHAT_BACKEND=java` 才切 Java。

## P4.7-6. 已知风险（不阻断 P4.8）

1. **未做真实端到端**：`DefaultChatTransport` 消费 Java SSE 已由 P4.6 的字节契约（`data: {json}\n\n`+`[DONE]`）+ P4.7
   逐字节透传测试覆盖；真实浏览器 ↔ Java 的 Playwright 留 P4.8。
2. **`message.id` 稳定性依赖客户端**：AI SDK `useChat` 跨重试复用 user-message id（`newUniqueSessionId`）；若未来客户端
   改为每次重试生成新 id，幂等键会变（此时应改由客户端显式传 `Idempotency-Key`）。
3. **gateway 逐 chunk 转发**：经 JS `ReadableStream`（非零拷贝 pipe），SSE 帧小，性能可接受；高吞吐场景可换
   `pipeThrough` + `AbortController`（受 TS `Transformer.cancel` 类型限制，当前用 ReadableStream 包装规避）。
4. **`Connection: keep-alive`**：透传上游值；实际是否下发取决于部署的反向代理。
5. **title / assistant 消息持久化**：仍由 Java 侧负责（P4.6 未做 title/消息落库），gateway 不参与。

## P4.7-7. 修改文件

```
frontend/src/app/api/ai/agent/route.ts                         (顶部 java 短路 + import)
frontend/src/lib/ai/chat/chat-backend.ts                       (新增：开关 + Java URL)
frontend/src/lib/ai/chat/java-chat-proxy.ts                    (新增：代理 + 凭据剥离 + 稳定幂等键 + SSE 流式 + 取消传播)
frontend/src/lib/ai/chat/chat-backend.test.ts                  (新增)
frontend/src/lib/ai/chat/java-chat-proxy.test.ts               (新增)
docs/delivery/p4-implementation-report.md                      (追加 P4.7)
```

## P4.7-8. 下一阶段（P4.8）计划

- fake model 端到端验证：`CHAT_BACKEND=java` + `SESSION_BACKEND=java`，`DefaultChatTransport` 消费 Java SSE 全流程
  （纯文本/reasoning/usage/error/cancel）。
- 可选真实 provider smoke（显式环境变量开关，不进常规 CI）。
- run resume / 事件重放（`Last-Event-ID`/`ds_agent_event`）+ assistant 消息落 `ds_chat_message` + title 注入。
- Node↔Java 字节级 diff（contract runner 抓真实字节）。

**P4.7 review 已通过；不自动开始 P4.8。**

## P4.7-9. Review 修复结论

review 发现并修复以下边界问题：

1. body 已携带 `clientRequestId` 时，原 gateway 仍从 message 派生 header，可能与 body 冲突并被 Java
   P4.6 拒绝；现优先复用 body key。
2. 原 gateway 仅在 downstream body 被 cancel 后 abort；客户端等待 Java 响应头期间断开不会取消 fetch。
   现同时绑定入站 `req.signal`，并在 downstream cancel 时显式 cancel upstream reader。
3. 原流包装在 `start()` 中持续读取，无 downstream backpressure；现改为 `pull()` 每次读取一个 chunk。
4. 补回 Node A01 的 10 MiB 请求边界，并在缺少可信认证 email 时 fail closed 为 401。
5. 原实现静默删除客户端凭据后继续请求，违背 `CLIENT_SECRET_NOT_ALLOWED` 的迁移诊断语义；现显式返回
   400 且不调用 Java。
6. 派生幂等键由短 DJB2 摘要升级为 SHA-256，避免碰撞导致不同请求错误去重。

验证：P4.7 专项 17/17、前端全量 82 files / 436 tests 全通过；typecheck、ESLint、Prettier
均通过。**P4.7 review 通过，可开始 P4.8。**

# P4.8：真实 Provider、事件重放与后端唯一数据源收口

## P4.8-1. Review 与修复结论

P4.8 已完成，并在原有真实 Provider、事件录制/重放基础上完成破坏式后端收口：

1. **真实 Provider**：AgentScope Java 的生产 `ModelAdapterProvider` 支持 OpenAI 与
   OpenAI-compatible Chat Completions。模型级 secret 优先于 provider 级 secret，只能由 Java
   服务端解密；浏览器提交 `apiKey`、`secret` 等字段仍返回
   `CLIENT_SECRET_NOT_ALLOWED`。
2. **事件重放**：V6 `ds_agent_event` 保存实际发送的 AI SDK SSE 帧。相同
   `Idempotency-Key` 携带 `Last-Event-ID` 时从下一帧恢复，不重复调用模型或创建 run；查询受
   tenant、user 与 run 归属约束。
3. **直接 Spring API**：前端直接调用 Java 的 `/api/ai/agent`、兼容别名
   `/api/ai/chat`、`/api/ai/chat/v2`、session、feedback、models、providers、skills、
   connections 与 user-state API。`frontend/src/app/api` 已无文件，不再存在 Next API route、
   Node gateway 或 Node 端业务 repository。
4. **后端唯一数据源**：系统模型、provider、加密凭据、用户设置、连接、Skill、RCA 模板及各类
   UI 业务状态均由 Java API 和数据库提供。新增 SQLite/MySQL V7–V10：
   `ds_clickhouse_connection`、`ds_agent_skill`/resource、`ds_user_state`、
   `ds_rca_template`；首次读取时系统模型与系统设置会物化到数据库。
5. **前端去持久化/去执行**：生产代码不再使用 `localStorage`、`sessionStorage`、
   `indexedDB` 或 cookie 保存业务数据；删除前端 LLM provider、Agent 编排、Skill 磁盘/数据库
   provider、ClickHouse 执行器和服务端 session repository。前端只保留展示、交互和协议类型。
6. **AgentScope 工具接入**：`execute_sql`、`get_tables`、`explore_schema`、
   `validate_sql`、SQL 优化证据、query log、cluster status 与 RCA evidence 均注册为 Java
   AgentScope 工具，按 run 的后端连接权限执行。
7. **ClickHouse 后端执行**：连接凭据使用 envelope encryption；URL 禁止内嵌账号密码；用户和
   tenant 隔离。浏览器查询通过 Java 流式转发，避免 WebClient 默认 256 KiB 聚合上限，并保留
   `Content-Type` 与 `X-ClickHouse-*` 响应头；连接失败/超时返回去敏的 502/504。
8. **运行边界**：run 终态与 assistant message 落库后才发送 terminal event；标题生成失败不
   影响回答；取消向模型流传播；100 次 fresh stream 字节稳定性测试保留。

## P4.8-2. 真实联调证据

在与开发者当前 3000/8080 进程隔离的 13000/18080 端口上完成：

- Java 使用持久 SQLite 启动、停止并再次启动后，3 项系统设置、4 个系统模型与
  `P4 E2E Playground` ClickHouse 连接仍能从数据库读取。
- Java 对 `https://play.clickhouse.com` 执行
  `SELECT 1 AS p4_e2e FORMAT JSON`，返回 `p4_e2e=1`，响应为 chunked 流并带真实
  `X-ClickHouse-Query-Id`、`X-ClickHouse-Summary` 等头。
- 浏览器打开 `http://localhost:13000` 后显示
  `P4 E2E Playground`、8 个数据库和 96 张表，证明链路为
  **浏览器 → Spring Boot → 数据库连接配置 → 真实 ClickHouse**。
- Playground 的只读 `play` 用户对部分 `system.*` 表权限不足，因此 dashboard 中相应卡片显示
  ClickHouse 497；这属于远端账号授权限制，schema、版本、表数、容量和允许的指标均已正常显示。

## P4.8-3. P5 前联调方式

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
./mvnw spring-boot:run

cd frontend
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://127.0.0.1:8080 \
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=dev@example.com \
npm run dev
```

设置页保存 provider API key 和模型，连接页保存 ClickHouse 连接。浏览器请求只携带配置 ID/连接
ID，不携带 secret。对话联调应验证两轮历史、刷新后的 assistant message，以及相同幂等键配合
`Last-Event-ID` 的剩余帧重放。当前架构不再提供切回 Node backend 的开关；回滚应使用 Git/部署
版本回滚，并保留数据库备份。

## P4.8-4. 最终回归与限制

- Java：`./mvnw spotless:check test`，**293/293** 通过；SQLite V1–V10 全部迁移。
- 前端：Prettier、TypeScript、ESLint 均通过；Vitest **55 files / 291 tests** 通过；
  `next build --webpack` 成功，产物路由仅包含页面路由，无 `/api/*`。
- 静态审计：前端无 Next request/response/Auth、无后端选择开关、无生产 browser storage 调用，
  不包含 provider SDK 或 ClickHouse 执行实现。
- 本机没有可用 Docker，`SchemaParityTest` 实际执行 **0** 项；MySQL V7–V10 仍需由有 Docker
  的 CI 环境执行，不能视作本机已验证。
- 未配置可消费的真实 LLM key，因此真实付费模型 smoke 未执行；真实 provider 构造、服务端解密
  和优先级由自动化测试覆盖。

**P4.8 已完成并停止；不自动开始 P5。前后端联调已经可以在 P5 开始前进行。**

## P4.8-5. 后续完整迁移审计修复（2026-07-25）

在与原 `datastoria` 仓库逐项核对时，继续修复了两个会破坏“Java 后端唯一执行者”约束的问题：

1. 前端已删除 `ai` 和 `@ai-sdk/react` 依赖，不再使用 `Chat`、`useChat` 或
   `DefaultChatTransport`。新的 `RemoteChat` 只负责直接调用 Spring REST、解析 Java 输出的
   UI-message SSE、维护视图状态及调用 action/resume API；它不包含 provider、Agent、Skill 或
   Tool 执行逻辑。
2. GitHub Copilot OAuth 模型在 Java 获取后会物化为真实 `ds_model_provider`/`ds_model` 配置，
   返回可选的 `configId`，运行时由认证用户身份解析服务端加密 OAuth credential，再构造
   AgentScope 模型。OAuth 模型不会混入 tenant 级 `systemModels`，避免重复展示及向没有该用户
   credential 的其他用户暴露不可执行配置。

本轮回归：

- Java：`./mvnw test`，**365/365** 通过；`spotless:apply` 后专项检查通过。
- 前端：TypeScript 通过；Vitest **57 files / 297 tests** 通过；`next build --webpack` 成功；
  生产依赖与源码均无 `ai`/`@ai-sdk/react`。
- 本机仍无 Docker，`SchemaParityTest` 为 **0 tests**；这项限制与上节相同。

完整迁移审计仍在继续：OpenAI Codex 订阅认证使用 ChatGPT Responses API，并非
Chat Completions。它需要独立的 Java AgentScope Responses adapter 后才能算真实接入，不能复用
现有 OpenAI-compatible adapter。此项完成前不开始 P5。

## P4.8-6. Codex Responses 与多轮上下文收口（2026-07-26）

上述遗留项已完成：

1. Java 新增独立的 Codex Responses AgentScope `ChatModel`，通过服务端保存的 Codex OAuth
   credential 调用 `chatgpt.com/backend-api/codex/responses`；请求带 access token 和从 JWT
   提取的 account id，支持 SSE 文本、reasoning summary、function call、usage 与图片输入。
   credential 临近过期时由 Java 自动刷新，token 不返回浏览器。
2. `/api/models/available` 为已连接 Codex 的当前用户物化独立 provider/model/configId；前端设置页
   只负责 PKCE 登录和展示，不包含 provider 执行代码。
3. A01 的图片 parts、mention context、`responseLanguage`、`reasoningLevel` 和
   `outputReasoning` 已在 Java 校验并转换到 AgentScope。非法语言 tag 不能注入 system prompt；
   关闭 reasoning 时 Java 不向 UI 输出 thinking 事件。
4. assistant 的工具调用与结果（包括 tool-only 回答）以 `dynamic-tool` parts 落库；下一轮或 JVM
   重启后重建为 AgentScope `ToolUseBlock`/`ToolResultMessage`，避免只恢复文字造成多轮上下文丢失。
5. 新增 REST inventory guard，将冻结 OpenAPI 中 A01–A29（A28 由 Spring Security/auth
   compatibility controller 承担）的业务 operation 与实际 WebFlux handler 对照，防止路由迁移回退。

本轮最终回归：

- Java 17：`./mvnw spotless:check test` 通过，Surefire 报告 **342 tests，0 failure，
  0 error**；Testcontainers 因本机无 Docker，MySQL schema parity 仍按既有限制跳过。
- 前端：TypeScript 通过；Vitest **57 files / 296 tests** 通过；`next build --webpack`
  成功，产物仍无 `/api/*` 路由。
- 静态审计：`frontend/src` 生产代码未发现 `ai`/`@ai-sdk`/provider SDK、browser storage 或
  ClickHouse client 执行入口。

P4.8 的 Codex、图片、多轮工具历史和 request-scoped 模型选项已完成；仍不自动开始 P5。

## P4.8-7. A28 与真实标题兼容收口（2026-07-26）

最终差异审计继续修复两项用户可见回退：

1. A28 不再从 REST inventory 中整体排除。自动化清单明确验证 Spring 的
   `GET /api/auth/providers`、`GET /api/auth/session`、`GET /api/auth/signin/{provider}` 与
   `POST /api/auth/signout`。生产环境 signout 由 Spring Security filter 失效 WebSession，
   非生产 profile 提供 204 compatibility fallback。前端恢复账户头像、用户信息与退出菜单，
   仅调用 Java auth REST wrapper，不重新引入 NextAuth。
2. 会话标题不再永远停留在前八词 provisional title。主回答成功后，Java 使用已解析的服务端
   model adapter/credential 发起独立 AgentScope 标题调用，限制输入 300 字符、输出 64 字符并
   设置 8 秒超时；成功标题写入 finish metadata，失败或超时继续使用 deterministic fallback，
   不影响主回答与事件重放。

静态边界复核：

- 原仓库 `src/app/api` 有 24 个 route 文件；当前前端为 **0**，冻结 A01–A29 由 Spring
  controller/Security handler 承担。
- `frontend/package*.json` 与生产源码未发现 AI SDK/provider SDK/ClickHouse client 执行依赖，
  未发现 `streamText`、`generateText`、`HarnessAgent`、browser storage 等服务端执行入口。
- 前端所有 SQL、schema、monitoring 查询仍通过 `Connection.query` 包装
  `/api/connections/{id}/query`，由 Spring 解析加密连接并访问 ClickHouse。

最终回归：Java 17 `spotless:check test` **344 tests / 0 failure / 0 error**；前端
TypeScript、Vitest **57 files / 297 tests**、`next build --webpack` 全部通过。构建路由仅包含
页面，不包含 `/api/*`。

## P4.8-8. 页面与 A01 语义差异复核（2026-07-26）

以原仓库的用户可见组件和 A01 分支重新对比后，修复了路径清单测试无法发现的差异：

1. 聊天文件链接仍生成 `/code-viewer`，但迁移时页面及其 Next 文件读取实现被整体删除，点击会
   404。现恢复纯前端代码查看页，并新增 Spring `GET /api/code/file`/`files` 只读包装；路径必须
   位于配置的 repository root，限制 400 行/100 KiB，拒绝绝对路径、穿越和 symlink escape。
2. Spring OAuth 登录恢复 `callbackUrl`。signin 只接受本地相对路径，使用短时 HttpOnly
   SameSite cookie 跨 OAuth round-trip 保存，认证成功后解析到配置的前端 origin，并清除 cookie；
   外部 URL、protocol-relative URL 与 CRLF 均回退首页。
3. A01 `ephemeral:true` 原先仍要求先创建 session，导致 SQL 错误解释等 one-off chat 返回 404。
   Java 现在创建临时 FK anchor、不加载历史、不生成标题，并在 SSE 正常结束、取消或启动失败后
   删除 session/run/message/event，不污染聊天历史。
4. Java 默认 AgentScope prompt 从通用 “helpful assistant” 恢复为原 ClickHouse
   orchestrator workflow，明确 Think、按需加载 Skill、工具失败后重试、时间范围复用与 markdown
   输出规则。
5. 新增 always-on `MigrationSetParityTest`：即使本机没有 Docker，也强制 SQLite/MySQL 保持相同
   V1–V14 版本集合和建表集合；真实 MySQL DDL/runtime parity 仍由已有 Testcontainers gate 承担。
6. 恢复原 A01 的历史消息压缩语义：默认从历史 assistant turn 移除全部 `validate_sql`
   call/result；`agentContext.pruneValidateSql:false` 可显式关闭。恢复执行使用 checkpoint 的活动
   工具上下文，不会误删待恢复调用。
7. `context` 中的 cluster name、server version、ClickHouse user 经长度与换行清洗后加入
   AgentScope system prompt，并与 request runtime options 一起持久化，使 JVM 重启后的
   checkpoint resume 仍使用相同诊断上下文。临时 session 的内存跟踪键也改为
   tenant/user/session 三元组，避免跨租户同名 session 相互清理。

本轮回归：Java 17 `spotless:check test` **351 tests / 0 failure / 0 error**；前端
TypeScript、Vitest **58 files / 299 tests** 和包含 `/code-viewer` 的生产构建全部通过。

## P4.8-9. 本地联调与 ClickHouse 节点路由收口（2026-07-26）

实际以前端 origin 跨域访问 Spring 时继续发现并修复以下联调缺口：

1. local profile 的 CORS 现在同时允许 `localhost:3000` 与 `127.0.0.1:3000`，补齐
   `PATCH`、`Idempotency-Key`、`X-Session-Share-Code`，并显式暴露 ETag、Location、
   AI UI stream 及前端消费的 `X-ClickHouse-*` 响应头。
2. `GET /api/auth/session` 在 local/test profile 使用与业务 API 相同的 `IdentityContext`，
   因此开发身份头可以得到一致的登录会话；生产环境仍要求 Spring Security OAuth 身份。
3. 原前端在 cluster 模式通过 ClickHouse `remote()` 固定查询选中的节点。迁移后该分支曾退化为
   普通查询。现在前端只向 `/api/connections/{id}/query` 发送 `targetNode/targetUser`，Spring
   校验节点、解密服务端凭据并构造节点查询；密码不会返回或进入浏览器请求。
4. 再次静态检查确认当前前端没有 Next `/api` route、AI/provider SDK、`streamText`、
   `generateText`、浏览器 Agent/Skill/Tool executor 或 ClickHouse client。页面中的 schema、
   monitoring 和 SQL 定义均通过 Spring query wrapper 执行。
5. legacy A02 `/api/ai/chat` 继续按既定退役策略映射到 A01，并返回弃用头；当前前端不调用
   A02。原 A02 允许浏览器提交 provider secret，与“secret 只存后端”的目标冲突，因此不会恢复
   该不安全请求字段。

真实 HTTP 验证确认开发会话返回 200，来自 `127.0.0.1:3000` 的 PATCH/幂等键/分享码 CORS
预检返回 200，且 ClickHouse 响应头处于 expose 列表。全量回归结果：

- Java 17：`./mvnw spotless:check test`，**392 tests / 0 failure / 0 error**；本机无 Docker，
  Testcontainers MySQL runtime parity 仍未执行。
- 前端：Prettier、TypeScript、ESLint、Vitest **58 files / 300 tests**、`next build --webpack`
  全部通过；构建路由仍只有页面，无 `/api/*`。
