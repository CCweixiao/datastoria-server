# P7 实施报告 — 完整 ClickHouse、诊断、SQL 与可视化

> 分支：`codex/p5-skill-readonly`
> 起始提交：`0ffc2db`
> 状态：**P7.1、P7.2 已完成；P7 整体仍在进行**

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

## P7 后续项

1. 诊断 Golden；
2. generate/optimize/visualize wrapper；
3. 受控 repository scope 的 search_file/read_file；
4. 核心 mock-model 工作流 E2E、全量回归和迁移清单。
