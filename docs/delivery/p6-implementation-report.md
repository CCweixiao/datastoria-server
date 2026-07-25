# P6 实施报告 — Toolkit 与只读 ClickHouse 工具

> 分支：`codex/p5-skill-readonly`
> 起始提交：`e952939`
> 状态：**P6.1、P6.2 已完成；P6 达到退出条件**

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

## P6.2：Golden、三工具 Agent E2E 与网络取消

`docs/fixtures/tools/p6-readonly-contract.json` 固定三个工具的 input/output Golden。Java
AgentScope schema snapshot 验证同一 fixture 的输入字段，前端 Zod 同时解析输入和输出样例；真实
ClickHouse 集成测试验证 Java 输出的对应字段和值。

本地集成测试还启动完整 `/api/ai/agent` SSE：

1. mock model 从真实 AgentScope schema 选择 `get_tables`；
2. 读取工具结果后调用 `explore_schema`；
3. 再调用 `validate_sql`；
4. 模型确认三份真实 ClickHouse 输出并结束，SSE 返回 `[DONE]`。

浏览器在这条链路中只提交 `connectionId` 并渲染 tool event，不执行 SQL。新增 Reactor Netty
网络测试在 ClickHouse 响应挂起时取消订阅，确认取消传播到服务端 response publisher；同时确认
密码只用于服务端 Basic Authorization，不进入 SQL body。生产代码日志扫描未发现记录 password、
Authorization、credential 或 secret 的语句；现有 API 测试继续验证明文密码不出现在响应。

## P6 最终门禁

```bash
./mvnw -B -ntp spotless:apply clean verify
npm run test -- --run && npm run typecheck && npm run format
DATASTORIA_LOCAL_CLICKHOUSE=true ./mvnw -B -ntp -Dtest=LocalClickHouseIT test
```

- Java 全量：310/310，package、Spotless 通过；
- 前端全量：295/295（新增 Golden 3/3），typecheck、Prettier 通过；
- 本地 ClickHouse：1/1，包含真实三工具 AgentScope SSE 链；
- 全量首次运行暴露 session pagination 测试依赖 `Thread.sleep`/墙钟的偶发排序；测试已改用
  确定性 `updated_at` fixture，专项 10/10，第二次 Java 全量通过。
- 本机无 Docker，MySQL `SchemaParityTest` 仍为 0 tests；按当前 SQLite 开发约束不阻塞 P6。

P6 退出条件已满足：`get_tables`、`explore_schema`、`validate_sql` 完全由服务端 Toolkit
执行，前端仅保留 schema、事件类型和渲染。下一阶段为 P7，其余五个工具不能沿用当前最小实现
直接验收，必须补只读 SQL classifier、参数/输出契约和真实工作流证据。
