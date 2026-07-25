# P8 实施报告 — HITL、权限与暂停恢复

> 分支：`codex/p5-skill-readonly`
> 起始提交：`1acedf9`
> 状态：**P8 已完成并合并到本地 master**

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

approval/deny 恢复链路现已完成：

- `POST /api/ai/runs/{runId}:resume` 返回 AI SDK SSE continuation；
- 仅 owner 且 run 为 `WAITING_INPUT` 时允许恢复，所有 checkpoint tool calls 必须已有
  APPROVED/DENIED 终态；
- APPROVED/DENIED 被转换为 AgentScope `ConfirmResult`，批准会执行原工具，拒绝会向模型
  注入 denied `ToolResultBlock`；
- 同 JVM 优先使用 run-scoped AgentScope state；Java 重启后仅使用安全 checkpoint 中的
  tool id/name/input、持久聊天消息、固定 agent/model/skill revisions 重建最小状态；
- 不把 prompt、provider credential 或 AgentScope 全量 state 写入 checkpoint；
- continuation mapper 从 checkpoint event sequence 继续，忽略重复 `AgentStart`；
- SSE 持久帧从 `ds_agent_event` 当前最大 sequence 追加，不会在续流时从 1 重置。

question suspension 与前端恢复链路也已完成：

- `ask_user_question` 是服务端 AgentScope suspension tool，浏览器不再执行此 tool；
- question 与 approval 均输出 `data-pending-action`，携带稳定的 run/action/tool 标识；
- 前端调用 `respond/approve/deny` action API，最后一个待处理 action 完成后再 POST
  `/api/ai/runs/{runId}:resume`；
- approval 通过 `ConfirmResult` 恢复；question 通过持久答案构造成功的
  `ToolResultBlock` 恢复；
- 新 JVM 中仅依赖安全 checkpoint、持久消息和固定 revision 重建最小 AgentScope 状态；
- 真实 fake-model HTTP 流覆盖首次暂停、action 落库、服务恢复与最终 SSE finish。

会话与上下文策略已固定：

- 同一 session 同时只允许一个 `QUEUED/RUNNING/WAITING_INPUT` run，新请求返回 409；
- 长会话启用 AgentScope in-context compaction（默认 50 messages）；
- compaction 禁止 memory flush 和 session offload，memory tools/hooks、workspace context
  仍保持关闭，不把 prompt 写入 AgentScope 工作区；
- compaction 使用安全错误包装，provider 原始异常不会被第三方 middleware 日志输出；
- DataStoria 持久 chat message 是跨 run/跨 JVM 的权威会话记忆。

### P8 最终证据

- pending action repository、HTTP controller、AgentScope event mapper、AI SDK encoder 专项：
  36/36；
- AgentScope 真实 ALLOW/ASK/DENY 与 run-scoped `RuntimeContext`：6/6；
- approval 边界集成测试验证 `WAITING_INPUT + pending action + PENDING_ACTION checkpoint`
  原子落库；
- 暂停原因回归测试覆盖 permission asking、tool suspended、middleware pause；
- approval、deny、question 的 JVM 内与模拟重启恢复均有 AgentScope/HTTP 集成测试；
- session 单活动 run、长历史 compaction、action TTL/CAS、断线事件重放均有回归测试；
- Java 全量：355/355；Spotless 通过；
- 前端 Vitest：58 files、302/302；TypeScript typecheck 通过；
- Next.js production build（webpack）通过；
- 本地 ClickHouse `LocalClickHouseIT` 实库场景通过；
- MySQL schema parity 测试因本机无 Docker 未启动容器；SQLite V1–V13 迁移与全部
  repository/API 测试通过。

## P8 退出结论

P8 权威范围内的服务端 suspension、持久 action、恢复、重放、前端 action API、
session 并发、compaction 和自动化回归均已完成。真实 provider 的浏览器对话仍要求本地
为所选 provider 配置有效的服务端 credential；credential 不属于代码交付物，缺失时
模型列表会被前端按可用性过滤。
