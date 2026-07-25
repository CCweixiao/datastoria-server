# 本地 ClickHouse 测试实例（无 Docker）

本项目在 macOS 开发机上使用官方单二进制运行隔离的 ClickHouse 测试实例，不依赖 Docker，
也不向系统目录写文件。默认固定为 `v26.5.6.64-stable`，二进制、数据、日志和 PID 均位于
gitignored 的 `.local/clickhouse/`。

## 安装与启动

```bash
tools/clickhouse/install.sh
tools/clickhouse/cluster.sh start
tools/clickhouse/cluster.sh seed
tools/clickhouse/cluster.sh status
```

默认端口：

- HTTP：`18123`
- Native TCP：`19000`
- 用户：`default`
- 密码：空
- 测试数据库：`datastoria_test`

在 DataStoria 的连接管理页创建连接时填写
`http://127.0.0.1:18123`、用户 `default`、空密码。测试连接成功后，Java 服务只保存
`connectionId` 并在服务端解析连接配置；浏览器不接触 ClickHouse 凭据。

## 停止

```bash
tools/clickhouse/cluster.sh stop
```

需要使用其他端口时：

```bash
CLICKHOUSE_HTTP_PORT=28123 CLICKHOUSE_TCP_PORT=29000 \
  tools/clickhouse/cluster.sh start
```

端口覆盖在同一数据目录中必须保持一致；不要同时用不同端口启动两个进程。若要完全隔离第二个
实例，同时设置不同的 `CLICKHOUSE_TEST_ROOT`。

## 版本策略

`install.sh` 默认固定经过本项目验证的 stable 版本，避免 CI/本地测试随上游 latest 漂移。显式
验证新版本时可以设置：

```bash
CLICKHOUSE_VERSION=v26.5.6.64-stable tools/clickhouse/install.sh
```

升级默认版本时必须重新执行 P6/P7 ClickHouse 工具、取消、超时和结果裁剪测试，并在交付报告中
记录版本。
