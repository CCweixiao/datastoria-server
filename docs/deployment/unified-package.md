# 前后端统一发布包

统一发布模式把 Spring Boot 可执行 JAR 与 Next.js standalone 服务放入同一个 `tar.gz`，
不依赖 Nginx，也不要求单独安装或发布前端。发布包仍然保留两个独立进程，未来可把 JAR 和
standalone 目录拆到不同主机。

## 构建

构建机需要 JDK 17、Node.js、npm 和 tar：

```bash
bin/build-package.sh
```

脚本依次执行 Java clean/package（包含测试）、Next.js production build，并生成：

```text
target/dist/datastoria-<version>.tar.gz
```

默认前端请求 `/backend/**`，由 Next.js 基础设施代理转发给同包 Java 服务。若要生成前后端
分离部署版本，在构建时指定公开 Java 地址：

```bash
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=https://api.example.com \
  bin/build-package.sh
```

## 安装和运行

```bash
tar -xzf datastoria-<version>.tar.gz
cd datastoria-<version>
bin/datastoria init
bin/datastoria start
bin/datastoria status
```

`init` 创建 `conf/datastoria.env`。在 local profile 下还会生成权限为 `0600` 的本地加密
主密钥；`start` 在没有显式执行 `init` 时也会安全生成该密钥。默认入口为
`http://服务器地址:3000`。

管理命令：

```bash
bin/datastoria stop
bin/datastoria restart
bin/datastoria logs 200
```

## Profile 与数据源

编辑 `conf/datastoria.env` 中的 `DATASTORIA_PROFILE`：

- `local`：SQLite，数据文件为发布目录下的 `data/datastoria.db`。
- `prod`：MySQL，必须配置 `DATASTORIA_DB_URL`、`DATASTORIA_DB_USERNAME`、
  `DATASTORIA_DB_PASSWORD` 和 `DATASTORIA_MASTER_KEY`。

非敏感覆盖项放到 `conf/application-<profile>.yaml`。启动脚本同时传入激活 profile 和
外部 `conf/`，Flyway 根据 profile 分别加载 SQLite 或 MySQL migration。生产 profile
仍会拒绝非 MySQL JDBC URL。

## 发布包结构

```text
datastoria-<version>/
├── app/
│   ├── backend/datastoria-server.jar
│   └── frontend/                 # Next.js standalone
├── bin/datastoria
├── conf/
│   ├── datastoria.env.example
│   ├── application-local.yaml
│   └── application-prod.yaml
├── data/
├── logs/
└── run/
```
