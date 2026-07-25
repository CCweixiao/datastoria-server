# DataStoria Server 迁移文档

本目录是将 DataStoria 的 Node.js 后端完整迁移到 JDK 17、Spring Boot 和
AgentScope Java 的实施依据。文档以现有代码为基线，目标读者是后续直接承担开发的 AI
或工程师。

## 阅读顺序

1. [整体迁移计划](migration-plan.md)：范围、阶段、依赖关系、完成定义。
2. [现状与迁移矩阵](inventory/current-state.md)：现有 API、存储、Agent、Skill、Tool
   的权威盘点。
3. [目标架构](target-architecture.md)：系统边界、模块和关键数据流。
4. [SQLite / MySQL 双方言数据模型](design/database-data-model.md)：模型、Agent、Skill、
   会话、运行状态及两套 DDL。
5. [HTTP 与流式契约](design/api-contracts.md)：前端所需 Java API 和兼容规则。
6. [Harness Agent 设计](design/harness-agent.md)：AgentScope Java、Skill、Toolkit、
   HITL 和状态恢复。
7. [分阶段 PRD/PDC](delivery/phase-prds.md)：每阶段可直接领取的开发任务、测试与验收。
8. [AI 实施手册](delivery/ai-implementation-playbook.md)：代码约束、执行顺序、交付模板。
9. [需求追踪与完成审计](delivery/acceptance-traceability.md)：逐项证明迁移完成的证据。
10. [本地 ClickHouse（无 Docker）](development/local-clickhouse.md)：固定版本、隔离数据目录、
    seed 与真实 Java 工具测试。

## P1 契约冻结产出

阶段 1 冻结的契约与测试脚手架（位于 `api/` 与 `fixtures/`）：

- [OpenAPI baseline](api/openapi-baseline.yaml)：A01-A29 的 HTTP 契约快照。
- [流式协议契约](api/stream-protocol.md)：AI SDK UI Message Stream 事件冻结。
- [API 迁移处置矩阵](api/migration-disposition.md)：每项 API 的 disposition 与目标阶段。
- [前端调用点与 Playwright 场景](api/frontend-call-sites.md)：调用点清单与等价性场景。
- [流式 fixture](fixtures/stream/)：8 个场景的 chunk 样本 + JSON Schema。
- [业务 fixture](fixtures/business/)：方言无关的会话/消息/Skill/ClickHouse 测试数据。
- 契约 runner：`tools/contract-runner/`，负责 fixture 校验、响应捕获与 semantic diff。


## 文档状态

| 文档 | 状态 | 用途 |
|---|---|---|
| 整体迁移计划 | Baseline | 控制范围和阶段门禁 |
| 现状与迁移矩阵 | Baseline | 防止漏迁 Node 能力 |
| 目标架构 | Baseline | 固定最终职责边界 |
| SQLite / MySQL 数据模型 | Design Ready | 可据此分别编写两套 Flyway migration |
| HTTP 与流式契约 | Design Ready | 可据此编写 Controller/OpenAPI |
| Harness Agent 设计 | Design Ready | 可据此接入 AgentScope Java |
| 分阶段 PRD/PDC | Ready for Delivery | 可逐阶段交给另一个 AI |
| AI 实施手册 | Ready for Delivery | 统一实现和验收方式 |
| 需求追踪与完成审计 | Baseline | 防止以局部完成代替整体完成 |

`Baseline` 表示迁移期间若发现现有行为与文档不一致，应先用测试或代码证据修正文档，再
继续实现。`Design Ready` 不表示功能已经完成。

## 文档维护规则

1. 每阶段开始前冻结该阶段 API、表结构和验收样例。
2. 每阶段结束时填写实际验证证据、已知差异和回滚结果。
3. API 迁移先更新 OpenAPI/事件 Schema，再修改实现。
4. 数据库变更只能通过 Flyway；SQLite/MySQL 两套 migration 必须同版本同步，已执行的
   migration 不可修改。
5. 架构选择变化时新增 ADR，并同步需求追踪表。
6. 不把“Java 接口存在”视为迁移完成；必须有前端调用和行为等价证据。
