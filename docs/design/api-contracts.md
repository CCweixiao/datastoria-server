# HTTP 与流式契约

## 1. 总则

- 迁移期间尽量保留现有 `/api/...` 路径，反向代理将其导向 Java。
- JSON 使用 UTF-8、camelCase；时间为 ISO-8601 UTC。
- REST 写请求接受 `Idempotency-Key`；配置写请求接受 `If-Match` revision/ETag。
- 身份由 HttpOnly session cookie 或 Bearer token 解析；禁止信任客户端 userId/tenantId。
- 所有响应带 `X-Request-Id`；错误不能包含密钥、SQL 凭据和 provider 原始响应。
- 旧前端依赖纯文本错误的接口在兼容层保留；新管理 API 使用统一 Problem Details。

统一错误：

```json
{
  "type": "https://datastoria.io/problems/validation",
  "title": "Validation failed",
  "status": 400,
  "code": "INVALID_REQUEST",
  "detail": "connectionId is required",
  "requestId": "01J...",
  "errors": [{"field": "connectionId", "code": "required"}]
}
```

## 2. 模型配置 API

### 管理端

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/ai/providers` | provider 列表，仅返回 `credentialConfigured/maskedHint` |
| POST | `/api/admin/ai/providers` | 新建 provider |
| GET | `/api/admin/ai/providers/{id}` | provider 详情 |
| PUT | `/api/admin/ai/providers/{id}` | `If-Match` 更新非敏感配置 |
| PUT | `/api/admin/ai/providers/{id}/credential` | 写入/轮换密钥，body 不记录日志 |
| DELETE | `/api/admin/ai/providers/{id}/credential` | 撤销密钥 |
| POST | `/api/admin/ai/providers/{id}/test` | 服务端连通性测试 |
| GET | `/api/admin/ai/models` | 模型目录 |
| POST | `/api/admin/ai/models` | 新建模型配置 |
| PUT | `/api/admin/ai/models/{id}` | 更新启停、能力和默认参数 |
| DELETE | `/api/admin/ai/models/{id}` | 软删除且检查引用 |
| POST | `/api/admin/ai/providers/{id}/models:discover` | 动态发现并返回 diff，确认后入库 |

credential 写请求示例：

```json
{"secretKind":"api_key","value":"sk-...","expiresAt":null}
```

响应只能是：

```json
{"configured":true,"maskedHint":"sk-…9x2","updatedAt":"2026-07-24T10:00:00Z"}
```

### 前端兼容

`POST /api/ai/models/available` 暂时保留路径。新 body 不需要 token；Java 根据当前用户和
租户返回：

```json
{
  "systemModels": [{
    "provider":"OpenAI",
    "modelId":"gpt-x",
    "description":"...",
    "source":"system",
    "disabled":false,
    "supportsImageInput":true
  }],
  "githubModels":[]
}
```

前端选择模型后写 `PUT /api/me/ai/model-preference`：

```json
{"modelConfigId":"mdl_01..."}
```

运行请求优先使用不可猜测的 `modelConfigId`；兼容期的 `{provider,modelId}` 只能由服务端
反查已授权配置，`apiKey` 字段一律拒绝或忽略并记录安全指标。

## 3. Agent 与系统配置 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/ai/agents` | Agent definitions |
| POST | `/api/admin/ai/agents` | 创建 draft |
| GET | `/api/admin/ai/agents/{id}` | definition + published/draft revision |
| POST | `/api/admin/ai/agents/{id}/revisions` | 创建不可变 revision |
| POST | `/api/admin/ai/agents/{id}/revisions/{revisionId}:publish` | 原子发布 |
| POST | `/api/admin/ai/agents/{id}:disable` | 禁用新 run |
| GET | `/api/me/ai/preferences` | 合并后的 effective config 与 revision |
| PUT | `/api/me/ai/preferences` | 更新允许的用户覆盖 |

用户偏好 request：

```json
{
  "outputReasoning": true,
  "reasoningLevel": "medium",
  "pruneValidateSql": true,
  "autoExplainClickHouseErrors": true,
  "autoExplainBlacklist": ["62","194"],
  "aiResponseLanguage": "zh-CN"
}
```

`mode=legacy` 只在切换期作为 feature flag，不进入长期用户配置。

## 4. Skill API

保留 A20-A26 路径和现有响应字段。写接口增加 `revision`/ETag；兼容前端未发送时可在短期
允许 last-write-wins，但必须产生日志，阶段 9 结束前前端应发送 revision。

关键约束：

- GET 默认只读 published；`includeDraft=1` 需要编辑权限。
- POST 创建 draft，不因创建成功自动发布。
- PATCH action 枚举：`saveDraft`、`publish`、`saveAndPublish`、
  `publishResources`；未知 action 返回 400。
- 发布是 bundle 原子操作，任何资源校验失败则全部失败。
- resource path 先 URL decode 一次，再规范化；禁止二次编码绕过。
- DELETE 对内置 seed 必须返回 409/403；数据库自建 Skill 遵循 owner/RBAC。

## 5. Chat REST 契约

会话 API 保持 A03-A11 现有 JSON 结构。重点不变量：

- sessionId 1..64 字符；connectionId trim 后 1..255。
- list limit 默认 100，范围 1..500；cursor 不透明。
- message role 当前允许 user/assistant；parts 为开放 JSON array。
- POST create 在一个事务中创建 session 和 messages；重复 id 幂等或明确 409。
- GET messages 严格按 sequence 升序。
- 分享码通过 `x-datastoria-session-share-code`；过期/撤销返回 403，缺失资源返回 404。
- DELETE 成功为 204。

## 6. Harness Chat 入站契约

兼容 `POST /api/ai/agent`：

```json
{
  "sessionId": "019...",
  "connectionId": "ch-prod",
  "message": {
    "id": "019...",
    "role": "user",
    "parts": [{"type":"text","text":"分析慢查询"}]
  },
  "continuation": false,
  "generateTitle": true,
  "ephemeral": false,
  "modelConfigId": "mdl_...",
  "agentContext": {
    "pruneValidateSql": true,
    "outputReasoning": true,
    "reasoningLevel": "medium",
    "responseLanguage": "zh-CN"
  }
}
```

禁止字段：`connection.password`、`model.apiKey`、OAuth token。兼容窗口内若收到，返回
`400 CLIENT_SECRET_NOT_ALLOWED`，以尽快暴露未迁移前端。

处理顺序：

1. 鉴权、大小限制（保持 10 MiB 上限但正常请求应远低于此值）。
2. 校验 session/connection ownership。
3. `Idempotency-Key` 去重。
4. 固定 agent/model/skill revisions，创建 run。
5. 写入用户消息。
6. 开始流并将 AgentScope event 映射为兼容帧。
7. 每个完成边界持久化 message/run/checkpoint。

## 7. 流式协议

### 7.1 兼容要求

阶段 1 必须从当前 AI SDK 版本捕获原始 response header 和字节帧。Java mapper 的输出要能
被未修改的 `DefaultChatTransport` 消费。不能仅凭“浏览器显示了文本”判定兼容。

必须覆盖的语义事件：

- message/run start、finish。
- text start/delta/end。
- reasoning start/delta/end。
- tool input start/delta/available。
- tool output available/error。
- tool progress。
- title、message metadata、usage。
- error、cancel、client disconnect。
- HITL question/approval pending 与 resume。

内部统一事件模型：

```json
{
  "runId":"run_...",
  "sequence":17,
  "type":"TOOL_OUTPUT_AVAILABLE",
  "messageId":"msg_...",
  "toolCallId":"call_...",
  "toolName":"explore_schema",
  "payload":{"output":{}},
  "occurredAt":"2026-07-24T10:00:00Z"
}
```

AgentScope adapter 只产生内部事件；`AiSdkDataStreamEncoder` 负责最终 wire format。这样未来
切换 AG-UI 不污染 Agent runtime。

### 7.2 顺序和重放

- `(runId, sequence)` 单调递增且唯一。
- start 必须先于 delta/end；tool input complete 先于 execution/output。
- 完成的 message 只落库一次；重复网络提交通过 idempotency key 返回原 run。
- 支持 `Last-Event-ID` 或 `GET /api/ai/runs/{runId}/events?after=` 重放。
- 客户端断开默认取消仍在模型生成中的 run；处于 HITL 的 run 不取消。

## 8. HITL API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ai/runs/{runId}` | run 状态 |
| GET | `/api/ai/runs/{runId}/events` | 断线事件重放 |
| POST | `/api/ai/runs/{runId}/actions/{actionId}:respond` | 回答问题 |
| POST | `/api/ai/runs/{runId}/actions/{actionId}:approve` | 批准工具 |
| POST | `/api/ai/runs/{runId}/actions/{actionId}:deny` | 拒绝工具 |
| POST | `/api/ai/runs/{runId}:cancel` | 取消 |
| POST | `/api/ai/runs/{runId}:resume` | 从 checkpoint 恢复并返回流 |

所有 action 写接口要求 idempotency key；已解决 action 重复提交相同内容返回原结果，不同
内容返回 409。

## 9. 管理权限

建议权限：

- `ai.model.read/manage/credential.manage`
- `ai.agent.read/manage/publish`
- `ai.skill.read/edit/publish/delete/review`
- `ai.feedback.report.read`
- `ai.run.read/cancel`

普通用户只可读被授权的有效模型、Agent、Skill 和自己的 session/run。所有管理写入、
密钥访问、发布、审批和危险工具执行写审计日志。

## 10. 契约验收

每个 API 至少包含：

- OpenAPI success + 400/401/403/404/409/429/500 样例（适用者）。
- Node 与 Java 相同 fixture 的状态码、header、JSON semantic diff。
- 权限/跨租户负例。
- 幂等和并发测试。
- 日志脱敏断言。
- 前端实际调用的 Playwright 场景。

流式接口另需字节级 fixture、语义序列校验、慢消费者、取消、断线重连和 100 次稳定性测试。
