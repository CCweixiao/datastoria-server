<div align="center">

<img src="datastoria-web/docs/public/logo.png" alt="DataStoria" width="120" />

# DataStoria

**The AI-native workbench for ClickHouse**

One sentence of natural language summons a rigorous SQL query; one click reveals the whole execution plan — every conversation with your data, backed by evidence.

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

**[Documentation](https://ccweixiao.github.io/datastoria-server/en/)** · **[Quick Start](#-quick-start)** · **[Key Features](#-key-features)** · **[HTTP API](https://ccweixiao.github.io/datastoria-server/en/reference/api/)**

[简体中文](README.md) | **English**

</div>

---

Before DataStoria, a pile of disconnected tools stretched between "noticing a problem" and "proving the answer". DataStoria brings connection management, a SQL workbench, cluster observability, AI-assisted diagnostics and shareable sessions into one browser interface. And unlike typical ClickHouse clients, its AI capability lives inside a **Java server-side security boundary** — encrypted credentials, read-only tool validation, traceable evidence. Suited to solo developers, and built to survive enterprise scrutiny.

## 🚀 Key Features

### 🤖 AI Capabilities

- **Natural Language Data Exploration** — describe what you need in plain language and get ClickHouse queries validated by read-only tools
- **Evidence-Based Query Optimization** — the AI inspects the real schema, validates SQL and gathers runtime evidence before suggesting improvements you can trace
- **Intelligent Visualization** — one prompt produces time series, pie charts and data tables
- **Agent Sessions & Skills** — streaming AgentScope Java sessions with approvals, follow-up questions, resumable runs and reusable skills

### ⚡ Query Experience

- **Advanced SQL Editor** — syntax highlighting, auto-completion, formatting and snippets
- **One-Click Error Diagnostics** — errors pinpointed to line and column, with one-click AI fixes
- **Query Log Inspector** — timeline views, topology graphs and execution metrics
- **Visual EXPLAIN** — AST and pipeline views that make execution plans obvious

### 📊 Cluster Monitoring & Management

- **Multi-Cluster Connections** — shards and replicas discovered automatically; every cluster in one console
- **Node & Cluster Dashboards** — live metrics, merges and replica status at a glance
- **System Log Introspection** — query_log, part_log, ZooKeeper, OpenTelemetry and more, out of the box
- **Schema Explorer** — browse databases, tables and columns in a tree view

### 🔒 Privacy & Security

- **Server-Side Security Boundary** — ClickHouse passwords and model credentials encrypted with AES-256; every request executed by Spring Boot; the browser never touches a secret
- **Query Guardrails** — enforced read-only mode plus scan/execution limits, applied equally to AI and human queries
- **Bring Your Own API Key** — provider credentials are managed by the backend, never the browser

## 📦 Quick Start

### Unified package (recommended)

Download and verify from [releases](https://github.com/CCweixiao/datastoria-server/releases):

```bash
sha256sum -c SHA256SUMS
tar -xzf datastoria-<version>.tar.gz && cd datastoria-<version>
bin/datastoria init && bin/datastoria start
```

Single-process deployment: Spring Boot serves both the API and the frontend — open `http://localhost:8080`, no Nginx and no separate Node.js required. Every configuration variable is documented in [Installation & Setup](https://ccweixiao.github.io/datastoria-server/en/manual/01-getting-started/installation).

### Run from source

Requirements: JDK 17, Node.js 22, npm, MySQL 5.7 and initialized Git submodules. The dev profile connects to a local `datastoria` database as `datastoria/datastoria`; override with `DATASTORIA_DB_*`.

```bash
git submodule update --init --recursive

export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -pl datastoria-boot -am package -DskipTests
SPRING_PROFILES_ACTIVE=dev \
  java -jar datastoria-boot/target/datastoria-boot-0.0.1-SNAPSHOT.jar
```

In another terminal:

```bash
cd datastoria-web
npm ci --force
NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=java \
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://127.0.0.1:8080 \
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=dev@example.com \
npm run dev
```

Open `http://localhost:3000`; the health check is `http://127.0.0.1:8080/actuator/health`. Local configuration is for development only. The dev profile uses the built-in `datastoria.master-key`; production reads `DATASTORIA_MASTER_KEY` first, and when unset generates `data/master.key` (0600) on first start — back it up, and keep the previous key in `DATASTORIA_MASTER_KEY_LEGACY` after rotations so existing ciphertexts stay decryptable.

## 🧰 Tech Stack & Architecture

A Java multi-module Maven backend, a Next.js frontend, and a bilingual versioned VitePress docs site:

| Module | Responsibility |
|---|---|
| `datastoria-common/` | Shared domain objects, DTOs, identity, crypto and common config |
| `datastoria-dao/` | Repository contracts, MyBatis-Plus mappers/entities and Flyway migrations |
| `datastoria-service/` | Business services and external access |
| `datastoria-agent/` | AgentScope Java, agent use cases, tools and skills |
| `datastoria-controller/` | Spring WebFlux HTTP/SSE controllers |
| `datastoria-boot/` | Spring Boot entry point, profiles and tests |
| `datastoria-web/` | Next.js console (React 19) and the docs site |
| `bin/` · `docs/` | Packaging scripts and engineering docs |

Development, testing and production share one MySQL 5.7 schema (Flyway/MyBatis-Plus); the HTTP contract is maintained as an OpenAPI baseline and cross-checked by backend tests and the frontend call inventory.

## 🛠️ Build & Verify

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw spotless:check test

cd datastoria-web
npm run format:check && npm run typecheck && npm run lint
npm test -- --run && npm run build
```

Build the unified package (backend + frontend + launcher):

```bash
DATASTORIA_PACKAGE_VERSION=1.1.0 bin/build-package.sh   # output in target/dist/
```

## 📖 Documentation

- [Documentation](https://ccweixiao.github.io/datastoria-server/en/) (bilingual, versioned)
- [HTTP API](https://ccweixiao.github.io/datastoria-server/en/reference/api/) / [OpenAPI YAML](https://ccweixiao.github.io/datastoria-server/api/openapi.yaml)
- [Product Vision](docs/product/vision.md) · [Architecture](docs/architecture/overview.md) · [Agent Runtime](docs/architecture/agent-runtime.md)
- [Development Guide](docs/development/getting-started.md) · [Production Deployment](docs/deployment/production.md)
- [Engineering Docs](docs/README.md)

## 🤝 Acknowledgments

The product shape and frontend interaction design of this project are inspired by and pay tribute to [FrankChen021/datastoria](https://github.com/FrankChen021/datastoria). Building on those ideas, this project re-architects the stack onto a Java server (Spring Boot + AgentScope Java + MySQL), differentiating through the server-side security boundary, single-process unified deployment, and versioned bilingual documentation. Our thanks to the original author for open-sourcing their work.

## 📜 License

[Apache License 2.0](LICENSE)
