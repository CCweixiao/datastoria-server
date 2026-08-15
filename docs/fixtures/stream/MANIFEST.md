# Stream Fixture Manifest

## 用途

本目录下的 `.jsonl` 文件是 AI SDK UI Message Stream 的 chunk 序列样本，每行一个
JSON 对象，对应 SSE 帧 `data: {json}` 的 payload 部分（不含 `data: ` 前缀和 `\n\n`）。

这些 fixture 用于：
1. 校验 Java `AiSdkDataStreamEncoder` 产出的事件序列结构正确。
2. 通过 JSON Schema 校验每个事件样例的字段和类型。
3. 文档化每个场景必须覆盖的事件类型。

## 文件清单

| 文件 | 覆盖场景 | 事件序列 |
|---|---|---|
| `schema.json` | chunk JSON Schema（draft 2020-12） | 用于校验单行结构 |
| `text-only.jsonl` | 纯文本回答 | start → start-step → text-start → text-delta×2 → text-end → finish-step → finish(usage) |
| `reasoning.jsonl` | reasoning 可见 | start → start-step → reasoning-start/delta/end → text-* → finish-step → finish |
| `tool-success.jsonl` | 工具成功 | start → text → tool-input-start/delta/available → tool-output-available → 第二 step text → finish |
| `tool-error.jsonl` | 工具执行错误 | start → tool-input-available → tool-output-error → finish(error) |
| `usage-title.jsonl` | usage + title | start → text → finish(metadata.usage + metadata.title) |
| `error.jsonl` | 流错误 | start → start-step → error |
| `cancel.jsonl` | 取消/断线 | start → start-step → text-start → text-delta → abort |
| `continuation.jsonl` | 续跑（continuation） | start → tool-output-available → text → finish |
| `question.jsonl` | HITL 提问挂起 | start → text → tool-input-start/delta/available → data-pending-action(question) → finish-step → finish |

## 来源与维护方式

- **当前状态**：基于 AI SDK v6 `uiMessageChunkSchema`（`ai@6.0.103`）手工构造的规范
  样本，用于冻结协议结构。
- **Java 实现**：Controller 和 Encoder 测试读取这些样例，校验事件类型、顺序和终止符。
- **变更要求**：协议变更必须同步修改 Schema、fixture、Java 测试和公开文档。

## 兼容性校验规则（摘要）

完整规则见 `docs/api/stream-protocol.md` 第 6 节。要点：

- 忽略 `messageId`、`toolCallId`、`id`、`sourceId` 等随机 ID。
- 忽略 ISO-8601 时间戳与 token 计数数值。
- 允许 `text-delta` 切分粒度差异（拼接后相等即可）。
- **禁止**忽略：事件 `type` 的出现/缺失、相对顺序、`[DONE]` 终止符、
  `toolName`/`errorText` 的存在性、`finish` chunk 的存在。

## 验证 fixture 自身有效性

```bash
cd datastoria-web
npm run docs:validate-fixtures
```

GitHub Pages 流水线执行 `npm run docs:check`，在发布前校验 fixture、OpenAPI 和 VitePress
文档构建。
