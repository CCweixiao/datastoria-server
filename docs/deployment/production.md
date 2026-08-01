# 生产部署

## 推荐拓扑

生产环境推荐使用统一安装包运行 Next.js 与 Java，并使用外部 MySQL 5.7。入口负载均衡器负责
TLS、域名、访问日志和限流；Java 只允许 Next.js/受信网络访问。ClickHouse 与模型供应商通过
受控出站网络访问。

## 1. 准备 MySQL

创建独立数据库和最小权限账号。字符集使用 `utf8mb4`。不要把密码写进命令历史；下列值均为
占位符：

```sql
CREATE DATABASE datastoria CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'datastoria'@'10.%' IDENTIFIED BY '<由密钥系统生成>';
GRANT ALL PRIVILEGES ON datastoria.* TO 'datastoria'@'10.%';
```

应用首次启动时 Flyway 自动执行 MySQL migration。生产中应先备份数据库，再升级应用；不要
手工修改 `flyway_schema_history`。

## 2. 安装

```bash
tar -xzf datastoria-<version>.tar.gz
cd datastoria-<version>
bin/datastoria init
```

编辑权限为 `0600` 的 `conf/datastoria.env`：

```dotenv
DATASTORIA_PROFILE=prod
BACKEND_HOST=127.0.0.1
BACKEND_PORT=8080
FRONTEND_HOST=0.0.0.0
FRONTEND_PORT=3000

DATASTORIA_DB_URL=jdbc:mysql://mysql.internal:3306/datastoria?useSSL=true
DATASTORIA_DB_USERNAME=datastoria
DATASTORIA_DB_PASSWORD=<从密钥系统注入>
DATASTORIA_MASTER_KEY=<base64 编码的 32 字节随机值>
DATASTORIA_CORS_ALLOWED_ORIGINS=https://datastoria.example.com
DATASTORIA_AUTH_SUCCESS_URL=https://datastoria.example.com
DATASTORIA_DEFAULT_TENANT=tenant-default

SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=<由身份平台提供>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=<从密钥系统注入>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=openid,profile,email
```

`DATASTORIA_MASTER_KEY` 用于解密已保存的供应商和连接凭据，必须稳定备份并限制访问。丢失后
不能恢复密文；轮换必须采用明确的数据重加密流程，不能直接替换。

## 3. 启动与检查

```bash
bin/datastoria start
bin/datastoria status
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:3000/
```

日志：

```bash
bin/datastoria logs 200
```

启动脚本校验 Java 17、Node.js 20+、后端健康和前端可达性；任一进程启动失败时不会把半可用
状态报告为成功。

## 4. TLS 与网络

- 公网只暴露 HTTPS 入口，不直接暴露 `8080`；
- `/actuator` 仅在内网开放；
- MySQL 和 ClickHouse 使用网络 ACL/TLS；
- ClickHouse 账号按业务库和只读诊断范围授权；
- CORS 只填写真实前端 Origin；
- 生产认证至少配置一个 OAuth2/OIDC Provider。

## 5. 升级与回滚

1. 备份 MySQL 和 `conf/`；
2. 阅读 Release Notes 和数据库 migration；
3. 在预生产环境用生产 profile 验证；
4. 停止旧版本，解压新目录，复制受控配置；
5. 启动并检查 health、登录、连接、模型和一条只读查询；
6. 保留旧二进制用于回滚。

数据库 migration 通常不可逆。若新版本已写入新结构，回滚应用前必须确认旧版本兼容新
Schema；否则恢复升级前备份。

## 6. 备份与监控

备份对象：

- MySQL `datastoria` 数据库；
- `conf/datastoria.env`（加密保管）；
- 主密钥（独立密钥系统，多副本）；
- 受控的环境变量配置清单。

监控至少覆盖 Java/Next.js 进程、`/actuator/health`、HTTP 5xx、SSE 断开、MySQL 连接池、
磁盘和证书到期。不要采集请求体中的 Prompt、SQL 结果或 Authorization Header 到普通日志。

统一包的构建、目录结构和前后端分离方式见[统一安装包](unified-package.md)。
