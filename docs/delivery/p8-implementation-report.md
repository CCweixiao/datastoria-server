# P8 实施报告 — HITL、权限与暂停恢复

> 分支：`codex/p5-skill-readonly`
> 起始提交：`1acedf9`
> 状态：**P8.1 已完成；P8.2 AgentScope approval 暂停边界已完成，恢复执行进行中**

## P8 权威范围

依据 `docs/migration-plan.md`、`docs/delivery/phase-prds.md` 和
`docs/design/api-contracts.md`，P8 只有在以下能力全部完成后才能退出：

- question/approval pending action 与 CAS 状态机；
- respond/approve/deny/cancel/resume/events API；
- AgentScope permission ALLOW/ASK/DENY；
- checkpoint、事件重放及进程重启恢复；
- 前端不执行 Agent tool，只渲染并调用 action API；
- action TTL、session 并发策略、memory/compaction；
- 幂等、过期、跨租户/用户、重启、断线和事务故障测试。

## P8.1：持久 pending action 与 run/action API

双方言 V13 新增 `ds_agent_pending_action`：

- `(tenant_id, run_id, tool_call_id)` 唯一；
- question/approval 与 pending/responded/approved/denied/expired/cancelled 均有 CHECK；
- 通过 run 复合外键级联删除；
- `revision` 条件更新提供 CAS；
- `resolution_digest` 对规范化 JSON 做 SHA-256，同内容重试返回原结果，不同内容返回 409；
- repository 的 find/create/resolve 均验证 run owner，跨用户和跨租户表现为不存在；
- 过期时间由服务器判定，过期 action 不能恢复执行。

新增 owner-scoped API：

| 方法 | 路径 | 当前行为 |
|---|---|---|
| GET | `/api/ai/runs/{runId}` | run 与未决 actions |
| GET | `/api/ai/runs/{runId}/events?after=` | 持久帧增量重放 |
| POST | `/api/ai/runs/{runId}/actions/{actionId}:respond` | question CAS |
| POST | `/api/ai/runs/{runId}/actions/{actionId}:approve` | approval CAS |
| POST | `/api/ai/runs/{runId}/actions/{actionId}:deny` | approval CAS |
| POST | `/api/ai/runs/{runId}:cancel` | active dispose/interrupt 或持久状态取消 |

所有 action/cancel 写入要求 `Idempotency-Key`。冲突和过期分别返回稳定的
`ACTION_ALREADY_RESOLVED`、`ACTION_EXPIRED` ProblemDetail。

### P8.1 证据

- SQLite repository 状态机：8/8；
- HTTP run/action/replay/cancel：5/5；
- V13 SQLite Flyway 迁移真实应用通过；
- Java 全量：338/338（P8.1 提交时）；
- Java 编译与 Spotless 通过。

## P8.2：AgentScope tool/HITL 事件与持久暂停边界

Java 运行时现已直接消费 AgentScope tool 生命周期事件，并转换为稳定的内部事件和
AI SDK data stream frame：

- tool input start/delta/available；
- tool output start/delta/available/error/denied；
- `RequireUserConfirmEvent` → `tool-approval-request`；
- approval action id 由 `runId + toolCallId` 确定性生成，断线重放不漂移；
- tool error/denied frame 只暴露稳定安全文案，不把 provider/tool 原始错误透传给浏览器。

`RunLifecycleRecorder` 在收到 approval 边界时，于同一事务内完成：

1. run 从 `RUNNING` 转为 `WAITING_INPUT`；
2. 每个待确认 tool call 创建持久 approval action；
3. 写入 DataStoria 自有的 `pending-action-v1` checkpoint，记录 AgentScope reply id、
   tool call id/name/input 和对应 action id；
4. checkpoint 绑定当前持久事件序号及 checksum，供后续恢复和进程重启校验。

AgentScope 的 `AGENT_RESULT` 并不总是终态。映射层现按 `GenerateReason` 区分：
`PERMISSION_ASKING`、`TOOL_SUSPENDED`、middleware/reasoning/acting pause 不再产生
`RunCompleted`，避免 `WAITING_INPUT` 被错误覆盖为 `SUCCEEDED`。

Harness 现通过显式 `(userId, runId)` `RuntimeContext` 调用 AgentScope，而不再使用
会落入默认 session 的弃用重载。AgentScope 可变状态按 run 隔离；DataStoria chat session
继续由持久消息重建，避免一个暂停 run 污染同一聊天中的后续 run。服务端注册的现有只读
工具由 DataStoria policy 显式 ALLOW；run capability 也可注入 ASK/DENY 规则。真实
AgentScope 推理/工具循环测试已验证：

- ALLOW 会执行工具并正常结束；
- ASK 不执行工具，产生 approval event 且不产生 `RunCompleted`；
- DENY 不执行工具，向 Agent 返回 denied tool result 后可继续结束。

同时显式注入 factory-scoped `InMemoryAgentStateStore`，不再依赖 Harness 默认写入
`~/.agentscope/state` 的文件 store。它支持同 JVM 内按 run 恢复且不会污染开发者目录；
跨进程恢复仍由 DataStoria checkpoint 重建，不能把含 prompt 的 AgentScope 全量 state
直接写入当前安全 checkpoint。

### P8.2 当前证据

- pending action repository、HTTP controller、AgentScope event mapper、AI SDK encoder 专项：
  36/36；
- AgentScope 真实 ALLOW/ASK/DENY 与 run-scoped `RuntimeContext`：6/6；
- approval 边界集成测试验证 `WAITING_INPUT + pending action + PENDING_ACTION checkpoint`
  原子落库；
- 暂停原因回归测试覆盖 permission asking、tool suspended、middleware pause；
- Java 全量：345/345；Spotless 通过。

## 尚未完成

- approval/deny 后的 AgentScope `ConfirmResult` 恢复执行；
- `ask_user_question` 服务端 suspension 工具和 question wire frame；
- `:resume` 与 Java 进程重启恢复；
- 前端 action API 交互替换 client executor；
- memory/compaction、session 并发策略和完整 P8 回归。
