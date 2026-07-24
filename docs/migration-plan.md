# DataStoria 后端整体迁移计划

## 1. 目标

将现有 DataStoria 中由 Next.js Node.js Runtime 承担的后端能力，分阶段迁移到独立的
Spring Boot 项目 `datastoria-server`。

最终达到：

1. 现有前端页面和交互原样迁移，不重新设计产品 UI。
2. 原有 Node.js REST API 全部由 Java API 替换。
3. chat 请求、消息、工具调用和流式事件与现有前端兼容。
4. AgentScope Java 提供 Harness Agent 运行能力。
5. Skill 加载、工具注册、工具调用、模型访问和 Agent 状态全部位于服务端。
6. Node.js 不再承担业务后端或 Agent Runtime，只保留 Next.js 前端构建与静态/BFF
   路由中确有必要的部分。

## 2. 迁移原则

- **每阶段可运行**：阶段结束时服务可以独立启动。
- **每阶段可测试**：必须有自动化测试或可重复的验证脚本。
- **每阶段可验证**：有清晰的验收标准和新旧行为对比。
- **契约先行**：先固定 API、SSE 和持久化语义，再替换实现。
- **纵向切片**：优先迁移一条完整但较小的业务链路，而不是一次铺满所有 Java 类。
- **单一变量**：避免同一阶段同时更换 Agent 框架、前端协议和数据库结构。
- **可回退**：Node 和 Java 并行期间通过配置选择后端。
- **凭据后移**：模型与 ClickHouse 凭据最终只能由 Java 服务读取。

## 3. 非目标

以下内容不在初始迁移中同步进行：

- 重写或重新设计前端界面。
- 在架构边界稳定前拆分微服务。
- 一次性迁移所有数据库表和所有 API。
- 同时把 AI SDK 前端协议改为 AG-UI。
- 开启 AgentScope Skill 自学习或自动发布。
- 为迁移而改变 ClickHouse 查询业务语义。

## 4. 基线技术

| 项目 | 选择 |
|---|---|
| Java | JDK 17 |
| Framework | Spring Boot 3.5.x（当前基线 3.5.16） |
| Build | Maven Wrapper |
| Web | Spring WebFlux |
| Streaming | HTTP SSE |
| Agent | AgentScope Java 2.x HarnessAgent |
| Development DB | SQLite 3 |
| Production DB | MySQL 8.0+（后续补充，上线前强制完成） |
| API contract | OpenAPI + SSE event schema |
| Test | JUnit 5、WebTestClient、SQLite、MySQL Testcontainers、Golden Test |
| Migration routing | 前端环境配置或反向代理开关 |

AgentScope 的具体 patch/minor 版本在引入阶段锁定，并通过最小 Harness 验证后再作为项目
依赖基线。

## 5. 阶段计划

### 阶段 0：项目初始化

**状态：已完成**

范围：

- 创建独立 Git 项目 `datastoria-server`。
- 使用 JDK 17、Maven、Spring Boot WebFlux 初始化。
- 加入 Actuator 健康检查。
- 建立文档目录和迁移计划。
- 保留最小 Spring Context 测试。

验收：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw test
./mvnw spring-boot:run
curl http://localhost:8080/actuator/health
```

退出条件：

- 测试通过。
- 应用能用 JDK 17 启动。
- `/actuator/health` 返回 `UP`。

### 阶段 1：现状盘点与契约冻结

目标：在写 Java 业务实现前，建立可自动验证的迁移清单。

范围：

- 盘点 `src/app/api` 下全部 Node REST API。
- 标记 frontend-only、BFF、server-only、废弃 API。
- 收集每个 API 的鉴权、请求、响应、错误码和持久化副作用。
- 捕获 chat SSE 的完整事件样本：
  - 普通文本；
  - reasoning；
  - 工具输入和输出；
  - 错误；
  - token usage；
  - title；
  - continuation；
  - stop/cancel。
- 输出 OpenAPI 基线和 SSE JSON Schema。
- 建立 Node API 契约测试，保存脱敏 Golden Fixtures。

最小运行结果：

- Java 服务提供 `/actuator/health`。
- 契约测试可针对当前 Node 服务运行。
- Java 尚不实现业务 API。

退出条件：

- 每个 Node API 都进入迁移矩阵。
- chat 的请求和所有事件类型都有样例。
- 已定义兼容判定规则。

### 阶段 2：Java API 基础设施与首个纵向切片

目标：验证前端可以访问 Java 服务，并建立统一 API 工程模式。

范围：

- 统一响应、错误模型、request id 和日志脱敏。
- CORS、反向代理和环境配置。
- 身份解析与开发环境测试身份。
- 实现第一个只读、无数据库依赖 API，例如 runtime config 或模型目录的 mock 版本。
- 前端增加 Node/Java endpoint 开关。
- 加入 WebTestClient 集成测试。

最小运行结果：

- 前端可切换到 Java endpoint。
- 至少一个真实页面功能由 Java API 支撑。
- 切回 Node 不影响使用。

退出条件：

- API 错误、鉴权和 tracing 模式确定。
- 前端 endpoint 切换机制稳定。

### 阶段 3：会话和消息持久化

目标：先迁移 chat 的产品数据，不引入 AgentScope。

范围：

- 迁移 session、message、message parts、title、share、feedback API。
- 使用 Flyway 分别管理 SQLite 和 MySQL 两套同版本 DDL。
- 开发 profile 使用 SQLite；生产 profile 使用 MySQL，禁止自动方言回退。
- 兼容现有消息顺序、幂等 upsert 和分享权限。
- 提供旧数据导入/校验工具。

最小运行结果：

- 前端可在 Java 后端创建、列表、打开、重命名和删除会话。
- 使用固定 assistant 响应验证消息保存和回放。

退出条件：

- Node/Java session API 契约测试一致。
- 会话权限和并发写测试通过。
- 产品消息表不承担 Agent checkpoint 职责。

### 阶段 4：AgentScope 最小 Harness Agent

目标：只证明 AgentScope Java 可以完成一轮兼容的流式对话。

范围：

- 锁定并引入 AgentScope Java 2.x。
- 配置一个模型 provider。
- 构建最小 `HarnessAgent`，暂不接 Skill 和业务工具。
- 将 `streamEvents()` 转换为现有前端兼容 SSE：
  - run start/finish；
  - text start/delta/end；
  - reasoning；
  - usage；
  - error。
- 支持取消请求。
- AgentScope 状态与产品消息分开保存。

最小运行结果：

- 现有 chat 页面可与 Java HarnessAgent 完成纯文本多轮对话。
- 刷新后能回放消息。

退出条件：

- 流式事件无丢失、乱序或重复。
- 取消、模型错误、客户端断开有自动化测试。
- API Key 不经过浏览器。

### 阶段 5：Skill 只读加载

目标：迁移现有 Skill 发现和按需读取，但暂不允许在线编辑。

范围：

- 将现有 `resources/skills` 复制为受版本控制的 Skill 基线。
- 校验所有 `SKILL.md` frontmatter 和资源路径。
- 接入 AgentScope Classpath/FileSystem Skill Repository。
- 使用内建 `load_skill_through_path` 替换 Node 的 `skill` 和
  `skill_resource` 工具。
- 映射当前 required-tools、global/self、available/disabled 语义。
- 实现 Skill 列表和详情只读 API，保持前端兼容。

最小运行结果：

- Agent 可以发现、选择、读取一个 Skill，并基于内容回答。
- 前端 Skill 列表和详情页从 Java 获取数据。

退出条件：

- 全部现有 Skill 可被扫描。
- 路径穿越、超大文件和非法 frontmatter 测试通过。
- Node/Java Skill catalog 对比无意外差异。

### 阶段 6：Toolkit 和只读 ClickHouse 工具

目标：把最安全的 ClickHouse 工具移到 Java 服务端。

范围：

- 建立 AgentScope `Toolkit` 和 Spring Bean 注册模式。
- 连接请求只传 `connectionId`，凭据由 Java 服务解析。
- 首批工具：
  - `get_tables`；
  - `explore_schema`；
  - `validate_sql`。
- 建立 Tool Group 和 Skill/Tool 可用性规则。
- 加入超时、并发限制、审计和输出裁剪。

最小运行结果：

- Agent 能针对真实 ClickHouse 完成表发现和 schema 分析。
- 浏览器不执行工具、不接触 ClickHouse 密码。

退出条件：

- 工具 JSON Schema 与预期一致。
- Node/Java Golden Test 对同一数据库返回等价结果。
- 不可用连接、超时和权限不足有清晰 UI 状态。

### 阶段 7：SQL 执行、诊断和可视化工具

目标：完成主要 ClickHouse Agent 工作流。

范围：

- 迁移 `execute_sql`、query log、昂贵查询搜索和诊断工具。
- 迁移 SQL 生成与优化能力。
- 服务端强制只读、最大行数、最大字节数和执行时间。
- 可视化工具返回 declarative spec/data，不返回前端可执行代码。
- 保持现有工具卡片所需字段兼容。

最小运行结果：

- Java Agent 可以完成：
  - schema discovery；
  - SQL generation；
  - validation；
  - execution；
  - result explanation；
  - visualization generation。

退出条件：

- 关键用户场景端到端测试通过。
- 查询取消能传播到 ClickHouse。
- 写 SQL、危险 SQL 和超限结果被阻止或审批。

### 阶段 8：HITL、暂停恢复和完整 Harness 能力

目标：把客户端工具执行替换为服务端协调的人机交互。

范围：

- 将 `ask_user_question` 映射为后端 question/approval event。
- 提供回答、允许、拒绝和恢复 API。
- 使用 AgentScope permission system 管理工具调用。
- 持久化 pending approval 和 Agent checkpoint。
- 接入 memory、compaction 和必要的 subagent。
- 支持实例重启后的 session 恢复。

最小运行结果：

- Agent 可以暂停等待用户输入，用户回答后从原位置继续。
- 重启 Java 服务后仍可恢复待审批运行。

退出条件：

- ALLOW/APPROVE/DENY 路径均测试通过。
- 重复提交和过期审批具有幂等行为。
- 多租户状态隔离通过测试。

### 阶段 9：Skill 管理和数据库 Repository

目标：恢复现有 Skill 编辑、草稿、发布和用户隔离能力。

范围：

- 接入数据库无关的 Skill Repository；开发使用 SQLite，生产使用 MySQL。
- 迁移 draft/published、global/self、owner 和资源版本。
- 替换 Skill 创建、编辑、审核、发布和删除 API。
- 管理操作加入权限和审计。
- 首期不启用 Agent 自动发布 Skill。

最小运行结果：

- 前端可以在 Java API 上完成现有 Skill 管理流程。
- 新发布 Skill 在后续 Agent turn 可见。

退出条件：

- 权限、覆盖优先级和并发发布测试通过。
- 旧数据迁移校验通过。

### 阶段 10：其余 Node REST API 替换

目标：清零 Node 业务 API。

范围：

- 按阶段 1 的 API 矩阵逐项迁移：
  - runtime/model；
  - auth integration；
  - feedback/report；
  - RCA/template；
  - connection/config；
  - 其他业务 API。
- 每个 API 都执行契约测试、前端验证和流量切换。
- Next.js route 只允许保留明确记录的前端/BFF 必需项。

最小运行结果：

- 前端所有业务操作只访问 Java API。

退出条件：

- API 矩阵中 Node business API 数量为零。
- 日志证明没有生产请求进入旧 Node API。

### 阶段 11：前端原样迁移与 Node 后端退役

目标：完成最终切换并删除双轨成本。

范围：

- 默认 endpoint 切换为 `datastoria-server`。
- 完成前端环境变量、部署、反向代理和文档调整。
- 删除 Node server-only Agent、Skill、Tool、session 实现。
- 移除模型和数据库服务端依赖。
- 观察期内保留部署级回滚，不再保留代码级双实现。

最小运行结果：

- Next.js 只提供 UI。
- Java 服务承载全部后端和 Agent 能力。

退出条件：

- 完整 E2E、性能、安全和恢复测试通过。
- 观察期无旧 API 请求。
- Node 后端代码和密钥配置已删除。

## 6. 每阶段统一交付物

每个阶段必须提交：

1. 实现代码。
2. 自动化测试。
3. API/事件契约变更。
4. 本地验证步骤。
5. 新旧行为对比结果。
6. 已知差异和后续项。
7. 回退方式。

## 7. 测试策略

### 单元测试

- DTO 和校验。
- Skill frontmatter 和路径安全。
- Tool 参数与结果转换。
- AgentScope event 到前端事件的映射。
- 权限决策和 SQL 安全策略。

### 集成测试

- WebTestClient REST/SSE。
- SQLite 临时数据库、MySQL/ClickHouse Testcontainers。
- 双方言 schema parity 与 repository contract test。
- AgentStateStore 暂停恢复。
- 数据库迁移和租户隔离。

### 契约测试

对相同 fixture 分别调用 Node 和 Java：

- 比较状态码和主要 header。
- 比较 JSON 结构和业务字段。
- 对 SSE 按事件语义比较，忽略时间戳、随机 ID 和 token 微小差异。

### 端到端测试

- 普通问答。
- 多轮会话和刷新回放。
- Skill 加载。
- schema discovery。
- SQL 生成、校验、执行。
- 工具错误和重试。
- 用户提问/审批。
- 取消和断线恢复。

## 8. 迁移完成定义

只有同时满足以下条件，迁移目标才算完成：

- 前端不再调用 Node 业务 REST API。
- 前端不再加载 Skill 或执行 Agent 工具。
- 模型和 ClickHouse 凭据不进入浏览器。
- 所有 chat/工具/HITL 状态均能由 Java 服务产生和恢复。
- AgentScope Java 是唯一 Agent Runtime。
- 原有关键用户流程通过端到端测试。
- Node 后端实现已删除，而不是仅通过配置隐藏。
- 运维、部署、监控和回滚文档齐全。

## 9. 当前下一步

进入阶段 1：

1. 生成 Node REST API 清单。
2. 找出前端对每个 API 的调用点。
3. 捕获 chat 请求与 SSE fixtures。
4. 建立 `docs/api/` 和初版 OpenAPI。
5. 建立 Node 契约测试入口。

## 10. 详细实施文档

本计划负责阶段边界；可直接开发的详细任务与设计见：

- [现状与迁移矩阵](inventory/current-state.md)
- [SQLite / MySQL 双方言数据模型](design/database-data-model.md)
- [HTTP 与流式契约](design/api-contracts.md)
- [Harness Agent 设计](design/harness-agent.md)
- [分阶段 PRD/PDC](delivery/phase-prds.md)
- [AI 实施手册](delivery/ai-implementation-playbook.md)
- [需求追踪与完成审计](delivery/acceptance-traceability.md)

阶段编号对应关系：本计划“阶段 0..11”分别对应 PRD/PDC 的 `P0..P11`。后续执行以
PRD/PDC 的任务、测试和退出条件为准，不得跳过阶段门禁。
