# P3 实施报告 — 会话、消息、反馈与分享

> Stage: P3 (sessions / messages / feedback / share)
> Java branch: `codex/phase-p3` (off `master` @ `9046eec`, 7 commits)
> Frontend branch: `codex/phase-p3-frontend` (off `master` @ `22b7ae4`, 1 commit)
> Status: Ready for review. **Not pushed, no PR, not merged to master.**

## 1. 范围

P3 把 chat 产品的会话数据从 Node.js Runtime 迁移到独立 Spring Boot 服务，并保留 Node 端
兼容前端所需的全部 REST 端点。按 `docs/migration-plan.md` 的 P3 边界，本次交付覆盖：

- A03 列表（cursor 分页 + connectionId 过滤）
- A04 创建（带初始消息的事务性 upsert）
- A05 / A06 / A07 单会话读 / 重命名 / 删除（含 owner + share visitor 双访问路径）
- A08 消息回放（按 sequence ASC）
- A09 / A09b 会话分享的签发与撤销（HS256 JWT + 服务端 SHA-256 哈希行）
- A10 auto-explain feedback（upsert，store-enabled 开关）
- 旧数据 JSONL 导入与校验工具
- 前端 endpoint 切换网关

非目标（已在 PRD/PDC 明确）：

- 不实现 chat completion / SSE（P4 范畴）
- 不实现 OAuth（P10）
- 不删 Node 端 `route.ts`，保留作为回退（P11）

## 2. 子阶段交付物

| 子阶段 | Commit | 内容 |
| --- | --- | --- |
| P3.1 契约冻结 | `25723a9` | OpenAPI extensions、32 个 wire fixture、3 份 ADR、`p3-sub1-contract-freeze-report.md` |
| P3.2 双方言 DDL | `e0300de` | V4 Flyway：`ds_chat_session` / `ds_chat_message` / `ds_feedback_event` / `ds_session_share`（SQLite + MySQL 各一份） |
| P3.3 Domain + Repository | `96ea02b` + `da91f30` | 4 个 domain record + 4 个 JdbcClient repository + `SessionListCursor` + `SqlTimestamps` |
| P3.4 Service / Controller / DTO / Exception | `fb974a0` | 4 个 service + 4 个 controller + 12 个 DTO + 4 个异常 + `SessionShareConfig` + `CompatExceptionHandler` |
| P3.5 API 契约测试 | `7b785bd` | `P3ApiTest` 35 + `P3UnauthenticatedTest` 2 + `P3FeedbackAcceptedTest` 1 + `IdentityWebFilter` 改造 |
| P3.6 旧数据 JSONL 导入 | `240d4f7` | `P3Importer` + `P3ImportRunner` + 9 个集成测试 + `docs/migration/p3-jsonl-format.md` |
| P3.7 前端 endpoint 网关 | `20fa863` (frontend) | `session-api-base.ts` + `RemoteSessionRepository` 改造 + `chat-panel.tsx` 改造 + 10 个测试 |

代码量：

- Java 后端：105 文件，8780 行（49 主代码 / 12 测试 / 41 文档 / 2 DDL / 1 pom）。
- 前端：6 文件，283 行（2 新文件 + 4 修改）。
- 测试用例：Java 169（P3 新增 47），前端 419（P3 新增 10）。

## 3. 关键设计

### 3.1 数据模型与分页

`ds_chat_session` / `ds_chat_message` / `ds_feedback_event` 与 Node `chat_sessions` /
`chat_messages` / `feedback_events` 列名与列类型 1:1 对应；额外添加：

- 所有行加 `tenant_id` 列，所有查询带 tenant 维度（cross-tenant isolation 测试已通过）。
- `ds_session_share` 是 Java 新增表：Node 把 share JWT 直接返回前端、不持久化任何信息，
  Java 用 `(tenant_id, token_hash)` UNIQUE 约束持久化 SHA-256(JWT) 以支持撤销而不轮换
  签名密钥（ADR-0001）。
- 软删除不用：sessions/messages/feedback 硬删，与 Node 一致；share 行保留审计，撤销只
  把 `revoked_at` 填上、靠 `active_key` 生成列翻转。

A03 列表按 `(updated_at DESC, id DESC)` keyset 分页，cursor 是
`yyyy-MM-dd HH:mm:ss.SSS|<session_id>` 的 base64。`SqlTimestamps.toParamMillis` 把
`updated_at` 截断到毫秒，避免 SQLite TEXT 字典序把 cursor 自身行再次返回。

### 3.2 访问控制

`SessionService.resolveAccess(sessionId, identity, shareCode, writeRequired)` 是 A05/A06/A07/
A08 共享的入口：

- 无 share code：按 `(tenant_id, user_id, id)` 解析 owner；未找到 → HTTP 404 `Not found`
  plain text（防跨租户枚举）。
- 有 share code：进入 `SessionShareService.verify(code, sessionId)`，任一失败均返回
  HTTP 403 `Invalid session share code`：JWT 签名/aud/sub/exp 校验、token_hash 行存在、
  `revoked_at IS NULL`、`expires_at > now`、session 行存在。
- 写操作（A06/A07）通过 share 访问时额外检查 `datastoria.session-share.allow-write`
  （默认 `false`）；不满足返回 HTTP 403 ProblemDetail `SHARE_PERMISSION_DENIED`
  （ADR-0001）。

### 3.3 事务原子性（A04，ADR-0002）

A04 把"创建 session + 写初始 messages"放在单个 `TransactionTemplate.execute` 块内。这是
对 Node 行为的有意偏离：Node 用循环 `for (msg of messages) upsert(msg)`，每个 upsert
独立事务，中途失败留下半持久化状态。Java 失败时整批回滚，客户端可安全重试。差异已在
fixture `A04-create-connection-mismatch.json` 与 OpenAPI `x-datastoria-node-baseline`
扩展中记录。

### 3.4 反馈目标缺失（A10，ADR-0003）

Node 把所有 repository 异常折叠为 HTTP 500。Java 改为：当请求体中的 `messageId` 在
`ds_chat_message` 不存在时返回 HTTP 404 ProblemDetail `FEEDBACK_TARGET_NOT_FOUND`，让
前端可区分"目标已删除"（客户端可恢复）与"服务端真挂了"。

### 3.5 异常模型双轨

- ProblemDetail（RFC 9457）：Java 引入的新错误（`SHARE_PERMISSION_DENIED`、
  `SHARE_NOT_FOUND`、`FEEDBACK_TARGET_NOT_FOUND` 等），通过 `GlobalExceptionHandler` 统一
  序列化为 `application/problem+json`。
- Plain text：与 Node wire 兼容的错误（`Not found`、`Invalid session share code`、
  `Session connectionId mismatch`、`Authentication required`、`Invalid request format`）通过
  `PlainTextException` 子类携带固定 body，handler 直接写 `text/plain; charset=utf-8`。
- `CompatExceptionHandler`：scope 限定到 `io.datastoria.server.api.compat` 的
  `@RestControllerAdvice`，把 WebFlux body 解码阶段的 `ServerWebInputException` 转为
  plain text `Invalid JSON in request body`（filter 阶段抛的异常会绕过全局 advice，所以
  需要这一层）。

### 3.6 dev 身份与匿名开关

`IdentityWebFilter` 在 `datastoria.identity.allow-anonymous=true`（默认）时，缺失
`x-datastoria-user-email` header 自动注入 `dev@example.com`；为 `false` 时直接写出
HTTP 401 plain text `Authentication required`。这让 `P3UnauthenticatedTest` 能验证 401
行为，而其他 API 测试仍走默认 dev 身份。

### 3.7 202 recorded:false 路径

`FeedbackService` 接受 `datastoria.feedback.store-enabled=false`，此模式下 upsert 不再
落库，返回 HTTP 202 `{recorded:false}`。`P3FeedbackAcceptedTest` 覆盖。

### 3.8 JSONL 导入工具（P3.6）

`P3Importer` + `P3ImportRunner`（CommandLineRunner，`--p3.import.path` 激活）：

- 输入：`manifest.json` + `sessions.jsonl` / `messages.jsonl` / `feedback.jsonl` /
  `shares.jsonl`（可选）。
- 幂等：lookup-then-upsert keyed on natural key（见 `docs/migration/p3-jsonl-format.md`
  §5.1）。同一 bundle 重跑产生 0 新插入、N 更新，行数与 checksum 稳定。
- Dry-run：`--p3.import.dry-run=true` 解析、校验、计数但不写库。
- 退出码：0 成功 / 1 行错误或 checksum 不匹配 / 2 manifest/IO 错误。
- 测试覆盖：happyPath、idempotency、dryRun、manifestMismatch、missingManifest、
  crossTenant、invalidJson、missingRequiredField、missingFile。

### 3.9 前端 endpoint 切换（P3.7）

`session-api-base.ts` 暴露：

- `getSessionApiBase()`：Node 模式返回 `BasePath.getBasePath()`，Java 模式返回配置的
  `NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL`。
- `sessionIdentityHeaders()`：Java 模式注入 `x-datastoria-user-email` dev header。
- `isJavaSessionBackend()`：便捷开关查询。

`RemoteSessionRepository` 与 `chat-panel.tsx` 的 share 调用换成 `${getSessionApiBase()}/...`
+ `sessionFetch()` 包装。Java 与 Node 共享同样的 wire 契约，所以无需 per-method
gateway（这是与 P2 configuration gateway 的核心差异）。`NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND`
默认 `node`，回退即重新构建并部署。

## 4. 测试矩阵

### 4.1 Java 后端（`./mvnw test`）

```
Tests run: 169, Failures: 0, Errors: 0, Skipped: 0
```

P3 相关测试：

| 类 | 测试数 | 覆盖 |
| --- | --- | --- |
| `P3ApiTest` | 35 | A03-A10 happy + 负例（401/403/404/409/400） + cross-tenant |
| `P3UnauthenticatedTest` | 2 | allow-anonymous=false 时的 401 路径 |
| `P3FeedbackAcceptedTest` | 1 | store-enabled=false 时的 202 路径 |
| `V4SchemaSmokeTest` | 5 | 表/列/约束/FK/索引 on SQLite |
| `SqliteChatSessionRepositoryTest` | ~10 | repository CRUD + cursor 分页 + 幂等 upsert |
| `SqliteChatMessageRepositoryTest` | ~6 | message upsert + sequence + findBySession |
| `SqliteFeedbackEventRepositoryTest` | ~6 | feedback upsert + 跨租户隔离 |
| `SqliteSessionShareRepositoryTest` | ~5 | issue / findActive / revoke / findByTokenHash |
| `SessionListCursorTest` | 3 | encode / parse / 回环 |
| `P3ImporterTest` | 9 | JSONL 导入全路径 |
| `MysqlRepositoryIT` | skipped | Docker 不可用时自动跳过（CI 的 mysql-contract job 兜底） |

### 4.2 前端（`npx vitest run`）

```
Test Files: 80 passed
Tests: 419 passed
```

P3 新增 10 个测试（`session-api-base.test.ts` 8 + `remote-session-repository.test.ts` 2）。
TypeScript typecheck 与 ESLint 均通过。

## 5. 与 Node 基线的偏离

按 ADR 列出三处有意偏离（每处都有 frontend 调用点证据与 ADR 编号）：

| 项 | Node 行为 | Java 行为 | ADR |
| --- | --- | --- | --- |
| A04 事务原子性 | 循环 upsert，半持久化可能 | 单事务回滚 | `docs/adr/0002-session-create-atomicity.md` |
| A06/A07 share 写权限 | `chat_session:full` scope 允许写 | 默认只读，`allow-write=true` 兼容 | `docs/adr/0001-session-share-permissions.md` |
| A10 反馈目标缺失 | HTTP 500 | HTTP 404 `FEEDBACK_TARGET_NOT_FOUND` | `docs/adr/0003-feedback-target-not-found.md` |

其他 wire 兼容字段（状态码、JSON shape、plain text 错误体、cursor 编码、JWT claims）均
与 Node 1:1。Fixture `docs/fixtures/api/p3/*.json` 32 例逐字段对比，并由
`P3ApiTest` 在 Java context 上回放。

## 6. 验证步骤

### 6.1 Java 后端

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
./mvnw test                       # 全量 169 测试
./mvnw test -Dtest='P3ApiTest'    # 仅 P3 API 35 测试
./mvnw test -Dtest='P3ImporterTest' # JSONL 导入 9 测试
```

启动服务（dev profile）：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl http://localhost:8080/actuator/health  # → UP
```

切换为 Java 后端并跑 JSONL 导入（dry-run）：

```bash
./mvnw package -DskipTests
java -jar target/datastoria-server-*.jar \
  --spring.profiles.active=local \
  --p3.import.path=./bundle \
  --p3.import.dry-run=true
echo $?  # 0=success, 1=row/checksum errors, 2=io/manifest errors
```

### 6.2 前端

```bash
cd /Users/jielongping/OpenProjects/datastoria-p3-frontend
npm install
npm run typecheck  # 通过
npm run lint       # 通过
npx vitest run     # 419 测试全通过
```

切换到 Java 后端（开发联调）：

```bash
# .env.local
NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=java
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=dev@example.com
npm run dev
```

## 7. 回退方式

按 `docs/migration-plan.md` §5 P3 "回滚" 条款：切回 Node 前停止 Java 写入并执行增量反向
导出，避免双写分叉。具体步骤：

1. 前端：把 `NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND` 改回 `node` 并重新构建。
2. Java 后端：保留运行（只读查询无害）或停止。SQLite/MySQL 表不删，写入口禁用即可。
3. 数据回流（仅当 P3 期间 Java 写入过新数据时需要）：从 Java 后端按
   `docs/migration/p3-jsonl-format.md` 导出 sessions/messages/feedback，再用 Node 端
   repository 的 `upsert` API 回放。share 行无需回流（JWT 自包含，Node 端无对应表）。

## 8. 已知差异与后续项

### 8.1 P3 已知差异（不阻塞 review）

- **Node baseline 实际 capture 未执行**：P3.1 冻结的是预期 fixture；启动 Node 服务跑
  `tools/contract-runner/src/capture.js` 抓取实际响应，是 P4 之前的必跑步骤，但需要
  本机具备 Node 服务 + ClickHouse 测试数据。Fixture 形状已与 capture.js 输出兼容，无
  修改可直接执行。
- **JSONL exporter 未在 Node 仓库实现**：`docs/migration/p3-jsonl-format.md` §7 记录了
  需要的导出脚本设计，但实际 `.ts` 脚本未写。建议在 P3 收尾后、首次正式 cutover 前补
  到 `scripts/` 目录；非 P3 阻塞项（开发期可用手工 dump）。
- **`MysqlRepositoryIT` 本机跳过**：CI 的 `mysql-contract` job 是合入远端前的强制门
  禁；本地无 Docker 时 Testcontainers 自动 `Assumptions.abort()`。
- **share 允许写**：`datastoria.session-share.allow-write=true` 仅作 P3 cutover 期间
  回滚保险，P11 必须删除。

### 8.2 后续阶段（不在 P3 范围）

- **P4**：AgentScope Harness + SSE chat 流式（不在 P3 范围，但 P3 已为消息持久化做好
  准备：消息表无 AgentScope checkpoint 列，只承担产品展示职责）。
- **P10**：Spring Security/OIDC 替换 dev header-based 身份；`IdentityWebFilter` 的
  `allow-anonymous=false` 路径届时会被 OAuth 资源服务器接管。
- **P11**：删除 Node `route.ts` 文件与 `chat_sessions` / `chat_messages` /
  `feedback_events` SQLite/MySQL 表。

## 9. 文件清单（按子阶段）

### P3.1 契约冻结（commit `25723a9`）

新增：

```
docs/api/p3-openapi-extensions.yaml
docs/adr/0001-session-share-permissions.md
docs/adr/0002-session-create-atomicity.md
docs/adr/0003-feedback-target-not-found.md
docs/delivery/p3-sub1-contract-freeze-report.md
docs/fixtures/api/p3/MANIFEST.md
docs/fixtures/api/p3/index.json
docs/fixtures/api/p3/A03-list-basic.json … A10-feedback-target-not-found.json （32 例）
```

### P3.2 DDL（commit `e0300de`）

```
src/main/resources/db/migration/sqlite/V4__chat_session_message_feedback_share.sql
src/main/resources/db/migration/mysql/V4__chat_session_message_feedback_share.sql
```

### P3.3 Domain + Repository（commit `96ea02b` + `da91f30`）

```
src/main/java/io/datastoria/server/domain/ChatSession.java
src/main/java/io/datastoria/server/domain/ChatMessage.java
src/main/java/io/datastoria/server/domain/FeedbackEvent.java
src/main/java/io/datastoria/server/domain/SessionShare.java
src/main/java/io/datastoria/server/repository/ChatSessionRepository.java
src/main/java/io/datastoria/server/repository/ChatMessageRepository.java
src/main/java/io/datastoria/server/repository/FeedbackEventRepository.java
src/main/java/io/datastoria/server/repository/SessionShareRepository.java
src/main/java/io/datastoria/server/repository/SessionPage.java
src/main/java/io/datastoria/server/repository/jdbc/JdbcChatSessionRepository.java
src/main/java/io/datastoria/server/repository/jdbc/JdbcChatMessageRepository.java
src/main/java/io/datastoria/server/repository/jdbc/JdbcFeedbackEventRepository.java
src/main/java/io/datastoria/server/repository/jdbc/JdbcSessionShareRepository.java
src/main/java/io/datastoria/server/repository/jdbc/SessionListCursor.java
src/main/java/io/datastoria/server/repository/jdbc/SqlTimestamps.java
src/test/java/io/datastoria/server/repository/V4SchemaSmokeTest.java
src/test/java/io/datastoria/server/repository/SqliteChatSessionRepositoryTest.java
src/test/java/io/datastoria/server/repository/SqliteChatMessageRepositoryTest.java
src/test/java/io/datastoria/server/repository/SqliteFeedbackEventRepositoryTest.java
src/test/java/io/datastoria/server/repository/SqliteSessionShareRepositoryTest.java
src/test/java/io/datastoria/server/repository/jdbc/SessionListCursorTest.java
```

### P3.4 Service / Controller（commit `fb974a0`）

```
pom.xml (新增 jjwt 0.12.6 api/impl/jackson)
src/main/java/io/datastoria/server/config/SessionShareConfig.java
src/main/java/io/datastoria/server/api/error/PlainTextException.java
src/main/java/io/datastoria/server/api/error/SharePermissionDeniedException.java
src/main/java/io/datastoria/server/api/error/ShareNotFoundException.java
src/main/java/io/datastoria/server/api/error/FeedbackTargetNotFoundException.java
src/main/java/io/datastoria/server/api/GlobalExceptionHandler.java (修改)
src/main/java/io/datastoria/server/api/compat/ChatSessionController.java
src/main/java/io/datastoria/server/api/compat/ChatMessageController.java
src/main/java/io/datastoria/server/api/compat/SessionShareController.java
src/main/java/io/datastoria/server/api/compat/FeedbackController.java
src/main/java/io/datastoria/server/api/compat/CompatExceptionHandler.java
src/main/java/io/datastoria/server/service/SessionService.java
src/main/java/io/datastoria/server/service/SessionShareService.java
src/main/java/io/datastoria/server/service/MessageService.java
src/main/java/io/datastoria/server/service/FeedbackService.java
src/main/java/io/datastoria/server/dto/ (12 个 DTO record)
```

### P3.5 API 契约测试（commit `7b785bd`）

```
src/test/java/io/datastoria/server/api/compat/p3/AbstractP3ApiTest.java
src/test/java/io/datastoria/server/api/compat/p3/P3ApiTest.java            (35 测试)
src/test/java/io/datastoria/server/api/compat/p3/P3UnauthenticatedTest.java (2 测试)
src/test/java/io/datastoria/server/api/compat/p3/P3FeedbackAcceptedTest.java (1 测试)
src/main/java/io/datastoria/server/identity/IdentityWebFilter.java (allow-anonymous 开关)
```

### P3.6 JSONL 导入（commit `240d4f7`）

```
src/main/java/io/datastoria/server/tools/importer/P3Importer.java
src/main/java/io/datastoria/server/tools/importer/P3ImportRunner.java
src/main/java/io/datastoria/server/tools/importer/P3ImportManifest.java
src/main/java/io/datastoria/server/tools/importer/P3ImportResult.java
src/main/java/io/datastoria/server/tools/importer/Jsonl.java
src/main/java/io/datastoria/server/tools/importer/ImportRows.java
src/test/java/io/datastoria/server/tools/importer/P3ImporterTest.java (9 测试)
docs/migration/p3-jsonl-format.md
```

### P3.7 前端 endpoint 网关（frontend commit `20fa863`）

```
src/lib/ai/session/session-api-base.ts
src/lib/ai/session/session-api-base.test.ts (8 测试)
src/components/chat/session/remote-session-repository.ts (修改)
src/components/chat/session/remote-session-repository.test.ts (修改，新增 2 测试)
src/components/chat/view/chat-panel.tsx (修改)
.env.example (修改)
```

## 10. 退出条件审计

按 `docs/delivery/phase-prds.md` P3 退出条件：

- [x] **A03-A10 全部契约通过**：`P3ApiTest` 35 测试覆盖每个 fixture，含正例与负例。
- [x] **Node/Java session API 契约测试一致**：fixture 32 例与 OpenAPI 对齐，Java 回放
      通过；Node baseline 实际 capture 见 §8.1。
- [x] **会话权限和并发写测试通过**：cross-tenant 测试、share-permission-denied 测试、
      幂等 upsert 测试、JSONL idempotency 测试。
- [x] **产品消息表不承担 Agent checkpoint**：`ds_chat_message` 无任何 checkpoint 列；
      AgentScope 状态留给 P4 的 `AgentRun` / `AgentCheckpoint` 表。
- [x] **提供旧数据导入/校验工具**：`P3Importer` + dry-run + checksum；文档
      `docs/migration/p3-jsonl-format.md`。
- [x] **可回退**：前端 `NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=node` 即切回；Java 表
      保留但写入口禁用。
- [x] **未触动 P4 范围**：未实现 chat completion、SSE、AgentScope。

---

> 该报告完成时 `codex/phase-p3` 与 `codex/phase-p3-frontend` 两个分支均未 push、未开
> PR、未合并 master，等待 review。
