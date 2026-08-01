# 本地 ClickHouse 安装与 DataStoria 联调（无容器）

本文用于在开发机上以原生进程运行 ClickHouse，并接入本地
`datastoria-server` 与 `datastoria-web`。不使用 Docker，不修改 DataStoria 的数据库结构。

推荐优先使用仓库自带的 macOS 隔离脚本。它把 ClickHouse 二进制、配置、数据、日志和 PID
全部放在 gitignored 的 `.local/clickhouse/`，不会写 `/etc`、`/var/lib` 等系统目录。

## 1. 最终端口与连接参数

仓库隔离实例的默认参数如下：

| 项目 | 值 |
|---|---|
| ClickHouse HTTP URL | `http://127.0.0.1:18123` |
| ClickHouse Native TCP | `127.0.0.1:19000` |
| 初始用户 | `default` |
| 初始密码 | 空 |
| Seed 数据库 | `datastoria_test` |
| Java API | `http://127.0.0.1:8080` |
| 前端 | `http://localhost:3000` |

DataStoria 连接 ClickHouse 使用 **HTTP 接口**，因此页面中必须填写 `18123`，不能填写 Native
TCP 的 `19000`。

## 2. macOS：使用仓库隔离脚本

### 2.1 前置检查

支持 Apple Silicon 与 Intel macOS：

```bash
uname -s
uname -m
curl --version
```

应分别看到 `Darwin`，以及 `arm64` 或 `x86_64`。

### 2.2 下载、启动和初始化测试数据

在仓库根目录执行：

```bash
cd ~/OpenProjects/datastoria-server

bin/dev/clickhouse/install.sh
bin/dev/clickhouse/cluster.sh start
bin/dev/clickhouse/cluster.sh seed
bin/dev/clickhouse/cluster.sh status
```

项目当前固定验证版本为 `v26.5.6.64-stable`。不要在日常联调中自动跟随 latest，以免模型、
系统表或查询行为随上游版本变化。

启动成功后检查 HTTP 与 Native 接口：

```bash
curl -fsS http://127.0.0.1:18123/ping

.local/clickhouse/bin/clickhouse client \
  --host 127.0.0.1 \
  --port 19000 \
  --query "SELECT version(), currentUser(), currentDatabase()"

.local/clickhouse/bin/clickhouse client \
  --host 127.0.0.1 \
  --port 19000 \
  --query "SHOW DATABASES"
```

`/ping` 应返回 `Ok.`，数据库列表应包含 `datastoria_test`。

### 2.3 创建专用用户名和密码（推荐）

空密码的 `default` 用户只适合最短路径冒烟测试。完整联调建议创建专用账号：

```bash
export DATASTORIA_CH_USER=datastoria_dev
export DATASTORIA_CH_PASSWORD="$(openssl rand -hex 16)"

.local/clickhouse/bin/clickhouse client \
  --host 127.0.0.1 \
  --port 19000 \
  --multiquery <<SQL
CREATE ROLE IF NOT EXISTS datastoria_dev_role;
GRANT ALL ON datastoria_test.* TO datastoria_dev_role;
GRANT SELECT ON system.* TO datastoria_dev_role;
GRANT CREATE TEMPORARY TABLE ON *.* TO datastoria_dev_role;

CREATE USER IF NOT EXISTS ${DATASTORIA_CH_USER}
IDENTIFIED WITH sha256_password BY '${DATASTORIA_CH_PASSWORD}';
GRANT datastoria_dev_role TO ${DATASTORIA_CH_USER};
ALTER USER ${DATASTORIA_CH_USER} DEFAULT ROLE datastoria_dev_role;
SQL

printf 'ClickHouse user: %s\n' "$DATASTORIA_CH_USER"
printf 'ClickHouse password: %s\n' "$DATASTORIA_CH_PASSWORD"
```

密码只在当前终端变量中保存。不要把它写入 Git、`.env`、命令历史文档或聊天消息。

验证新账号：

```bash
.local/clickhouse/bin/clickhouse client \
  --host 127.0.0.1 \
  --port 19000 \
  --user "$DATASTORIA_CH_USER" \
  --password "$DATASTORIA_CH_PASSWORD" \
  --database datastoria_test \
  --query "SELECT currentUser(), currentDatabase(), count() FROM system.tables"

curl -fsS \
  -u "$DATASTORIA_CH_USER:$DATASTORIA_CH_PASSWORD" \
  'http://127.0.0.1:18123/?database=datastoria_test' \
  --data-binary 'SELECT 1'
```

> `SELECT ON system.*` 用于 DataStoria 的 schema、进程、查询日志、集群状态和诊断页面。
> `ALL ON datastoria_test.*` 允许本地 SQL 编辑器测试 DDL/DML。生产环境应按业务数据库和只读
> 范围收紧权限。

### 2.4 只读 Agent 账号（可选）

如果只验证 AI 诊断和查询，不希望账号执行写操作：

```bash
export DATASTORIA_CH_READONLY_USER=datastoria_ai
export DATASTORIA_CH_READONLY_PASSWORD="$(openssl rand -hex 16)"

.local/clickhouse/bin/clickhouse client \
  --host 127.0.0.1 \
  --port 19000 \
  --multiquery <<SQL
CREATE ROLE IF NOT EXISTS datastoria_readonly_role
SETTINGS
    readonly = 1,
    max_execution_time = 30,
    max_memory_usage = 2000000000,
    max_threads = 4;
GRANT SELECT ON datastoria_test.* TO datastoria_readonly_role;
GRANT SELECT ON system.* TO datastoria_readonly_role;

CREATE USER IF NOT EXISTS ${DATASTORIA_CH_READONLY_USER}
IDENTIFIED WITH sha256_password BY '${DATASTORIA_CH_READONLY_PASSWORD}';
GRANT datastoria_readonly_role TO ${DATASTORIA_CH_READONLY_USER};
ALTER USER ${DATASTORIA_CH_READONLY_USER} DEFAULT ROLE datastoria_readonly_role;
SQL
```

只读账号不能完成 DataStoria SQL 编辑器中的 INSERT、CREATE、ALTER 或 DROP 测试，这是预期行为。

## 3. Linux：原生软件包安装

仓库的 `bin/dev/clickhouse/install.sh` 当前只处理 macOS。Linux 请使用 ClickHouse 官方 DEB/RPM
仓库安装 `clickhouse-server` 和 `clickhouse-client`，然后由 systemd 管理：

```bash
sudo systemctl enable --now clickhouse-server
sudo systemctl status clickhouse-server

clickhouse-client --query "SELECT version()"
curl -fsS http://127.0.0.1:8123/ping
```

官方软件包默认通常使用：

- HTTP：`8123`
- Native TCP：`9000`
- 配置：`/etc/clickhouse-server/`
- 数据：`/var/lib/clickhouse/`
- 日志：`/var/log/clickhouse-server/`

创建用户和授权可复用上一节 SQL，将客户端端口从 `19000` 改为 `9000`。DataStoria 页面 URL
则填写 `http://127.0.0.1:8123`。

若 ClickHouse 与 Java 不在同一台机器，需要在 ClickHouse 配置中显式设置监听地址、防火墙和
TLS；不要为了方便直接把无密码的 `default` 用户暴露到外网。

## 4. 启动 datastoria-server

确认使用 JDK 17：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

启动开发环境：

```bash
cd ~/OpenProjects/datastoria-server
SPRING_PROFILES_ACTIVE=dev \
  java -jar datastoria-boot/target/datastoria-boot-0.0.1-SNAPSHOT.jar
```

或者在 IntelliJ 的 Run Configuration 中设置：

```text
Active profiles: dev
JDK: 17
```

验证：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS \
  -H 'x-datastoria-user-email: dev@example.com' \
  http://127.0.0.1:8080/api/connections
```

本地 profile 使用 `./data/datastoria.db` 保存 DataStoria 自身配置。ClickHouse 密码经 Java
AES-256-GCM 加密后保存；连接列表接口不会把密码返回浏览器。

## 5. 启动前端

首次运行先安装依赖：

```bash
cd ~/OpenProjects/datastoria-server/datastoria-web
npm install
```

启动：

```bash
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://127.0.0.1:8080 \
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=dev@example.com \
npm run dev
```

打开 `http://localhost:3000`。

## 6. 在 DataStoria 页面创建连接

点击左侧数据库按钮或首页 **Create a ClickHouse Connection**，填写：

### 方案 A：最短冒烟测试

| 页面字段 | 值 |
|---|---|
| URL | `http://127.0.0.1:18123` |
| User | `default` |
| Password | 留空 |
| Cluster | 留空 |
| Connection Name | `Local ClickHouse` |

### 方案 B：专用账号（推荐）

| 页面字段 | 值 |
|---|---|
| URL | `http://127.0.0.1:18123?database=datastoria_test` |
| User | `datastoria_dev` |
| Password | 第 2.3 节生成的密码 |
| Cluster | 留空 |
| Connection Name | `Local ClickHouse Dev` |

操作顺序：

1. 点击 **Test Connection**，应看到 `Successfully connected.`。
2. 点击 **Save**。
3. 选择刚保存的连接。
4. 展开 `datastoria_test`，确认能加载数据库、表和列。
5. 在 SQL 编辑器执行：

```sql
SELECT
    version() AS version,
    currentUser() AS current_user,
    currentDatabase() AS current_database,
    hostName() AS host;
```

本地单节点实例没有 ClickHouse cluster 名称，因此 **Cluster 必须留空**。该字段只用于已配置
`remote_servers` 的真实多节点集群。

## 7. 使用 Java API 自动验证

页面的 **Test Connection** 实际调用：

```text
POST /api/connections/test
```

可以直接用 API 验证临时连接，不会保存记录：

```bash
curl -fsS \
  -X POST \
  -H 'Content-Type: application/json' \
  -H 'x-datastoria-user-email: dev@example.com' \
  http://127.0.0.1:8080/api/connections/test \
  --data "{
    \"name\": \"Local ClickHouse Dev\",
    \"url\": \"http://127.0.0.1:18123?database=datastoria_test\",
    \"username\": \"${DATASTORIA_CH_USER}\",
    \"password\": \"${DATASTORIA_CH_PASSWORD}\",
    \"cluster\": null,
    \"enabled\": true
  }"
```

成功响应示例：

```json
{
  "ok": true,
  "latencyMs": 12,
  "message": "Connection succeeded"
}
```

该命令会把密码放入当前进程参数展开后的请求体，仅适合本机临时验证。共享终端或 CI 中应从受控
secret 注入，并避免开启 shell trace。

## 8. 日志、停止与重置

查看状态和日志：

```bash
bin/dev/clickhouse/cluster.sh status
tail -f .local/clickhouse/log/clickhouse.log
tail -f .local/clickhouse/log/clickhouse.err.log
```

停止与重启：

```bash
bin/dev/clickhouse/cluster.sh stop
bin/dev/clickhouse/cluster.sh restart
```

重新执行 seed（SQL 使用 `IF NOT EXISTS` 时可重复执行）：

```bash
bin/dev/clickhouse/cluster.sh seed
```

如需彻底删除本地 ClickHouse 数据，必须先停止进程，再手工删除明确目录：

```bash
bin/dev/clickhouse/cluster.sh stop
```

然后确认目标确实是仓库内的 `.local/clickhouse/`，再使用系统废纸篓或其他可恢复方式清理。

### 自定义端口或第二实例

```bash
CLICKHOUSE_HTTP_PORT=28123 \
CLICKHOUSE_TCP_PORT=29000 \
bin/dev/clickhouse/cluster.sh start
```

同一数据目录必须始终使用同一组端口。完全隔离第二个实例时同时设置：

```bash
CLICKHOUSE_TEST_ROOT="$PWD/.local/clickhouse-2" \
CLICKHOUSE_HTTP_PORT=28123 \
CLICKHOUSE_TCP_PORT=29000 \
bin/dev/clickhouse/install.sh

CLICKHOUSE_TEST_ROOT="$PWD/.local/clickhouse-2" \
CLICKHOUSE_HTTP_PORT=28123 \
CLICKHOUSE_TCP_PORT=29000 \
bin/dev/clickhouse/cluster.sh start
```

## 9. 常见问题

### `Connection refused`

```bash
bin/dev/clickhouse/cluster.sh status
lsof -nP -iTCP:18123 -sTCP:LISTEN
curl -v http://127.0.0.1:18123/ping
```

确认 DataStoria URL 使用 HTTP 端口 `18123`，而不是 Native TCP `19000`。

### `Authentication failed`

先绕开 DataStoria，用同一用户名密码直接测试：

```bash
curl -v \
  -u "$DATASTORIA_CH_USER:$DATASTORIA_CH_PASSWORD" \
  http://127.0.0.1:18123/ \
  --data-binary 'SELECT currentUser()'
```

若直接请求也失败，重新创建/修改 ClickHouse 用户；若直接请求成功，重新编辑 DataStoria 连接并
轮换保存密码。

### `Not enough privileges`

```sql
SHOW GRANTS FOR datastoria_dev;
```

至少确认业务数据库权限和 `SELECT ON system.*` 已授予。只读用户执行写 SQL 时出现此错误属于
预期。

### 页面测试成功，但看不到数据库或表

1. 确认 URL 中 `database=datastoria_test` 拼写正确。
2. 运行 `SHOW GRANTS FOR datastoria_dev`。
3. 确认已执行 `bin/dev/clickhouse/cluster.sh seed`。
4. 刷新 schema 树或重新选择连接。

### Java 能启动，但前端请求失败

确认前端使用 `http://127.0.0.1:8080`，并且开发身份头为 `dev@example.com`。本地 CORS 已允许
`localhost:3000` 与 `127.0.0.1:3000`，不要混用其他端口或自定义 origin。

### macOS DNS native library 错误

该问题与 ClickHouse 账号无关。使用 Apple Silicon 时应确保 Maven 解析了
`netty-resolver-dns-native-macos` 的 `osx-aarch_64` classifier，并使用 JDK 17 启动 Java。

## 10. 安全边界

- ClickHouse 密码只提交给 Java，保存后不会由列表 API 返回前端。
- 不要在 URL 中携带用户名或密码；页面有独立 User/Password 字段。
- 不要把无密码的 `default` 用户监听到 `0.0.0.0`。
- 本地 profile 和开发身份头只用于开发机，生产环境必须使用正式认证和独立 master key。
- 生产环境不要直接照搬 `ALL ON datastoria_test.*`，应按业务库、只读范围和资源限制授权。

## 11. 官方资料

- [ClickHouse 安装文档](https://clickhouse.com/docs/en/getting-started/install/)
- [ClickHouse 支持平台](https://clickhouse.com/support/platforms)
- [ClickHouse SQL 用户与角色](https://clickhouse.com/docs/en/operations/access-rights)
- [ClickHouse HTTP 接口](https://clickhouse.com/docs/en/interfaces/http)
