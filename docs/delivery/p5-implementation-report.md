# P5 实施报告 — 数据库 Skill 只读运行链

> 分支：`codex/p5-skill-readonly`
> 基线：`cd9c297`
> 状态：**P5.1 已完成；P5 整体仍在进行，不得开始 P6 阶段验收**

## P5.1：受版本控制的 Skill 基线与 SQLite seed

本切片把现有 9 个内置 Skill 迁入 Java classpath：

- `clickhouse`
- `clickhouse-system-queries`
- `diagnose-clickhouse-clusters`
- `diagnose-clickhouse-errors`
- `optimize-clickhouse-sql`
- `source-code-inspection`
- `sql-expert`
- `visualization`
- `vizlayer`

生产运行时不直接从 classpath 读取 Skill。`ClasspathSkillBundleLoader` 对 `SKILL.md` 和资源执行
严格扫描，`BuiltinSkillProvisioner` 再将 bundle 幂等导入当前 tenant 的数据库；catalog、
detail、resource、commands 和 Agent run 均继续只访问 `AgentSkillRepository`。

### 已完成

- 9 个 Skill、共 51 个文件进入 `src/main/resources/skills`。
- SafeConstructor YAML frontmatter 解析，禁止重复 key 和过量 alias。
- Skill/resource 路径、UTF-8、单文件 1 MiB、bundle 5 MiB 上限。
- 对排序后的路径和字节计算 SHA-256 bundle checksum。
- SQLite/MySQL V11 同步增加 `bundle_checksum` 和 `builtin`。
- 相同 checksum 不重复写入；tenant 数据库中已存在的同 id 非内置 Skill 优先。
- 内置 seed 只读：详情 `canEdit=false`，修改、发布和删除返回 409。
- required tools 来自 YAML，缺少 Java 工具的 Skill 标记为 disabled，不进入 command 或
  AgentScope run。
- Agent run 启动前从数据库解析可见 Skill，并交给 run-scoped AgentScope Skill Repository；
  浏览器不再加载 Skill 给 Agent。

### 自动化证据

```bash
./mvnw -B -ntp spotless:apply test \
  -Dtest='ClasspathSkillBundleLoaderTest,BuiltinSkillProvisionerTest,AgentSkillApiTest'
```

- 9 个 bundle 全量扫描成功。
- `source-code-inspection` 的 `search_file/read_file` requiredTools 被正确解析。
- `clickhouse` 的规则资源可从数据库读取。
- SQLite seed 幂等，tenant 同 id Skill 优先级已覆盖。

## Docker-free ClickHouse 开发基线

为了给 P6/P7 的 schema、SQL 和监控工具提供真实目标，本切片同时建立 macOS Apple Silicon
本地实例：

- 官方 `v26.5.6.64-stable` 单二进制；
- 官方同版本 server `config.xml/users.xml`；
- `.local/clickhouse` 隔离 binary/config/data/log/run，全部 gitignored；
- HTTP `18123`、Native `19000`；
- `datastoria_test.query_events` 测试表和 3 行确定性 seed；
- 官方 query log 已开启，用于 `search_query_log` 真实测试。

实测 install、start、seed、status、stop、restart 均成功。启用真实 smoke：

```bash
tools/clickhouse/install.sh
tools/clickhouse/cluster.sh start
tools/clickhouse/cluster.sh seed
DATASTORIA_LOCAL_CLICKHOUSE=true ./mvnw -B -ntp -Dtest=LocalClickHouseIT test
```

该测试覆盖浏览器形态的 connection create/query API，以及 Java AgentScope 的
`get_tables`、`explore_schema`、`validate_sql`、`collect_cluster_status` 和
`search_query_log`。真实测试发现并修复 `/{id}/query` 未默认请求 JSON、却声明 JSON
Content-Type 的缺陷；controller 现默认补 `default_format=JSON`。

测试 schema 已按 ClickHouse Skill 的 `schema-pk-*`、`schema-types-*` 和
`schema-partition-*` 规则 review：tenant/service/date/UUID 的 ORDER BY 顺序由常用过滤路径和
基数决定；UUID、Date、UInt、Enum8 使用原生窄类型；重复字符串使用 LowCardinality；无非必要
Nullable；月分区用于测试 query log/事件生命周期。

## P5 未完成项

以下退出条件尚无证据，因此 P5 不能标记完成：

1. `ds_skill_revision` / revision-scoped resource 的不可变 bundle 模型，以及 published/draft
   pointer。
2. Agent run 对实际 Skill revision 的持久化 pin；运行中发布新版本不影响该 run 的并发测试。
3. MySQL revision repository contract（本机无 Docker，可由 CI 执行）。
4. Node/Java catalog semantic diff 和完整 Skill load E2E（模拟 LLM 即可）。
5. Toolkit Registry 接替当前临时的 requiredTools 固定集合。

下一切片为 **P5.2：不可变 Skill revision、published pointer 与 run pinning**。在这些条目完成前
不把 P4.8 中提前出现的 Skill/工具代码视为 P5/P6 已交付。
