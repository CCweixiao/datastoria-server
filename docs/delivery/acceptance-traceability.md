# 需求追踪与完成审计

## 1. 目的

本表把最终目标映射到代码、测试和运行证据。只有所有 MUST 行均为 `PASS` 且证据可复核，
整体迁移才能完成。`接口存在`、`单元测试通过`或`页面看起来正常`都不是单独充分证据。

## 2. 追踪矩阵

| ID | 最终需求 | 阶段 | 必需证据 | 当前 |
|---|---|---|---|---|
| R01 | 独立 Spring Boot/JDK17 后端 | P0 | build/test/health、部署 artifact | 基础骨架已验证 |
| R02 | 模型 provider/catalog 配置落数据库 | P2 | SQLite/MySQL Flyway、共用 repository contract、重启持久化 | 未实现 |
| R03 | API key/OAuth token 服务端加密 | P2/P10 | crypto test、DB/日志/响应扫描、轮换演练 | 未实现 |
| R04 | 后端模型管理接口供前端使用 | P2 | OpenAPI、RBAC、前端 Playwright | 未实现 |
| R05 | Agent 与系统配置落数据库 | P2/P4 | 双方言 definition/revision/config 表、effective config test | 未实现 |
| R06 | 后端 Agent 配置接口供前端使用 | P2 | 管理/用户 API、revision conflict、前端验证 | 未实现 |
| R07 | AgentScope Java HarnessAgent 是唯一 runtime | P4-P11 | dependency/runtime code、E2E、Node code 删除 | 未实现 |
| R08 | Skill 资源落表并由后端按需加载 | P5/P9 | 9 个 seed checksum、SQLite/MySQL load trace、路径测试 | 未实现 |
| R09 | 工具注册和调用全部在 Harness/Toolkit 后端 | P6-P8 | tool inventory 100%、浏览器 executor 删除、E2E | 未实现 |
| R10 | HITL/暂停恢复由后端协调 | P8 | ask/approve/deny、重启恢复、幂等/隔离测试 | 未实现 |
| R11 | chat 数据结构与现有前端兼容 | P1/P3/P4 | UIMessage round-trip、字节/语义 stream fixtures、E2E | 未实现 |
| R12 | 原 Node REST API 全部迁移 | P1-P11 | A01-A29 每行 PASS、旧路由零流量/代码删除 | 未实现 |
| R13 | 前端仅保留交互页面 | P11 | repo scan、network trace、无 server tool/skill/secret | 未实现 |
| R14 | 每阶段最小可运行可测试可验证 | 全部 | 每阶段实施报告与退出条件证据 | P0 部分 |
| R15 | 数据迁移可对账、切换可回滚 | P3/P5/P11 | dry-run/import checksum、灰度/回滚演练 | 未实现 |
| R16 | 多租户、权限和审计 | P2-P11 | 双方言 cross-tenant negatives、RBAC、audit queries | 未实现 |
| R17 | 开发 SQLite、生产 MySQL，两套 DDL 同步 | P2-P11 | 同版本 migration、schema parity、双 repository contract、prod fail-fast | 未实现 |

## 3. Node API 清零审计

阶段 11 执行：

1. 从 DataStoria `src/app/api/**/route.ts` 重新生成清单。
2. 与 A01-A29 对比；新增项必须补迁移记录。
3. `rg` 搜索前端 `/api/` 调用，逐个映射 Java OpenAPI operation。
4. 在生产代理对 Node API path 加计数，完整观察周期为零。
5. 删除 Node route 及其仅服务端依赖。
6. 再运行前端 build/E2E 和 Java E2E。

若保留任一路由，必须有批准 ADR 证明它只服务 Next.js 前端基础设施，不含业务、凭据、
Agent、Skill、Tool 或持久化逻辑。

## 4. Agent/Tool/Skill 清零审计

- 当前工具基线 8 个 ClickHouse + 8 个 server/编排 + 1 个交互工具逐项对账。
- 当前 9 个内置 Skill 的 bundle/resource checksum 对账。
- 浏览器 bundle 搜索 AgentScope/AI SDK server tool executor、ClickHouse password、provider
  key；不得存在执行路径。
- 运行 trace 显示 model、skill load、tool call、checkpoint 均发生于 Java。
- 停掉 Node server-only runtime 后完整 E2E 仍通过。

## 5. 数据与密钥审计

- SQLite 与 MySQL schema 均与设计/ADR 一致，两个 Flyway location 分别 validate 成功。
- 双方言拥有相同 migration 版本集合，schema parity 和 repository contract 均通过。
- `dev` 实际连接 SQLite，`prod` 实际连接 MySQL；MySQL 不可用时生产启动失败而非回退。
- 从备份恢复到空环境并通过 smoke/E2E。
- secret 表只能解密于授权服务路径；普通 DBA dump 不含明文。
- 浏览器 DevTools network/localStorage/indexedDB 不含模型/ClickHouse secret。
- 应用日志、trace、metrics、error response 的自动扫描无 secret。
- 删除/轮换 provider key 后旧 key 不再可用。

## 6. 流协议审计

- Node baseline 与 Java 对所有固定 fixture 做 semantic diff。
- 当前生产前端无需消息组件重写即可消费。
- text/reasoning/tool/progress/usage/title/error/cancel/HITL 全覆盖。
- 慢客户端、chunk 任意分割、UTF-8、多并发、断线重放无丢失/重复。
- message DB replay 与流完成后的 UIMessage semantic equal。

## 7. 最终签署

完成时建立 `docs/delivery/final-migration-report.md`，包含：

- 本表每行最终状态和证据链接。
- A01-A29 最终 disposition。
- 性能/SLO、安全和灾备报告。
- 数据对账结果。
- 灰度时间线和旧 Node 零流量证据。
- 删除的 Node 后端文件清单。
- 已批准 ADR 和剩余非阻塞风险。

在此报告完成前，不得把“默认已切 Java”当作“迁移完成”。
