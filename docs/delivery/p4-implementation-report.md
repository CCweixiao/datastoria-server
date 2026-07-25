# P4 实施报告 — AgentScope Java 最小 Harness

> Stage: P4（AgentScope 最小 Harness）
> 本次交付：**P4.1（已通过 review）+ P4.2（本次）**
> 分支：`codex/phase-p4`（worktree `/Users/jielongping/OpenProjects/datastoria-server-p4`）
> 基线 master：`a540e8b`
> 状态：P4.1、P4.2 review 已通过；可开始 P4.3，P4.3–P4.8 未开始。

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
