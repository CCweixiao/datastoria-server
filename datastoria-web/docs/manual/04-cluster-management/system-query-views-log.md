---
title: system.query_views_log 内省
description: 监控 ClickHouse 物化视图和 Live 视图的执行。跟踪视图性能，分析读/写模式，并借助详细指标调试视图错误。
head:
  - - meta
    - name: keywords
      content: query_views_log, system.query_views_log, materialized views, live views, view monitoring, view performance, view execution, ClickHouse views, view metrics
---

# system.query_views_log 内省

Query 视图日志内省工具提供对 ClickHouse 集群上所有查询视图执行的洞察。它跟踪物化视图、Live 视图及其他视图类型的执行情况及其性能指标。

它提供多种过滤器和更多基于视图指标的 Dashboard，以实现更好的内省。

## 前置条件

> **注意**：使用该内省工具需要对 `system.query_views_log` 表的读权限。请确保你的用户具备必要的系统表权限。

## UI

<Video src="/manual/04-cluster-management/img/system-query-views-log.webm" alt="system.query_views_log 界面，展示带过滤和排序功能的查询执行历史" />


## Query 视图日志使用场景

### 视图性能分析

1. **监控视图耗时**：跟踪视图的平均执行时间，识别慢速视图
2. **分析读取模式**：使用读行数/字节数图表理解数据消费情况
3. **跟踪写入模式**：监控写入的行数/字节数，了解视图输出量
4. **对比视图**：按 view_name 过滤，对比不同视图的性能

### 错误调试

1. **按异常过滤**：使用 exception_code 过滤器聚焦失败的视图执行
2. **查看错误详情**：展开行查看完整错误信息
3. **跟踪错误频率**：使用分布图查看错误激增
4. **识别问题视图**：按 view_name 和异常过滤，找出有问题的视图

### 视图优化

1. **识别慢速视图**：按 view_duration_ms 排序，找出需要优化的视图
2. **监控资源使用**：跟踪 peak_memory_usage 和读/写模式
3. **对比时间段**：使用时间范围选择器对比不同时期的性能
4. **节点对比**：按主机名过滤，对比各节点的视图性能

### 物化视图监控

1. **跟踪物化活动**：监控 written_rows 和 written_bytes，了解物化活动
2. **监控滞后**：检查事件时间，识别物化视图更新的延迟
3. **资源规划**：使用读/写指标进行容量规划
4. **视图健康**：跟踪异常率，确保视图正常运行

## 下一步

- **[Query Log Inspector](../03-query-experience/query-log-inspector.md)** —— 分析具体查询的执行
- **[系统日志内省](./system-log-introspection.md)** —— 所有系统日志工具概览
- **[system.ddl_distribution_queue 内省](./system-ddl-distributed-queue.md)** —— 监控分布式 DDL 操作
- **[system.part_log 内省](./system-part-log.md)** —— 监控 Part 级操作
- **[system.query_log 内省](./system-query-log.md)** —— 分析查询执行日志
- **[system.zookeeper 内省](./system-zookeeper.md)** —— 浏览 ZooKeeper 数据
