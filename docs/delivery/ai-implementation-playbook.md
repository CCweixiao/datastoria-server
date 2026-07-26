# AI 实施手册

## 1. 开始一个阶段

执行 AI 必须：

1. 阅读文档导航中前六份设计文档和目标阶段。
2. 检查两个仓库的当前 git 状态与 `AGENTS.md`。
3. 在 DataStoria 前端仓库按其规则使用独立 worktree；Java 仓库也使用 `codex/` feature
   branch，避免直接在主线开发。
4. 把阶段任务拆成可验证的纵向切片，最多一个 `in_progress`。
5. 先补/冻结契约测试，再实现。

若代码事实与文档冲突，不可猜测：引用文件/测试证据更新现状矩阵，并在影响架构时写 ADR。

## 2. Java 工程规则

- JDK 17；禁止使用更新 JDK 才支持的 API。
- Spring Boot 3.5.x；AgentScope 版本精确锁定。
- 初期保持单 Maven module，按 package 隔离。
- Controller 只做 transport、validation、identity；业务在 application service。
- domain 不依赖 WebFlux/JPA/AgentScope wire types。
- 三方言首选 Spring JDBC `JdbcClient`/`NamedParameterJdbcTemplate` + Spring transaction，
  SQL 由 repository adapter 显式维护；不要依赖 ORM 自动建表。
- JDBC 和 AgentScope 阻塞调用放到有上限的专用 scheduler/executor，不阻塞 Netty event
  loop。若改用 R2DBC，必须先证明 SQLite/MySQL/PostgreSQL driver、Flyway 和事务语义均满足三方言
  contract，并记录 ADR。
- 所有 DDL 通过 Flyway；每个版本同时维护 `db/migration/sqlite`、
  `db/migration/mysql` 和 `db/migration/postgresql`，版本号与业务语义一致。
- 本地开发使用 SQLite；生产使用 MySQL 或 PostgreSQL。生产 profile 不得自动回落到 SQLite。
- `spring.jpa.hibernate.ddl-auto`（若引入 JPA）必须为 `none`；schema 的唯一来源是 Flyway。
- API DTO 使用 Bean Validation；开放 UI message parts 使用 Jackson `JsonNode` 保真。
- `ProblemDetail` 统一错误；兼容纯文本由显式 adapter 产生。

## 3. 安全硬规则

- 不提交、打印、回传 API key、refresh token、ClickHouse password。
- 不接受 userId/tenantId 作为授权事实。
- secret write endpoint 禁用 body logging。
- SQL 默认只读、单语句、超时、行数/字节限制。
- Skill resource 路径做 canonical validation。
- 工具权限来自服务端 policy，不来自 prompt/Skill 自述。
- 跨租户测试是每个 repository/controller 的必选项。

违反任一硬规则，该阶段不得验收。

## 4. 每个纵向切片的开发顺序

1. 写/更新 OpenAPI 和 Schema。
2. 写失败的 controller/contract test。
3. 写 domain model 和 application service test。
4. 写 repository contract test，并对 SQLite、MySQL Testcontainers 与 PostgreSQL
   Testcontainers 复用同一测试集。
5. 实现 controller/adapter。
6. 跑窄测试。
7. 接前端 adapter，保持页面和 UIMessage 结构。
8. 跑 Node/Java diff 和 Playwright。
9. 更新实际验证、差异、指标和回滚说明。

## 5. AgentScope 开发顺序

1. fake model/fake event，不先依赖真实 provider。
2. event adapter 与 stream encoder。
3. run/checkpoint 事务。
4. 单个 fake tool。
5. 数据库 Skill load（先 SQLite，生产门再验证 MySQL）。
6. 单个真实 provider smoke。
7. 只读 ClickHouse tools。
8. HITL/restart。
9. 完整工具和压力/故障注入。

不得在 stream mapper 未通过 Golden Test 前批量迁移工具。

## 6. 测试命令基线

Java（以实际 pom 脚本为准）：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -q test
./mvnw verify
```

数据库验证至少拆成：

- 默认 `test`：SQLite migration、约束和 repository contract，保证开发环境快速运行。
- `verify -Pmysql-it`：MySQL Testcontainers、migration、同一 repository contract。
- `verify -Ppostgres-it`：PostgreSQL Testcontainers、migration、同一 repository contract。
- dialect parity：比较三套 migration 后的逻辑 schema，不允许漏表、漏列、漏约束或索引。

DataStoria 前端遵循其仓库命令：

```bash
npm run format
npm run typecheck
npm run lint
npm run test
npm run build
```

只改 Java 不需要无意义运行前端全套；一旦改前端调用/类型，必须按 DataStoria
`AGENTS.md` 执行对应检查。无法运行的验证必须明确记录，不能以“应当通过”替代。

## 7. 阶段交付报告模板

```markdown
# Pn 实施报告

## 范围
- 完成：
- 未完成：
- 明确非目标：

## 变更
- Java：
- Frontend：
- DB/Flyway：
- Contract：

## 验证证据
| Requirement | Command/Scenario | Result | Artifact |
|---|---|---|---|

## Node/Java 差异
- 无差异：
- 已批准差异（ADR）：

## 安全检查
- secret scan：
- tenant isolation：
- authorization：

## 迁移/回滚演练
- migration：
- rollback：

## 已知风险
- ...
```

## 8. 提交质量门

- 只提交本阶段文件，不夹带用户已有改动。
- changed files 与任务一一对应。
- SQLite/MySQL/PostgreSQL migration、API、实现、测试、文档同步。
- 没有 TODO 代替本阶段必需行为。
- mock 测试不能作为真实数据库/流协议/前端兼容的唯一证据。
- 完成声明必须引用命令结果或报告 artifact。

## 9. 建议任务提示词

把以下内容附在阶段 PRD 后交给另一个 AI：

```text
请实施 DataStoria Server 的 Pn。严格保持该阶段范围，但完成所有任务和退出条件。
先检查当前代码与文档事实，先契约和测试后实现。任何行为差异必须记录 ADR。
最终提供 changed files、验证命令及结果、Node/Java 契约差异、安全检查、迁移和回滚演练。
不要把后续阶段 stub 宣称为完成，不要在浏览器或日志暴露任何凭据。
```
