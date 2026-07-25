# P7 实施报告 — 完整 ClickHouse、诊断、SQL 与可视化

> 分支：`codex/p5-skill-readonly`
> 起始提交：`0ffc2db`
> 状态：**P7.1 已完成；P7 整体仍在进行**

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

## P7 后续项

1. 对齐 query log、optimization evidence、cluster status、RCA 的前端 input/output；
2. RCA template A27 与诊断 Golden；
3. generate/optimize/visualize wrapper；
4. 受控 repository scope 的 search_file/read_file；
5. 核心 mock-model 工作流 E2E、全量回归和迁移清单。
