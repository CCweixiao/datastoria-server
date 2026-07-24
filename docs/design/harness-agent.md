# AgentScope Java Harness Agent 设计

## 1. 目标

以 AgentScope Java 2.x `HarnessAgent` 作为唯一 Agent runtime，将模型调用、Skill 加载、
Toolkit 注册/执行、HITL、memory、checkpoint、用量和审计全部放到 Spring Boot 后端。
前端只负责消息输入、流渲染、工具卡片和用户审批。

实施阶段锁定经过最小验证的 AgentScope patch 版本，不使用浮动版本。

## 2. 分层

```text
AgentController (HTTP/SSE)
  -> AgentApplicationService
     -> RuntimeContextFactory
     -> HarnessAgentFactory
        -> ModelAdapter
     -> DatabaseSkillRepositoryAdapter
        -> ToolkitRegistry
        -> PermissionPolicy
        -> Memory/CheckpointAdapter
     -> AgentScopeEventAdapter
     -> CompatibilityStreamEncoder
```

包建议：

```text
io.datastoria.server.agent
├── api
├── application
├── domain
├── runtime
├── events
├── persistence
└── policy
```

Spring domain 不直接暴露 AgentScope 类型给 controller 和 JPA/MyBatis mapper，避免框架升级
扩散。

## 3. Run 创建

`RuntimeContextFactory` 从服务端解析：

- tenant/user/request/session/message/run id。
- connectionId 对应的 ClickHouse connection 和权限。
- agent definition + immutable revision。
- model config + server secret。
- effective user preference。
- 当前可见 published Skill revisions。
- tool policy、超时、预算和 trace context。

客户端仅能选择已授权 model/agent/connection id，不能覆盖 prompt、tool allowlist、API key
和危险执行策略。

每个 run 固定引用 revision；配置更新只影响新 run。

## 4. HarnessAgentFactory

职责：

1. 根据 provider 构造 AgentScope model client。
2. 从 revision 构造 system prompt 和 runtime options。
3. 装配 memory/compaction。
4. 注册按策略过滤后的 Toolkit groups。
5. 注册 Skill repository 和按需加载工具。
6. 注册 permission/HITL callback。
7. 返回 run-scoped HarnessAgent，禁止跨用户复用有状态实例。

模型 provider 工厂必须显式支持能力矩阵：tools、reasoning、image、streaming、max context。
不支持能力时在 run 前失败，不在流中静默降级。

## 5. Skill Repository

### 发现

实现数据库无关的 `DatabaseSkillRepositoryAdapter`，开发连接 SQLite、生产连接 MySQL：

- 根据 tenant/user 查询 global published + owner self published。
- 过滤 disabled/invalid 和 requiredTools 不满足的 Skill。
- 同 id 选择明确优先级，返回固定 revision。
- catalog 查询不加载全部正文。

### 按需加载

Agent 获得精简 catalog；只有调用 Skill load tool 时读取 `skill_md`。资源通过规范化 path
按需加载。保留当前 progressive disclosure，避免把所有 Skill 塞入 prompt。

若 AgentScope 内建 `load_skill_through_path` 只能使用文件系统，可采用以下适配之一：

1. 优先：实现其 Skill repository SPI，直接读取当前 profile 数据库。
2. 若 SPI 不支持：run 启动时把选中 revision 物化到 run 隔离临时目录，再注册内建工具；
   目录名不可来自用户输入，run 结束后安全清理。

不得把整个数据库 Skill 集导出为共享可写目录。

### 发布一致性

- draft 校验：frontmatter、required tools、资源路径/大小、内容 checksum。
- review 与 publish 分离。
- publish 事务更新 published pointer。
- run 启动后只读固定 revision。

## 6. Toolkit 注册

`ToolkitRegistry` 聚合 Spring `ToolContributor`：

```java
interface ToolContributor {
  String group();
  Collection<ToolDefinition> definitions();
}
```

每个工具包含：

- 稳定 name/version。
- input/output JSON Schema。
- risk：READ_ONLY、QUERY、MUTATING、EXTERNAL。
- timeout、max concurrency、max output bytes。
- required permission/capability。
- executor 与 redactor。

启动时验证 tool name 唯一、Schema 可序列化、Skill requiredTools 均可解析。运行时按
agent revision、用户权限、connection 能力计算 allowlist。

## 7. ClickHouse 工具迁移

顺序：

1. `get_tables`、`explore_schema`、`validate_sql`。
2. `execute_sql`。
3. `search_query_log`、`collect_sql_optimization_evidence`。
4. `collect_cluster_status`、`collect_rca_evidence`。
5. SQL generation/optimization/visualization wrapper。

统一执行链：

```text
schema validation
 -> authorization
 -> SQL classifier/read-only policy
 -> timeout/rate/concurrency budget
 -> ClickHouse query_id tagged with run/tool call
 -> execute
 -> row/byte truncation
 -> redaction
 -> audit + typed output
```

必须阻止多语句、写入、DDL、危险 table function 和无法分类的 SQL，除非显式策略与 HITL
批准。取消要传播到 HTTP client/ClickHouse query。

## 8. 交互和权限

`ask_user_question` 不在浏览器执行。Harness 产生 pending action：

1. 事务保存 checkpoint + pending action。
2. run 状态变为 `waiting_input`。
3. 流输出兼容 tool input/question 事件并结束当前连接。
4. 前端调用 respond/approve/deny。
5. 后端 CAS pending revision，恢复 checkpoint。
6. 新 SSE 继续同一 run 或创建明确的 continuation attempt。

permission 决策：

- allow：直接执行。
- deny：向 Agent 返回结构化拒绝。
- ask：创建 approval。

高风险工具永远不能由 prompt 或 Skill 内容自行提升权限。

## 9. 事件适配

`AgentScopeEventAdapter` 将 typed events 映射到内部 domain event：

- lifecycle、content、reasoning。
- tool input/output/progress。
- usage。
- checkpoint/pending action。
- error/cancel。

适配器测试以 AgentScope fake event publisher 驱动，不调用真实模型。Compatibility encoder
使用阶段 1 Golden Fixture，分别测试完整流和逐 chunk 分割，避免 UTF-8/JSON 边界错误。

## 10. Memory 与消息

- `ds_chat_message` 是 UI 产品记录。
- Agent memory/checkpoint 是恢复执行的运行记录。
- 入站 UIMessage 通过 normalizer 转为 AgentScope Msg，保留 text/image/tool result。
- 未知 UI parts 保存但不盲目注入模型。
- compaction 产生摘要和覆盖范围，写 checkpoint；原产品消息不删除。
- session 并发 run 默认串行；同 session 第二个写 run 返回 409 或排队，策略必须固定。

## 11. 失败模型

错误分为：

- `CLIENT_*`：校验/不允许密钥。
- `AUTH_*`：登录、租户、资源权限。
- `MODEL_*`：认证、限流、上下文、provider unavailable。
- `TOOL_*`：schema、权限、timeout、output limit、execution。
- `AGENT_*`：max steps、checkpoint、internal mapping。
- `STREAM_*`：disconnect/replay gap。

前端获得安全 message 与 retryable；原始 provider/tool error 只进入脱敏后的内部观测。

## 12. 可观测性

指标至少包括：

- run 数、成功率、首 token/总耗时、取消、等待 HITL。
- model provider latency/error/token/cost。
- tool latency/error/timeout/output truncation。
- Skill load/reject/cache。
- SSE active connections、backpressure、disconnect/replay。
- checkpoint save/restore。

trace 关联 requestId、runId、sessionId、toolCallId；禁止把 prompt、密钥、完整 SQL 结果作为
默认 span attribute。

## 13. 最小 Harness 验证门

引入完整业务前必须通过：

1. JDK 17/Spring Boot 3.5.x 编译启动。
2. 一个模型的流式 text/reasoning/usage。
3. 一个假工具的 schema、调用、输出。
4. 一个数据库 Skill 的 catalog + 按需正文加载，并在 SQLite/MySQL contract 中验证。
5. ask/approve 的暂停、服务重启、恢复。
6. AgentScope 事件到 AI SDK fixture 的兼容测试。

任何一项无法实现时先写 ADR 和 spike 结论，不继续批量迁移工具。
