# SQLite / MySQL 双方言数据模型

## 1. 设计目标

- 开发和测试环境使用 SQLite 3，生产环境使用 MySQL 8.0+。
- 两种数据库承载同一套模型、Agent、Skill、会话、运行状态和系统配置语义。
- 多租户字段显式存在，默认单租户也不能省略隔离边界。
- 密钥不以明文写库；API 永不回传密钥。
- 配置可版本化、可审计、可回滚。
- 产品消息与 Agent checkpoint 分离。
- 使用 Flyway 管理两套独立 DDL，表名统一 `ds_` 前缀，应用层时间统一 UTC。

## 2. 双方言原则

### 2.1 脚本目录

```text
datastoria-dao/src/main/resources/db/migration/
├── sqlite/
│   ├── V1__identity_config_and_audit.sql
│   ├── V2__model_provider_and_secret.sql
│   └── ...
└── mysql/
    ├── V1__identity_config_and_audit.sql
    ├── V2__model_provider_and_secret.sql
    └── ...
```

- 两个目录必须拥有相同的版本号、描述和业务变更；只允许 SQL 方言不同。
- `local`/`dev` profile 只加载 `classpath:db/migration/sqlite`。
- `prod` profile 只加载 `classpath:db/migration/mysql`。
- 禁止同时加载多个目录，也禁止把 SQLite 脚本用于生产。生产 profile 必须校验 JDBC
  URL 方言并 fail fast，不能自动回落到 SQLite。

建议配置拆分：

```text
application.yaml          通用配置，不提供生产数据库默认值
application-local.yaml    SQLite 文件库，例如 ./data/datastoria.db
application-test.yaml     SQLite 临时库
application-prod.yaml     MySQL datasource，缺少必需环境变量即启动失败
```

任何 ORM 的自动 DDL 功能必须关闭，Flyway 是 schema 的唯一来源。SQLite 数据文件和
`-wal`/`-shm` 文件不提交 Git。

`db/schema/{sqlite,mysql}/schema.sql` 是从 migration 生成的当前版本建库快照，仅供人工
检查或新建空库；`seed.sql` 是静态初始化数据入口。应用通过
`spring.sql.init.mode=never` 禁止自动执行快照，已有数据库只能通过 Flyway 增量升级。
修改 migration 后运行 `node bin/dev/generate-schema-snapshots.mjs` 并提交同步后的快照。

### 2.2 类型映射

| 逻辑类型 | SQLite DDL | MySQL DDL | 应用约束 |
|---|---|---|---|
| ID/枚举/短文本 | `TEXT` | `varchar(n)` | 长度、枚举用 Bean Validation + CHECK |
| 长文本 | `TEXT` | `longtext` | 设置业务大小上限 |
| JSON | `TEXT` + `json_valid()` CHECK | `json` | Jackson 序列化；契约测试比较语义 |
| boolean | `INTEGER` CHECK 0/1 | `boolean` | Java `boolean/Boolean` |
| UTC 时间 | `TEXT` ISO-8601 | `datetime(6)`/`timestamp(6)` | repository 统一 `Instant` |
| binary/密文 | `BLOB` | `mediumblob/varbinary` | 不做字符编码转换 |
| 自增审计序号 | `INTEGER PRIMARY KEY` | `bigint auto_increment` | 不作为外部资源 ID |

SQLite 连接初始化必须执行 `PRAGMA foreign_keys=ON`、合理的 `busy_timeout`，开发并发需要时
使用 WAL。不能依赖 SQLite 宽松类型：所有重要枚举、非空、唯一性和引用约束必须在 DDL
或 repository 测试中证明。

### 2.3 一致性门禁

- 每次 schema 变更必须在同一提交中增加 SQLite、MySQL 两份 migration。
- 建立 dialect parity test：分别迁移空库，读取 metadata，比较表、列的逻辑类型、非空、
  主键、外键、唯一键和索引清单。
- repository contract test 使用同一测试集运行 SQLite 和 MySQL Testcontainers。
- SQLite 用于快速本地开发，不是生产行为的替代证明；生产发布门必须包含所选生产方言测试。
- 方言确实无法等价时必须写 ADR，并在 application service 中统一可观察行为。

## 3. 通用约定

- 主键：外部可见资源使用 26 字符 ULID 或当前 32/64 字符兼容 ID，字段 `varchar(64)`。
- 租户：`tenant_id varchar(64) not null`；用户：`user_id varchar(255)`。
- 乐观锁：配置表使用 `revision bigint not null default 0`。
- JSON：仅用于真正开放的协议数据；需要索引/约束的字段独立成列。
- 软删除：管理型配置保留 `deleted_at`；消息等受产品删除策略控制。
- 审计：写操作记录 actor、resource、action、before/after 摘要和 request_id。
- 时间在 Java 中统一为 `Instant`；SQLite 保存 ISO-8601 TEXT，MySQL 保存 UTC
  `datetime(6)`。

## 4. 模型与凭据

### `ds_model_provider`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | varchar(64) PK | provider config id |
| tenant_id | varchar(64) | 租户 |
| provider_key | varchar(64) | openai/anthropic/google/github-copilot/openai-codex |
| display_name | varchar(128) | UI 名称 |
| base_url | varchar(1024) null | 自定义 endpoint |
| auth_type | varchar(32) | api_key/oauth/none |
| enabled | boolean | 是否启用 |
| config_json | json | 非敏感 provider 选项 |
| revision | bigint | 乐观锁 |
| created_by/updated_by | varchar(255) | 操作者 |
| created_at/updated_at/deleted_at | datetime(6) | 生命周期 |

唯一键：`(tenant_id, provider_key, deleted_at)` 的语义唯一需通过 active 标志或应用层保证。

### `ds_secret`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | varchar(64) PK | secret id |
| tenant_id | varchar(64) | 租户 |
| owner_user_id | varchar(255) null | null 为系统/租户密钥 |
| secret_kind | varchar(32) | api_key/access_token/refresh_token |
| cipher_text | mediumblob | AES-GCM envelope 密文 |
| key_version | varchar(64) | KMS/主密钥版本 |
| nonce | varbinary(32) | GCM nonce |
| masked_hint | varchar(32) | 仅 UI 展示 |
| expires_at | datetime(6) null | token 过期 |
| created_at/updated_at/deleted_at | datetime(6) | 生命周期 |

应用主密钥来自 KMS/环境，不入 MySQL。日志、异常、审计 before/after 均不得包含密文或原文。

### `ds_model`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | varchar(64) PK | model config id |
| tenant_id | varchar(64) | 租户 |
| provider_id | varchar(64) FK | provider |
| model_key | varchar(255) | provider model id |
| display_name | varchar(255) | UI 名称 |
| description | text null | 描述 |
| source | varchar(32) | system/discovered/custom |
| enabled/free | boolean | 配置 |
| capabilities_json | json | reasoning、image、tool、context window 等 |
| generation_defaults_json | json | temperature、maxTokens 等白名单默认值 |
| secret_id | varchar(64) null FK | 模型专属凭据；空则用 provider 凭据 |
| revision | bigint | 乐观锁 |
| created_at/updated_at/deleted_at | datetime(6) | 生命周期 |

唯一键：活动记录 `(tenant_id, provider_id, model_key)`。

### `ds_user_model_preference`

`tenant_id + user_id` 唯一；保存 `selected_model_id`、允许的偏好 JSON、revision 和时间戳。
删除模型时不得留下悬空默认值。

## 5. Agent 与系统配置

### `ds_agent_definition`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | varchar(64) PK | Agent id |
| tenant_id | varchar(64) | 租户 |
| agent_key | varchar(64) | harness/main/sql-review 等 |
| name/description | varchar/text | UI |
| status | varchar(32) | draft/published/disabled |
| published_revision_id | varchar(64) null | 当前发布版本 |
| revision | bigint | 管理记录版本 |
| created_by/updated_by | varchar(255) | 操作者 |
| created_at/updated_at/deleted_at | datetime(6) | 生命周期 |

### `ds_agent_revision`

不可变版本：

- `id`、`agent_id`、`version`。
- `model_id`。
- `system_prompt`（longtext）与 `prompt_checksum`。
- `runtime_config_json`：max steps、timeout、memory、compaction、reasoning 默认值。
- `tool_policy_json`：allow/deny/approval/group。
- `skill_policy_json`：allowed ids、required ids、自动发现策略。
- `created_by/created_at`。

发布操作在事务中把 definition 指向 revision；已有 run 始终引用创建时的 revision。

### `ds_config_entry`

用于非 Agent 专属系统设置：

- PK：`id`；唯一：`tenant_id + scope_type + scope_id + config_key`。
- `scope_type`：system/tenant/user。
- `value_json`、`schema_version`、`revision`。
- Agent UI 当前字段使用 key `ai.agent.preferences`；仅允许覆盖语言、reasoning 级别、
  auto-explain 等白名单。
- 合并顺序：system default < tenant < user；响应返回 effective value 和各层 revision。

## 6. Skill

### `ds_skill`

- `id varchar(128)`：稳定 folder/slug id。
- `tenant_id`、`owner_user_id`。
- `scope`：global/self。
- `status`：draft/published/disabled/invalid。
- `published_revision_id`、`draft_revision_id`。
- `created_by/updated_by`、revision、时间戳、deleted_at。

### `ds_skill_revision`

不可变 bundle 版本：

- `id`、`skill_id`、`version`、`state`。
- `name`、`description`、`summary`。
- `skill_md longtext`。
- `metadata_json`：author、url、slash/quick-action flags。
- `required_tools_json`。
- `content_checksum`。
- `review_status`：pending/passed/failed/not_required。
- `created_by/created_at`。

### `ds_skill_resource`

- `id`、`skill_revision_id`、`resource_path varchar(512)`。
- `media_type`、`content longblob`、`size_bytes`、`checksum`。
- 唯一 `(skill_revision_id, resource_path)`。

资源路径必须是规范化相对 POSIX 路径，不允许空、绝对路径、`..`、NUL、反斜杠逃逸；
单文件和 bundle 总大小有配置上限。

### `ds_skill_review`

记录 revision、reviewer/model、规则版本、结果、finding JSON、token usage 和时间。审核结果
不直接发布，发布仍需显式权限操作。

## 7. 会话与产品消息

### `ds_chat_session`

- `id`、`tenant_id`、`user_id`、`connection_id`、`agent_id`。
- `title`、`status`、`last_message_sequence`。
- `created_at/updated_at/deleted_at`。
- 索引 `(tenant_id,user_id,updated_at,id)` 和
  `(tenant_id,user_id,connection_id,updated_at,id)`，用于稳定 cursor。

### `ds_chat_message`

- `id`、`tenant_id`、`session_id`、`user_id`、`role`。
- `parts_json json`、`metadata_json json null`。
- `sequence bigint`，唯一 `(session_id, sequence)`，另有 `(session_id,id)` 唯一。
- `created_at/updated_at`。

Java 必须用 Jackson tree/开放 union 保留未知 parts 字段，序列化回放不能丢字段。

### `ds_feedback_event`

沿用现有字段：source、session_id、message_id、solved、reason_code、payload_json、free_text、
recovery_action_taken；唯一 `(tenant_id,user_id,source,session_id,message_id)` 实现 upsert。

### `ds_session_share`

推荐服务端存储 hashed token，而不是仅长期 JWT：

- `id`、session_id、owner_user_id、token_hash、expires_at、revoked_at、created_at。
- API 仍返回兼容 code；数据库只保存 hash。
- 分享权限默认只读；若兼容期必须允许写，使用 feature flag 并记录 ADR。

## 8. Agent 运行状态

### `ds_agent_run`

- `id`、tenant/user/session/message、agent_revision_id、model_id。
- `status`：queued/running/waiting_input/succeeded/failed/cancelled/expired。
- `idempotency_key`、`request_id`、`connection_id`。
- `input_snapshot_json`、`usage_json`、error_code/safe_message。
- started/finished/created/updated 时间。

唯一 `(tenant_id,user_id,idempotency_key)` 防重复启动。

### `ds_agent_checkpoint`

- `id`、run_id、sequence、checkpoint_type。
- `state_blob` 或 `state_json`、codec_version、checksum。
- `created_at`，唯一 `(run_id,sequence)`。

AgentScope 状态格式通过 adapter 封装；业务代码不能依赖序列化内部字段。

### `ds_agent_pending_action`

- `id`、run_id、tool_call_id、action_type(question/approval)。
- `request_json`、`response_json`、status。
- `expires_at`、resolved_by、resolved_at、revision。
- 唯一 `(run_id,tool_call_id)`；回答/批准 API 必须幂等。

### `ds_tool_execution`

- run_id、tool_call_id、tool_name、tool_revision、risk_level。
- input_redacted_json、output_summary_json、status。
- started/finished、duration、error_code。
- 查询全文/大结果根据保留策略另存，不进入普通日志。

### `ds_agent_event`

可选但建议，用于断线续传：

- `run_id + sequence` 唯一。
- `event_type`、`payload_json`、`created_at`。
- 按 TTL 清理；SSE 支持 `Last-Event-ID` 从持久事件补发。

## 9. 连接、模板与审计

- `ds_connection`：ClickHouse endpoint、database、username、secret_id、TLS/readonly 配置；
  Agent 请求只传其 id。
- `ds_connection_template`：替换当前空模板 API。
- `ds_rca_template`/`ds_rca_template_revision`：导入 YAML seed，运行固定 revision。
- `ds_audit_log`：append-only，含 tenant、actor、action、resource、request_id、safe diff、
  result、时间。

## 10. Flyway 双轨拆分

SQLite 和 MySQL 目录各自包含：

1. `V1__identity_config_and_audit.sql`
2. `V2__model_provider_and_secret.sql`
3. `V3__agent_definition_and_revision.sql`
4. `V4__chat_session_message_feedback.sql`
5. `V5__skill_and_resources.sql`
6. `V6__agent_run_checkpoint_and_events.sql`
7. `V7__connection_and_rca_templates.sql`
8. `V8__indexes_retention_and_constraints.sql`

每个版本必须先通过 SQLite migration/repository test；MySQL 方言补充后还必须通过 MySQL
Testcontainers、双方言 parity 和关键约束测试。每份 migration 都写 downgrade 说明；
回滚采用备份恢复/前向修复，不编写破坏性自动 down migration。

## 11. 数据导入

采用“导出 JSONL -> 校验 -> 导入 staging -> 事务 merge -> 对账”的方式：

- 会话对账：行数、每 session 消息数、sequence、parts checksum。
- Skill 对账：bundle 数、resource 数、published/draft 指针、checksum。
- 模型与 Agent 配置以服务端数据为准；浏览器 localStorage 不作为配置数据源。
- 导入工具支持 dry-run、重复执行和按 tenant 回滚标记。
- 导出格式必须与数据库方言无关，同一 JSONL fixture 可分别导入 SQLite 和 MySQL 并产生
  相同业务 checksum。
