# P8 实施报告 — HITL、权限与暂停恢复

> 分支：`codex/p5-skill-readonly`
> 起始提交：`1acedf9`
> 状态：**P8.1 已完成；P8.2 运行时暂停/恢复进行中**

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

### 当前证据

- SQLite repository 状态机：8/8；
- HTTP run/action/replay/cancel：5/5；
- V13 SQLite Flyway 迁移真实应用通过；
- Java 全量：338/338；
- Java 编译与 Spotless 通过。

## 尚未完成

- AgentScope ASK/DENY 事件到 pending action/checkpoint 的适配；
- `ask_user_question` 服务端工具和 AI SDK HITL wire frames；
- `:resume` 与 Java 进程重启恢复；
- 前端 action API 交互替换 client executor；
- memory/compaction、session 并发策略和完整 P8 回归。
