# API 迁移处置矩阵

本文档是 A01-A29 的冻结清单。每一项都有明确的 `disposition`，作为 P1 契约冻结的
退出证据。`disposition` 取值：

- `active`：当前 Node 实现存在且有前端调用，必须迁移。
- `alias`：与另一个 API 共享 handler，不单独实现。
- `deprecated`：当前仍可用，但计划退役，迁移窗口内返回 deprecation 头。
- `frontend-only`：只服务 Next.js 前端基础设施，无业务/凭据逻辑，保留。
- `stub`：当前返回空/占位，迁移时按目标契约实现。

## 矩阵

| ID | 方法 + 路径 | disposition | 目标阶段 | 前端调用点 | 备注 |
|---|---|---|---|---|---|
| A01 | `POST /api/ai/agent` | active | P4/P6/P7/P8 | `chat-factory.ts:534` (DefaultChatTransport) | UI Message Stream；A01b 别名同 handler |
| A01b | `POST /api/ai/chat/v2` | alias | P4 | 同 A01 | `route.ts` 直接 re-export agent POST |
| A02 | `POST /api/ai/chat` | deprecated | P7/P11 | `chat-factory.ts:534` (legacy mode) | 自定义 SseStreamer；HarnessAgent 达标后退役 |
| A03 | `GET /api/ai/chat/sessions` | active | P3 | `remote-session-repository.ts:91` | limit 1..500，cursor 不透明 |
| A04 | `POST /api/ai/chat/sessions` | active | P3 | `remote-session-repository.ts:126` | 事务创建 session+messages，幂等 sessionId |
| A05 | `GET /api/ai/chat/sessions/{id}` | active | P3 | `remote-session-repository.ts:65` | owner 或 share code |
| A06 | `PATCH /api/ai/chat/sessions/{id}` | active | P3 | `remote-session-repository.ts:153` | 重命名；分享者是否可改名需 ADR |
| A07 | `DELETE /api/ai/chat/sessions/{id}` | active | P3 | `remote-session-repository.ts:172` | 204 成功 |
| A08 | `GET /api/ai/chat/sessions/{id}/messages` | active | P3 | `remote-session-repository.ts:108` | 按 sequence 升序 |
| A09 | `POST /api/ai/sessions/{id}/share` | active | P3 | `chat-panel.tsx:553` | 仅 owner；JWT code，far-future 过期 |
| A10 | `POST /api/ai/chat/feedback/auto-explain` | active | P3 | `query-error-ai-explanation.tsx:164` | 无远程存储返回 202 |
| A11 | `GET /api/ai/chat/feedback/report` | active | P10 | `auto-explain-feedback-report.tsx:38` | RBAC allowlist |
| A12 | `POST /api/ai/models/available` | active | P2 | `available-models-client.ts:32` | 当前可传 github.token，目标改服务端 |
| A13 | `POST /api/ai/codex/auth/token` | active | P10 | `codex-login-component.tsx:117` | OAuth code 交换，token 经浏览器（目标后移） |
| A14 | `POST /api/ai/codex/auth/refresh` | active | P10 | `use-model-config.ts:127` | token 经浏览器 |
| A15 | `POST /api/ai/github/auth/device/code` | active | P10 | `github-login-component.tsx:70` | device flow 启动 |
| A16 | `POST /api/ai/github/auth/device/token` | active | P10 | `github-login-component.tsx:87` | token 经浏览器 |
| A17 | `POST /api/ai/github/auth/refresh` | active | P10 | `use-model-config.ts:121` | token 经浏览器 |
| A18 | `GET /api/ai/github/models` | active | P10 | 内部代理（Authorization 转发） | token 经浏览器 header |
| A19 | `GET /api/ai/commands` | active | P5 | `agent-command-context.tsx:28` | 由 published skill 生成 |
| A20 | `GET /api/ai/skills` | active | P5/P9 | `skills-edit.tsx:20` | System-authored 排除 |
| A21 | `POST /api/ai/skills` | active | P9 | `skills-edit.tsx` | 草稿，不自动发布 |
| A22 | `GET /api/ai/skills/{id}` | active | P5 | `skills-detail-view.tsx:163,263` | includeDraft 仅编辑者 |
| A23 | `PATCH /api/ai/skills/{id}` | active | P9 | `skills-detail-view.tsx:286` | action: publish/saveAndPublish/publishResources |
| A24 | `DELETE /api/ai/skills/{id}` | active | P9 | `skills-detail-view.tsx` | disk-backed 返回 400 |
| A25 | `GET /api/ai/skills/{id}/resource` | active | P5 | `skills-detail-view.tsx:188,227` | path 路径穿越防护 |
| A26 | `POST /api/ai/skills/actions/review` | active | P9 | `skills-detail-view.tsx:527` | scope=file 当前唯一 |
| A27 | `GET /api/ai/rca/templates` | active | P7/P10 | `template-based-collector.ts:289,305` | YAML seed |
| A28 | `GET/POST /api/auth/{*}` | active | P2/P10 | `base-path.ts:30`、`auth.ts:143` | NextAuth；最终 Spring Security/OIDC |
| A29 | `GET /api/connections/templates` | stub | P2/P10 | `connection-edit-component.tsx:153` | 当前返回 `[]` |

## 安全待处理项（迁移时强制）

- A01/A02 body 中的 `model.apiKey`、`connection.password`、OAuth token 必须被 Java
  拒绝（`400 CLIENT_SECRET_NOT_ALLOWED`）。
- A12/A13/A14/A16/A17/A18 当前让 token 进入浏览器；最终必须服务端托管，浏览器
  只持 session cookie。
- A06/A07 分享者可写行为先冻结，安全评审后用 ADR 决定是否收紧。

## 退出条件映射

- 每个 `active` 项：P1 阶段已有 OpenAPI operation + 前端调用点记录。
- 每个 `alias`/`deprecated`/`stub`：已记录处置和目标阶段。
- 流式事件（A01）的全部 chunk `type` 已进入 `stream-protocol.md`。
