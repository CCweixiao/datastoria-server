# 开发与调试

## 环境要求

| 工具 | 版本 |
|---|---|
| JDK | 17 |
| Node.js | 22（最低 20） |
| npm | 与 `datastoria-web/package-lock.json` 兼容 |
| Git | 支持 submodule |
| MySQL | 5.7 |
| ClickHouse | 可选；真实查询联调需要 |

macOS：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
java -version
node --version
git submodule update --init --recursive
```

## 后端

先创建开发数据库与账号（示例密码只用于本机开发）：

```sql
CREATE DATABASE datastoria CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'datastoria'@'%' IDENTIFIED BY 'datastoria';
GRANT ALL PRIVILEGES ON datastoria.* TO 'datastoria'@'%';
```

仓库只提供 `dev` 与 `prod` 两个 Profile。不要把真实密钥写进 YAML：

```bash
export DATASTORIA_MASTER_KEY="$(openssl rand -base64 32)"
./mvnw -pl datastoria-boot -am package -DskipTests
SPRING_PROFILES_ACTIVE=dev \
  java -jar datastoria-boot/target/datastoria-boot-0.0.1-SNAPSHOT.jar
```

默认地址：

- API：`http://127.0.0.1:8080`
- 健康检查：`http://127.0.0.1:8080/actuator/health`
- MySQL：`jdbc:mysql://127.0.0.1:3306/datastoria`
- 开发身份：`dev@example.com`

检查 API：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS \
  -H 'x-datastoria-user-email: dev@example.com' \
  http://127.0.0.1:8080/api/connections
```

IntelliJ Run Configuration 使用 JDK 17，Active profiles 填 `dev`，环境变量中设置
`DATASTORIA_MASTER_KEY`。

## 前端

完整的前端开发方式、Maven Profile、浏览器断点和故障处理见
[datastoria-web 开发与调试](datastoria-web.md)。

```bash
cd datastoria-web
npm ci --force
NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=java \
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://127.0.0.1:8080 \
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=dev@example.com \
npm run dev
```

前端访问 `http://localhost:3000`。浏览器请求应发送到 Java API，而不是在请求体中携带模型
API Key。若出现 `CLIENT_SECRET_NOT_ALLOWED`，删除浏览器旧配置/旧请求中的 `secret` 字段，
改为在“系统设置 → 模型”由服务端保存凭据。

## ClickHouse 联调

无容器的完整安装、专用用户、权限和 Seed 步骤见
[本地 ClickHouse](local-clickhouse.md)。最短验证：

```bash
bin/dev/clickhouse/install.sh
bin/dev/clickhouse/cluster.sh start
bin/dev/clickhouse/cluster.sh seed
curl -fsS http://127.0.0.1:18123/ping
```

在页面添加连接时填 HTTP 端口 `18123`，不要填 Native TCP 端口 `19000`。

## 调试建议

### 前端

- Network 中确认 `/api/**` 最终到 `8080` 或 `/backend/**`；
- SSE 请求查看 `Last-Event-ID`、响应头和事件序号；
- `.next` 缓存异常时先停止进程，再删除 `datastoria-web/.next` 后重启；
- `concurrently: command not found` 表示依赖未完整安装，执行 `npm ci --force`。

### 后端

- 对 API 使用 IDE 断点；WebFlux 链路避免在 Netty 线程执行 JDBC；
- MySQL 可用只读账号或数据库客户端检查，禁止手工修改 Flyway 历史；
- Agent 问题优先查看 Run、Event、Checkpoint、Pending Action，而不是打印 Prompt/Key；
- ClickHouse 查询错误保留 exception code，但日志中不得记录连接密码。

## 测试

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw spotless:check test

cd datastoria-web
npm run format:check
npm run typecheck
npm run lint
npm test -- --run
```

测试默认连接本机 MySQL 5.7 的 `datastoria_test` 数据库，可通过
`DATASTORIA_TEST_DB_URL/USERNAME/PASSWORD` 覆盖。测试会清理 `ds_*` 业务表，请勿指向开发或
生产数据库。
