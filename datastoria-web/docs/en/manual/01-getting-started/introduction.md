---
title: Introduction to DataStoria
description: Discover DataStoria - an AI-native ClickHouse console with natural-language queries, intelligent optimization, and privacy-first architecture.
head:
  - - meta
    - name: keywords
      content: DataStoria introduction, ClickHouse console, AI database management, natural language SQL, ClickHouse GUI, database admin tool, privacy-first database tool
---

# Introduction to DataStoria

Welcome to **DataStoria**, an AI-native workbench for ClickHouse. One sentence of natural language summons a rigorous SQL query; one click reveals the whole execution plan — data conversations and cluster governance finally happen in the same place.

## What is DataStoria?

In day-to-day data work, a gap usually stretches between "noticing a problem" and "proving the answer": queries written from memory, execution plans read from a terminal, slow-query diagnosis assembled from scattered clues. DataStoria closes that gap into one continuous workflow — connection management, a SQL workbench, cluster observability, AI-assisted diagnostics and shareable sessions, all in a single browser interface.

What sets it apart from typical ClickHouse clients is that **AI is not a bolt-on chat window but a native capability**: every SQL statement the AI produces is validated through read-only tools, and every optimization suggestion arrives with evidence you can re-check. All of it runs inside a server-side security boundary — credentials never reach the browser, and your data never leaves your control.

### Core Philosophy

Three principles shape DataStoria's design:

1. **A Server-Side Security Boundary** — Credentials are encrypted and held by the Spring Boot backend, which also executes every ClickHouse request and model call. The browser never touches a saved secret: the most sensitive link in an enterprise environment stays locked on the server.

2. **Evidence-Driven AI** — Turning natural language into SQL is never a guess. The AI inspects the real schema through read-only tools, validates syntax, and gathers runtime evidence before drawing conclusions — every suggestion traceable, every visualization verifiable.

3. **One Console, Full Control** — Multi-cluster connections, live dashboards, schema exploration, system-log introspection and AI sessions converge in one interface, with pausable, resumable agent runs that make exploring data as composed as governing it.

## Key Features

### 🤖 AI Features

- **Natural Language Data Exploration** — Describe your data needs in plain English and receive optimized ClickHouse queries instantly.
- **Smart Query Optimization** — AI analyzes your queries based on evidence and provides actionable performance improvements.
- **Intelligent Visualization** — Generate stunning visualizations like time series, pie charts, and data tables with simple prompts.

### ⚡ Powerful Query Experience

- **Advanced SQL Editor** — Enjoy syntax highlighting, auto-completion, and query formatting for a seamless coding experience.
- **Smart Error Diagnostics** — Pinpoint syntax errors instantly with precise line and column highlighting, and get AI-powered fix suggestions with one click.
- **Query Log Inspector** — Dive deep into query execution with timeline views, topology graphs, and performance analysis.
- **One-Click Explain** — Instantly understand query execution plans with visual AST and pipeline views.

### 📊 Cluster Monitoring & Management

- **Multi-Cluster Support** — Manage multiple ClickHouse clusters effortlessly from a single interface.
- **Multi-Node Dashboard** — Monitor all nodes with real-time metrics, merge operations, and replication status.
- **Built-in Dashboards** — Access pre-configured panels for query performance, ZooKeeper status, and more.
- **Schema Explorer** — Navigate databases, tables, and columns with an intuitive tree view.

### 🔒 Privacy & Security

- **Server-side execution** — SQL and AgentScope tools execute through the selected backend connection.
- **Encrypted secrets** — ClickHouse passwords and provider credentials are encrypted at rest and omitted from API responses.
- **Bring Your Own API Key** — Submit provider credentials directly to Spring Boot and manage them through backend APIs.

## Who is DataStoria For?

DataStoria is designed for:

- **Data Engineers** who need efficient tools to query and analyze ClickHouse data
- **Database Administrators** managing multiple ClickHouse clusters from a single UI
- **Analysts** who want to explore data using natural language
- **Developers** building applications on top of ClickHouse

## What's Next?

Ready to get started? Follow these steps:

1. **[Installation & Setup](./installation.md)** — Learn how to install and configure DataStoria
2. **[First Connection](./first-connection.md)** — Connect to your ClickHouse instance and start exploring

Download a preview package from the
[project releases](https://github.com/CCweixiao/datastoria-server/releases), or run the source
checkout locally.

---

*DataStoria — every conversation with your data, backed by evidence.*
