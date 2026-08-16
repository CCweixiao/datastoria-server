---
title: 面向 AI Agent 的 ClickHouse Skills
description: 了解 DataStoria 中的 Skill 是什么、它们的好处，以及 ClickHouse 官方 Skills 如何改进 AI 驱动的 ClickHouse 工作流。
head:
  - - meta
    - name: keywords
      content: ClickHouse Skills, Agent Skills, AI Skills, DataStoria Skills, ClickHouse AI, 可复用工作流, Token 效率, ClickHouse Agent Skills
---

# 基于 Skill 的 Agent 架构

Skill 是可复用、面向任务的构建模块，用于在 DataStoria 中引导 AI 的行为。它们帮助助手遵循一致的工作流、应用 ClickHouse 专属知识，并为常见任务交付可靠的结果。

## Skill 的好处

- **Token 效率**：Skill 将重复的指令压缩为单一的可复用单元。
- **结果一致**：标准化的步骤降低了相似任务之间的差异性。
- **领域准确性**：ClickHouse 专属的指引提升查询和诊断质量。
- **更快上手**：团队可以共享经过验证的工作流，无需重写 Prompt。
- **更安全的自动化**：Skill 可以强制执行防护措施和最佳实践约束。

## 支持的 ClickHouse 官方 Skills

ClickHouse 维护着一个面向使用 ClickHouse 的 AI 助手的官方 Skills 集合。这些 Skills 为查询探索、性能调优和运维指导等任务提供高层次的领域感知工作流，用户无需每次都精心编写详细的 Prompt。

你可以在以下地址查看官方目录：

- **ClickHouse Agent Skills**: https://github.com/ClickHouse/agent-skills

## 在 DataStoria 中使用 Skills

Agent 会根据用户的请求自动应用 Skills。

例如，如果你提出这样的问题：

```text
Visualize the number of commits in 2021 Feb by day in line chart
```

Agent 会自动加载 `Visualization` Skill，而 `Optimization` 等其他 Skill 则不会被加载。

如果你想应用官方 Skills，可以在问题中加入 'best practice' 关键词来激活相应 Skill。例如：

```text
Apply the best practice to review the table: default.sampel_table
```

## 后续步骤

要启用 AI 功能，请完成模型设置：

- [AI 模型配置](./ai-model-configuration.md)
