# AI SDK 流式协议契约

本文档冻结 DataStoria 主聊天流 (`POST /api/ai/agent`, A01) 使用的 AI SDK
UI Message Stream 协议。它是 Java `AiSdkDataStreamEncoder` 必须复刻的字节级行为
事实，而不是 AI SDK 内部类型。

## 1. 版本与事实来源

| 项 | 值 | 证据 |
|---|---|---|
| AI SDK npm 包 | `ai@6.0.103`（`package.json` 声明 `^6.0.3`） | `node_modules/ai/package.json` |
| React 绑定 | `@ai-sdk/react@^3.0.3` | `package.json` |
| 协议 | UI Message Stream（**非** legacy Data Stream 的 `0:`/`2:` 前缀格式） | `node_modules/ai/dist/index.d.ts` `uiMessageChunkSchema` |
| 响应构造 | `createUIMessageStreamResponse({ stream, headers })` | `src/app/api/ai/agent/route.ts` |
| 流来源 | `result.toUIMessageStream({ originalMessages, generateMessageId, sendReasoning, onFinish, messageMetadata, onError })` | 同上 |

> legacy `/api/ai/chat` (A02) 使用自定义 `SseStreamer`，**不**走该协议；它在 P7
> 被 HarnessAgent 替代后退役，本文不冻结其字节格式，但 contract runner 仍捕获其
> 原始字节以便 diff。

## 2. HTTP 头

响应固定头（来自 `createUIMessageStreamResponse` 显式 `headers` + SDK 默认）：

| 头 | 值 |
|---|---|
| `Content-Type` | `text/event-stream` |
| `Cache-Control` | `no-cache` |
| `Connection` | `keep-alive` |
| `X-Vercel-AI-UI-Message-Stream` | `v1`（SDK 默认注入） |
| `X-Accel-Buffering` | `no`（SDK 默认注入，关闭 Nginx buffering） |

## 3. SSE 帧格式

每个事件为一行 `data: {json-object}\n\n`。流以 `data: [DONE]\n\n` 终止。

JSON 对象必有 `type` 字段。下表列出 AI SDK v6 的全部 chunk `type`，并标注
DataStoria 当前实际会产生的类型（基于 `route.ts` 的 `streamText` + tools 注册与
`sendReasoning`/`messageMetadata`/`onFinish` 配置）。

| `type` | 关键字段 | DataStoria 是否产生 | 备注 |
|---|---|---|---|
| `start` | `messageId`, `messageMetadata?` | 是 | 消息开始 |
| `start-step` | — | 是 | 每个 LLM step 开始（`stopWhen: stepCountIs(10)`） |
| `finish-step` | — | 是 | 每个 step 结束 |
| `text-start` | `id` | 是 | 文本块开始 |
| `text-delta` | `id`, `delta` | 是 | 增量文本 |
| `text-end` | `id` | 是 | 文本块结束 |
| `reasoning-start` | `id` | 条件 | 仅 `outputReasoning=true` 且模型支持 |
| `reasoning-delta` | `id`, `delta` | 条件 | 同上 |
| `reasoning-end` | `id` | 条件 | 同上 |
| `tool-input-start` | `toolCallId`, `toolName`, `dynamic?`, `title?` | 是 | 工具调用开始 |
| `tool-input-delta` | `toolCallId`, `inputTextDelta` | 是 | 工具输入流式 |
| `tool-input-available` | `toolCallId`, `toolName`, `input` | 是 | 工具输入完成 |
| `tool-input-error` | `toolCallId`, `toolName`, `input`, `errorText` | 可能 | 工具输入解析错误 |
| `tool-approval-request` | `approvalId`, `toolCallId` | 否（当前） | HITL 阶段引入（P8） |
| `tool-output-available` | `toolCallId`, `output`, `preliminary?` | 是 | 工具结果 |
| `tool-output-error` | `toolCallId`, `errorText` | 可能 | 工具执行错误 |
| `tool-output-denied` | `toolCallId` | 否（当前） | HITL deny 路径（P8） |
| `source-url` | `sourceId`, `url`, `title?` | 否 | DataStoria 未使用 |
| `source-document` | `sourceId`, `mediaType`, `title`, `filename?` | 否 | DataStoria 未使用 |
| `file` | `url`, `mediaType` | 否 | DataStoria 未使用 |
| `data-{custom}` | `data`, `id?`, `transient?` | 否 | DataStoria 未使用自定义 data part |
| `message-metadata` | `messageMetadata` | 是 | usage 以 metadata 在 `finish` 上携带 |
| `finish` | `finishReason`, `messageMetadata` | 是 | 消息结束；被 TransformStream 注入 title |
| `error` | `errorText` | 是 | `onError` 返回脱敏文本 |
| `abort` | `reason?` | 是 | 客户端取消 |

终止：`data: [DONE]\n\n`。

## 4. title 与 usage 的承载方式

- **usage**：`messageMetadata({ part })` 回调在 `part.type === 'finish'` 时返回
  `{ usage: { ... } }`，嵌入 `finish` chunk 的 `messageMetadata`。
- **title**：`route.ts` 在 `toUIMessageStream` 之上挂了一个 `TransformStream`，
  拦截 `finish` chunk 并把 `SessionTitleGenerator`（3 秒超时）产出的标题注入
  `messageMetadata.title`。Java 复刻时必须保证标题失败不影响主回答。

## 5. 必须捕获的 fixture 场景

下列场景必须各自有一份原始字节 fixture，存放于 `docs/fixtures/stream/`。fixture
是脱敏的（替换 apiKey / 用户邮箱 / 真实 ClickHouse 主机），但保留 `type`、字段名、
顺序和 `[DONE]` 终止符。

| Fixture | 路径 | 覆盖事件序列 |
|---|---|---|
| 纯文本回答 | `text-only.jsonl` | start → start-step → text-start → text-delta* → text-end → finish-step → finish(messageMetadata.usage) → [DONE] |
| reasoning 回答 | `reasoning.jsonl` | 上述 + reasoning-start/delta/end（sendReasoning=true） |
| 工具调用（成功） | `tool-success.jsonl` | text → tool-input-start → tool-input-delta* → tool-input-available → tool-output-available → text → finish |
| 工具调用（错误） | `tool-error.jsonl` | tool-input-available → tool-output-error → finish |
| usage / title | `usage-title.jsonl` | finish chunk 携带 `messageMetadata.usage` 与 `messageMetadata.title` |
| 错误流 | `error.jsonl` | start → error(errorText) → [DONE] |
| 取消 | `cancel.jsonl` | start → text-delta* → abort → [DONE] |
| 续跑（continuation） | `continuation.jsonl` | assistant 续跑，tool-output-available 后继续 text |

## 6. 兼容判定（semantic diff 规则）

对 fixture 做 Node ↔ Java semantic diff 时：

**忽略**：
- `messageId`、`toolCallId`、`id`（text block id）、`sourceId` 等随机 ID。
- `messageMetadata.createdAt` / 任何 ISO-8601 时间戳。
- `messageMetadata.usage` 内 token 计数的微小差异（仅比较字段存在性，不比较数值相等）。
- `messageMetadata.title` 的具体文本（仅断言存在/缺失与 Node 一致）。
- `text-delta` 的切分粒度（允许 Java 把多个 delta 合并或拆分，只要最终拼接文本相等）。

**禁止忽略**（缺失或顺序错误即判失败）：
- 任何 `type` 出现/缺失。
- 事件相对顺序：`start` 必须先于 `text-start`；`text-start` 先于 `text-delta` 先于
  `text-end`；`tool-input-available` 先于 `tool-output-available`；`finish` 必须存在。
- `[DONE]` 终止符的存在。
- 响应头 `Content-Type`、`X-Vercel-AI-UI-Message-Stream`。
- `toolName`、`errorText` 的存在性。

## 7. 捕获流程

1. 在原项目 `datastoria` 起 `npm run dev`（只读，不修改代码）。
2. 用固定 mock 模型 provider 或受控真实模型重放，确保可复现。
3. 通过 `tools/contract-runner`（见 P1）抓取原始 SSE 字节，按场景切片。
4. 脱敏后写入 `docs/fixtures/stream/*.jsonl`，并记录捕获脚本、模型版本、
   AI SDK 版本到 `docs/fixtures/stream/MANIFEST.md`。

> 当前 P1 阶段先交付协议定义、schema 与语义规则；真实字节 fixture 在具备受控
> provider 环境后由 contract runner 批量捕获并入库。
