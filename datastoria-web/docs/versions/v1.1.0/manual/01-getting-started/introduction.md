---
title: DataStoria 简介
description: 了解 DataStoria —— 一个 AI 原生的 ClickHouse 控制台，提供自然语言查询、智能优化与隐私优先的架构。
head:
  - - meta
    - name: keywords
      content: DataStoria 简介, ClickHouse 控制台, AI 数据库管理, 自然语言 SQL, ClickHouse 图形界面, 数据库管理工具, 隐私优先的数据库工具
---

# DataStoria 简介

欢迎使用 **DataStoria**——一个 AI 原生的 ClickHouse 控制台，它将改变你与数据交互以及管理集群的方式。

## 什么是 DataStoria？

DataStoria 是一个功能全面的 ClickHouse Web 界面，它将强大的查询能力与人工智能相结合，让数据库管理更加直观、高效、易用。

### 核心理念

DataStoria 建立在三条基本原则之上：

1. **后端安全边界** —— Spring Boot 负责存储加密的凭据，并执行 ClickHouse 请求与模型请求。浏览器永远不会收到已保存的密钥。

2. **AI 增强智能** —— 借助前沿 AI 将自然语言转换为优化后的 SQL 查询，获得智能查询建议，并自动生成可视化。

3. **完全掌控** —— 在一个统一的界面中管理多个 ClickHouse 集群、监控性能并浏览 Schema。

## 核心特性

### 🤖 AI 特性

- **自然语言数据探索** —— 用平实的语言描述你的数据需求，立即获得优化后的 ClickHouse 查询。
- **智能查询优化** —— AI 基于证据分析你的查询，并提供可落地的性能改进建议。
- **智能可视化** —— 通过简单的提示词生成时序图、饼图、数据表等精美可视化。

### ⚡ 强大的查询体验

- **高级 SQL 编辑器** —— 享受语法高亮、自动补全与查询格式化，带来流畅的编码体验。
- **智能错误诊断** —— 通过精确的行列高亮即时定位语法错误，并可一键获取 AI 修复建议。
- **Query Log Inspector** —— 通过时间线视图、拓扑图和性能分析深入探究查询执行过程。
- **一键 Explain** —— 借助可视化 AST 与 Pipeline 视图，即刻理解查询执行计划。

### 📊 集群监控与管理

- **多集群支持** —— 在单一界面中轻松管理多个 ClickHouse 集群。
- **多节点 Dashboard** —— 通过实时指标、merge 操作与副本状态监控所有节点。
- **内置 Dashboard** —— 访问预配置面板，查看查询性能、ZooKeeper 状态等信息。
- **Schema Explorer** —— 通过直观的树形视图浏览数据库、表和列。

### 🔒 隐私与安全

- **服务端执行** —— SQL 与 AgentScope 工具均通过所选的后端连接执行。
- **密钥加密** —— ClickHouse 密码与提供商凭据静态加密，并且不会出现在 API 响应中。
- **自带 API Key（Bring Your Own API Key）** —— 将提供商凭据直接提交给 Spring Boot，并通过后端 API 进行管理。

## DataStoria 适合谁？

DataStoria 面向以下用户：

- 需要高效查询和分析 ClickHouse 数据的**数据工程师**
- 希望通过单一 UI 管理多个 ClickHouse 集群的**数据库管理员**
- 希望使用自然语言探索数据的**分析师**
- 基于 ClickHouse 构建应用的**开发者**

## 接下来做什么？

准备好开始了吗？请按照以下步骤操作：

1. **[安装与配置](./installation.md)** —— 了解如何安装和配置 DataStoria
2. **[首次连接](./first-connection.md)** —— 连接到你的 ClickHouse 实例并开始探索

从[项目 releases](https://github.com/CCweixiao/datastoria-server/releases) 下载预览包，或在本地运行源码检出。

---

*DataStoria —— 面向查询、可视化与诊断的 AI 原生 ClickHouse 工作流。*
