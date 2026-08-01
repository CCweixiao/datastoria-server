# 前后端统一发布包

> 生产环境的 MySQL、TLS、备份、升级和密钥要求见[生产部署](production.md)。

统一发布模式把 Spring Boot 可执行 JAR 与 Next.js standalone 服务放入同一个 `tar.gz`，
不依赖 Nginx，也不要求单独安装或发布前端。发布包仍然保留两个独立进程，未来可把 JAR 和
standalone 目录拆到不同主机。

## 构建

构建机需要 JDK 17、Node.js 22、npm、Git 和 tar。构建会执行 Java 测试与前端生产构建：

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

`init` 创建 `conf/datastoria.env`。在 dev profile 下还会生成权限为 `0600` 的开发加密
主密钥；`start` 在没有显式执行 `init` 时也会安全生成该密钥。默认入口为
`http://服务器地址:3000`。

管理命令：

```bash
bin/datastoria stop
bin/datastoria restart
bin/datastoria logs 200
```

## Profile 与数据源

编辑权限为 `0600` 的 `conf/datastoria.env` 中的 `DATASTORIA_PROFILE`：

- `dev`：MySQL 5.7，默认连接本机 `datastoria` 数据库，可通过环境变量覆盖。
- `prod`：MySQL 5.7，必须配置示例文件列出的数据库、主密钥、租户、CORS 和认证变量。

配置文件中的密码和主密钥只是变量说明，真实值应由部署平台或密钥管理系统注入，不能提交到
Git 或放入工单。`DATASTORIA_MASTER_KEY` 丢失后无法解密已保存的连接/模型凭据。

部署配置统一放在 `conf/datastoria.env`。两个 Profile 都加载同一套 MySQL migration，应用
统一拒绝非 MySQL JDBC URL。

## 发布包结构

```text
datastoria-<version>/
├── app/
│   ├── backend/datastoria-server.jar
│   └── frontend/                 # Next.js standalone
├── bin/datastoria
├── conf/
│   └── datastoria.env.example
├── data/
├── logs/
└── run/
```

## 验证发布包

```bash
sha256sum -c SHA256SUMS
tar -tzf datastoria-<version>.tar.gz | head
```

安装后至少验证：

```bash
bin/datastoria init
bin/datastoria start
bin/datastoria status
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:3000/
bin/datastoria stop
```
