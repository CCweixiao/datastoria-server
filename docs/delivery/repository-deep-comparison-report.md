# DataStoria 全量迁移与清理审计

## 1. 对比基线

- 原项目：`/Users/jielongping/OpenProjects/datastoria`，
  `22b7ae42bca3927a30dd464f7326a3bbbb2217d7`
- 迁移项目：`/Users/jielongping/OpenProjects/datastoria-server`，
  `ffa8c67a` 加本报告所在工作树修改
- 审计日期：2026-07-26
- 原项目 `src/app/api`：24 个 `route.ts`，2 个 route 测试
- 迁移前端 `frontend/src/app/api`：0 个文件

本报告以三份证据交叉检查迁移结果：原项目实际 route、迁移前端实际网络调用、
Spring WebFlux 实际 handler。仅有同名文件或同路径 handler 不视为能力完成。

## 2. REST 能力覆盖

原项目业务路由已冻结为 `docs/api/openapi-baseline.yaml` 的 A01–A29。
`RestApiInventoryParityTest` 从冻结 OpenAPI 生成 method/path 集合，并与 Spring
`RequestMappingHandlerMapping` 比对。当前覆盖如下：

| 能力组 | 原 Node 能力 | Spring 实现 |
|---|---|---|
| Chat/Agent | A01、A01b、A02 | `AiAgentController`；另有 run、event replay、action、resume、cancel API |
| Session/Message/Share | A03–A09 | `ChatSessionController`、`ChatMessageController`、`SessionShareController` |
| Feedback | A10–A11 | `FeedbackController` |
| Model/OAuth | A12–A18 | `AvailableModelsController`、`OAuthCompatibilityController` |
| Command/Skill | A19–A26 | `AgentSkillController`、`SkillReviewController` |
| RCA | A27 | `RcaTemplateController` |
| Auth | A28 | Spring Security + `AuthCompatibilityController` |
| Connection template | A29 | `ClickHouseConnectionController` |

Spring 还承担原前端本地状态迁移后新增的连接 CRUD/测试/查询、provider/model 管理、
用户模型偏好、用户状态、Agent 配置和代码只读浏览接口。前端调用均通过
`backendApiFetch`/`backendApiUrl` 直连 Java；不存在 Next API proxy。

第二轮审计新增 `docs/api/frontend-spring-call-inventory.yaml`，冻结当前浏览器实际使用的
55 个 method/path/call-site 三元组。`RestApiInventoryParityTest` 现在同时校验 A01–A29
基线与这 55 个前端操作：调用文件必须仍存在、必须仍包含对应静态路径前缀、Spring
handler 集合必须覆盖全部操作。

## 3. 服务端代码迁移结果

- 原前端 AI 目录中 92 个 Agent、provider adapter、数据库 repository、Skill loader、
  Tool executor、SSE server 文件未进入迁移前端。
- 迁移前端保留的 `src/lib/ai` 文件只负责浏览器展示类型、消息序列化、远程 chat
  transport、API client，以及工具名称/参数的 UI 展示契约；不执行模型、Skill 或 Tool。
- 原 Node `CommandManager.expand` 的 Slash Command 展开已由 Java
  `SlashCommandExpander` 接管；运行时 Skill 使用 frontmatter `name`/`description`，
  而非目录 ID，避免 `/clickhouse-best-practices` 等命令与已注册 Skill 名不一致。
- 运行时 9 个内置 Skill 与引用资源只存在于 `src/main/resources/skills` 并由 Java 加载。
  前端生成的 `resources/skills` 副本已清除。
- 原 Node 的 SQLite/MySQL/PostgreSQL 会话与 Skill DDL 已从前端删除；数据库结构唯一来源为
  Spring Flyway 的 SQLite/MySQL V1–V15。
- 原 Node 会话持久化时的消息清理语义已迁入 `SessionService`：不保存图片 data URL、
  `step-start` 和无 provider continuation metadata 的空 reasoning；仅图片消息保存安全占位符。
  已删除前端未被任何运行路径引用的旧 `serialization.ts`。
- 原登录页的品牌、OAuth 错误提示、未配置提示、文档入口和协议弹窗已恢复，但供应商目录与
  登录跳转仍完全来自 Spring `/api/auth/**`。协议内容已更新为当前 Java 加密凭据存储事实。
- 原代码查看器的目录树、搜索、语法高亮、行高亮和有界分页已恢复；文件读取仍只通过
  Spring `/api/code/**`。Java 对源码窗口按 2,000 行/256 KiB 限制并返回 `truncated`，
  同时排除 `.local`、构建目录和非源码文件，避免本地 ClickHouse 数据耗尽文件清单上限。
- 前端原本由 `localStorage` 覆盖写入的 SQL 草稿、布局、查询历史等状态迁移至
  `/api/me/state/**` 后继续采用 last-write-wins；只有调用方显式发送 `If-Match` 时才执行
  乐观锁并返回 409。无版本写入使用先更新、后插入并处理并发插入竞争，避免多个页面或
  Java 实例同时保存草稿时出现伪冲突。

## 4. 本轮冗余清理

- 删除 `frontend/resources/database/{sqlite,mysql,postgres}.sql`。
- 删除未使用的 Node 后端依赖：`@auth/core`、`jose`、`knex`、`server-only`。
- 删除未使用的前端依赖/类型：`js-yaml`、`lz-string`、3 个 TanStack Router 包、
  `msw`、`raw-loader`，以及已有上游类型的 `@types/marked`、
  `@types/react-grid-layout`、`@types/uuid`。
- 删除 `serverExternalPackages: ["knex"]`、无 API route 后失效的 Server Actions body
  配置、`server-only` Vitest alias、已不存在的 `external/clickhouse` TypeScript 排除项。
- 修正前端 README，不再把浏览器端描述为 Agent/Skill/Tool/数据库运行时。
- 增加本地默认 `public/release-notes.json` 空数组，避免非 release 构建每五分钟请求一个
  不存在的静态资源；`npm run release` 仍会用发布分支的真实内容覆盖该文件。

## 5. 当前验证证据

| 验证 | 结果 |
|---|---|
| 前端格式、TypeScript、ESLint | PASS |
| 前端 Vitest | PASS，57 files / 298 tests |
| 前端生产构建 | PASS；构建路由中无 `/api/**` |
| Java Spotless | PASS |
| Java tests | PASS，406 tests |
| SQLite Flyway | PASS，V1–V15 |
| Spring handler inventory | PASS，覆盖 A01–A29 及 55 个前端操作 |
| 迁移边界测试 | PASS：无 Next API route、Node 后端依赖、重复 Skill/DDL |
| 浏览器真实联调 | PASS：Spring 会话、持久化连接、真实 ClickHouse schema/monitoring、模型供应商目录、用户状态覆盖写 |
| 新增供应商 UI | PASS：GLM、Kimi、MiniMax、百炼、DeepSeek 模板表单可打开 |
| 恢复页面回归 | PASS：登录未配置提示/隐私弹窗；代码读取/语法高亮/行高亮 |
| 本地静态资源 | PASS：`release-notes.json` 返回 200，登录协议弹窗无可访问性告警 |
| MySQL Testcontainers | 未执行：本机无 Docker；双方言 migration 静态 parity 仍由测试覆盖 |

浏览器联调先使用 `http://localhost:3000` → `http://127.0.0.1:8080`，再使用当前工作树
独立启动的 `http://localhost:3001` → `http://127.0.0.1:8081` →
本地 ClickHouse `http://127.0.0.1:18123`，成功读取 5 个数据库、175 张表及节点监控数据。
最新工作树中连续编辑 SQL 触发多次 `/api/me/state/query-draft/**` 保存，均成功并将 revision
推进到 12；控制台无 409。`system.opentelemetry_span_log` 不存在时，列能力探测通过
`system.columns` 安全返回 false，不再调用会抛出 `UNKNOWN_TABLE` 的 `hasColumnInTable`。
控制台无业务错误或 release notes 404。

## 6. 后续审计门槛

在宣布整个深度迁移目标完成前，仍需：

1. 对设置、连接、SQL、Session、Skill、Agent chat/HITL/重放继续执行浏览器网络轨迹回归；
   其中会真实调用模型的场景需要可用 provider credential。
2. 在可用 MySQL/Docker 环境执行 repository/runtime parity，而不只验证双方言 migration
   文件。当前本机 Testcontainers 探测不到 Docker，因此该项不能用 SQLite 结果替代。
3. 用户当前由 IDE 管理的 8080 Java 进程仍需在方便时重启，以加载本轮最后的
   user-state/repository 修改；本轮已在独立 8081 Java 进程复验最新 class。
