# P0 + P1 实施报告

> Review 状态：已修订并通过。2026-07-24 的 review 修正了 AI SDK v6 响应头、OpenAPI
> 3.1 nullable 结构、stream semantic diff、精确字节捕获、默认 profile、CI 和依赖漏洞。
> 真实 Node 路由流捕获仍按本文退出条件保留为 P4/P7 前置硬门，不被规范 fixture 替代。

## 范围

### 完成

- **P0 仓库基线**：JDK 17 / Spring Boot 3.5.16 / WebFlux / Validation / Actuator
  脚手架；Spotless 格式化（google-java-format）于 verify 阶段门禁；CI workflow
  （wrapper 校验、格式化检查、test、package、gitleaks 密钥扫描）；profile 结构
  （base / local example / test）；actuator health + info 测试。
- **P1 契约冻结与测试脚手架**：A01-A29 OpenAPI baseline（24 path、31 operation、
  22 schema）；AI SDK UI Message Stream 流式协议契约与事件目录；API 迁移处置
  矩阵；前端调用点清单 + 10 个 Playwright 场景；8 个流式 fixture + JSON Schema；
  方言无关业务 fixture + 脱敏 ClickHouse 测试数据；Node contract runner（fixture
  校验、精确字节响应捕获、JSON + stream semantic diff）及 CI 自动测试。

### 未完成（明确）

- 真实 Node 路由字节流 fixture 捕获：当前 fixture 为基于 AI SDK v6 `uiMessageChunkSchema`
  手工构造的规范样本；真实脱敏字节需在具备受控 provider 环境后由 contract runner
  对运行中的 Node 服务抓取并覆盖。协议结构与 schema 已冻结。
- MySQL 方言、Flyway、数据库 repository：属 P2 范围，本阶段不实现。
- AgentScope、Skill、Tool、业务 API：属 P4+ 范围，本阶段不实现。

### 明确非目标

- 不引入 SQLite/Flyway 依赖（P2）。
- 不实现模型、Agent、Skill、会话等业务表。
- 不修改原项目 `/Users/jielongping/OpenProjects/datastoria`（只读检查）。

## 变更

### Java / 工程

| 文件 | 变更 |
|---|---|
| `pom.xml` | 新增 Spotless 插件（google-java-format 1.17.0 + sortPom），verify 阶段 check |
| `src/main/java/.../DatastoriaServerApplication.java` | 格式化（2-space） |
| `src/main/resources/application.yaml` | 新增：base profile + actuator health/info 暴露 |
| `src/main/resources/application-local.yaml.example` | 新增：本地开发模板（无密钥） |
| `src/test/resources/application-test.yaml` | 新增：test profile |
| `src/test/.../ActuatorHealthTest.java` | 新增：health UP + info 元数据断言 |
| `src/test/.../DatastoriaServerApplicationTests.java` | 加 `@ActiveProfiles("test")` |
| `.editorconfig` | 新增 |
| `.gitignore` | 扩充：secrets、local DB、contract-runner node_modules |
| `.github/workflows/ci.yml` | 新增：Java、contract runner 和密钥扫描 CI pipeline |

### 契约 / 文档

| 文件 | 内容 |
|---|---|
| `docs/api/openapi-baseline.yaml` | A01-A29 OpenAPI 3.1，含 x-datastoria 扩展 |
| `docs/api/stream-protocol.md` | UI Message Stream 协议冻结 + 事件目录 + diff 规则 |
| `docs/api/migration-disposition.md` | A01-A29 disposition 矩阵 + 前端调用点 |
| `docs/api/frontend-call-sites.md` | 调用点清单 + 10 个 Playwright 场景 |
| `docs/README.md` | 增加 P1 产出索引 |

### Fixture

| 文件 | 内容 |
|---|---|
| `docs/fixtures/stream/schema.json` | UI Message chunk JSON Schema (draft 2020-12) |
| `docs/fixtures/stream/*.jsonl` | 8 个场景：text/reasoning/tool-success/tool-error/usage-title/error/cancel/continuation |
| `docs/fixtures/stream/MANIFEST.md` | 来源与捕获状态 |
| `docs/fixtures/business/*.jsonl` | sessions/messages/feedback/share（方言无关） |
| `docs/fixtures/business/skill-catalog.json` | 9 个内置 Skill catalog |
| `docs/fixtures/business/clickhouse/schema.sql` | 脱敏 ClickHouse 测试 schema + 数据 |
| `docs/fixtures/business/MANIFEST.md` | checksum 与对账流程 |

### Contract Runner（`tools/contract-runner/`）

| 文件 | 职责 |
|---|---|
| `src/validate-fixtures.js` | 用 ajv 2020-12 校验 stream fixture |
| `src/semantic-diff.js` | JSON + stream 语义 diff（CLI + 库），校验状态、必需头和 DONE |
| `src/capture.js` | 捕获服务响应（JSON + SSE），保留 Base64 精确响应字节 |
| `src/sse.js` | SSE 解析为 chunk 数组 |
| `test/*.test.js` | SSE parser 与 semantic diff 回归测试 |

## 验证证据

| Requirement | Command / Scenario | Result | Artifact |
|---|---|---|---|
| P0 build + test | `JAVA_HOME=$(...jdk-17...) ./mvnw clean test` | 3 tests, 0 failures | 本报告 |
| P0 health UP | `ActuatorHealthTest.healthEndpointIsUp` | PASS | `target/surefire-reports/*` |
| P0 info 元数据 | `ActuatorHealthTest.infoEndpointExposesApplicationMetadata` | PASS | 同上 |
| P0 格式化门禁 | `./mvnw spotless:check` | PASS | — |
| P0 package | `./mvnw package -DskipTests` | BUILD SUCCESS, jar 产出 | `target/datastoria-server-0.0.1-SNAPSHOT.jar` |
| P1 fixture schema | `npm run validate-fixtures` | 8 fixtures, 65 chunks, 0 errors | `tools/contract-runner` |
| P1 runner tests | `npm run test:unit` | parser/diff regression PASS | `tools/contract-runner/test` |
| P1 OpenAPI 可解析 | `npx js-yaml openapi-baseline.yaml` | 24 path / 31 op / 22 schema | `/tmp/openapi-parsed.json` |
| P1 OpenAPI 3.1 lint | `npm run validate-openapi` | PASS | `redocly.yaml` |
| P1 semantic diff 负例（JSON 缺字段） | `semantic-diff.js cap-a cap-b(missing title)` | SEMANTIC DIFF, exit 1 | — |
| P1 semantic diff 正例（stream，随机 ID/token 忽略） | `semantic-diff.js node java-ok` | SEMANTIC MATCH, exit 0 | — |
| P1 stream diff 负例（缺 text-start） | `semantic-diff.js node java-bad` | SEMANTIC DIFF(5), exit 1 | — |
| 安全：密钥扫描 | `grep -RE '(sk-[...]|api_key=...)'` | No matches | — |
| 安全：gitignore | local profile、.env、data/、node_modules | 全部忽略 | `.gitignore` |

## Node / Java 差异

- **无运行时差异**：P1 只产出契约、fixture 与测试工具，无生产行为变更。Java 服务尚未
  实现任何业务 API，因此尚无可对比的 Node/Java 行为。
- 契约差异基线已为后续阶段定义：semantic diff 的忽略规则与禁止忽略规则记录在
  `docs/api/stream-protocol.md` §6。

## 安全检查

- **secret scan**：仓库内无明文 API key / refresh token / ClickHouse password。
  CI 引入 gitleaks-action 做持续扫描。
- **tenant isolation**：P1 无业务表，无租户隔离测试对象（P2 起为必选项）。
- **authorization**：OpenAPI 已标注每个 API 的 `x-datastoria-auth` 与目标权限模型；
  `apiKey`/`connection.password`/OAuth token 在 body 中的字段已显式标注 `REJECTED by
  Java target`。
- **凭据后移**：处置矩阵记录了 A12-A18 当前 token 经浏览器的事实，目标阶段全部
  改为服务端托管。

## 迁移 / 回滚演练

- **migration**：P1 不含数据库 migration。profile 结构已就绪：`local`（SQLite，P2）、
  `prod`（MySQL，缺失即 fail fast，P2/P11）。
- **rollback**：P1 只增加测试与文档，回滚即删除新增文件，对运行时无影响。
  feature flag（Node/Java endpoint 切换）在 P2 随前端接入引入。

## 退出条件对照

### P0

- [x] JDK 17 下 `./mvnw test` 通过。
- [x] 应用启动后 actuator health 为 UP。
- [x] 仓库扫描无密钥。
- [x] CI 增加 wrapper validate、test、package。
- [x] Spotless 格式化。
- [x] `application-local.yaml.example`，不提交密钥。

### P1

- [x] A01-A29 建 OpenAPI baseline，标注 alias/deprecated/stub/active。
- [x] Node contract runner，采集 status/header/JSON/SSE。
- [x] 规范流 fixture 覆盖 text、reasoning、tool（success/error）、usage/title、error、
  cancel、continuation。
- [x] semantic diff 定义：忽略随机 ID/时间/token 数值，禁止忽略字段缺失与顺序错误。
- [x] 每个前端调用点记录 + Playwright 场景。
- [x] 方言无关业务 fixture + 脱敏 ClickHouse 测试数据。
- [x] 故意删除字段时 diff 必须失败（已演示验证）。
- [ ] 从运行中的 Node A01/A02 路由捕获并脱敏真实字节 fixture；当前只能证明
  Schema/wire 规范和 runner 行为，不能证明 provider/route 的所有运行时变体。

## 已知风险与后续

1. **真实字节 fixture 待捕获**：当前 stream fixture 是协议规范样本，真实 AI SDK
   输出（含 provider 特定的 reasoning 字段、metadata 结构）需在 P4 引入 AgentScope
   时用 contract runner 对 Node 基线抓取后覆盖。
2. **A02 legacy SSE 字节未冻结**：自定义 `SseStreamer` 格式未捕获，P7 退役前需补。
3. **A06/A07 分享者可写行为**：当前兼容 Node 行为，安全评审后可能需要 ADR 收紧。
4. **OpenAPI 错误码为 plain text**：legacy 兼容要求；新管理 API 将使用 Problem Details，
   双协议在 P2 起通过显式 adapter 处理。
5. **真实路由捕获仍是后续硬门**：P4 开始实现 Java stream encoder 前必须至少捕获 A01；
   P7 退役 legacy A02 前必须捕获 A02。未完成时不得宣布流协议等价。

## 交付文件清单

```
新增（P1）:
  docs/api/openapi-baseline.yaml
  docs/api/stream-protocol.md
  docs/api/migration-disposition.md
  docs/api/frontend-call-sites.md
  docs/fixtures/stream/schema.json
  docs/fixtures/stream/{text-only,reasoning,tool-success,tool-error,usage-title,error,cancel,continuation}.jsonl
  docs/fixtures/stream/MANIFEST.md
  docs/fixtures/business/{sessions,messages,feedback,sessions-share}.jsonl
  docs/fixtures/business/skill-catalog.json
  docs/fixtures/business/clickhouse/schema.sql
  docs/fixtures/business/MANIFEST.md
  tools/contract-runner/{package.json,package-lock.json,README.md}
  tools/contract-runner/src/{validate-fixtures,semantic-diff,capture,sse}.js
  docs/delivery/p0-p1-implementation-report.md

变更（P0 已提交 + P1 文档）:
  pom.xml, .editorconfig, .gitignore, .github/workflows/ci.yml
  src/main/resources/application{,-local.yaml.example}.yaml
  src/test/resources/application-test.yaml
  src/test/java/io/datastoria/server/{DatastoriaServerApplicationTests,ActuatorHealthTest}.java
  docs/README.md
```
