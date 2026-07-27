# HTTP API 概览

默认 Java 地址为 `http://127.0.0.1:8080`。统一部署时浏览器通过 Next.js `/backend/**`
代理访问。JSON 错误使用 Problem Details，并附稳定业务错误码。

本地开发请求可带：

```http
x-datastoria-user-email: dev@example.com
```

生产环境身份来自 Spring Security/OAuth2，不能信任客户端自报管理员角色。

## API 分组

| 前缀 | 功能 |
|---|---|
| `/api/connections` | ClickHouse 连接 CRUD、模板、测试与查询 |
| `/api/ai/agent`, `/api/ai/chat*` | AI SDK SSE 会话入口 |
| `/api/ai/runs` | Run、事件、Action、取消与恢复 |
| `/api/ai/chat/sessions` | 会话、消息、分享与反馈 |
| `/api/ai/skills`, `/api/ai/commands` | Skill 与 Slash Command |
| `/api/admin/ai/providers` | 模型供应商、凭据、测试和模型发现 |
| `/api/admin/ai/models` | 模型 CRUD 与启停 |
| `/api/admin/ai/agents` | Agent Definition、Revision、发布与停用 |
| `/api/me/ai/*`, `/api/me/state` | 用户模型偏好与界面状态 |
| `/api/code/*` | 受限仓库文件浏览 |
| `/api/auth/*` | 登录、会话与退出兼容入口 |
| `/actuator/health` | 运行健康 |

完整路径和 Schema 见 `docs/api/openapi-baseline.yaml` 及 Controller。发布前应通过 OpenAPI
校验和 Controller 测试避免文档漂移。

## ClickHouse 查询

```http
POST /api/connections/{connectionId}/query
Content-Type: application/json

{
  "query": "SELECT version()",
  "parameters": {},
  "targetNode": "10.0.0.12:9000"
}
```

`targetNode` 用于按地址路由到节点，格式受服务端限制；调用者仍必须拥有该连接。保存的密码由
Java 解密并注入，不进入响应。

## 模型供应商流程

1. `POST /api/admin/ai/providers` 创建供应商元数据；
2. `PUT /api/admin/ai/providers/{id}/credential` 写入凭据；
3. `POST /api/admin/ai/providers/{id}:test` 测试；
4. `POST /api/admin/ai/providers/{id}/models:discover` 发现；
5. `POST /api/admin/ai/models` 保存需要启用的模型。

客户端请求不得包含 `secret`。读取供应商时只返回凭据状态或遮罩提示。

## 幂等与并发

创建 Run/会话等写入支持业务幂等键；带 Revision 的对象使用乐观锁。客户端遇到冲突应重新
读取最新对象，不要覆盖其他管理员的修改。
