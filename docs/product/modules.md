# 功能模块

## 用户界面

| 模块 | 主要能力 | 后端数据来源 |
|---|---|---|
| 连接管理 | 创建、编辑、测试、启停 ClickHouse 连接；内置模板 | `/api/connections` |
| Schema Explorer | 数据库、表、列、依赖与系统对象浏览 | 连接查询 API |
| SQL 工作台 | 编辑、执行、历史、片段、结果导出、Explain | 连接查询与用户状态 API |
| Dashboard | 指标、查询、缓存、节点/集群视图，分片副本切换 | ClickHouse 系统表 |
| 系统日志 | query_log、part_log、processes、ZooKeeper 等 | ClickHouse 系统表 |
| AI 会话 | 自然语言 SQL、诊断、优化、可视化、会话分享 | Agent/Session API |
| 模型设置 | 供应商模板、凭据测试、模型发现和多模型配置 | Admin Model API |
| Agent 与 Skill | Agent Revision、发布/停用、Skill 资源与命令 | Admin/Skill API |
| 反馈 | AI 消息反馈、自动错误解释与反馈报表 | Feedback API |

## 连接与集群

保存连接时 Java 服务端测试 HTTP 可达性并加密凭据。前端读取元数据后查询
`system.clusters`：配置了集群名时按名称匹配；未配置且只发现一个集群时自动采用；发现多个
集群时要求用户明确配置，避免误选。

Dashboard 提供：

- **集群汇总**：使用 `clusterAllReplicas` 查询全部分片/副本；
- **指定节点**：按 `host_address:port` 路由到单个节点；
- **入口节点**：无集群拓扑时使用当前连接。

## 模型与 Agent

系统不初始化任何可调用模型。管理员先从模板创建供应商，填写 Base URL 与 API Key，再测试
连接并发现模型。Key 不返回浏览器。用户可以选择管理员启用的模型作为默认模型。

Agent 运行时包含：

- 流式文本、reasoning、工具调用和 usage 事件；
- ClickHouse 只读 SQL 分类与执行；
- 查询日志、集群状态、SQL 优化和 RCA 证据收集；
- 文件搜索/读取（限制在配置的仓库根目录）；
- approve/deny、ask-user-question 与 resume；
- 事件重放、取消和服务重启恢复。

## 数据持久化

主要数据域包括身份与配置、模型供应商、Agent/Revision、Skill、会话/消息/分享/反馈、
ClickHouse 连接、Agent Run/Event/Checkpoint/Pending Action、用户状态和 RCA 模板。详细字段与
双方言约束见[数据模型](../design/database-data-model.md)。
