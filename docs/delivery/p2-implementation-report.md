# P2 实施报告

> SQLite Configuration Vertical Slice & MySQL Production Dialect

## 范围

### 完成

- **P2.1 SQLite / Flyway / Profile / Error Foundation**：`pom.xml` 依赖
  （spring-boot-starter-jdbc、spring-boot-starter-security、flyway-core、flyway-mysql、
  sqlite-jdbc、mysql-connector-j、testcontainers）；4 个 YAML profile（base / local
  example / test / prod fail-fast）；`DataSourceConfig`（SQLite PRAGMA）；
  `FlywayConfig`（per-profile location + prod guard）；`JdbcSchedulerConfig`（bounded-elastic
  Scheduler）；`GlobalExceptionHandler`（RFC 9457 ProblemDetail）；V1 SQLite migration
  （ds_config_entry、ds_audit_log）。
- **P2.2 Dual-dialect DDL V1–V3 + Parity Test**：6 个 Flyway migration 文件（SQLite +
  MySQL 各 3 个）；`SchemaParityTest` 对比两引擎表结构、列、PK、FK。
- **P2.3 Secret Encryption (AES-GCM)**：`MasterKeyProvider`（base64 256-bit key）；
  `EnvelopeEncryptionService`（AES-256-GCM，12-byte nonce，128-bit tag）；
  `MaskedHintBuilder`（`sk-…DEF` 格式）；6 个加密回环 / 篡改 / 明文扫描测试。
- **P2.4 Provider / Model / Secret Repository + Admin API**：`ModelProvider`、`Model`、
  `Secret` domain；JDBC repository（乐观锁、软删除、tenant 隔离）；`ProviderService`、
  `ModelService`、`SecretService`（加密 → 存储 → 返回 masked hint）；
  `ProviderAdminController`（GET/POST/PUT + credential PUT/DELETE）；
  `ModelAdminController`（GET/POST/PUT/DELETE）；DTO + Bean Validation；17 个测试。
- **P2.5 Agent Definition / Revision Repository + Admin API**：`AgentDefinition`、
  `AgentRevision` domain；`JdbcAgentDefinitionRepository`（CRUD + publish + disable）；
  `JdbcAgentRevisionRepository`（immutable insert）；`AgentDefinitionService`（SHA-256
  checksum + atomic publish）；`AgentAdminController`（7 路由）；13 个测试。
- **P2.6 User Preference + Effective Config**：`ConfigEntry`、`UserModelPreference`、
  `EffectiveConfig` domain；`JdbcConfigEntryRepository`（upsert + 3-layer merge query）；
  `JdbcUserModelPreferenceRepository`（upsert by tenant+user）；`UserPreferenceService`
  （system < tenant < user 合并）；`UserPreferenceController`、`UserModelPreferenceController`；
  9 个测试。
- **P2.7 `/api/ai/models/available` Compat Layer (A12)**：`AvailableModelsService`
  （enabled models → ModelProps）；`AvailableModelsController`（reject apiKey → 400，
  ignore github.token）；4 个测试。
- **P2.8 Frontend API Contract Docs**：`p2-openapi-extensions.yaml`（所有新 admin/user/
  compat 端点 OpenAPI 片段）；`frontend-migration-guide-p2.md`（localStorage → server API
  逐步迁移指南）。
- **P2.9 Security / Identity / MySQL IT / Cross-tenant / E2E**：`DevIdentityResolver`
  （convention-based identity：admin-users 列表 + tenant- 前缀）；`IdentityWebFilter`
  RBAC（`/api/admin/**` 要求 ROLE_ADMIN）；`SecurityConfig`；5 个安全测试类
  （RBAC、cross-tenant isolation、apiKey rejection、optimistic lock、secret redaction）
  共 18 个测试；生产 profile 在 P10 OAuth 落地前对业务 API fail closed；
  `MysqlRepositoryIT`（Testcontainers MySQL 8.0，Docker 不可用时自动跳过）。

### Review 修复（2026-07-25）

- 修复 SQLite/MySQL 软删除唯一键：原 `(tenant_id, key, deleted_at)` 在 `NULL` 语义下
  无法约束活动记录，改为双方言一致的 generated `active_key`。
- 为 provider、secret、model 的引用增加 tenant 复合外键，并在 service 边界校验
  model/provider/revision 引用，阻断跨租户关联。
- 开发身份 resolver/filter 仅在 `local`/`test` 启用；`prod` 除 health/info 外全部拒绝，
  等待 P10 OAuth。
- credential rotation/clear 改为单个 JDBC 事务，避免失败后 provider 指向已删除密钥。
- `If-Match` 支持标准 quoted/weak ETag；补充 quoted ETag 回归测试。
- `-Pmysql-it` 增加 Failsafe，确保 `MysqlRepositoryIT` 在 integration-test 阶段执行。
- CI 增加 `mysql-contract` job，在 GitHub runner 的 Docker 环境运行 schema/repository 门禁。
- 增加 Maven Enforcer，使用非 JDK 17 时立即给出明确失败。
- 补齐 A12 OpenAPI path/schema，修正文档中的错误码和 ETag 描述。
- 补齐 provider DELETE、model detail GET、agent update/delete，provider 被活动 model
  引用时返回 `409 RESOURCE_IN_USE`。
- 新增 provider connection test 与 remote model discovery；远端调用仅在服务端解密并使用
  credential，响应与日志均不返回明文。
- 新增开发环境 CORS（仅 localhost:3000）并暴露 ETag，支持前端直连 Java 配置 API。
- 前端新增 `NEXT_PUBLIC_DATASTORIA_CONFIG_BACKEND=node|java` 网关；Java 模式下
  available models、model preference 与 provider credential 均由服务端持有，启动时清理
  浏览器中的旧 provider/model secret。

### 延后项（不阻止 P2 合并）

- **MySQL IT 在无 Docker 环境跳过**：`MysqlRepositoryIT` 和 `SchemaParityTest` 的 MySQL
  部分在无 Docker 时自动 `Assumptions.abort()`；CI 的 `mysql-contract` job 是合入远端前的
  强制门禁。本地已验证 Failsafe 能发现该 IT。
- **GitHub Copilot models**：`/api/ai/models/available` 的 `githubModels` 始终返回空数组，
  待 P10 OAuth 实现后填充。
- **Spring Security OAuth 集成**：当前使用 header-based dev identity + 手动 RBAC。
  P10 将替换为真实 OAuth。

### 明确非目标

- 不实现 OAuth / token 交换（P10）。
- 不实现 chat completion / streaming（P4+）。
- 不实现 ClickHouse 事件写入（P3）。
- 不修改原项目 `/Users/jielongping/datastoria`（只读检查）。

## 变更

### Java / 工程

| 区域 | 文件 | 变更 |
|---|---|---|
| Build | `pom.xml` | 新增 jdbc、security、flyway、sqlite、mysql、testcontainers 依赖 |
| Config | `config/DataSourceConfig.java` | SQLite PRAGMA initializer |
| Config | `config/FlywayConfig.java` | per-profile location + prod URL guard |
| Config | `config/JdbcSchedulerConfig.java` | bounded-elastic Scheduler（32 threads） |
| Config | `config/SecurityConfig.java` | `@EnableWebFluxSecurity` permitAll baseline |
| Config | `config/CryptoConfig.java` | EnvelopeEncryptionService bean |
| Identity | `identity/Identity.java` | record(tenantId, userId, roles) + isAdmin() |
| Identity | `identity/DevIdentityResolver.java` | convention-based dev identity resolution |
| Identity | `identity/IdentityWebFilter.java` | header → Identity + RBAC enforcement |
| Identity | `identity/IdentityContext.java` | Reactor Context extractor |
| Error | `api/error/*Exception.java` | NotFoundException, RevisionConflictException, ClientSecretNotAllowedException |
| Error | `api/ProblemDetailFactory.java` | RFC 9457 builder |
| Error | `api/GlobalExceptionHandler.java` | `@RestControllerAdvice` |
| Crypto | `crypto/MasterKeyProvider.java` | base64 256-bit key from env |
| Crypto | `crypto/EnvelopeEncryptionService.java` | AES-256-GCM encrypt/decrypt |
| Crypto | `crypto/MaskedHintBuilder.java` | `sk-…DEF` format |
| Domain | `domain/*.java` | Ulid, AuditLog, ModelProvider, Model, Secret, AgentDefinition, AgentRevision, ConfigEntry, UserModelPreference, EffectiveConfig |
| Repository | `repository/jdbc/Jdbc*.java` | 6 JDBC repository implementations + SqlTimestamps |
| Service | `service/*.java` | SecretService, ProviderService, ModelService, AgentDefinitionService, UserPreferenceService, AvailableModelsService |
| Controller | `api/admin/*Controller.java` | ProviderAdminController, ModelAdminController, AgentAdminController |
| Controller | `api/user/*Controller.java` | UserPreferenceController, UserModelPreferenceController |
| Controller | `api/compat/AvailableModelsController.java` | A12 endpoint |
| DTO | `dto/*.java` | 20+ request/response records with Bean Validation |
| Test | `test/**/*.java` | 81 tests（另有 MySQL IT / schema parity Docker 门禁） |
| Test | `TestDbHelper.java` | FK-safe table cleanup |

### DB / Flyway

| 文件 | 内容 |
|---|---|
| `db/migration/sqlite/V1__identity_config_and_audit.sql` | ds_config_entry, ds_audit_log |
| `db/migration/sqlite/V2__model_provider_and_secret.sql` | ds_model_provider, ds_secret, ds_model, ds_user_model_preference |
| `db/migration/sqlite/V3__agent_definition_and_revision.sql` | ds_agent_definition, ds_agent_revision |
| `db/migration/mysql/V1-V3` | MySQL 等价 DDL（datetime(6)、varchar、json CHECK、mediumblob） |

### Contract / 文档

| 文件 | 内容 |
|---|---|
| `docs/api/p2-openapi-extensions.yaml` | 全部新端点 OpenAPI 3.0 片段 |
| `docs/api/frontend-migration-guide-p2.md` | localStorage → server API 迁移指南 |

### YAML Profiles

| 文件 | 用途 |
|---|---|
| `application.yaml` | base profile 文档 |
| `application-local.yaml.example` | SQLite file DB 模板 |
| `application-test.yaml` | SQLite in-memory + test identity |
| `application-prod.yaml` | MySQL fail-fast 占位符 |
| `application-mysql-it.yaml` | Testcontainers MySQL IT |

## 验证证据

| Requirement | Command / Scenario | Result | Artifact |
|---|---|---|---|
| P2 Java 回归 | `JAVA_HOME=...jdk-17... ./mvnw -B -ntp spotless:apply clean verify` | 81 tests PASS；MySQL parity 本机无 Docker 跳过 | 本报告 |
| OpenAPI lint | `redocly lint docs/api/p2-openapi-extensions.yaml` | valid（17 条非阻断 4xx/license warnings） | 本报告 |
| 格式化门禁 | `./mvnw spotless:check` | PASS | — |
| 加密回环 | `EnvelopeEncryptionServiceTest` | 6 tests PASS | surefire-reports |
| Provider CRUD | `SqliteProviderRepositoryTest` + `ProviderAdminApiTest` | 14 tests PASS | 同上 |
| Model CRUD | `SqliteModelRepositoryTest` + `ModelAdminApiTest` | 8 tests PASS | 同上 |
| Agent publish | `SqliteAgentDefinitionRepositoryTest` + `AgentAdminApiTest` | 13 tests PASS | 同上 |
| User preference merge | `UserPreferenceServiceTest` + `UserPreferenceApiTest` | 9 tests PASS | 同上 |
| A12 compat | `AvailableModelsApiTest` | 4 tests PASS | 同上 |
| RBAC negative | `RbacNegativeTest` | 6 tests PASS（非 admin → 403） | 同上 |
| Cross-tenant isolation | `CrossTenantIsolationTest` | 3 tests PASS（tenant B 看不到 tenant A 数据） | 同上 |
| apiKey rejection | `ApiKeyRejectionTest` | 3 tests PASS（400 CLIENT_SECRET_NOT_ALLOWED） | 同上 |
| Optimistic lock | `OptimisticLockTest` | 3 tests PASS（stale If-Match → 409） | 同上 |
| Secret redaction | `SecretRedactionTest` | 3 tests PASS（响应无明文密钥） | 同上 |
| Provider remote operations | `ProviderDiscoveryApiTest` | connection test/discovery PASS；credential 不出服务端 | 同上 |
| Frontend static checks | `npm run format && npm run typecheck && npm run lint` | PASS | 前端分支 |
| Frontend gateway tests | targeted Vitest（gateway/model manager/bootstrap） | 8 tests PASS | 前端分支 |
| Frontend production build | `npm run build` | PASS | 前端分支 |
| MySQL schema parity | `SchemaParityTest` | 需 Docker；当前环境未执行 | 同上 |
| MySQL repository IT | `./mvnw verify -Pmysql-it` | 需 Docker；当前环境未执行 | 同上 |
| 安全：密钥扫描 | `grep -RE '(sk-[a-zA-Z0-9]{20,}|api_key\s*[:=])'` | 仅测试 fixture 中的假密钥 | — |

## Node / Java 差异

- **无运行时差异**：P2 不实现 chat/streaming API，无 Node/Java 行为对比对象。
- **配置后移**：A12 `/api/ai/models/available` 的 `apiKey` 字段被 Java target 拒绝
  （400 CLIENT_SECRET_NOT_ALLOWED），`github.token` 被忽略并记录安全警告。
  这与 Node 当前行为不同（Node 接受 apiKey），是有意的安全收紧。

## 安全检查

- **secret scan**：源码中无明文 API key；唯一匹配是测试文件中的 fixture 密钥
  （`sk-test1234567890abcdef`）。CI gitleaks 在 P0 已配置。
- **tenant isolation**：所有 SQL 查询包含 `tenant_id` 过滤（来自 server-resolved Identity，
  不来自 request body）。`CrossTenantIsolationTest` 验证 provider、agent、user preference
  在跨租户场景下隔离。
- **authorization**：`IdentityWebFilter` 对 `/api/admin/**` 路径强制 ROLE_ADMIN；
  非 admin 用户收到 403 Forbidden。
- **credential handling**：密钥仅通过 `/api/admin/ai/providers/{id}/credential` 写入，
  AES-256-GCM 加密存储，响应仅返回 `maskedHint`（`sk-…DEF` 格式）。`SecretRedactionTest`
  验证 list/get 响应不包含 `cipherText`、`nonce` 或 `value` 字段。
- **master key**：从 `DATASTORIA_MASTER_KEY` 环境变量读取（base64 256-bit），prod profile
  缺失时 fail-fast。

## 迁移 / 回滚演练

- **migration**：Flyway V1→V3 顺序执行。SQLite 在 test/local profile 下自动迁移；
  MySQL 需在 prod profile 下通过 `application-prod.yaml` 配置后执行。
  `SchemaParityTest` 验证两引擎产出逻辑等价的 schema。
- **rollback**：P2 是纯增量（新表、新 API、新 service）。回滚 = 降级到 P1 JAR +
  drop V3→V1 Flyway undo（FlywayCommunity 不支持 undo，需手动 `DROP TABLE`）。
  对已有 P1 运行时无影响（P1 无业务表）。
- **feature flag**：前端可通过环境变量切换调用旧 Node API 或新 Java API。

## 已知风险与后续

1. **MySQL IT 本地未执行**：CI 已增加 `mysql-contract` job；本机无 Docker，因此本次
   review 只能确认 Failsafe 已发现 `MysqlRepositoryIT`，不能确认 MySQL DDL/行为通过。
2. **Dev identity convention**：`DevIdentityResolver` 使用 email 前缀约定（`tenant-*`
   前缀 → 对应租户；`admin-users` 列表 → ROLE_ADMIN）。P10 OAuth 将完全替换此机制。
3. **Config upsert 竞态**：`JdbcConfigEntryRepository.upsertUserEntry` 使用 check-then-insert
   模式，在高并发下可能产生 UNIQUE 约束冲突。P4 引入 chat 高频路径前需改为
   `INSERT ... ON CONFLICT DO UPDATE`（SQLite）/ `INSERT ... ON DUPLICATE KEY UPDATE`
   （MySQL）。
4. **githubModels 恒为空**：`/api/ai/models/available` 的 `githubModels` 始终返回 `[]`，
   待 P10 GitHub OAuth 集成后填充。
5. **AgentRevision 的 tenantId 不在行内**：revision 表不存储 `tenant_id`，通过 JOIN
   `ds_agent_definition` 获取。如果 agent 被 soft-delete，revision 变为不可查。
   当前设计是有意的（revision 属于 agent 的生命周期）。
6. **P2 本地退出条件已满足**：admin CRUD、remote provider test/discovery、前端
   server-backed gateway、feature flag、Java/前端回归与生产构建均已完成。远端合入仍应以
   GitHub `mysql-contract` job 通过为准。
