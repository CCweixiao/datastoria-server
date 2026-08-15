# DataStoria

DataStoria 是面向 ClickHouse 的开源智能数据工作台。它把连接管理、SQL 工作流、集群可观测、
AI 辅助诊断和团队会话放在一个浏览器界面中，让开发者与数据库管理员可以从“发现问题”连续
走到“验证结论”。

项目采用单仓库和 Maven 多模块结构：

- `datastoria-common/`：共享领域对象、DTO、身份、错误和通用配置；
- `datastoria-dao/`：Repository 契约、MyBatis-Plus Mapper/Entity 与数据库迁移；
- `datastoria-service/`：业务服务和外部服务访问；
- `datastoria-agent/`：AgentScope、Agent 用例、Tool 与 Skill；
- `datastoria-controller/`：Spring WebFlux HTTP/SSE Controller；
- `datastoria-boot/`：Spring Boot 入口、环境配置与测试；
- `datastoria-web/`：Next.js 管理平台及其 Maven 构建模块；
- `bin/`：统一安装包、运行管理和本地开发辅助脚本；
- `docs/`：产品、架构、开发、部署和操作文档。

## 核心能力

- 管理单节点或集群 ClickHouse 连接，自动发现分片与副本；
- SQL 编辑、执行、历史、Explain、错误诊断和可视化；
- 节点/集群 Dashboard 与系统表观测；
- 配置 OpenAI 兼容模型供应商，发现和管理多个模型；
- 基于 AgentScope Java 的流式 AI 会话、只读 SQL 工具、审批/提问和断点恢复；
- Skill、Agent、会话、反馈和用户偏好的服务端持久化；
- 开发、测试和生产统一使用 MySQL 5.7 与同一套 Flyway/MyBatis-Plus 结构。

## 5 分钟本地启动

前置要求：JDK 17、Node.js 22、npm、MySQL 5.7，以及已初始化的 Git submodule。开发库默认
使用 `datastoria/datastoria` 账号连接本机 `datastoria` 数据库，也可通过 `DATASTORIA_DB_*`
覆盖。

```bash
git submodule update --init --recursive

export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -pl datastoria-boot -am package -DskipTests
SPRING_PROFILES_ACTIVE=dev \
  java -jar datastoria-boot/target/datastoria-boot-0.0.1-SNAPSHOT.jar
```

另开终端：

```bash
cd datastoria-web
npm ci --force
NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=java \
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://127.0.0.1:8080 \
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=dev@example.com \
npm run dev
```

打开 `http://localhost:3000`，健康检查为 `http://127.0.0.1:8080/actuator/health`。
本地配置只能用于开发。dev profile 使用内置的 `datastoria.master-key`；生产部署优先读取
`DATASTORIA_MASTER_KEY`，未设置时首次启动自动生成 `data/master.key`（0600），必须稳定备份。
轮换密钥时把旧 key 放入 `DATASTORIA_MASTER_KEY_LEGACY` 以保持存量密文可解密。

## 构建与验证

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw spotless:check test

cd datastoria-web
npm run format:check
npm run typecheck
npm run lint
npm test -- --run
npm run build
```

也可以在仓库根目录通过 Maven 构建或调试前端：

```bash
./mvnw -pl datastoria-web package
./mvnw -pl datastoria-web -Pweb-dev generate-resources
```

生成包含前后端和启动脚本的安装包：

```bash
DATASTORIA_PACKAGE_VERSION=0.1.0-preview bin/build-package.sh
```

产物位于 `target/dist/datastoria-<version>.tar.gz`。

## 文档

- [在线文档](https://ccweixiao.github.io/datastoria-server/)
- [HTTP API 文档](https://ccweixiao.github.io/datastoria-server/reference/api/)
- [OpenAPI YAML](https://ccweixiao.github.io/datastoria-server/api/openapi.yaml)
- [仓库内工程文档](docs/README.md)
- [产品愿景](docs/product/vision.md)
- [系统架构](docs/architecture/overview.md)
- [AgentScope Java AI Agent 架构](docs/architecture/agent-runtime.md)
- [功能模块](docs/product/modules.md)
- [开发与调试](docs/development/getting-started.md)
- [datastoria-web 开发与调试](docs/development/datastoria-web.md)
- [生产部署](docs/deployment/production.md)
- [管理平台操作手册](docs/manual/admin-console.md)
- [安全与敏感信息](docs/security/secrets.md)

## 开源协作

提交改动前请运行与改动范围匹配的 Java、前端测试和格式检查。架构决策记录在
`docs/adr/`；HTTP/流式契约及自动化 fixture 位于 `docs/api/` 和 `docs/fixtures/`。
