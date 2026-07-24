# 分阶段 PRD / PDC

## 使用说明

本文件的每个阶段都是一个独立交付单元，可直接复制给另一个 AI。执行 AI 必须先阅读：

1. `docs/inventory/current-state.md`
2. `docs/design/database-data-model.md`
3. `docs/design/api-contracts.md`
4. `docs/design/harness-agent.md`
5. 当前阶段条目

每阶段只在退出条件全部有证据时完成。不得预先宣称后续阶段功能已实现。

## P0：仓库基线

### 用户价值

获得可重复启动、测试、演进的独立 Java 后端骨架。

### 范围

- JDK 17、Spring Boot 3.5.x、Maven Wrapper。
- WebFlux、Validation、Actuator。
- 配置 profile、统一包名、基础测试和 CI。
- SQLite/Flyway 依赖可在 P1 引入；MySQL driver/Testcontainers 在生产方言阶段补充。

### 任务

- [x] 创建 `datastoria-server` 和基础文档。
- [x] 健康检查和 Spring context test。
- [ ] 增加 CI：wrapper validate、test、package。
- [ ] 增加 spotless/checkstyle 或项目统一格式化。
- [ ] 增加本地 `application-local.yaml.example`，不提交密钥。

### 验收

JDK 17 下 `./mvnw test` 通过，应用启动后 actuator health 为 UP；仓库扫描无密钥。

### 非目标

业务 API、数据库表、AgentScope。

## P1：契约冻结与测试脚手架

### 用户价值

建立“迁移没有丢功能”的可测基准。

### 输入

DataStoria Node 仓库与现状矩阵 A01-A29。

### 任务

- [ ] 为 A01-A29 建 `docs/api/openapi-baseline.yaml`，标注 alias/deprecated。
- [ ] 编写 Node contract runner，采集状态码、header、JSON。
- [ ] 采集 AI SDK 主流式场景原始字节 fixture。
- [ ] 覆盖 text、reasoning、tool、usage、title、error、cancel、continuation。
- [ ] 定义 semantic diff：忽略随机 ID/时间，禁止忽略字段缺失和顺序错误。
- [ ] 记录每个前端调用点和 Playwright 场景。
- [ ] 冻结一份数据库方言无关的业务 fixture 和脱敏 ClickHouse 测试数据。

### 测试

- contract runner 可重复对 Node 基线运行。
- fixture JSON/schema 校验通过。
- 故意删除字段时 diff 必须失败，证明测试有效。

### 交付物

OpenAPI、stream schema、fixtures、runner、基线报告、已知行为 ADR。

### 退出条件

A01-A29 每项都有 disposition、测试或明确“无运行行为”证据；所有流式事件有 fixture。

### 回滚

只增加测试/文档，无生产行为变化。

## P2：SQLite 配置纵向切片与 MySQL 生产方言

### 用户价值

管理员可在开发环境通过 SQLite 安全管理模型；同一逻辑模型具备可上线的 MySQL DDL，
前端不再以 localStorage 作为事实源。

### 范围

- SQLite 开发库、Flyway SQLite migrations。
- MySQL 生产 migrations 与 Testcontainers（允许作为 P2 的后续子任务，但 P11 上线前必须
  完成）。
- tenant/user identity abstraction、Spring Security 开发身份。
- provider/secret/model/user preference。
- Agent/system config entry。
- 模型和偏好管理 API、A12 兼容 API。

### 任务

- [ ] 实现 V1-V3 的 SQLite DDL 和 repository。
- [ ] 创建相同版本的 MySQL DDL；未补齐期间 `prod` profile fail fast。
- [ ] 建立双方言 schema parity 和共用 repository contract test。
- [ ] 实现 envelope encryption，测试日志无 secret。
- [ ] 实现 provider/model CRUD、credential rotation/test、model discovery。
- [ ] 实现 agent definition/revision 最小 CRUD（此时不运行 Agent）。
- [ ] 实现 effective preference GET/PUT。
- [ ] 实现 A12 并保持 `systemModels/githubModels` 结构。
- [ ] 前端增加 server-backed adapter；迁移成功后清理 localStorage secret。
- [ ] 增加 Node/Java endpoint feature flag。

### API 验收

- 管理员完整 CRUD；普通用户 403。
- 查询永不返回原始密钥/refresh token。
- revision 冲突返回 409/412。
- chat body 携带 `apiKey` 被拒绝。

### 测试

repository、加密轮换、跨租户、OpenAPI、WebTestClient、SQLite migration/contract、
MySQL Testcontainers/contract、前端模型设置 Playwright。

### 最小演示

配置一个 provider 和 model，测试连接，前端模型列表/选择来自 Java；服务重启后仍存在。

### 退出条件

浏览器不再是模型/Agent 系统配置事实源；A12 对当前前端可用；开发环境 SQLite 可重启
持久化。P2 若暂缓 MySQL 实现，必须把“生产数据库门”保留为未完成，不能进入 P11 上线。

### 回滚

feature flag 切回 Node；Java 表不删除，禁用写入口。

## P3：会话、消息、反馈与分享

### 用户价值

聊天产品数据由 Java 数据层可靠保存和回放；开发使用 SQLite，生产使用 MySQL。

### 范围

A03-A11（报告可只完成存储，管理报告可在 P10 收尾）。

### 任务

- [ ] 为 SQLite/MySQL 同步增加 Flyway session/message/feedback/share DDL。
- [ ] 实现 cursor、message sequence、事务和幂等。
- [ ] 实现分享签发/校验/撤销；明确只读权限 ADR。
- [ ] 实现旧数据 JSONL 导出、dry-run 导入和 checksum 对账。
- [ ] 以固定 assistant stub 串起前端创建、列表、打开、改名、删除和回放。

### 测试

Node/Java contract、跨用户/分享、并发 upsert、分页稳定性、未知 message part round-trip、
导入重复执行。

### 最小演示

前端完整会话生命周期走 Java；刷新页面消息不丢失；分享链接按权限可访问。

### 退出条件

A03-A10 全部契约通过；消息产品表不保存 AgentScope checkpoint。

### 回滚

切回 Node 前停止 Java 写入并执行增量反向导出，避免双写分叉。

## P4：AgentScope 最小 Harness

### 用户价值

现有 chat 页面可通过 Java 完成纯文本、多轮、流式 Agent 对话。

### 范围

- 锁定 AgentScope Java。
- 一个 provider、一个 published Agent revision。
- text/reasoning/usage/error/cancel。
- 无 Skill、无业务工具。

### 任务

- [ ] 完成最小版本兼容 spike 并记录 ADR。
- [ ] 建 ModelAdapter、HarnessAgentFactory、Run/Checkpoint repository。
- [ ] 建内部 event model 和 AI SDK compatibility encoder。
- [ ] 实现 A01 最小入站、run idempotency、会话写入。
- [ ] 取消传播和客户端断开策略。
- [ ] 标题生成可用独立 service，失败不影响主回答。

### 测试

fake event mapper、Golden stream、真实 provider 可选 smoke、取消/断开/限流/错误、100 次
流式稳定性、未知 UI part 保留。

### 最小演示

未修改消息渲染逻辑的前端完成多轮聊天，刷新可回放，API key 不在浏览器网络请求。

### 退出条件

P4 的“最小 Harness 验证门”适用项通过；A01 纯文本语义兼容。

### 回滚

按 endpoint flag 切回 Node A01；Java run 标记 cancelled，不删除记录。

## P5：数据库 Skill 只读运行链

### 用户价值

Agent 和 Skill 管理页可从 Java 数据库发现并按需加载已发布 Skill。

### 范围

- A19、A20、A22、A25 只读。
- 内置 9 个 Skill seed 导入。
- Agent 使用数据库无关的 Skill repository；开发 SQLite、生产 MySQL。

### 任务

- [ ] SQLite/MySQL Flyway/seed 同步建立 skill/revision/resource。
- [ ] 编写内置 Skill bundle 导入器和 checksum manifest。
- [ ] 实现 visibility、requiredTools、global/self。
- [ ] 实现 catalog、detail、resource、commands。
- [ ] 接入 Harness 按需 load；记录实际 revision。
- [ ] 路径、大小、编码、非法 frontmatter 防护。

### 测试

9 个 Skill 全量扫描；Node/Java catalog semantic diff；owner 隔离；路径穿越；运行中发布
模拟不改变固定 revision；Skill load E2E。

### 最小演示

Agent 发现并加载 `optimize-clickhouse-sql`，回答中使用其规则；管理页可浏览详情/资源。

### 退出条件

运行时 Skill 正文来自当前 profile 数据库；前端不加载 Skill 给 Agent。

### 回滚

切回 Node Skill provider；SQLite/MySQL seed 可保留。

## P6：Toolkit 与只读 ClickHouse 工具

### 用户价值

Agent 可在服务端安全发现表、查看 schema 和校验 SQL。

### 范围

`get_tables`、`explore_schema`、`validate_sql`，connection server-side。

### 任务

- [ ] 建 connection/secret 表和只读解析 API。
- [ ] 建 ToolContributor/Registry、Schema 导出、group/policy。
- [ ] 迁移三个工具，保持 input/output 字段。
- [ ] 加超时、并发、output cap、审计、trace。
- [ ] 移除这三个工具的浏览器 executor 路径。

### 测试

Node/Java Golden Test 对同一 ClickHouse；无权限连接；超时；取消；超大 schema 裁剪；
工具 schema snapshot；凭据网络/日志扫描。

### 最小演示

Agent 对真实测试 ClickHouse 完成表搜索、schema 探索和 SQL 校验，浏览器请求只含
connectionId。

### 退出条件

三个工具完全由 Toolkit 执行，前端仅渲染。

### 回滚

工具组 feature flag 回 Node；禁止 Java/浏览器同时执行同一 toolCallId。

## P7：完整 ClickHouse、诊断、SQL 与可视化

### 用户价值

Java Agent 覆盖 DataStoria 的核心 SQL 分析、优化、诊断和可视化流程。

### 范围

其余 5 个 ClickHouse 工具、RCA 模板、SQL generation/optimization、visualization、代码
搜索能力的明确迁移/替代。

### 任务

- [ ] 迁移 `execute_sql` 并实现只读 SQL classifier。
- [ ] 迁移 query log/evidence/cluster/RCA 工具和 A27。
- [ ] 迁移 generate/optimize/visualize wrapper。
- [ ] 可视化只返回 declarative spec/data。
- [ ] 迁移 search_file/read_file；使用受控 repository scope。
- [ ] 保持工具卡片所需进度和 output shape。
- [ ] 对 legacy `/api/ai/chat` 建兼容或退役策略。

### 测试

SQL 注入/多语句/DDL/DML/table function、限行/限字节、取消、ClickHouse 异常、诊断
Golden、可视化 schema、核心用户路径 E2E。

### 最小演示

从“找出慢查询”到证据收集、优化 SQL、校验、执行和图表生成的完整 Java Agent 流。

### 退出条件

现有 Tool 基线逐项标记 migrated；核心工作流无需浏览器执行工具。

### 回滚

按 tool group 回退；写/危险能力默认 fail closed。

## P8：HITL、权限、暂停恢复

### 用户价值

Agent 可安全等待用户回答/批准，并在断线或服务重启后继续。

### 范围

`ask_user_question`、approval、run APIs、checkpoint/event replay、memory/compaction。

### 任务

- [ ] 实现 pending action 表和 CAS 状态机。
- [ ] 实现 respond/approve/deny/cancel/resume/events。
- [ ] 映射 AgentScope permission callback。
- [ ] 前端移除 client tool executor，改为 API 回答。
- [ ] 实现 checkpoint codec/version、重启恢复和 event replay。
- [ ] 配置 session 并发 run 策略、action TTL。

### 测试

allow/ask/deny；重复相同/不同回答；过期；跨用户；重启；断线；事件无重复；compaction
后语义；故障注入到 checkpoint 事务边界。

### 最小演示

Agent 提问后 Java 重启，用户回答，Agent 从原位置继续并完成工具调用。

### 退出条件

前端没有任何 Agent tool executor；运行状态可恢复且租户隔离。

### 回滚

等待中的 run 保留只读查询/取消能力；不尝试跨 runtime 恢复到 Node。

## P9：Skill 编辑、审核、发布

### 用户价值

现有 Skill 管理流程完整迁到 Java 数据层，并在 SQLite/MySQL 上具备一致的版本和审计语义。

### 范围

A21、A23、A24、A26。

### 任务

- [ ] 实现 draft bundle、resource diff、review、publish、delete。
- [ ] 实现 revision/ETag、owner/RBAC、全量审计。
- [ ] review 使用独立 Agent/model config，输出结构化 findings。
- [ ] 发布前 requiredTools、frontmatter、资源和安全校验。
- [ ] 前端管理页改用 revision，处理 409。

### 测试

并发编辑、原子发布、失败回滚、内置 Skill 删除、owner 隔离、review provider error、发布后
新 run 可见/旧 run 不变。

### 最小演示

创建 draft、上传资源、审核、发布，下一次 Agent run 加载新版本。

### 退出条件

Skill CRUD 全部由 Java；运行时只读取 published revision。

### 回滚

关闭编辑/发布，已发布 revision 继续只读服务。

## P10：剩余 REST、OAuth、报告与认证

### 用户价值

前端所有业务 API 均由 Spring Boot 提供。

### 范围

A11-A18、A28-A29，以及 P1 发现的新增 Node API。

### 任务

- [ ] Spring Security/OIDC 替换 NextAuth server behavior。
- [ ] OAuth code/device/refresh 和 token 加密托管。
- [ ] feedback report RBAC。
- [ ] connection templates/config。
- [ ] 每条矩阵完成 contract + frontend call switch。
- [ ] 生产 access log 对旧 Node API 做零流量观测。

### 测试

OAuth provider mock server、token refresh race、CSRF/CORS/cookie、RBAC、契约、前端设置/
报告/连接 E2E。

### 最小演示

全新浏览器登录、配置模型/连接、聊天、Skill 管理、查看报告，全程只有 Java 业务请求。

### 退出条件

迁移矩阵 Node business disposition 均为 migrated 或有批准的 frontend-only ADR。

### 回滚

反向代理按路径回退；token 数据不反向复制到浏览器。

## P11：前端原样切换与 Node 后端退役

### 用户价值

系统只维护一个 Java 后端和一个纯交互前端。

### 范围

默认路由切换、Node server code 删除、生产观察、文档和运维。

### 任务

- [ ] 运行完整契约/E2E/性能/安全/灾备测试。
- [ ] `prod` profile 强制使用 MySQL，执行全量 Flyway、parity 和 repository contract；
  SQLite 只保留开发用途。
- [ ] 前端默认 API base 指向 Java，同源代理只转发。
- [ ] 删除 Node Agent/Skill/Tool/session/API 实现和 server secrets。
- [ ] 清理客户端 model/agent secret/local executor。
- [ ] 灰度 5%/25%/50%/100%，每档满足 SLO。
- [ ] 观察期验证旧 Node API 请求为零。
- [ ] 演练数据库恢复、Java rollback 和 provider/ClickHouse 故障。

### SLO 建议

- REST 可用性 ≥ 99.9%。
- chat 建连 p95 < 1s（不含 provider 首 token）。
- 服务侧首事件 p95 < 300ms。
- 非 provider 原因 run 失败率 < 0.5%。
- checkpoint 恢复成功率 ≥ 99.9%。

### 退出条件

需求追踪表全部有证据；Node 后端代码实际删除；密钥扫描和生产流量证明浏览器/Node 无密钥
与 Agent 执行。

### 回滚

仅保留部署版本回滚；数据库采用向前兼容 schema。观察期结束后不保留代码级双实现。
