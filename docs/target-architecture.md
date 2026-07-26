# 目标架构

## 项目命名

项目名称确定为 **DataStoria Server**，仓库名为 `datastoria-server`，Maven 坐标为：

```text
io.datastoria:datastoria-server
```

选择该名称的原因：

- 表达它是 DataStoria 的统一服务端，而不只是 AI 或 Agent 子服务。
- 不把项目名称绑定到 Spring Boot、AgentScope 或 ClickHouse 的具体实现。
- 未来可以承载 REST API、Agent Runtime、会话、连接和管理能力。

## 最终职责边界

### DataStoria 前端

- 保留现有 Next.js/React 页面和交互方式。
- 渲染聊天消息、推理过程、工具状态、图表和审批界面。
- 负责请求发起、SSE 消费、取消和断线处理。
- 不持有模型 API Key。
- 不加载 Skill，不注册或执行 Agent 工具。
- 不直接保存 Agent 状态。

### DataStoria Server

- 提供原 Node.js REST API 的 Java 等价实现。
- 负责身份、租户、连接和权限校验。
- 保存聊天会话、消息、反馈和运行元数据。
- 通过 AgentScope Java 提供 Harness Agent 能力。
- 在服务端发现、加载和管理 Skill。
- 在服务端注册、授权和执行工具。
- 管理模型配置、Agent 状态、暂停、恢复和可观测性。
- 访问 ClickHouse，并执行超时、限流、只读和结果裁剪策略。

## 建议模块

初期保持单体 Maven 工程，通过 Java package 建立边界；在边界稳定前不拆成多个微服务
或 Maven module。

```text
io.datastoria.server
├── api             REST/SSE controller、DTO、异常映射
├── security        身份、租户、权限
├── connection      ClickHouse 连接管理
├── session         会话、消息、反馈
├── model           模型目录、凭据和模型工厂
├── agent           HarnessAgent 构造、运行和事件映射
├── skill           Skill repository、发布和权限
├── tool            Toolkit、工具组和工具实现
├── clickhouse      ClickHouse client 和查询保护
├── persistence     数据库无关 repository、事务和方言适配
└── observability   指标、审计、日志和 tracing
```

## 数据库运行环境

- `local`、`dev`、默认测试环境使用 SQLite 3，便于开发者零依赖启动。
- `prod` 使用 MySQL 8.0+；`postgres` 使用 PostgreSQL 16+。两个生产 profile 都必须
  校验对应 JDBC URL 并 fail fast。
- 业务层只依赖 repository 接口，不根据数据库类型分叉业务规则。
- Flyway 分别维护 `db/migration/sqlite`、`db/migration/mysql` 与
  `db/migration/postgresql` 三套 DDL；每次变更使用相同版本号和描述。
- 同一套 repository contract test 分别在 SQLite、MySQL 和 PostgreSQL 上运行，并增加
  schema parity 检查。SQLite 通过不能替代所选生产方言验收。

## 核心数据流

```text
React UI
  -> POST /api/ai/agent
  -> Spring WebFlux
  -> RuntimeContext(userId, sessionId, connectionId)
  -> AgentScope HarnessAgent.streamEvents(...)
  -> Skill / Toolkit / Model / AgentState
  -> AgentScope typed events
  -> compatibility event mapper
  -> SSE response
  -> existing React message and tool renderers
```

## 兼容策略

迁移期间以现有前端实际使用的 HTTP/SSE 行为为契约，而不是以 Node.js 内部 TypeScript
类型为契约。

Spring Boot 后端首先输出与当前 AI SDK UI 消息流兼容的数据。只有完整替换 Node API
并稳定运行后，才单独评估是否将前端协议迁移到 AG-UI。这样避免同时替换后端运行时和
前端消息模型。

## 状态模型

产品聊天数据与 Agent 运行状态分开：

- 产品数据：session、message、message part、feedback、title、usage。
- Agent 状态：checkpoint、memory、compaction、pending approval、subagent state。

前者服务于 UI 展示和产品查询，后者服务于准确暂停、恢复和横向扩容。
