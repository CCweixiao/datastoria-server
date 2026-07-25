# P7 实施报告 — 完整 ClickHouse、诊断、SQL 与可视化

> 分支：`codex/p5-skill-readonly`
> 起始提交：`0ffc2db`
> 状态：**P7.1、P7.2、P7.3 已完成；P7 全量回归通过**

## P7.1：`execute_sql` 只读边界

早期 Java 工具将任意 SQL 直接发往 ClickHouse，并标记为 `readOnly=false`。本切片增加
`ClickHouseReadOnlySqlClassifier`，采用 fail-closed 双层防护：

- 词法层只允许 `SELECT/WITH/EXPLAIN/DESCRIBE/SHOW/EXISTS`；
- 正确跳过字符串、quoted identifier、行注释和块注释中的关键字/分号；
- 拒绝多语句、DDL、DML、SYSTEM/KILL/control 语句；
- 拒绝 `INTO OUTFILE`、自定义 `FORMAT`、安全 setting 覆盖；
- 拒绝 file/url/S3/HDFS/JDBC/remote/executable 等本地文件或外部网络 table function；
- ClickHouse 请求固定 `readonly=2`，并设置 30 秒、1000 行、1,000,000 bytes 限制。

输出转换为前端工具卡片契约：

```json
{
  "columns": [{ "name": "value", "type": "UInt64" }],
  "rows": [{ "value": 1 }],
  "rowCount": 1,
  "sampleRow": { "value": 1 }
}
```

ClickHouse SQL 错误进入 `error` 字段；连接失败和超时继续作为基础设施错误上抛。调用仍受 P6
run-scoped 并发、最终输出 cap 和审计策略保护。

### 当前证据

- classifier 与 output/settings 专项：9/9；
- 本地 ClickHouse：只读 SELECT 返回 2 columns/3 rows；
- 对真实工具调用 DDL 和多语句在网络请求前被拒绝；
- 真实 P6 AgentScope SSE 回归继续通过。

## P7.2：诊断与证据工具

本切片将四个历史 Node/browser 工具契约迁入 Java AgentScope：

- `search_query_log`：支持 patterns/executions、六种排序指标、聚合方式、相对/绝对时间窗和
  经过字段/操作符白名单编译的 predicates；用户值只作为转义后的 ClickHouse literal。
- `collect_cluster_status`：支持 snapshot/windowed、健康分类、阈值和趋势摘要；本地 profile
  无历史源的指标明确返回 `UNKNOWN`，不伪造数据。
- `collect_sql_optimization_evidence`：支持 SQL 或 query_id、light/full 模式，并从真实
  `EXPLAIN indexes = 1` 与 `EXPLAIN PIPELINE` 构造证据。
- `collect_rca_evidence`：输出 `schema_version=1` 的 observations/candidates/actions/gaps
  结构，严格校验 symptom、scope、target、时间范围和阈值。

A27 不再由 Controller 直接读取数据库：`RcaTemplateCatalog` 统一提供 enabled template，
运行开始时固定 `template_key/revision/checksum`，RCA 输出携带该版本证据。模板被禁用时只会
阻止对应 RCA 调用，不会阻断普通 chat run。内置模板属于启动配置，测试清理不再误删。

### 当前证据

- AgentScope schema、输入验证、SQL 编译和输出 shape 专项：10/10；
- Docker-free ClickHouse 26.5.6.64：query log、cluster snapshot、full EXPLAIN、结构化 RCA
  及 A27 HTTP endpoint 均访问真实服务并通过；
- P6 模拟模型三工具 SSE 链继续在同一真实集成测试中通过；
- Spotless 通过。

## P7.3：SQL workflow、声明式可视化与代码检索

`AgentRunCapabilities` 现在支持一组 run-scoped tool contributors；同一个 AgentScope Toolkit
注册 ClickHouse、SQL workflow 和 repository inspection 工具，不再要求把无关工具塞入单个
类中。新增能力：

- `generate_sql`：使用当前 run 已解析的服务端模型做一次**无工具**嵌套 JSON 生成；输出先过
  只读 classifier，再对当前 `connectionId` 执行真实 `EXPLAIN SYNTAX`。
- `optimize_sql`：只允许基于显式 evidence 改写，改写结果同样经过 classifier 与真实校验。
- `generate_visualization`：先校验 SQL，只返回白名单化的 declarative descriptor 和
  datasource SQL；模型返回的 HTML、脚本或未知字段不会穿过边界。
- `search_file/read_file`：根目录只由服务端
  `datastoria.agent.repository-root`/`DATASTORIA_AGENT_REPOSITORY_ROOT` 决定；canonical path、
  traversal、absolute path、逃逸 symlink、binary、文件大小、行数、字节数和结果数均受限，
  `.git/.next/node_modules/target/dist/build` 不遍历。

这五个工具与 ClickHouse 工具共享 run-scoped timeout、并发、256k output cap 和审计策略。
共享 Golden `docs/fixtures/tools/p7-workflow-contract.json` 同时由 AgentScope schema 测试和
前端 Zod contract 测试消费。

真实本地 ClickHouse 最小演示已覆盖：

`用户问题 -> generate_sql -> classifier -> EXPLAIN SYNTAX -> execute_sql ->`
`generate_visualization -> declarative spec`

全链由模拟模型驱动，不需要真实 LLM key；SQL 校验和执行访问 ClickHouse 26.5.6.64。

legacy `POST /api/ai/chat` 继续由 Spring 直接兼容 A01，但现在返回
`Deprecation: true` 和指向 `/api/ai/agent` 的 successor link；`/api/ai/chat/v2` 保留为 A01
alias。

### Tool 基线迁移状态

| 基线 | 状态 |
|---|---|
| 8 个 ClickHouse tools | migrated：Java AgentScope |
| `skill` / `skill_resource` | replaced：AgentScope skill repository/resource loader，run 固定 revision |
| `search_file` / `read_file` | migrated：受控 Java repository scope |
| `generate_sql` / `optimize_sql` / `generate_visualization` | migrated：Java model-backed wrappers |
| legacy `plan` | replaced：HarnessAgent 主循环；前端不执行 |
| `ask_user_question` | P8 HITL 范围，未提前实现 |

### 当前证据

- workflow/repository/schema/controller 专项：38/38；
- Java 全量：325/325；
- 前端 shared contract 专项：12/12，前端全量：304/304；
- Java Spotless、前端 typecheck 与 Prettier 均通过；
- 本地 ClickHouse 真实集成：readonly chain 与完整 SQL/chart chain 均通过；
- legacy route deprecation header 与 successor link 已由 Spring 集成测试覆盖。

## P7 结论

P7 已完成诊断 Golden、Java/前端全量回归、格式与类型检查，以及 Docker-free 本地
ClickHouse 真实链路验证。P8 未启动。
