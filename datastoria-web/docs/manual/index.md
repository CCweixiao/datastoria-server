---
title: 使用手册
description: DataStoria 完整使用指南——AI 原生 ClickHouse 控制台。学习自然语言查询、查询优化、集群管理与高级功能。
head:
  - - meta
    - name: keywords
      content: DataStoria 手册, ClickHouse 文档, AI SQL 指南, 数据库管理教程, ClickHouse 控制台指南, 自然语言 SQL, 查询优化指南
---

# 使用手册

欢迎阅读 DataStoria 使用手册。本指南覆盖 AI 原生 ClickHouse 控制台的核心工作流。

## 目录

### 1. 快速开始
- [DataStoria 产品介绍](./01-getting-started/introduction.md)
- [安装与配置](./01-getting-started/installation.md)
  - 从源码构建
  - 使用 Docker 运行
  - 在线访问
- [首次连接](./01-getting-started/first-connection.md)
  - 连接 ClickHouse
  - 认证配置
  - 基础导航

### 2. AI 功能
- [自然语言数据探索](./02-ai-features/natural-language-sql.md)
  - 使用方法
  - 最佳实践
  - 示例与用例
- [智能查询优化](./02-ai-features/query-optimization.md)
  - 理解 AI 建议
  - 应用优化
  - 性能影响分析
- [智能可视化](./02-ai-features/intelligent-visualization.md)
  - 从提示生成图表
  - 可用图表类型
  - 自定义可视化
- [AI 助手求助](./02-ai-features/ask-ai-for-help.md)
  - 即时错误协助
  - 理解查询错误
  - 获取 AI 修复建议
- [AI 模型配置](./02-ai-features/ai-model-configuration.md)
  - 配置 API Key
  - 支持的供应商
  - 隐私与安全

### 3. 查询体验
- [SQL 编辑器](./03-query-experience/sql-editor.md)
  - 语法高亮
  - 自动补全
  - 查询格式化
  - 键盘快捷键
- [SQL 代码片段](./03-query-experience/sql-snippets.md)
  - 内置查询模板
  - 创建自定义片段
  - 片段管理
  - 自动补全集成
- [错误诊断](./03-query-experience/error-diagnostics.md)
  - 理解错误信息
  - AI 修复建议
  - 语法错误解决
- [查询执行](./03-query-experience/query-execution.md)
  - 运行查询
  - 查看结果
  - 导出数据
  - 查询历史
- [查询日志检查器](./03-query-experience/query-log-inspector.md)
  - 时间线视图
  - 拓扑图
  - 性能分析
  - 理解执行指标
- [执行计划分析](./03-query-experience/query-explain.md)
  - 可视化 AST 视图
  - 管道可视化
  - 理解执行计划
  - 性能洞察

### 4. 数据库管理
- [模式浏览器](./04-cluster-management/schema-explorer.md)
  - 浏览数据库
  - 探索表
  - 列信息
  - 表元数据

### 5. 监控与仪表盘
- [节点仪表盘](./05-monitoring-dashboards/node-dashboard.md)
  - 单节点指标
  - 节点健康指标
  - 实时性能监控
- [集群仪表盘](./05-monitoring-dashboards/cluster-dashboard.md)
  - 集群级指标
  - 多节点聚合
  - 集群健康总览

### 6. 安全与隐私
- [隐私特性](./06-security-privacy/privacy-features.md)
  - 本地执行模型
  - 数据隐私保证
  - 不收集数据政策

### 7. 管理控制台
- [管理平台操作手册](./07-admin-console/admin-console.md)
  - 首次进入
  - 连接管理
  - 模型供应商配置
  - AI 会话与分享

---

## 快速入门

第一次使用 DataStoria？从这里开始：

1. **安装**：阅读[安装与配置](./01-getting-started/installation.md)指南
2. **首次连接**：学习如何[连接 ClickHouse 实例](./01-getting-started/first-connection.md)
3. **体验 AI 功能**：探索[自然语言数据探索](./02-ai-features/natural-language-sql.md)
4. **运行第一个查询**：使用 [SQL 编辑器](./03-query-experience/sql-editor.md)

## 需要帮助？

- 从[项目 releases](https://github.com/CCweixiao/datastoria-server/releases) 下载安装包
