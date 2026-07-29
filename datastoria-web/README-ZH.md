# DataStoria 前端开发

`datastoria-web/` 是 DataStoria 的 Next.js 16 + React 19 管理平台，也是 Maven reactor 中独立的
前端构建模块。连接、凭据、模型、会话和 Agent Run 均由 Spring Boot 服务端管理，前端不能作为
独立后端运行。

## 启动

先在仓库根目录以 `local` profile 启动 Java，然后执行：

```bash
git submodule update --init --recursive
cd datastoria-web
npm install
NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=java \
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://127.0.0.1:8080 \
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=dev@example.com \
npm run dev
```

访问 `http://localhost:3000`。

也可以在仓库根目录通过 Maven 启动前端开发服务器：

```bash
./mvnw -pl datastoria-web -Pweb-dev generate-resources
```

环境变量、端口覆盖、浏览器/IDE 断点、定向测试和常见故障见
[datastoria-web 开发与调试](../docs/development/datastoria-web.md)。

## 验证

```bash
npm run format:check
npm run typecheck
npm run lint
npm test -- --run
npm run build
```

## 安全边界

- 不得把 API Key 或数据库密码放入 `NEXT_PUBLIC_*`；
- 浏览器只引用连接/供应商 ID，Java 解密并注入凭据；
- 统一包使用 `/backend/**` 同源代理；
- approve/deny/respond 通过 Java Action API，前端不执行受信 Tool。

完整文档见 [../docs/README.md](../docs/README.md)。
