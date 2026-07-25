# P6 实施报告 — Toolkit 与只读 ClickHouse 工具

> 分支：`codex/p5-skill-readonly`
> 起始提交：`e952939`
> 状态：**P6.1 已完成；P6 整体仍在进行**

## P6.1：三工具契约与运行时护栏

本切片修复 P4.8 最小 Java 工具与前端既有 wire contract 的漂移，并把 P6 三个只读工具归入
AgentScope `clickhouse-readonly` group：

- `get_tables` 接收 `name_pattern/database/engine/partition_key/limit`，拒绝完全无过滤条件的
  全库扫描，输出前端所需的 table 数组；
- `explore_schema` 接收 `tables[{table,columns}]`，支持一次最多 20 张
  `database.table`，输出 engine、sortingKey、primaryKey、partitionBy、totalColumns；
- `validate_sql` 输出 `{success,error?}`，ClickHouse 语法错误转为可渲染结果，连接不可用和超时
  仍作为基础设施错误上抛。

三个工具统一发送 `readonly=2`、`max_execution_time=30`、`max_result_rows=2500` 和
`result_overflow_mode=break`。宽表每表最多返回 100 列，并用 `truncated/guidance` 保持合法 JSON
输出；run-scoped `AgentToolExecutionPolicy` 再提供：

- 最多 2 个并发工具调用，第三个 fail fast；
- 35 秒工具级超时；
- 256,000 字符最终输出硬上限，超限 fail closed；
- 取消或终止时释放并发 permit；
- 成功、失败和拒绝均写入 `ds_audit_log`，只记录 runId、tool、connectionId，不记录 SQL、
  password、Authorization 或返回数据。

前端只保留 ClickHouse Zod schema 和工具卡片渲染；`onToolCall` 不执行这三个工具，
`client-tools.ts` 的旧“双端 executor”注释已移除。

### 当前自动化证据

```bash
./mvnw -B -ntp spotless:apply test \
  -Dtest='AgentToolExecutionPolicyTest,ClickHouseAgentToolsTest,AgentToolRegistryTest'
DATASTORIA_LOCAL_CLICKHOUSE=true ./mvnw -B -ntp -Dtest=LocalClickHouseIT test
```

- 策略、schema snapshot、registry group 专项：7/7；
- 本地 ClickHouse `26.5.6.64`：1/1；
- 真实库覆盖 filtered table discovery、schema exploration、SQL validation；
- 另一用户复用 connectionId 返回 NotFound；
- 临时 105 列 MergeTree 返回 100 列、`totalColumns=105`、`truncated=true`；
- P6 三工具审计记录真实写入 SQLite。
- Java 全量 308/308，package、Spotless 通过；
- 前端全量 292/292，typecheck、Prettier 通过。

## P6 后续项

在以下证据完成前不得把 P6 标记完成：

1. mock-model 通过真实 AgentScope Toolkit 连续调用三个工具的 SSE E2E；
2. Java/前端共享的三工具 input/output Golden fixture；
3. 网络请求和日志的 password/Authorization 扫描；
4. 连接中途取消传播到 WebClient 的专项证据；
5. 完成上述增量后再次执行全量 Java、前端和真实 ClickHouse 最终门禁。
