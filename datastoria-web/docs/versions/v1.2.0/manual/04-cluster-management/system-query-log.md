---
title: system.query_log 内省
description: 通过过滤、图表和 AI 洞察分析 ClickHouse query_log。监控查询性能、调试错误，并借助可视化查询日志分析跟踪执行模式。
head:
  - - meta
    - name: keywords
      content: query_log, system.query_log, query log analysis, query monitoring, query performance, ClickHouse queries, query debugging, query metrics, execution tracking
---

# system.query_log 内省

Query 日志内省工具以可视化方式提供对 ClickHouse 集群上所有已执行查询的深度洞察。

它提供多种过滤器和分布图表以及明细表，让我们无需在 `system.query_log` 表上手动编写多条 SQL，即可从 UI 中快速找到查询。

## 前置条件

> **注意**：使用该内省工具需要对 `system.query_log` 表的读权限。请确保你的用户具备必要的系统表权限。

## UI

<Video src="/v1.2.0/manual/04-cluster-management/img/system-query-log.webm" alt="system.query_log 界面，展示详细的查询执行指标，包括耗时、内存使用和处理行数" />

## 何时使用 Query 日志内省工具

### 性能分析

1. **按耗时过滤**：按耗时排序找出慢查询
2. **分析模式**：使用分布图识别高峰时段
3. **对比节点**：按主机名过滤对比各节点性能
4. **跟踪趋势**：使用时间范围选择器查看性能随时间的变化

### 错误调试

1. **按异常过滤**：使用 exception_code 过滤器聚焦错误
2. **查看错误详情**：展开行查看完整错误信息
3. **使用 AI Explain**：点击"Explain Error"获取 AI 驱动的错误分析
4. **跟踪错误频率**：使用分布图查看错误激增

### 查询优化

1. **识别高开销查询**：按 read_bytes 或耗时排序
2. **使用 AI 优化**：对慢查询点击"Ask AI for Optimization"
3. **对比查询**：按表过滤查看某张表的所有查询
4. **监控改进**：随时间跟踪查询性能

### 安全与审计

1. **按用户过滤**：监控特定用户的查询
2. **跟踪表访问**：按表过滤查看谁在访问什么
3. **审查失败查询**：按异常过滤查看与安全相关的错误
4. **导出数据**：使用表格功能导出审计日志

## Query 日志过滤

Query 日志支持全面的过滤：

### 时间过滤器

- **类型**：DateTime 范围选择器
- **默认**：最近 15 分钟
- **选项**：预定义范围或自定义时间选择
- **时区**：遵循你配置的时区

### 主机名过滤器

- **类型**：多选下拉框
- **来源**：`system.clusters` 中的去重主机名
- **默认**：当前节点（单节点模式下隐藏此过滤器）
- **使用场景**：在集群中按特定节点过滤查询

### 查询类型过滤器

- **类型**：多选下拉框
- **选项**：
  - QueryStart
  - QueryFinish
  - ExceptionBeforeStart
  - ExceptionWhileProcessing
- **默认**：排除 QueryStart（展示已完成/失败的查询）
- **使用场景**：聚焦已完成的查询或错误

### 查询 Kind 过滤器

- **类型**：多选下拉框
- **来源**：`system.query_log` 中去重的 query_kind 值
- **选项**：Select、Insert、Create、Drop、Alter 等
- **默认**：排除 Insert 查询
- **使用场景**：按操作类型过滤

### 数据库过滤器

- **类型**：多选下拉框
- **来源**：`system.query_log` 中去重的数据库
- **使用场景**：聚焦特定数据库的查询

### 表过滤器

- **类型**：多选下拉框
- **来源**：`system.query_log` 中去重的表
- **支持的比较运算符**：=、!=、in、not in
- **使用场景**：跟踪访问特定表的查询

### 异常代码过滤器

- **类型**：多选下拉框
- **来源**：去重的 exception_code 值
- **使用场景**：按特定错误类型过滤

### 用户过滤器

- **类型**：多选下拉框
- **来源**：去重的 initial_user 值
- **使用场景**：监控特定用户的查询

### 输入过滤器

- **类型**：使用 ClickHouse 过滤表达式的自由文本搜索
- **范围**：搜索所有列
- **使用场景**：快速搜索特定查询、用户或错误信息
- **示例**：

  ```sql
  query like '%metrics%'
  ```

## AI 驱动的操作

每条 Query 日志行的操作菜单中包含 AI 驱动的功能：

### Ask AI for Optimization（请求 AI 优化）

- **图标**：Sparkle/Wand 图标
- **功能**：分析查询并给出优化建议
- **流程**：
  1. 从日志中提取查询文本
  2. 打开一个带优化请求的新聊天
- **使用场景**：获取改进查询性能的 AI 建议

### Explain Error（解释错误）

- **图标**：Alert circle 图标（仅在带异常的查询上显示）
- **功能**：解释错误并给出修复建议
- **流程**：
  1. 从日志中提取查询和错误信息
  2. 打开一个带错误解释请求的新聊天
- **使用场景**：快速理解并修复查询错误

## 下一步

- **[Query Log Inspector](../03-query-experience/query-log-inspector.md)** —— 分析具体查询的执行
- **[系统日志内省](./system-log-introspection.md)** —— 所有系统日志工具概览
- **[system.ddl_distribution_queue 内省](./system-ddl-distributed-queue.md)** —— 监控分布式 DDL 操作
- **[system.part_log 内省](./system-part-log.md)** —— 监控 Part 级操作
- **[system.query_views_log 内省](./system-query-views-log.md)** —— 监控查询视图执行
- **[system.zookeeper 内省](./system-zookeeper.md)** —— 浏览 ZooKeeper 数据
