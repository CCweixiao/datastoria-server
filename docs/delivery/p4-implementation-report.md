# P4 实施报告 — AgentScope Java 最小 Harness

> Stage: P4（AgentScope 最小 Harness）
> 本次交付：**仅 P4.1 — AgentScope 兼容性 Spike**
> 分支：`codex/phase-p4`（worktree `/Users/jielongping/OpenProjects/datastoria-server-p4`）
> 基线 master：`a540e8b`
> 状态：P4.1 review 已通过；可开始 P4.2，P4.2–P4.8 尚未实现。

## 0. 范围说明（P4.1 only）

按指令“只交付 P4.1”，本轮只完成 **P4.1：AgentScope 兼容性 Spike**，并产出第一轮验收物：

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
