# ADR-0004: AgentScope Java 基线（P4.1 兼容性 Spike）

- 状态：Accepted（P4.1 review）
- 日期：2026-07-25
- 阶段：P4.1 — AgentScope 兼容性 Spike
- 相关：`docs/design/harness-agent.md`、`docs/api/stream-protocol.md`、`docs/delivery/phase-prds.md` P4

## 1. 背景

P4 要求把 chat 页面的 Agent 能力迁到 Java 后端，能力由 AgentScope Java 提供。在写任何
Controller / Repository 之前，必须先验证 AgentScope Java 的官方 API、版本、JDK 17 兼容性，
并证明以下四点可用：

1. 纯文本 + reasoning 流式输出。
2. 多帧 text/reasoning 合并成单一逻辑块。
3. usage / 终止事件可观测。
4. 错误传播与取消（客户端断开）可控。

本 ADR 记录锁定版本、已验证的 API、Spike 实测结论、需要 DataStoria 适配的能力，以及替换/
升级 AgentScope 的隔离边界。**所有结论均来自对发布产物 `2.0.0` 的 javap 与一次真实编译运
行的 Spike，不依赖记忆。**

## 2. 决策

### 2.1 锁定版本

| 项 | 值 | 证据 |
| --- | --- | --- |
| 仓库 | `https://github.com/agentscope-ai/agentscope-java`（官方，Apache-2.0） | 仓库 README |
| 标签 | **`v2.0.0`** GA（2026-07-10 发布） | GitHub releases / tags API |
| commit | `44c304ec84d5fbd8588c1af8bc71b1edb9663380` | tags API（`v2.0.0` 指向） |
| Maven 坐标 | `io.agentscope:agentscope-core:2.0.0`、`io.agentscope:agentscope-harness:2.0.0` | Maven Central `central.sonatype.com` + 已在本机成功解析 |
| BOM | `io.agentscope:agentscope-dependencies-bom:2.0.0`（**不导入**，见 §3.1） | `pom.xml` |

**不使用 snapshot。** 仓库 `main` 当前为 `2.0.1-SNAPSHOT`（HEAD
`17fee94`），RC1–RC5 均已被 GA 取代。GA `v2.0.0` 已在 Maven Central 发布，Spike 证明其满足
P4 全部四项需求，无需承担 snapshot 风险。

### 2.2 JDK 17 兼容性

AgentScope `2.0.0` 以 `--release 17` 编译，README 标注 “JDK 17+”，无 toolchains 配置、无
JDK-21 专属依赖。本仓库 JDK 17.0.16 下 `./mvnw clean test` 编译并通过（见 §6）。

### 2.3 采用的 API（均为 GA 标签上的稳定、非 deprecated 类型）

| 能力 | 类型 / 方法 | 包 |
| --- | --- | --- |
| Agent（最小运行时） | `ReActAgent` + `ReActAgent.builder()` | `io.agentscope.core` |
| Agent（设计文档指定的运行时） | `HarnessAgent` + `HarnessAgent.builder()` | `io.agentscope.harness.agent` |
| 自定义 / fake 模型边界 | `Model.stream(List<Msg>, List<ToolSchema>, GenerateOptions) → Flux<ChatResponse>` | `io.agentscope.core.model` |
| 模型响应 | `ChatResponse`（builder：`content/usage/finishReason/metadata`） | `io.agentscope.core.model` |
| 用量 | `ChatUsage`（`inputTokens/outputTokens/cachedTokens/time`） | `io.agentscope.core.model` |
| 内容块 | sealed `ContentBlock`：`TextBlock`、`ThinkingBlock` 等（均带 builder） | `io.agentscope.core.message` |
| 入站消息 | `Msg.builder().role(MsgRole.USER).textContent(...).build()` | `io.agentscope.core.message` |
| 流式事件 | `agent.streamEvents(Msg) → Flux<AgentEvent>`（31 个子类型） | `io.agentscope.core.event` |
| 取消（协作式） | `Agent.interrupt()` / `interrupt(Msg)` | `io.agentscope.core.agent` |
| 状态 / checkpoint | `AgentStateStore`（`InMemoryAgentStateStore` / `JsonFileAgentStateStore`） | `io.agentscope.core.state` |

**显式避开 deprecated API**（`2.0.0` 仍存在但 `forRemoval=true`）：`StreamableAgent.stream(...) → Flux<io.agentscope.core.agent.Event>`
与 `io.agentscope.core.memory.Memory` 接口。P4 不引用它们。

## 3. 关键约束与结论

### 3.1 不导入 agentscope BOM，让 Spring Boot 管理共享传递依赖

`agentscope-core` 显式 pin 了 `reactor-core:3.8.2`、`jackson-databind:2.21.1`、
`sqlite-jdbc:3.47.1.0`、`snakeyaml:2.6`。若导入其 BOM，会**覆盖** Spring Boot 3.5.16 parent
管理的这些版本，可能破坏现有应用。因此 `pom.xml` 只声明两个 plain artifact，**不导入 BOM**。

`mvn dependency:tree` 实测：Spring Boot parent 生效，共享依赖被钉在 SB 版本：

| 依赖 | AgentScope pin | 实际生效（SB 管理） |
| --- | --- | --- |
| `reactor-core` | 3.8.2 | **3.7.19** |
| `jackson-databind` | 2.21.1 | 2.21.4 |
| `sqlite-jdbc` | 3.47.1.0 | 3.49.1.0 |
| `snakeyaml` | 2.6 | 2.4 |

**Spike 结论：AgentScope 2.0.0 在 reactor-core 3.7.19 下流式 / 取消 / 错误全部正常。** 这是
本次兼容性验证的核心证据。AgentScope 专属依赖（`okhttp 5.3.2`、`mcp 0.17.0`、
`tree-sitter 0.24.4`、`victools 4.38.0`、`json-schema-validator 2.0.0`、
`commons-compress 1.27.1`、`opentelemetry`）按其 pin 解析，无冲突。

> 风险记录：`reactor-core` 被 SB 降级 3.8.2→3.7.19。若 P4.2+ 发现 AgentScope 用了 3.8-only
> API（目前未发现），需要升级 SB 或显式 pin reactor 3.8 并回归全量测试。当前 Spike 未触发。

### 3.2 流式事件映射（实测固定）

`ReActAgent.streamEvents(userMsg)` 对多帧 fake 模型响应产出如下精确序列（见
`AgentScopeSpikeTest#streamEventsProducesExpectedEventSequence`）：

```
AGENT_START
→ MODEL_CALL_START
→ THINKING_BLOCK_START → THINKING_BLOCK_DELTA* → THINKING_BLOCK_END
→ TEXT_BLOCK_START    → TEXT_BLOCK_DELTA*    → TEXT_BLOCK_END
→ MODEL_CALL_END(usage)
→ AGENT_RESULT → AGENT_END
```

- 同类型连续帧被 AgentScope **合并为单个逻辑块**（Start 一次、每帧一个 Delta、End 一次）。
- usage 随 `ModelCallEndEvent.getUsage()` 携带（`inputTokens/outputTokens/...`）。
- reasoning 来自 `ThinkingBlockDeltaEvent.getDelta()`，文本来自 `TextBlockDeltaEvent.getDelta()`。
- 终止：`AGENT_RESULT`（带 `Msg` 终态）→ `AGENT_END`。

DataStoria 内部事件映射（P4.2 落地）：

| AgentScope | DataStoria 内部 AgentEvent |
| --- | --- |
| `AGENT_START` | `RunStarted` |
| `TEXT_BLOCK_DELTA` | `TextDelta` |
| `THINKING_BLOCK_DELTA` | `ReasoningDelta` |
| `MODEL_CALL_END` | `Usage` |
| `AGENT_RESULT` / `AGENT_END` | `RunCompleted` |
| Reactor `onError`（根因保留） | `RunFailed` |
| 订阅 dispose（见 §3.3） | `RunCancelled` |

### 3.3 取消与客户端断开（关键）

Spike 实测两种取消语义：

1. **订阅 dispose（可靠，作为客户端断开的真实原语）**：取消 `streamEvents` 的 Reactor 订阅
   会**向上游传播**并取消模型 provider flux（`FakeStreamModel.wasCancelled()==true`），token
   发射随即停止。`AgentScopeSpikeTest#disposingSubscriptionCancelsModelFlux` 已证明。
2. **`Agent.interrupt()`（协作式，步边界生效）**：在单步纯文本响应中，mid-stream 调用
   `interrupt()` **不会**中止当前模型调用——模型仍完整流式输出后正常 `onComplete`
   （伴随一条被 Reactor `onErrorDropped` 的迟到 `InterruptedException` 日志，仅噪声）。
   `interrupt()` 只在多步（工具调用）的步间检查点生效。

**DataStoria 取消策略（P4.6 落地）**：客户端断开时，WebFlux 一侧 **dispose `streamEvents`
订阅**（停止 provider token、释放上游），并调用 `agent.interrupt()`（对多步/工具链做协作清
理）。两者并用：dispose 负责“省 token + 立即停”，interrupt 负责“agent 协作收尾”。

### 3.4 错误传播（实测）

模型 `Flux.error(...)` 经 `streamEvents` 以 `onError` 透出，**根因保留**
（`IllegalStateException: provider Boom` 未被吞）。`AgentScopeSpikeTest#modelErrorPropagatesAsOnError`
已证明。DataStoria 在 P4.2 把根因映射为脱敏的 `RunFailed`（不向浏览器透传 provider 原文）。

测试使用 `StepVerifier` 消费并断言终止错误，不安装全局 Reactor hook，也不会产生
`onErrorDropped`。P4.2 同样必须在流边界显式消费并映射错误。

### 3.5 HarnessAgent 是 P4 唯一运行时

- `docs/design/harness-agent.md` 指定 `HarnessAgent` 为唯一运行时。
- `HarnessAgent` 组合一个 `ReActAgent`，但 DataStoria 生产代码不得直接使用其 delegate 绕过
  Harness runtime。
- 默认 builder 会注册 filesystem、shell、memory、skill 和 subagent 工具，不符合 P4 的
  “无 Skill、无业务工具”范围。因此 P4 factory 必须显式调用
  `disableFilesystemTools/disableShellTool/disableMemoryTools/disableMemoryHooks/`
  `disableSessionPersistence/disableWorkspaceContext/disableAtPathExpansion/disableSubagents/`
  `disableDynamicSubagents/disableDynamicSkills/disableDefaultWorkspaceSkills/disableToolsConfig`。
- AgentScope `2.0.0` 即使关闭上述能力仍会注册 `wait_async_results`；P4 无 async tool，factory
  构建后必须通过 `agent.getToolkit().removeTool("wait_async_results")` 显式移除。
- Spike 已验证上述最小配置可以正常流式，并断言传给 `Model.stream` 的 tool schema 数量为 0。

**决策**：P4 起始终使用 `HarnessAgent` 作为唯一 runtime。P5+ 逐项开启 Skill、sandbox、
subagent 和业务工具；不得依赖默认开启状态。

### 3.6 memory / checkpoint

`AgentStateStore`（`(userId, sessionId, key)` 键）+ `JsonFileAgentStateStore`（文件 checkpoint）
为稳定 API；`Memory` 接口已 deprecated。P4 的 checkpoint 表（P4.3 `ds_agent_checkpoint`）由
DataStoria 自有 repository 持久化 run 级状态，**不**把 AgentScope `State` blob 写进产品消息表
（`ds_chat_message`）。AgentScope `State` 的序列化/反序列化在 P4.4/P4.8 按需接入。

## 4. 需要 DataStoria 适配的能力

- **ModelAdapter**：fake 模型与真实 provider 共用同一 `Model` 边界；P4.6 的真实 provider 实现
  `Model.stream`（或引入 `agentscope-extensions-model-*` 扩展模块），凭据经
  `GenerateOptions.getApiKey()` 服务端注入，绝不进浏览器。
- **AgentEventMapper**：把 §3.2 的 `AgentEvent` 映射为 DataStoria 内部 event（不引用前端 AI SDK
  类型）；未知事件按 §3.2 策略保留/忽略（P4.5 encoder 定）。
- **AiSdkStreamEncoder**（P4.5）：把内部 event 编码为 AI SDK UI Message Stream
  （`docs/api/stream-protocol.md`），AgentScope event 不直接透传浏览器。
- **CancellationRegistry**（P4.2）：按 run 记录 `streamEvents` 订阅，断开时 dispose + interrupt。

## 5. 最小真实 provider smoke（如何开启）

- 默认关闭。仅当显式环境变量（如 `DATASTORIA_P4_PROVIDER_SMOKE=true`）开启。
- P4.6 实现 `Model` 的真实 provider 适配（或引入官方 extension），凭据来自服务端配置/解密，
  不读环境变量明文到日志。
- smoke 测试标注 `@EnabledIfEnvironmentVariable`，**不进入常规 CI**；失败不影响 deterministic
  fake 测试。Spike 阶段（P4.1）不执行真实 provider smoke。

## 6. 验证证据

```
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -B -ntp spotless:apply clean verify
```

- Spike：`AgentScopeSpikeTest` 4 测试（事件序列 / HarnessAgent 构建 / 错误传播 / dispose 取消）全过。
- 全量：`Tests run: 173, Failures: 0, Errors: 0, Skipped: 0`（P3 基线 169 + P4.1 新增 4）。
- 无真实网络、无 API key、无真实 provider。

## 7. 替换 / 升级 AgentScope 的隔离边界

- AgentScope 类型**只出现在** adapter/runtime 层（`io.datastoria.server.agent.runtime` /
  `agent.model`）。Controller、Repository、内部 event model 不直接引用 `io.agentscope.*`。
- 版本集中在 `pom.xml` 的 `agentscope.version` 属性；升级只改一处 + 跑 Spike + 全量回归。
- 若未来替换为其他 runtime，只需重写 `ModelAdapter` 与 `AgentEventMapper`，上层 Controller /
  encoder / repository 不变。

## 8. P4.1 阻断项（review 必读）

1. **reactor 降级风险**（§3.1）：当前正常；P4.2 若引入更复杂 AgentScope 特性需复跑全量回归。
2. **真实 provider smoke 未执行**（§5）：P4.1 范围内不执行，留待 P4.6 且仅在显式开关下运行。
3. **MySQL contract 本机未执行**：与 P3 一致，无 Docker 时 Testcontainers 自动跳过，CI
   `mysql-contract` job 兜底；P4.1 未新增数据库变更，不产生新 MySQL 风险。
