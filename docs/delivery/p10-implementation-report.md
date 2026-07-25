# P10 实施报告：剩余 REST、OAuth 与生产认证

## 交付范围

- A11 已由 Java RBAC 报告接口提供。
- A12 读取 Java 模型目录，并在存在服务端 GitHub OAuth 凭据时合并 Copilot 模型。
- A13-A18 已迁移到 Java；OAuth token bundle 使用 `SecretService` AES-256-GCM 加密，
  浏览器响应不包含 access/refresh token。
- A28 使用 Spring Security OAuth2/OIDC，生产身份只取认证 principal，忽略开发身份头；
  提供 providers、session、signin、signout 兼容路径。
- A29 由 Java 保留原 `[]` stub 契约。
- 前端不再保存或执行 OAuth token；所有 Java REST/SSE 请求统一携带服务端 session
  cookie，并新增登录页与认证门禁。

## 数据与安全

- Flyway V14（SQLite/MySQL）新增 `ds_oauth_credential`，只保存 `ds_secret` 外键和非敏感
  元数据。
- 令牌刷新先写入新密文并更新凭据，再软删除旧 secret。
- `/api/ai/github/models` 明确拒绝浏览器 `Authorization`。
- `prod` 的 `/api/admin/**` 要求 `ROLE_ADMIN`；tenant/user/roles 从 OAuth/OIDC claims 和
  Spring authorities 映射。
- 跨域配置显式列出允许 origin、允许 cookie，并开放 `Last-Event-ID`。

## 验证

- `OAuthCompatibilityApiTest`：令牌不回传、密文扫描、服务端 refresh、模型代理。
- `AvailableModelsApiTest`：服务端 GitHub OAuth 凭据能填充 A12 `githubModels`。
- `AuthenticatedIdentityWebFilterTest`：伪造开发身份头不能覆盖认证 principal。
- `AuthCompatibilityControllerTest`：providers/session/signin 兼容响应不泄漏 token。
- 前端：57 个测试文件、299 个测试通过；typecheck 与 production build 通过。

## 部署配置

生产环境至少配置一个 Spring OAuth2 client registration，并设置：

```text
DATASTORIA_AUTH_SUCCESS_URL=https://app.example.com
DATASTORIA_CORS_ALLOWED_ORIGINS=https://app.example.com
```

Google/GitHub 可使用 Spring Boot 标准
`SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_<PROVIDER>_*` 环境变量。前后端分域时必须使用
HTTPS；本地联调应统一使用 `localhost`，不要混用 `localhost` 与 `127.0.0.1`。
