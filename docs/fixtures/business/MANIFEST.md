# 业务 Fixture 清单（数据库方言无关）

本目录冻结一组与数据库方言无关的业务 fixture，用于：

1. P3 起的 repository contract test（同一测试集分别跑在 SQLite 和 MySQL 上）。
2. 旧数据导入校验：同一 JSONL 导入 SQLite 与 MySQL 后产生相同业务 checksum。
3. P5 起 Skill catalog 的 Node/Java semantic diff。
4. P6 起 ClickHouse 工具的 Golden Test。

## 设计原则

- **方言无关**：fixture 只用 JSON/JSONL，不含任何 SQLite 或 MySQL 方言语法。
- **可脱敏**：所有邮箱、token、ClickHouse 主机均为合成值（`example.test`、
  `placeholder`、`ds_test`），不含任何真实凭据。
- **可对账**：导入后用 `tools/contract-runner` 计算业务 checksum（行数、
  sequence、parts checksum），SQLite 与 MySQL 结果必须一致。

## 文件清单

| 文件 | 内容 | 行数 | SHA-256 |
|---|---|---|---|
| `sessions.jsonl` | 会话 | 3 | `45787236d65b9943ed80c4c5d1a164b48b6c4bb0d137c1a0bd92ce0a4b2984c6` |
| `messages.jsonl` | 消息（含 text/tool-call/tool-result parts） | 6 | `6c49bc200da19b4f4c8ea70abe7f4e49ac1295f203d6c9e41e350ec89ead0859` |
| `feedback.jsonl` | 反馈事件 | 2 | `fb95623ae944001ec03b0960a7138772e34291de79cb7b22db003c73de2eb70e` |
| `sessions-share.jsonl` | 分享 token（hash 占位） | 1 | `1cd46e118c0a16466a94f9671bfe3407153425791443bcc19a5467f2d7819fbb` |
| `skill-catalog.json` | 9 个内置 Skill catalog 元数据 | 9 skills | `fc29b19acefecb417155937b99c7b9c383455479f313fb5ac84cc90709c0a37c` |
| `clickhouse/schema.sql` | 脱敏 ClickHouse 测试 schema + 数据 | — | `37f7c293745131077a6d891f3e681029754c3e85ea553793f24487ef22cb8fcd` |

## 业务覆盖

### 会话/消息

- 3 个会话，跨 2 个用户（`dev@example.com`、`qa@example.com`），同一租户。
- `sess_01HX` 有 4 条消息（2 轮 user/assistant），含 tool-call + tool-result part，
  用于验证 parts JSON 保真与 sequence 排序。
- `sess_01HY` 2 条消息，最小用例。
- 验证点：
  - `GET messages` 严格按 sequence 升序。
  - parts 中 `tool-call`/`tool-result` 的 `toolCallId` 关联。
  - 未知 part 类型必须 round-trip（Java 用 Jackson `JsonNode`）。

### 反馈

- `solved=true` 无 reasonCode；`solved=false` 有 `reasonCode=too_vague`。
- 验证 upsert 幂等键 `(tenant,user,source,session,message)`。

### 分享

- `tokenHash` 为占位值，真实部署由服务端签名后只存 hash。

### Skill catalog

- 9 个内置 Skill 的 `requiredTools` 字段用于验证 Skill 可用性过滤
  （`requiredTools` 不满足时 Skill 不可用）。
- 完整 SKILL.md bundle 在 P5 由导入器加载，checksum 在那时单独冻结。

### ClickHouse 测试数据

- `ds_test.events`、`ds_test.users`、`ds_test.query_log_sample`。
- `query_log_sample` 中 `q_42` 扫描 10 亿行，用于慢查询诊断 Golden Test。
- 仅用于容器化 ClickHouse，不指向任何真实集群。

## 导入/对账流程（P3 起启用）

```bash
# 1. 导入到 SQLite（开发/测试）
./mvnw -Dtest=SessionImportContractTest test

# 2. 导入到 MySQL（Testcontainers）
./mvnw verify -Pmysql-it

# 3. 业务 checksum 必须 equal
#    - session 数 = 3
#    - 每会话消息数与 sequence 一致
#    - parts checksum（每条消息 parts_json 的 SHA-256）一致
```
