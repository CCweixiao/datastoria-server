# DataStoria Server

DataStoria 的 Spring Boot 后端。该项目将分阶段接管现有 DataStoria Node.js
后端的 REST API、会话持久化、工具执行、Skill 加载和 Agent 运行时。

当前状态：P0–P3 已落地。仓库从 P3 起采用单仓库结构：

- Spring Boot 后端位于仓库根目录。
- Next.js 前端位于 `frontend/`。
- 前端第三方 Git submodule 位于 `frontend/external/`，由根目录 `.gitmodules` 管理。

## 技术基线

- JDK 17
- Spring Boot 3.5.16
- Maven Wrapper
- Spring WebFlux
- Spring Boot Actuator

AgentScope Java 将在 Agent 运行时阶段引入，避免项目初始化阶段提前耦合尚未验证的
运行时配置。

## 本地运行

确保 `JAVA_HOME` 指向 JDK 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw spring-boot:run
```

P2 引入 SQLite 后，本地运行需要先复制 local profile 示例并显式激活：

```bash
cp src/main/resources/application-local.yaml.example \
  src/main/resources/application-local.yaml
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

基础配置不默认激活 `local`，防止未来生产环境因遗漏 profile 而误用 SQLite。

验证服务：

```bash
curl http://localhost:8080/actuator/health
```

运行测试：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw test
```

前端开发与验证：

```bash
git submodule update --init --recursive
cd frontend
npm install
npm run dev
```

前端的格式化、类型检查、测试和生产构建仍从 `frontend/` 执行：

```bash
cd frontend
npm run format
npm run typecheck
npm run lint
npm run test
npm run build
```

## 文档

- [文档索引](docs/README.md)
- [整体迁移计划](docs/migration-plan.md)
- [目标架构](docs/target-architecture.md)
