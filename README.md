# DataStoria Server

DataStoria 的 Spring Boot 后端。该项目将分阶段接管现有 DataStoria Node.js
后端的 REST API、会话持久化、工具执行、Skill 加载和 Agent 运行时。

当前状态：阶段 0，项目初始化。

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

验证服务：

```bash
curl http://localhost:8080/actuator/health
```

运行测试：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw test
```

## 文档

- [文档索引](docs/README.md)
- [整体迁移计划](docs/migration-plan.md)
- [目标架构](docs/target-architecture.md)
