<div align="center">

<img src=".github/assets/logo.png" alt="DataStoria" width="150" />

# DataStoria

**AI 原生的 ClickHouse 智能数据工作台**

一句自然语言唤起一条严谨的 SQL，一次点击看清整条执行链路——让每一次与数据的对话，都有证据可依。

[![Release](https://img.shields.io/github/v/release/CCweixiao/datastoria-server?style=flat-square&logo=github)](https://github.com/CCweixiao/datastoria-server/releases)
[![CI](https://img.shields.io/github/actions/workflow/status/CCweixiao/datastoria-server/ci.yml?branch=master&style=flat-square&label=CI)](https://github.com/CCweixiao/datastoria-server/actions/workflows/ci.yml)
[![Docs](https://img.shields.io/github/actions/workflow/status/CCweixiao/datastoria-server/docs-pages.yml?branch=master&style=flat-square&label=docs)](https://ccweixiao.github.io/datastoria-server/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](LICENSE)
[![Stars](https://img.shields.io/github/stars/CCweixiao/datastoria-server?style=flat-square&color=yellow)](https://github.com/CCweixiao/datastoria-server/stargazers)
[![Forks](https://img.shields.io/github/forks/CCweixiao/datastoria-server?style=flat-square&color=teal)](https://github.com/CCweixiao/datastoria-server/forks)

![JDK](https://img.shields.io/badge/JDK-17-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?style=flat-square&logo=springboot)
![MySQL](https://img.shields.io/badge/MySQL-5.7-4479A1?style=flat-square&logo=mysql&logoColor=white)
![ClickHouse](https://img.shields.io/badge/ClickHouse-24.8-FFCC01?style=flat-square&logo=clickhouse&logoColor=black)
![Next.js](https://img.shields.io/badge/Next.js-16-black?style=flat-square&logo=nextdotjs)
![Node](https://img.shields.io/badge/Node.js-22-339933?style=flat-square&logo=nodedotjs&logoColor=white)

**[在线文档](https://ccweixiao.github.io/datastoria-server/)** · **[快速开始](#-快速开始)** · **[核心特性](#-核心特性)** · **[HTTP API](https://ccweixiao.github.io/datastoria-server/reference/api/)**

**简体中文** | [English](README.en.md)

</div>

---

在 DataStoria 出现之前，"发现问题"与"验证结论"之间隔着一堆割裂的工具。DataStoria 把连接管理、SQL 工作台、集群观测、AI 辅助诊断与可分享的会话收进一个浏览器界面；而与常见 ClickHouse 客户端不同，它的 AI 能力构建在 **Java 服务端安全边界**之内——凭据加密托管、只读工具验证、证据可溯源，适合个人开发者，也经得起企业环境的审视。

## 🚀 核心特性

### 🤖 AI 能力

- **自然语言数据探索** —— 用平实的语言描述需求，即刻获得经只读工具验证的 ClickHouse 查询
- **基于证据的查询优化** —— AI 检视真实 schema、验证 SQL、收集运行证据，给出条条可溯源的性能建议
- **智能可视化** —— 一句提示词生成时序图、饼图、数据表等可视化结果
- **Agent 会话与技能** —— 基于 AgentScope Java 的流式会话，支持提问交互、断点恢复与可复用 Skill

### ⚡ 查询体验

- **高级 SQL 编辑器** —— 语法高亮、自动补全、查询格式化与代码片段
- **一键错误诊断** —— 精确到行列的错误定位，可一键获取 AI 修复建议
- **Query Log Inspector** —— 时间线视图、拓扑图与执行指标深度剖析查询过程
- **可视化 Explain** —— AST 与 Pipeline 双视图，执行计划一目了然

### 📊 集群监控与管理

- **多集群连接** —— 自动发现分片与副本，单一界面管理多套 ClickHouse
- **节点/集群仪表盘** —— 实时指标、merge 操作与副本状态一览
- **系统日志内省** —— query_log、part_log、ZooKeeper、OpenTelemetry 等系统表开箱即用
- **Schema Explorer** —— 树形视图浏览数据库、表与列结构

### 🔒 隐私与安全

- **服务端安全边界** —— ClickHouse 密码与模型凭据 AES-256 加密存储，经 Spring Boot 执行所有请求，浏览器永不接触密钥
- **查询安全护栏** —— 只读强制、扫描量与执行时间上限，AI 查询与人工查询同级约束
- **自带 API Key** —— 供应商凭据直接交给后端托管，不进浏览器、不进前端请求

## 📦 快速开始

### 统一安装包（推荐）

从 [releases](https://github.com/CCweixiao/datastoria-server/releases) 下载并校验：

```bash
sha256sum -c SHA256SUMS
tar -xzf datastoria-<version>.tar.gz && cd datastoria-<version>
bin/datastoria init && bin/datastoria start
```

单进程部署：Spring Boot 同时托管 API 与前端，打开 `http://localhost:8080` 即用，无需 Nginx、无需单独的 Node.js。全部配置项说明见[安装与配置](https://ccweixiao.github.io/datastoria-server/manual/01-getting-started/installation)。

### 从源码运行

前置要求：JDK 17、Node.js 22、npm、MySQL 5.7，以及已初始化的 Git submodule。开发库默认使用 `datastoria/datastoria` 账号连接本机 `datastoria` 数据库，可通过 `DATASTORIA_DB_*` 覆盖。

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

打开 `http://localhost:3000`，健康检查为 `http://127.0.0.1:8080/actuator/health`。本地配置只能用于开发。dev profile 使用内置的 `datastoria.master-key`；生产部署优先读取 `DATASTORIA_MASTER_KEY`，未设置时首次启动自动生成 `data/master.key`（0600），必须稳定备份。轮换密钥时把旧 key 放入 `DATASTORIA_MASTER_KEY_LEGACY` 以保持存量密文可解密。

## 🧰 技术栈与架构

后端为 Java 单体多模块（Maven），前端为 Next.js，文档站为 VitePress（中英双语、按版本组织）：

| 模块 | 职责 |
|---|---|
| `datastoria-common/` | 共享领域对象、DTO、身份、加密与通用配置 |
| `datastoria-dao/` | Repository 契约、MyBatis-Plus Mapper/Entity 与 Flyway 迁移 |
| `datastoria-service/` | 业务服务与外部访问 |
| `datastoria-agent/` | AgentScope Java、Agent 用例、Tool 与 Skill |
| `datastoria-controller/` | Spring WebFlux HTTP/SSE Controller |
| `datastoria-boot/` | Spring Boot 入口、环境配置与测试 |
| `datastoria-web/` | Next.js 管理平台（React 19）与文档站 |
| `bin/` · `docs/` | 统一安装包脚本与工程文档 |

开发、测试和生产统一使用 MySQL 5.7 与同一套 Flyway/MyBatis-Plus 结构；HTTP 契约以 OpenAPI 基线管理，并由后端测试与前端调用清单双向校验。

## 🛠️ 构建与验证

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw spotless:check test

cd datastoria-web
npm run format:check && npm run typecheck && npm run lint
npm test -- --run && npm run build
```

生成包含前后端和启动脚本的统一安装包：

```bash
DATASTORIA_PACKAGE_VERSION=1.1.0 bin/build-package.sh   # 产物位于 target/dist/
```

## 📖 文档

- [在线文档](https://ccweixiao.github.io/datastoria-server/)（中英双语，支持版本切换）
- [HTTP API 文档](https://ccweixiao.github.io/datastoria-server/reference/api/) / [OpenAPI YAML](https://ccweixiao.github.io/datastoria-server/api/openapi.yaml)
- [产品愿景](docs/product/vision.md) · [系统架构](docs/architecture/overview.md) · [Agent 架构](docs/architecture/agent-runtime.md)
- [开发与调试](docs/development/getting-started.md) · [生产部署](docs/deployment/production.md)
- [仓库内工程文档](docs/README.md)

## 🤝 致谢

本项目的产品形态与前端交互设计参考并致敬 [FrankChen021/datastoria](https://github.com/FrankChen021/datastoria)。在其理念基础上，本项目将架构重构为 Java 服务端（Spring Boot + AgentScope Java + MySQL），以服务端安全边界、统一单进程部署和版本化双语文档为主要差异化方向。感谢原作者的开源分享。

## 📜 License

[Apache License 2.0](LICENSE)
