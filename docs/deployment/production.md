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
SERVER_HOST=0.0.0.0
SERVER_PORT=8080

DATASTORIA_DB_URL=jdbc:mysql://mysql.internal:3306/datastoria?useSSL=true
DATASTORIA_DB_USERNAME=datastoria
DATASTORIA_DB_PASSWORD=<从密钥系统注入>

# 认证：JWT 密钥与初始管理员（首次启动引导创建）
DATASTORIA_JWT_SECRET=<随机长密钥，从密钥系统注入>
DATASTORIA_BOOTSTRAP_ADMIN_USERNAME=datastoria
DATASTORIA_BOOTSTRAP_ADMIN_PASSWORD=<从密钥系统注入>

# 多租户标识（默认 default）
DATASTORIA_DEFAULT_TENANT=tenant-default

# 仅前后端分离部署（前端独立域名）时配置；统一单进程部署为同源访问，无需 CORS
# DATASTORIA_CORS_ALLOWED_ORIGINS=https://app.example.com
```

凭据加密主密钥（`datastoria.master-key`）按以下顺序解析，无需手工配置即可安全运行：

1. `DATASTORIA_MASTER_KEY` 环境变量（base64 编码的 32 字节，从密钥系统注入；
   也可用 `openssl rand -base64 32` 自行生成）；
2. 否则首次启动自动生成随机密钥并写入 `data/master.key`（权限 0600）。

无论哪种来源，都必须**稳定备份并限制访问**：密钥丢失后已保存的供应商和连接凭据不可恢复。
轮换主密钥时把旧 key 追加到 `DATASTORIA_MASTER_KEY_LEGACY`（逗号分隔，仅用于解密），存量
密文仍可读取；确认没有旧密文后可清空该列表。

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
- 仅前后端分离部署时配置 CORS（统一部署同源访问，无需配置）；
- 生产认证使用用户名/密码 + JWT（首启通过 bootstrap 管理员引导，之后建议修改默认密码）。

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
