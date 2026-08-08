# Agent DDL 工单收敛设计（V4）

## 1. 结论

现有 V1/V2/V3 设计的安全基础是正确的，但整体不是当前需求下的最优形态：V1 同时设计自动执行与
手工执行，超出当前边界；V2 的声明式 intent schema 很有价值，但实现仍有部分字段契约散落在 DTO
和 Descriptor；V3 的 Request / Policy / Plan / Approval / Execution / Audit 六个概念适合作为职责
划分，却不应演变成六套持久化模型或复杂 DAG。

本期收敛为一条主路径：

```text
用户请求 -> Agent 澄清 -> 服务端编译并前置检查 -> 草稿 Plan
         -> 用户确认提交 -> 管理员审批 -> 管理员手工执行 -> 审计结果
```

核心不是传统“填表—流转”，而是类似基础设施变更的 **plan / review / apply**：Agent 负责把自然语言
收敛为结构化 Intent，服务端把 Intent 编译为确定的 SQL Plan，审批冻结 Plan 的摘要，执行只接受该
冻结版本。Agent 永远没有 DDL 执行工具。

## 2. 不变量与明确边界

1. Agent 的 SQL 工具只读；Agent 只能调用 `prepare_ddl_approval`、`submit_ddl_approval` 和查询工具。
2. SQL 必须由服务端已注册的 Descriptor 生成；Agent 不能提交任意 SQL 绕过规则。
3. `prepare` 只产生草稿，不改变 ClickHouse；`submit` 只进入管理员审批。
4. 审批通过固定停在 `APPROVED`。当前不读取任何 `AUTO_AFTER_APPROVAL` 配置，不进入自动队列。
5. 只有管理员 UI 可以触发执行；执行前再次 revalidate，防止 prepare 后环境漂移。
6. 审批对象是带 digest 的有序 SQL Plan，不是 Agent 的自然语言推理过程。
7. 同一会话、同一申请人、同一工单类型只能有一个已提交工单；同一资源仍由 resource claim 做跨
   会话并发保护。这两层去重解决不同问题，不合并。
8. 首期不做工单 DAG。数据库不存在时阻断建表工单，并建议先创建数据库；前置工单执行完成后重新
   prepare 主工单。

## 3. 最小分层

| 层 | 单一职责 | 当前载体 |
| --- | --- | --- |
| Conversation | 识别类型、按契约澄清、展示 Plan、取得提交确认 | DDL approval Skill + Agent tools |
| Contract / Policy | 声明字段问题、可配置规则和允许的操作 | 工单类型目录 + `generationRuleJson` |
| Compiler | 将 Intent 和规则编译为确定 SQL；不访问审批状态 | `DdlWorkOrderTypeDescriptor` |
| Preflight | 只读检查目标与依赖；阻断不可生成的工单 | `DdlSchemaInspector` + command service |
| Workflow | 草稿、提交、审批、人工执行及 CAS | `ApprovalCommandService` |
| Persistence / Audit | 冻结 Plan、资源 claim、事件和执行结果 | 现有审批表 |

不新增规则引擎 DSL、工单 DAG、独立 Plan 表或“每种 DDL 一套状态机”。扩展一种 DDL 的标准动作是：
注册一个 Descriptor、一个字段契约、默认规则和针对性测试。

## 4. prepare 与 submit 契约

### 4.1 prepare

执行顺序必须固定：

1. 校验连接权限和已启用工单类型；
2. 按类型契约校验 Intent；缺字段时返回稳定错误 `DDL_INTENT_INVALID`，Agent 使用类型契约中的双语
   `questionEn/questionZh` 提问；
3. 查询必要 Schema，并由 Descriptor 编译 Plan；
4. 在落库前执行只读 preflight：
   - `CREATE_DATABASE`：库已存在 -> `DDL_TARGET_ALREADY_EXISTS`，不创建草稿；
   - `CREATE_TABLE`：库不存在 -> `DDL_DATABASE_REQUIRED`，建议创建库工单；任一目标表已存在 ->
     `DDL_TARGET_ALREADY_EXISTS`；
   - ALTER：目标表、字段、索引等存在性由 Schema snapshot 和 Descriptor 校验；
5. 保存草稿、SQL、规则快照、环境快照与 digest。

执行前保留同样的检查，这是 TOCTOU 防护，不是替代 prepare 前检查。

### 4.2 submit

提交时验证 revision、digest、类型定义版本和 revalidate 结果，再检查
`tenant + applicant + sourceSessionId + workOrderTypeKey` 是否已有非草稿工单。存在时返回
`APPROVAL_CONVERSATION_DUPLICATE`。草稿反复 prepare 通过 `draftId + revision` 更新，不算重复提交。

资源级重复继续依靠 `ds_approval_resource_claim`：会话去重负责用户语义，resource claim 负责数据库
并发安全。

## 5. 澄清问题目录

问题应一次只问一个有明确答案的主题；可以连续提问，但不要把十几个字段塞进一个自由文本框。动态
选项（数据库、cluster、表、字段）来自只读 Schema 查询，不在提示词中硬编码。

### 5.1 当前已支持类型

| 类型 | 必问 / 必须确认 | 可由 Agent 建议 | 服务端验证 |
| --- | --- | --- | --- |
| 创建数据库 | 数据库名、cluster | 无 | 名称合法、库不存在、cluster 有效；引擎固定 Atomic |
| 标准建表 | 库、逻辑表名、字段名/类型/空值语义、cluster | ORDER BY、PARTITION BY、分片键；说明选择依据 | 库存在、可配置后缀规则、两张目标表均不存在、键与分区列属于字段 |
| 加列 | 库、表、列名、完整类型；未来补位置/default/comment | 类型可根据业务语义建议 | 表存在、列不存在、类型安全 |
| 改列 | 库、表、列名、目标类型；兼容性与数据风险确认 | 迁移建议 | 表/列存在、关键字段保护、类型安全 |
| 删列 | 库、表、列名、下游依赖确认 | 风险摘要 | 表/列存在、关键字段保护 |
| 加跳数索引 | 库、表、索引名、字段/表达式、索引类型、granularity、是否物化 | 根据谓词和基数建议类型 | 表/列存在、索引类型白名单、粒度范围 |
| 重命名表 | 库、原表、新表、cluster、依赖确认 | 迁移顺序建议 | 原表存在、新表不存在、标识符安全 |
| 删除表 | 库、表、cluster、备份和下游依赖确认 | 风险与恢复建议 | 表存在、CRITICAL、仅人工执行 |
| 清空表 | 库、表、cluster、数据不可恢复确认 | 维护窗口建议 | 表存在、CRITICAL、仅人工执行 |
| 删除跳数索引 | 库、表、索引名、性能影响确认 | 替代索引建议 | 表和索引存在、仅允许标识符 |

标准建表当前是一个**策略化工单类型**：本地表使用 ReplicatedMergeTree，分布式表使用 Distributed，
并固定生成一对表。Agent 应说明这些是当前类型规则，而不是询问一个实现尚未消费的“任意引擎”答案。
`partitionBy` 当前支持单列，或 `toDate`、`toYYYYMM`、`toYYYYMMDD`、`toStartOfHour`、
`toStartOfDay`、`toStartOfMonth` 这类单列时间桶表达式；Descriptor 会校验函数白名单和引用列，
不会直接拼接任意表达式。
要支持 MergeTree、ReplacingMergeTree、AggregatingMergeTree 等选择，应新增规则字段或新的明确工单类型，
并让编译器真实消费该字段后再加入必问问题。副本拓扑同理，不允许只问不执行。

### 5.2 后续类型的澄清模板

| DDL 类型 | 需要澄清的核心问题 |
| --- | --- |
| 创建视图 | 库、视图名、SELECT 定义、源表、目标表（物化视图）、POPULATE/回填策略 |
| 删除视图 | 库、视图名、普通/物化视图、目标表处理、下游依赖 |
| 创建字典 | 库、字典名、主键、属性、source、layout、lifetime、凭据引用方式 |
| 加/删 Projection | 库、表、名称、SELECT 定义、是否物化、磁盘与回填窗口 |
| 加/删索引 | 库、表、索引名、表达式、类型、参数、granularity、是否物化/清理历史数据 |
| TTL | 库、表、时间列、保留期、DELETE/MOVE/RECOMPRESS 动作、目标卷、历史数据生效策略 |
| 表设置 | 库、表、setting 名和值、作用范围、版本兼容性、回滚值 |
| 分区操作 | 库、表、分区表达式和值、DROP/DETACH/ATTACH/MOVE、备份与目标磁盘 |
| 修改引擎/重建表 | 当前表、目标引擎与参数、数据量、写入停机窗口、回填/校验/切换/回退计划 |

这些模板是新增 Descriptor 的验收清单，不代表当前代码已经允许对应 DDL。

## 6. 规则模型

规则分三类，避免把所有东西塞进可编辑 JSON：

- 不可关闭的安全规则：标识符/类型安全、操作白名单、Agent 不可执行、审批后人工执行、关键字段保护；
- 用户可配置的组织规则：`localSuffix`、`distributedSuffix`、允许的引擎、索引类型、默认粒度、命名
  正则、风险阈值；
- Agent 建议：ORDER BY、分片键、分区建议。建议必须写入 Intent 并经过服务端校验，不能直接变成 SQL。

`generationRuleJson` 只保存组织规则，并由对应 Descriptor 的 `validateRules` 校验。任何规则变化提升
定义 revision 和 checksum；已提交 Plan 不随规则漂移，草稿提交前必须重新生成。

内置类型的双语名称、描述、允许操作和默认规则集中声明在
`approval-types/clickhouse-ddl.json`。`DdlWorkOrderTypeSpecificationRegistry` 启动时读取并检查清单，
Catalog 只对数据库缺失的 `typeKey` 执行 `createTypeIfAbsent`，不会覆盖租户已有配置。具体 Descriptor
负责不可关闭的安全规则校验；Catalog 不再包含按 `generatorKey` 增长的 `switch`。清单引用了未注册的
Descriptor、默认规则不满足 Descriptor 安全约束，应用启动即失败，避免带着半注册类型运行。

表级 Descriptor 统一继承 `AbstractTableDdlDescriptor`，通过 `TableTargetPolicy` 声明物理目标：

| 策略 | 当前工单 | 执行目标与顺序 |
| --- | --- | --- |
| `LOGICAL_PAIR_LOCAL_FIRST` | 建表、加列、改列 | `_local` 后 `_all` |
| `LOGICAL_PAIR_DISTRIBUTED_FIRST` | 删列、重命名表、删表 | `_all` 后 `_local` |
| `LOCAL_ONLY` | 加/物化索引、删索引、清空表 | 仅 `_local`，Distributed 自动反映本地数据/索引语义 |

若用户显式传入带配置后缀的物理表名，非建表工单保持单目标兼容；建表只接受逻辑名。新增表级 DDL
类型必须选择一个目标策略，不允许在具体 Descriptor 内重复拼后缀。

## 7. 可参考的成熟模式

- Terraform 的 saved plan 将“生成变更”和“应用确切变更”分开，并强调环境变化后计划可能过期；这里
  对应 `prepare + digest + revalidate + manual execute`：
  https://developer.hashicorp.com/terraform/cli/commands/plan
- HCP Terraform 的 saved plan 不自动 apply，状态变化会使旧计划失效；对应本设计的审批后仍需人工执行
  与环境快照校验：
  https://developer.hashicorp.com/terraform/enterprise/workspaces/run/modes-and-options
- GitHub Environments 把 deployment protection rule 放在执行之前，可要求人工 reviewer 并防止自批；
  对应管理员审批与执行权限隔离：
  https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments

借鉴的是“冻结变更、策略门禁、人工 apply、审计”四个机制，不复制它们的 workspace、pipeline 或
多阶段 deployment 模型。

## 8. 验收标准

- Agent 工具注册表中没有 DDL execute/review 工具，普通 SQL 工具拒绝非只读 SQL；
- 每个已启用类型都返回精确字段契约和中英文问题；
- 缺字段、库不存在、对象已存在均不会留下新草稿；
- 同会话同类型第二次提交返回稳定双语错误；
- 审批通过只到 `APPROVED`，只有管理员手工 execute 才进入 `RUNNING`；
- suffix 等用户规则由服务端生成和校验，Agent 无法覆盖强制规则；
- prepare、submit、execute 三个时点均有与风险相称的测试，所有新增错误有双语测试。
