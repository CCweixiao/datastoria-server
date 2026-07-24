# 前端调用点与 Playwright 场景

本文档记录每个 API 的前端实际调用点（事实来自 `/Users/jielongping/OpenProjects/datastoria`
冻结分支），并定义验证迁移等价性的 Playwright 场景。前端调用点明细同时记录在
`migration-disposition.md` 的矩阵中。

## 1. 调用点清单

所有前端 HTTP 调用使用原生 `fetch`，无 axios。URL 通过 `BasePath.getURL()` 解析
（前缀 `NEXT_PUBLIC_BASE_PATH`，默认空）。鉴权依赖同源 cookie + proxy 注入的
`x-datastoria-user-email`。

| API | 调用文件:行 | 触发上下文 |
|---|---|---|
| A01 | `src/components/chat/chat-factory.ts:534` | `DefaultChatTransport` fetch wrapper，`mode=v2` 时命中 |
| A02 | `src/components/chat/chat-factory.ts:534` | 同 transport，`mode!=v2` 时命中 |
| A03 | `src/components/chat/session/remote-session-repository.ts:91` | 会话列表加载 |
| A04 | `src/components/chat/session/remote-session-repository.ts:126` | 首条消息后创建会话 |
| A05 | `src/components/chat/session/remote-session-repository.ts:65` | 打开会话 |
| A06 | `src/components/chat/session/remote-session-repository.ts:153` | 重命名 |
| A07 | `src/components/chat/session/remote-session-repository.ts:172` | 删除 |
| A08 | `src/components/chat/session/remote-session-repository.ts:108` | 消息回放 |
| A09 | `src/components/chat/view/chat-panel.tsx:553` | 分享按钮 |
| A10 | `src/components/query-tab/query-response/query-error-ai-explanation.tsx:164` | 反馈提交 |
| A11 | `src/components/ai/feedback/auto-explain-feedback-report.tsx:38` | 报告页加载 |
| A12 | `src/lib/ai/llm/available-models-client.ts:32` | 模型设置加载 |
| A13 | `src/components/settings/models/codex-login-component.tsx:117` | Codex 登录回调 |
| A14 | `src/hooks/use-model-config.ts:127` | Codex token 刷新 |
| A15 | `src/components/settings/models/github-login-component.tsx:70` | GitHub device 登录 |
| A16 | `src/components/settings/models/github-login-component.tsx:87` | device token 轮询 |
| A17 | `src/hooks/use-model-config.ts:121` | Copilot token 刷新 |
| A18 | （内部模型目录刷新） | Authorization 头代理 |
| A19 | `src/components/chat/agent-command-context.tsx:28` | slash command 列表 |
| A20 | `src/components/settings/skills/skills-edit.tsx:20` | Skill 管理页 |
| A21 | `src/components/settings/skills/skills-edit.tsx` | 创建 Skill |
| A22 | `src/components/settings/skills/skills-detail-view.tsx:163,263` | Skill 详情 |
| A23 | `src/components/settings/skills/skills-detail-view.tsx:286` | 发布/保存 |
| A24 | `src/components/settings/skills/skills-detail-view.tsx` | 删除 |
| A25 | `src/components/settings/skills/skills-detail-view.tsx:188,227` | 资源加载 |
| A26 | `src/components/settings/skills/skills-detail-view.tsx:527` | 文件审核 |
| A27 | `src/lib/ai/tools/clickhouse/rca/impl/template-based-collector.ts:289,305` | RCA 收集 |
| A28 | `src/lib/base-path.ts:30`、`src/auth.ts:143` | NextAuth basePath |
| A29 | `src/components/connection/connection-edit-component.tsx:153` | 连接编辑加载 |

## 2. Playwright 场景

下列场景用于在 Node/Java 双轨下做前端行为等价验证。每个场景在 `endpoint` flag
切到 Node 和 Java 时各跑一次，比较网络请求与可见结果。

> 当前 P1 阶段只定义场景规范与期望断言；Playwright spec 实现随对应阶段前端接入
> 一起落地（首个接入点为 P2 的模型设置页）。

### S1 会话生命周期（A03/A04/A05/A06/A07/A08）

1. 登录，打开聊天页。
2. 发送一条消息 → 断言 `POST /api/ai/chat/sessions` 200，session 写入。
3. 刷新 → 断言 `GET sessions` 200 且列表包含新会话；`GET messages` 200 顺序正确。
4. 重命名 → 断言 `PATCH` 200，UI 标题更新。
5. 分享 → 断言 `POST share` 200，匿名窗口 `GET {id}` 200。
6. 删除 → 断言 `DELETE` 204，列表不再包含。

### S2 普通多轮聊天流（A01）

1. 发送 "分析慢查询" → 断言 `POST /api/ai/agent` 返回 `text/event-stream` 且
   `X-Vercel-AI-Data-Stream: v1`。
2. 断言页面出现 assistant 文本（start/text-delta/text-end/finish 事件序列）。
3. 刷新后 `GET messages` 回放与流结果语义一致。
4. 取消 → 断言 abort 事件与 UI 取消态。

### S3 工具调用渲染（A01 + tools）

1. 触发一次需要工具的提问 → 断言 tool-input-start/delta/available 与
   tool-output-available 出现，工具卡片渲染输入/输出。
2. 工具错误场景 → 断言 tool-output-error 卡片与可重试态。

### S4 reasoning 可见性（A01）

1. `outputReasoning=true` → 断言 reasoning-start/delta/end 事件，UI 显示推理块。
2. `outputReasoning=false` → 断言无 reasoning 事件。

### S5 title 与 usage（A01）

1. 完成一轮 → 断言 finish chunk 的 `messageMetadata.title` 与
   `messageMetadata.usage` 存在；会话标题被更新。

### S6 模型设置（A12，P2 接入）

1. 打开设置页 → 断言 `POST /api/ai/models/available` 200，`systemModels` 列表渲染。
2. 选择模型 → 断言偏好写入；刷新后保持。

### S7 Skill 管理只读（A19/A20/A22/A25，P5 接入）

1. Skill 列表页 → 断言 `GET /api/ai/skills` 200，catalog 渲染。
2. 打开详情 → 断言 `GET /api/ai/skills/{id}` 200 与资源加载。
3. slash command 菜单 → 断言 `GET /api/ai/commands` 200。

### S8 反馈与报告（A10/A11）

1. 错误解释卡片提交反馈 → 断言 `POST feedback/auto-explain` 200/202。
2. 管理员打开报告页 → 断言 `GET feedback/report` 200，聚合数字渲染。

### S9 分享访问边界（A05/A06/A07/A08）

1. 匿名窗口带 `X-Session-Share-Code` 访问 → 断言只读成功。
2. 匿名尝试 `PATCH`/`DELETE` → 断言与 Node 行为一致（当前允许，ADR 后收紧）。

### S10 取消与断线（A01）

1. 流式过程中关闭页面再重连 → 断言 `GET messages` 能回放已落库部分。
2. 慢消费者 / 网络中断 → 断言 run 被取消（生成中）或保持（HITL 中）。

## 3. 双轨比较规则

对每个场景：
- 记录 Node 与 Java 两次运行的请求/响应（status、关键 header、JSON 或 SSE）。
- 用 `tools/contract-runner` 的 semantic diff 比较（见 contract-runner README）。
- 差异必须进入 `docs/api/adr/` 或在报告中标注为已批准差异。
