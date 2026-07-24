# DataStoria Contract Runner

Node.js 工具，用于捕获 Node 基线 API 的响应并与 Java 实现做 semantic diff。
本工具是 P1 契约冻结的执行入口，也用于后续每个阶段的 Node/Java 等价性验证。

## 依赖

- Node.js >= 18（使用原生 `fetch`）

## 安装

```bash
cd tools/contract-runner
npm ci
```

## 命令

### 1. 校验 fixture 自身有效性

验证 `docs/fixtures/stream/*.jsonl` 的每一行是否符合 `schema.json`：

```bash
npm run validate-fixtures
```

这是 fixture 完整性的守门命令：故意删除某 chunk 的 `type` 字段时必须失败，
证明测试有效。

### 2. 捕获响应

对运行中的服务（Node 或 Java）发起一次请求并保存原始响应：

```bash
# JSON API
node src/capture.js http://localhost:3000 POST /api/ai/chat/sessions \
  payload.json capture-node-session.json

# 流式 API（需要鉴权头）
DS_USER_EMAIL=dev@example.com \
node src/capture.js http://localhost:3000 POST /api/ai/agent \
  chat-payload.json capture-node-agent.json
```

环境变量：
- `DS_USER_EMAIL`：注入 `x-datastoria-user-email` 头。
- `DS_SHARE_CODE`：注入 `X-Session-Share-Code` 头。

> 捕获文件含原始响应字节，**提交前必须脱敏**（替换真实邮箱、token、ClickHouse
> 主机）。本仓库 `.gitignore` 不自动排除捕获文件，请人工审查。

### 3. 语义 diff

比较两个捕获文件：

```bash
# JSON 响应
node src/semantic-diff.js capture-node-session.json capture-java-session.json

# 流式响应（自动检测 capture.stream）
node src/semantic-diff.js capture-node-agent.json capture-java-agent.json
```

退出码：`0` 表示 SEMANTIC MATCH，`1` 表示存在差异。

## semantic diff 规则

### JSON 模式

- 字段缺失/新增 → 差异（不可忽略）。
- 值差异 → 差异，**除了**：
  - `messageId`、`toolCallId`、`id`、`sourceId`、`approvalId`：仅要求存在。
  - `createdAt`、`updatedAt`、`occurredAt`：完全忽略。
  - `messageMetadata.usage.*Tokens`：仅要求存在。
  - `messageMetadata.title`：仅要求存在。

### Stream 模式（UI Message Stream）

- 每个 `type` 的出现次数必须一致。
- 顺序不变量：`start` 先于 `text-start` 先于 `text-delta` 先于 `text-end`；
  `tool-input-available` 先于 `tool-output-available`；`start` 先于 `finish`。
- `finish` chunk 必须存在且 `finishReason` 一致。
- `text-delta`/`reasoning-delta` 按 `id` 折叠后拼接文本必须相等（允许切分差异）。
- `tool-input-available` 的 `toolName` 序列必须一致。
- `errorText`、`toolName` 的存在性不可忽略。
- `[DONE]` 终止符：由 capture 的 `done` 标记，缺失记为差异。

完整规则见 `docs/api/stream-protocol.md` §6。

## 目录结构

```
tools/contract-runner/
├── package.json
├── README.md
└── src/
    ├── capture.js          # 捕获原始响应
    ├── semantic-diff.js    # JSON + stream 语义 diff（也可作为库导入）
    ├── sse.js              # SSE 解析
    └── validate-fixtures.js # fixture schema 校验
```
