# DataStoria 现状与迁移矩阵

## 1. 盘点基线

本清单基于 DataStoria 仓库以下位置：

- `src/app/api/**/route.ts`：Node/Next.js HTTP 入口。
- `src/components/chat/chat-factory.ts`：聊天请求、AI SDK transport 和客户端工具执行。
- `src/lib/ai/agent`：规划、SQL、优化、可视化和标题 Agent。
- `src/lib/ai/skills`：磁盘/数据库复合 Skill Provider。
- `src/lib/ai/tools`：ClickHouse、Skill、代码搜索和交互工具。
- `src/lib/ai/session`：会话、消息、分享与反馈存储。
- `src/components/settings/models/model-manager.ts`：当前浏览器模型配置。
- `src/components/settings/agent/agent-manager.ts`：当前浏览器 Agent 配置。

迁移实施前应在阶段 1 将本文件中的每一行转换为可执行契约测试。代码继续演进时，以迁移
分支冻结时的代码和 Golden Fixture 为最终事实。

## 2. Node API 迁移矩阵

| ID | 方法与路径 | 当前职责 | 鉴权/关键输入 | Java 目标 | 阶段 |
|---|---|---|---|---|---|
| A01 | `POST /api/ai/agent` | AI SDK 主 Agent 流、会话写入、标题、Skill/Tool | 用户；远程模式传 sessionId、connectionId、message、model、agentContext；当前仍可传明文 connection/apiKey | 保持路径和 Data Stream 协议；只接受配置 ID，不接受密钥 | 4、6、7、8 |
| A02 | `POST /api/ai/chat` | legacy 两段式 planner/sub-agent 流 | messages、context、model | 兼容期保留；HarnessAgent 达标后返回弃用头，最终删除前端调用 | 7、11 |
| A03 | `GET /api/ai/chat/sessions` | 分页/按 connection 查询会话 | 登录；connectionId、cursor、limit(1..500) | 等价实现 | 3 |
| A04 | `POST /api/ai/chat/sessions` | 创建会话并批量写初始消息 | 登录；connectionId、可选 sessionId/title、messages | 等价实现、事务写入、幂等 | 3 |
| A05 | `GET /api/ai/chat/sessions/{id}` | 获取本人或分享会话 | 可匿名分享；`x-datastoria-session-share-code` | 等价实现 | 3 |
| A06 | `PATCH /api/ai/chat/sessions/{id}` | 重命名会话 | 本人或现有代码允许的分享访问 | 首先兼容；安全评审决定分享者是否可改名 | 3 |
| A07 | `DELETE /api/ai/chat/sessions/{id}` | 删除会话 | 本人或现有代码允许的分享访问 | 首先兼容；数据级联、审计 | 3 |
| A08 | `GET /api/ai/chat/sessions/{id}/messages` | 回放 UIMessage | 本人或分享码 | 保持顺序、parts/metadata JSON 结构 | 3 |
| A09 | `POST /api/ai/sessions/{id}/share` | 生成限时分享码和 URL | 登录且是 owner | Java 签名、可轮换密钥、兼容 header/query | 3 |
| A10 | `POST /api/ai/chat/feedback/auto-explain` | 幂等写反馈事件 | 登录；无远程存储时返回 202/recorded=false | 数据库无关 upsert | 3 |
| A11 | `GET /api/ai/chat/feedback/report` | 管理员反馈聚合 | 登录、报告权限；days/source | Java RBAC 与等价聚合 | 10 |
| A12 | `POST /api/ai/models/available` | 系统模型和可选 Copilot 动态模型 | 当前请求可带 GitHub token | 改为服务端凭据和用户可见配置；响应兼容 | 2 |
| A13 | `POST /api/ai/codex/auth/token` | Codex OAuth code exchange 代理 | code、code_verifier、redirect_uri | Java OAuth client | 10 |
| A14 | `POST /api/ai/codex/auth/refresh` | Codex token refresh | refresh_token | Java OAuth client；token 加密落表 | 10 |
| A15 | `POST /api/ai/github/auth/device/code` | GitHub device flow 启动 | server client id | Java OAuth client | 10 |
| A16 | `POST /api/ai/github/auth/device/token` | GitHub device token 轮询 | device_code | Java OAuth client | 10 |
| A17 | `POST /api/ai/github/auth/refresh` | GitHub token refresh | refresh_token | Java OAuth client | 10 |
| A18 | `GET /api/ai/github/models` | 代理 Copilot 模型目录 | Authorization header | 服务端 token；兼容期可代理，最终不让 token 经过浏览器 | 10 |
| A19 | `GET /api/ai/commands` | 从可用 Skill 生成 slash commands | 可选用户身份、运行时工具集合 | 从数据库 published Skill 生成 | 5 |
| A20 | `GET /api/ai/skills` | 有效 Skill catalog | includeDraft 仅受权限约束 | 数据库 Repository、等价过滤 | 5、9 |
| A21 | `POST /api/ai/skills` | 创建 Skill bundle | 登录、编辑权限 | 草稿/发布事务、版本检查 | 9 |
| A22 | `GET /api/ai/skills/{id}` | Skill 详情 | includeDraft | 等价实现 | 5 |
| A23 | `PATCH /api/ai/skills/{id}` | 保存/发布 Skill 或资源 | 登录、编辑权限、action | 乐观锁、审计 | 9 |
| A24 | `DELETE /api/ai/skills/{id}` | 删除 DB Skill | 登录、owner/权限 | 软删除或受控硬删、审计 | 9 |
| A25 | `GET /api/ai/skills/{id}/resource?path=` | 读取 Skill 资源 | 路径必须受限于 Skill | 数据库 blob/text，防路径穿越 | 5 |
| A26 | `POST /api/ai/skills/actions/review` | LLM 审核 Skill 文件 | 登录、编辑权限；当前仅 file review | Harness/专用 review service | 9 |
| A27 | `GET /api/ai/rca/templates` | 返回 YAML RCA 模板 | 无 | 模板落 MySQL 或 classpath seed，API 等价 | 7、10 |
| A28 | `GET/POST /api/auth/{*}` | NextAuth 或 disabled fallback | 部署配置相关 | 最终由 Spring Security/OIDC 替换；前端只持 session/cookie | 2、10 |
| A29 | `GET /api/connections/templates` | 当前返回空模板列表 | 无 | Java connection config API 的兼容入口 | 2、10 |

`src/app/api/ai/chat/v2/route.ts` 当前只是 re-export/别名时，应在契约冻结时确认实际路由
行为并归并到 A01，不单独创建重复 Java 业务实现。

## 3. 当前浏览器配置必须后移

### 3.1 模型配置

当前 `ModelManager` 在浏览器存储：

- `settings:ai:model-settings`：`provider + modelId + disabled + free`。
- `settings:ai:provider-settings`：API key、refresh token、过期时间和 authError。
- 当前选择模型：`provider + modelId`。

目标：

- provider、model catalog、凭据引用、启停、能力、默认模型落库；开发 SQLite、生产 MySQL。
- 密钥/refresh token 必须服务端加密，任何查询 API 只返回 `configured`、掩码和元数据。
- 用户选择模型可按用户落表；Agent 运行时只接收 `modelConfigId` 或使用 Agent 默认值。
- 禁止 chat body 继续携带 `apiKey`。

### 3.2 Agent 配置

当前 `AgentConfigurationManager` 在浏览器存储：

- `mode`：`v2 | legacy`。
- `pruneValidateSql`、`outputReasoning`、`reasoningLevel`。
- `autoExplainClickHouseErrors`、`autoExplainBlacklist`。
- `aiResponseLanguage`。

目标：

- 系统默认、租户覆盖、用户偏好分层落库，并在 SQLite/MySQL 保持相同语义。
- Agent 定义与运行策略分开：定义包含模型、prompt、Skill/Tool policy；用户偏好只覆盖允许字段。
- API 返回合并后的 effective config，同时返回 revision/ETag 防止覆盖更新。

## 4. Agent 能力清单

| 能力 | 当前实现 | HarnessAgent 目标映射 |
|---|---|---|
| 普通多轮对话 | AI SDK `streamText` | `HarnessAgent.streamEvents()` |
| 系统 prompt | orchestrator prompt builder | Agent definition + prompt template revision |
| 规划/路由 | PlanningAgent + specialized agent | Harness tool/subagent 或统一 reasoning loop |
| SQL 生成 | sql-generation-agent | server tool/subagent |
| SQL 优化 | sql-optimization-agent | Skill + evidence tools |
| 可视化 | visualization-agent | declarative visualization tool |
| 标题生成 | SessionTitleGenerator | 独立后台/轻量模型任务 |
| 上下文 mention | MentionContext | 入站 message normalizer |
| 消息裁剪 | MessagePruner | AgentScope memory/compaction adapter |
| token usage | normalize/sum usage | AgentScope event adapter + run usage table |
| 续跑 | assistant tool output continuation | run/checkpoint + resume API |
| 交互提问 | 客户端 `ask_user_question` | Harness HITL pause/resume |
| Skill 发现/加载 | disk + DB provider，`skill`/`skill_resource` | 数据库 SkillRepository + `load_skill_through_path` 等价工具 |

## 5. Skill 基线

当前内置 Skill：

- `clickhouse`
- `clickhouse-system-queries`
- `diagnose-clickhouse-clusters`
- `diagnose-clickhouse-errors`
- `optimize-clickhouse-sql`
- `source-code-inspection`
- `sql-expert`
- `visualization`
- `vizlayer`

必须保留的语义：

- catalog：id、name、description、source、status、state、scope、version、author、url、
  summary、hasResources、slash/quick-action flags、requiredTools。
- 状态：`draft | published`。
- 范围：`global | self`，self 只能 owner 可见。
- 数据库内容优先于同 id 磁盘 seed；迁移完成后当前 profile 数据库是运行时事实源。
- Skill 与资源作为一个 bundle 发布；Agent 一次 run 固定 Skill revision，运行中发布新版本
  不影响该 run。
- requiredTools 不满足时 Skill 不可用，并向管理 API暴露原因。

## 6. Tool 基线

### ClickHouse 工具

- `explore_schema`
- `get_tables`
- `execute_sql`
- `validate_sql`
- `collect_sql_optimization_evidence`
- `search_query_log`
- `collect_cluster_status`
- `collect_rca_evidence`

### Server/编排工具

- `skill`、`skill_resource`
- `search_file`、`read_file`
- `generate_sql`、`optimize_sql`、`generate_visualization`
- legacy `plan`

### 交互工具

- `ask_user_question`

所有工具必须迁到 Java Toolkit。前端仅渲染 tool input/output/progress/HITL，不注册执行器。
ClickHouse 工具只接收 `connectionId` 解析出的服务端连接，不再接收浏览器提交的密码。

## 7. 已知迁移风险

1. AI SDK Data Stream 不是普通 `text/event-stream` 语义；必须以抓取到的帧为契约。
2. 当前分享访问似乎可走 PATCH/DELETE，兼容与安全目标可能冲突；先锁定行为，再用显式
   ADR 变更。
3. parts/metadata 是开放 JSON union，Java DTO 不应在早期丢弃未知字段。
4. Java 需要长期支持开发 SQLite 和生产 MySQL 两种方言；repository 行为必须由同一套
   contract test 约束，不继续承诺 PostgreSQL。
5. OAuth provider token 当前进入浏览器；迁移中要分两步兼容，最终必须服务端托管。
6. 工具输出可能很大；WebFlux、SQLite TEXT/MySQL JSON 和 SSE 都需要大小、裁剪、背压
   与超时策略。
