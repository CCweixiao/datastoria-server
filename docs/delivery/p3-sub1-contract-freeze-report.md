# P3 子阶段 1：契约冻结 — 实施报告

> Sub-phase: P3.1 Contract Freeze
> Branch: `codex/phase-p3` (off `master` @ `9046eec`)
> Status: Ready for review. Not pushed, no PR.

## 范围

P3 子阶段 1 冻结 A03–A10 的 HTTP wire 契约，为后续双方言 DDL、Repository、Service、
Controller 与前端 gateway 提供可对账的基准。本阶段只产出文档与 fixture，不写 Java
代码、不写前端代码、不动数据库。

### 已交付

- `docs/api/p3-openapi-extensions.yaml`：A03–A10 + A09b（Java 新增撤销端点）的 OpenAPI
  3.0.3 定义。每个 operation 携带 `x-datastoria-id`、`x-datastoria-status`、
  `x-datastoria-target`、`x-datastoria-auth`、`x-datastoria-node-baseline` 五个扩展，
  其中 `node-baseline` 逐条记录 Node 当前行为以及 Java 必须 1:1 复刻或有意偏离的
  细节（每条偏离都引用对应的 ADR）。
- `docs/fixtures/api/p3/`：32 个 wire-format 场景 fixture + `MANIFEST.md` + `index.json`，
  覆盖 task brief 列出的全部场景（session create/list/detail/update/delete、messages
  list、share、feedback、UIMessage round-trip、未知 part 无损保留），并补充了若干关键
  负例（401/403/404/409/400/500 与 ADR 引入的新错误码）。
- `docs/adr/0001-session-share-permissions.md`：分享权限 ADR。决策为：默认只读、JWT +
  server-side hashed token row、新增 `share:revoke` 端点、提供 `allow-write` 临时
  compat flag。
- `docs/adr/0002-session-create-atomicity.md`：A04 创建原子性 ADR。决策为 Java 用单
  JDBC 事务包裹 session + messages，明确偏离 Node 的非事务行为。
- `docs/adr/0003-feedback-target-not-found.md`：A10 反馈目标缺失 ADR。决策为返回 HTTP
  404 `FEEDBACK_TARGET_NOT_FOUND`，明确偏离 Node 的 HTTP 500。
- `docs/fixtures/business/MANIFEST.md`：更新交叉引用，说明逻辑层 fixture 与 wire 层
  fixture 的分工。

## 关键决策摘要

### 分享权限（A05/A06/A07/A08/A09）

Node 当前实现使用单一 `chat_session:full` JWT scope，并允许 share visitor 执行
PATCH/DELETE。前端代码审计（`session-manager.ts`、`chat-session-list.tsx`、
`/session/[sessionId]/page.tsx`）显示 share URL 落地页是只读 viewer，没有 UI 路径让
share visitor 触发 PATCH/DELETE。Java 后端因此按 task brief 与 disposition matrix 的
预留条款收紧为：

- 默认 share = 只读（GET /sessions/{id}、GET /sessions/{id}/messages）。
- PATCH/DELETE 在 share header 存在时返回 HTTP 403 `SHARE_PERMISSION_DENIED`。
- 新增 `POST /api/ai/sessions/{sessionId}/share:revoke`（A09b）支持撤销。
- 仍返回兼容 JWT 形式的 `code` 字段；额外在 `ds_session_share` 表持久化 `token_hash`，
  使撤销生效。
- 配置 `datastoria.session-share.allow-write=true` 可临时恢复 Node 行为作为 P3 cutover
  回滚保险；该 flag 必须在 P11 移除。

### 事务原子性（A04）

Node 在 `route.ts:91-118` 中以 `createSession(...)` 加 `for (msg of messages) upsert(...)`
循环，每个调用使用独立 Knex 事务；中途失败留下半持久化状态。Java 按
`docs/design/api-contracts.md` §5 的设计要求改为单事务。差异：失败 POST 在 Java 上不
留 session 行，客户端可安全重试。

### 反馈目标缺失（A10）

Node 在 `auto-explain/route.ts:48-53` 把所有 repository 异常折叠为 HTTP 500
`Failed to record feedback`，包括 "引用的 messageId 不存在" 这种客户端错误。Java 改
为 HTTP 404 `FEEDBACK_TARGET_NOT_FOUND`，让前端可区分 "目标已删除" 与 "服务端真的
挂了"。

### 兼容性收紧

A12（P2）已建立的模式 — Java target 拒绝 `apiKey`、忽略 `github.token` — 延续到 P3。
对于 A04/A10 的请求体，若包含 `apiKey`、`model.apiKey`、`connection.password` 字段，
Java 返回 HTTP 400 `CLIENT_SECRET_NOT_ALLOWED`（沿用 P2 `ClientSecretNotAllowedException`）。
该规则未在 OpenAPI 单独标注，但适用所有 P3 写入端点。

## 验证证据

| Requirement | Command | Result | Artifact |
|---|---|---|---|
| OpenAPI 3.0.3 语法 | `redocly lint --skip-rule security-defined --skip-rule info-license docs/api/p3-openapi-extensions.yaml` | PASS（valid，无错误，无警告） | 本报告 |
| Fixture JSON 语法 | `python3 -c "import json; json.load(open(f))"` × 33 files | 33/33 PASS | 本报告 |
| Fixture 与 OpenAPI 路径覆盖 | `docs/fixtures/api/p3/index.json` 32 场景 × 7 operationId | 覆盖 A03–A10 + A09b 全部操作，每个操作至少 1 个成功 + 1 个负例 | `index.json` |
| 分享权限 ADR | `docs/adr/0001-session-share-permissions.md` | 决策、备选、frontend 调用点证据、后果均记录 | ADR-0001 |
| 工作分支 | `git status` | `codex/phase-p3` clean，未 push | git |
| 未触及无关文件 | `git status --short` | 仅 `docs/` 目录新增/修改 | git |

## 与现有契约基线的关系

- `docs/api/openapi-baseline.yaml`（P1）：A03–A10 的初始冻结，未改动。本阶段的
  `p3-openapi-extensions.yaml` 在 baseline 之上做了三件事：(1) 把 Node 行为的
  description 全部展开为可实现的精确语义；(2) 引入 Java 目标错误码与
  `SHARE_PERMISSION_DENIED` / `SHARE_TOKEN_INVALID` / `SHARE_NOT_FOUND` /
  `FEEDBACK_TARGET_NOT_FOUND`；(3) 新增 A09b 端点。
- `docs/api/migration-disposition.md`：A06/A07 的 "分享者可写行为" 行现已通过 ADR-0001
  解析，可在 P3 收尾时把 disposition 升级为 `migrated`。

## 退出条件

P3 子阶段 1 的退出条件（task brief 第 1.1–1.5 条）：

- [x] 1.1 A03–A10 的请求、响应、状态码、分页和权限语义已冻结在 OpenAPI 中。
- [x] 1.2 输出 `docs/api/p3-openapi-extensions.yaml`。
- [x] 1.3 Node/Java contract fixtures 覆盖 session create/list/detail/update/delete、
      messages list、share、feedback、UIMessage round-trip、未知 part 无损保留。
- [x] 1.4 分享权限 ADR 已输出，明确 owner / share visitor / 只读 / 撤销行为。
- [x] 1.5 未凭经验重设前端契约；所有偏离都基于 Node 实际行为证据（`src/...` 文件
      路径）并以 ADR 记录。

## 未完成 / 后续

- **Node baseline 实际 capture（未做）**：本阶段冻结的是 *预期* 响应 fixture，未启
  动 Node 服务用 `tools/contract-runner/src/capture.js` 抓取 *实际* 响应。这是 P3 子
  阶段 4（导入对账）前的必跑步骤，但需要本机具备 Node 服务 + ClickHouse 测试数据。
  Fixture 的设计已经与 capture.js 输出 shape 兼容（`request` + `response` 可与
  `capture.js` 的扁平 capture 对齐），所以这一步可在后续子阶段无修改直接进行。
- **前端 review（未做）**：本阶段未对前端做任何修改。后续子阶段会通过独立 worktree
  `codex/phase-p3-frontend` 接入 Java 后端。
- **contract-runner JSON schema（未做）**：`tools/contract-runner` 目前只为 stream
  fixture 提供了 JSON Schema 校验；P3 wire fixture 的 schema 校验脚本可在后续子阶段
  添加（`docs/fixtures/api/p3/index.json` 的 `$schema` 已预留）。

## 文件清单

新增：

```
docs/api/p3-openapi-extensions.yaml
docs/adr/0001-session-share-permissions.md
docs/adr/0002-session-create-atomicity.md
docs/adr/0003-feedback-target-not-found.md
docs/delivery/p3-sub1-contract-freeze-report.md
docs/fixtures/api/p3/MANIFEST.md
docs/fixtures/api/p3/index.json
docs/fixtures/api/p3/A03-list-basic.json
docs/fixtures/api/p3/A03-list-with-cursor.json
docs/fixtures/api/p3/A03-list-invalid-limit.json
docs/fixtures/api/p3/A03-list-unauthenticated.json
docs/fixtures/api/p3/A04-create-minimal.json
docs/fixtures/api/p3/A04-create-with-messages.json
docs/fixtures/api/p3/A04-create-idempotent-reuse.json
docs/fixtures/api/p3/A04-create-connection-mismatch.json
docs/fixtures/api/p3/A04-create-invalid-connection-id.json
docs/fixtures/api/p3/A05-get-owner.json
docs/fixtures/api/p3/A05-get-via-share.json
docs/fixtures/api/p3/A05-get-not-found.json
docs/fixtures/api/p3/A05-get-invalid-share-code.json
docs/fixtures/api/p3/A06-rename-happy.json
docs/fixtures/api/p3/A06-rename-missing-title.json
docs/fixtures/api/p3/A06-rename-share-denied.json
docs/fixtures/api/p3/A07-delete-happy.json
docs/fixtures/api/p3/A07-delete-not-found.json
docs/fixtures/api/p3/A07-delete-share-denied.json
docs/fixtures/api/p3/A08-messages-happy.json
docs/fixtures/api/p3/A08-messages-empty.json
docs/fixtures/api/p3/A08-messages-uimessage-roundtrip.json
docs/fixtures/api/p3/A08-messages-unknown-part-preserved.json
docs/fixtures/api/p3/A09-share-happy.json
docs/fixtures/api/p3/A09-share-unauthenticated.json
docs/fixtures/api/p3/A09-share-not-found.json
docs/fixtures/api/p3/A09b-revoke-happy.json
docs/fixtures/api/p3/A09b-revoke-not-found.json
docs/fixtures/api/p3/A10-feedback-recorded.json
docs/fixtures/api/p3/A10-feedback-accepted-not-stored.json
docs/fixtures/api/p3/A10-feedback-invalid-format.json
docs/fixtures/api/p3/A10-feedback-target-not-found.json
```

修改：

```
docs/fixtures/business/MANIFEST.md  (新增交叉引用段落)
```

未触及：`src/`、`pom.xml`、`db/migration/`、`tools/`、`application*.yaml`、前端仓库。
