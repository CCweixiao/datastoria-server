---
title: 管理平台操作手册
description: DataStoria 管理控制台端到端操作指南：连接管理、SQL 工作台、仪表盘、模型供应商、AI 会话、技能与 Agent、分享及常见问题排查。
head:
  - - meta
    - name: keywords
      content: DataStoria 管理控制台, ClickHouse 连接, SQL 工作台, 仪表盘, 模型供应商, AI 会话, Agent 技能, 故障排查
---

本文以桌面浏览器为例。不同版本的图标位置可能略有变化，但对象名称和服务端行为保持一致。
所有图例均使用本地演示数据；密码和 API Key 不会出现在图片中。

[观看 12 秒无声功能导览](../../en/manual/07-admin-console/img/datastoria-tour.mp4)

## 1. 首次进入

1. 打开管理员提供的 DataStoria 地址；
2. 使用用户名和密码登录（引导管理员账号由服务端首次启动时自动创建）；
3. 首次进入且没有连接时，页面会引导创建 ClickHouse Connection。

![DataStoria 主界面](../../en/manual/07-admin-console/img/workbench-overview.jpg)

## 2. 管理 ClickHouse 连接

### 新建连接

1. 点击左侧连接入口；
2. 选择内置模板或"新建连接"；
3. 填写 HTTP URL，例如 `https://clickhouse.example.com:8443`；
4. 填写用户名、密码和可选 Database；
5. 集群环境填写 `system.clusters` 中的准确 Cluster 名；
6. 点击测试，成功后保存。

![创建 ClickHouse 连接](../../en/manual/07-admin-console/img/connection-dialog.jpg)

注意：

- DataStoria 使用 ClickHouse HTTP 端口，默认是 `8123/8443`，不是 Native `9000/9440`；
- 编辑已保存连接时，空密码表示保留原密码，不会把明文密码加载回浏览器；
- 生产账号建议只授予业务库和必要 `system.*` 的读取权限；
- 发现多个集群时必须明确填写 Cluster，系统不会猜测。

### 切换和删除

从左侧连接列表切换当前连接。删除连接前确认没有需要保留的会话绑定；会话历史不会自动改绑
到另一个数据库。

## 3. SQL 工作台

1. 在 Schema 树选择数据库和表；
2. 新建 Query 标签页；
3. 输入 SQL，使用执行按钮或快捷键运行；
4. 在结果区切换表格、JSON、可视化或 Explain；
5. 通过历史记录恢复之前的 SQL。

AI 解释错误时，系统会把必要的错误上下文发送给已配置模型。涉及敏感表时先检查 SQL 与错误
内容，不要把业务数据粘贴进 Prompt。

## 4. Dashboard 与集群节点

打开 Dashboards 后，使用顶部作用域选择器：

- **集群汇总 · 全部分片与副本**：聚合所有拓扑节点；
- **分片 N · 副本 N · 节点名**：只查看指定副本；
- 没有集群元数据时：使用当前连接入口节点。

![ClickHouse 查询 Dashboard](../../en/manual/07-admin-console/img/dashboard-query.jpg)

时间范围、刷新间隔和面板布局可在 Dashboard 顶部调整。某些图表依赖 ClickHouse
`metric_log`、`query_log`、`part_log` 或 OpenTelemetry 日志；目标表未启用时，该面板不可用
不代表连接失败。

当拓扑包含两个或更多节点时，顶部会出现 Monitoring scope 选择器；单节点连接不会显示这个
控件。

## 5. 配置模型供应商

进入 **Settings → Models**。系统没有预置可调用模型，必须由管理员配置。

![模型与供应商设置](../../en/manual/07-admin-console/img/model-settings.jpg)

### 使用模板

可从智谱 GLM、Kimi/Moonshot、MiniMax、阿里云百炼、DeepSeek 等模板开始：

1. 点击"添加供应商"；
2. 选择模板，核对 Base URL；
3. 输入 API Key；
4. 保存并点击测试；
5. 点击发现模型，选择需要启用的模型。

如果服务不支持模型列表 API，可手工添加 Model ID，并填写显示名称、上下文窗口、等级和
多模态能力。Base URL 应指向供应商的 OpenAI 兼容 API 根路径。

### 安全规则

- API Key 保存后只显示"已配置"，不会返回完整值；
- 更新 Key 时输入新值；留空不会覆盖已有凭据；
- 不要在聊天输入框、浏览器环境变量或前端请求体中传 Key；
- 删除供应商前先确认没有默认模型、Agent 或运行任务引用。

## 6. AI 会话

1. 选择 ClickHouse 连接和模型；
2. 新建会话并描述问题；
3. 查看 AI 的 SQL、工具结果和证据；
4. 遇到补充问题时填写答案，服务端通过 Action API 恢复原 Run；
5. 网络中断后页面使用事件序号重连，不要重复提交同一问题。

旧会话若创建时选择"无连接"，不会自动使用后来新增的连接。请新建会话或显式选择连接，
避免 AI 在错误数据库上执行查询。

## 7. Skill 与 Agent

**Settings → Skills** 展示内置和自定义 Skill。管理员可以创建、编辑、查看资源并启停 Skill；
Slash Command 来自已启用 Skill。

Agent 管理由 Agent Definition 和不可变 Revision 组成。修改后先创建 Revision，再发布；停用
只阻止新运行，不应破坏已经持久化的历史 Run。

## 8. 会话、分享和反馈

- 会话列表支持重命名和删除；
- 分享链接按权限读取，管理员可以撤销；
- 对 AI 消息提交赞/踩和说明，反馈报表仅管理员可见；
- 分享前检查会话是否包含业务 SQL、表名或结果。

## 9. 常见问题

| 现象 | 处理 |
|---|---|
| 添加供应商按钮无响应 | 刷新到最新版本，确认弹窗没有被另一个 Modal 覆盖 |
| 连接成功但 AI 无连接 | 新建会话并选择该连接 |
| 集群汇总被禁止 | 连接中填写与 `system.clusters` 完全一致的 Cluster |
| Dashboard 某面板报表不存在 | 在 ClickHouse 启用相应 system log，或忽略该可选面板 |
| 模型发现为空 | 先测试供应商；不支持列表 API 时手工添加模型 |
| 流式回答中断 | 保持会话页面打开，系统会按 Last-Event-ID 重放 |

更多错误见[故障排查](https://github.com/CCweixiao/datastoria-server/blob/master/docs/operations/troubleshooting.md)。
