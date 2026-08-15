---
title: 安全与隐私
description: DataStoria 的凭证、查询与 AI 数据边界。
---

# 安全与隐私

## 存储的数据

Spring Boot 在开发与生产环境中均将应用配置、用户、ClickHouse 连接、模型提供商、
会话、消息、反馈和 Agent Run 状态存储在 MySQL 5.7 中。
ClickHouse 密码和模型 API 密钥使用 AES-256-GCM 加密，保存后绝不会返回给浏览器。

## 查询与 AI 数据

SQL 会被发送到 Java，由其针对所选的 ClickHouse 连接执行。AI 工作流可能会将
提示词、结构信息、错误、SQL 或工具证据发送给所配置的模型提供商。管理员
必须根据自身的数据分级选择合适的提供商和保留策略。

DataStoria 不做"数据绝不离开本机"的一揽子承诺：具体行为取决于
运维人员所配置的 ClickHouse 端点、模型提供商和部署拓扑。

## 运维人员职责

- 为面向用户的端点提供 HTTPS，并为数据库/提供商提供 TLS。
- 限制对 `datastoria.master-key`、数据库密码和 OAuth 密钥的访问。
- 使用最小权限的 ClickHouse 账户，并限制 Java 的网络访问。
- 配置备份、日志保留和提供商数据策略。
- 切勿在截图和问题报告中包含凭证或生产数据行。

参见仓库中的
[安全指南](https://github.com/CCweixiao/datastoria-server/blob/master/docs/security/secrets.md)。
