# DataStoria 文档中心

这里是 DataStoria 当前产品与工程文档。一次性阶段报告和历史比对材料不再作为正式文档；
内容以仓库现状和可执行命令为准。

## 按角色阅读

| 角色 | 建议入口 |
|---|---|
| 初次了解项目 | [产品愿景](product/vision.md) → [功能模块](product/modules.md) |
| 架构师/后端开发 | [系统架构](architecture/overview.md) → [数据模型](design/database-data-model.md) → [HTTP API](api/http-api.md) |
| 前端开发 | [datastoria-web 开发与调试](development/datastoria-web.md) → [流式协议](api/stream-protocol.md) |
| 运维/SRE | [生产部署](deployment/production.md) → [统一安装包](deployment/unified-package.md) → [故障排查](operations/troubleshooting.md) |
| 管理员/使用者 | [管理平台操作手册](manual/admin-console.md) |
| 安全审计 | [密钥与敏感信息](security/secrets.md) → [ADR](adr/) |

## 文档地图

```text
docs/
├── product/       产品愿景、能力边界和功能模块
├── architecture/  当前系统结构、数据流和部署边界
├── development/   本地开发、调试、ClickHouse 联调
├── deployment/    生产部署和统一安装包
├── manual/        管理平台操作手册
├── operations/    运行维护与故障排查
├── security/      密钥、隐私和发布安全
├── reference/     导入格式等稳定参考
├── design/        数据、API、Agent 的详细设计
├── api/           OpenAPI 与流式协议
├── adr/           已接受的架构决策
└── fixtures/      自动化契约测试样例，不是用户文档
```

## 权威性约定

1. 启动和构建命令必须能从仓库根目录直接执行。
2. 运行配置以 `application-*.yaml`、`deploy/conf/` 和 `bin/` 脚本为准。
3. API 清单以 Controller 与 OpenAPI 为准；文档不复制密钥或真实凭据。
4. 数据库结构只能由 Flyway migration 演进。
5. 截图使用演示账号和虚构凭据；任何 Token、API Key、Cookie、数据库密码都不得进入仓库。
