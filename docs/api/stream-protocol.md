# AI 流式协议

`POST /api/ai/agent`、`/api/ai/chat` 和 `/api/ai/chat/v2` 返回 AI SDK UI Message Stream。
响应头：

```http
Content-Type: text/event-stream
X-Vercel-AI-UI-Message-Stream: v1
Cache-Control: no-cache
```

每帧使用标准 SSE：

```text
id: 42
data: {"type":"text-delta","id":"text-1","delta":"hello"}

```

## 事件类别

| 类别 | 常用 `type` | 说明 |
|---|---|---|
| 生命周期 | `start`, `start-step`, `finish-step`, `finish` | 回答和 Step 边界 |
| 文本 | `text-start`, `text-delta`, `text-end` | 增量正文 |
| 推理 | `reasoning-start`, `reasoning-delta`, `reasoning-end` | 可选推理摘要 |
| 工具 | `tool-input-start`, `tool-input-delta`, `tool-input-available`, `tool-output-available`, `tool-output-error` | 工具输入与结果 |
| 数据 | `data-*`, `message-metadata` | usage、标题、扩展数据 |
| 错误 | `error` | 只包含稳定错误码和安全消息 |

具体样例与 JSON Schema 位于 `docs/fixtures/stream/`。Fixture 是契约测试输入，不得放入真实
Prompt、SQL 结果或供应商响应。

## 重连与重放

- 每个持久化事件具有 Run 内单调递增序号；
- SSE `id` 与持久化事件游标对应；
- 客户端重连时发送 `Last-Event-ID`；
- 服务端先重放游标之后的事件，再接入实时流；
- 已发送事件不因重连重复执行工具。

事件查询也可使用：

```http
GET /api/ai/runs/{runId}/events?after=<sequence>
```

## Suspension 与恢复

工具需要用户确认或补充信息时，Run 进入等待状态并发送 Pending Action。前端不执行客户端
Tool，而是调用服务端 Action API：

```http
POST /api/ai/runs/{runId}/actions/{actionId}:approve
POST /api/ai/runs/{runId}/actions/{actionId}:deny
POST /api/ai/runs/{runId}/actions/{actionId}:respond
POST /api/ai/runs/{runId}:resume
```

服务端把结果转换为 `ConfirmResult` 或用户回答，从安全 Checkpoint 恢复 AgentScope 状态。

## 取消

```http
POST /api/ai/runs/{runId}:cancel
```

取消会传播到模型流和工具执行，并以终态持久化。终态不可被后来的重连、Resume 或迟到事件
恢复成 Running。

## 安全约束

- 流中不得出现 API Key、OAuth Token、连接密码或原始供应商错误；
- 工具结果按 UI Message Part 编码，未知字段向前兼容；
- Prompt 与消息保存在会话数据中，不写入安全 Checkpoint；
- 客户端必须忽略未知事件类型，但保留事件顺序。
