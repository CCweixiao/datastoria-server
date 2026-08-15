---
title: system.part_log 内省
description: 监控 ClickHouse Part 操作——Merge、Mutation、下载、删除。跟踪 Part 级性能，识别瓶颈，并分析数据 Part 生命周期。
head:
  - - meta
    - name: keywords
      content: part_log, system.part_log, merge monitoring, mutation tracking, part operations, ClickHouse merges, data parts, part lifecycle, merge performance
---

# system.part_log 内省

Part 日志内省工具跟踪 ClickHouse 集群中的所有 Part 级操作，包括 Merge、Mutation、下载和删除。

它提供多种过滤器和分布图表以及明细表，让我们无需在 `system.part_log` 表上手动编写多条 SQL，即可从 UI 中快速找到查询。

## 前置条件

> **注意**：使用该内省工具需要对 `system.part_log` 表的读权限。请确保你的用户具备必要的系统表权限。

## UI

<Video src="/manual/04-cluster-management/img/system-part-log-introspection.webm" alt="system.part_log 界面，展示数据 Part 的 Merge 操作、Mutation 和分区管理活动" />

## 使用场景

### Merge 监控

1. **按 MergeParts 过滤**：聚焦 Merge 操作
2. **监控耗时**：按耗时排序找出慢速 Merge
3. **跟踪频率**：使用分布图查看 Merge 模式
4. **识别问题**：按错误过滤，找出失败的 Merge

### Mutation 跟踪

1. **按 MutatePart 过滤**：跟踪 ALTER 操作
2. **监控进度**：检查耗时和状态
3. **识别瓶颈**：找出慢速 Mutation
4. **错误分析**：按错误过滤以调试问题

### 复制监控

1. **按 DownloadPart 过滤**：跟踪从副本下载的 Part
2. **监控滞后**：检查事件时间以识别复制延迟
3. **错误跟踪**：按错误过滤，找出复制失败
4. **节点对比**：按主机名过滤以对比节点

### 存储分析

1. **按 NewPart/RemovePart 过滤**：跟踪 Part 生命周期
2. **监控大小**：按 size_in_bytes 排序找出大 Part
3. **跟踪增长**：使用时间范围查看存储趋势
4. **Part 类型分析**：按 part_type 过滤以理解存储格式

## 下一步

- **[Cluster Dashboard](../05-monitoring-dashboards/cluster-dashboard.md)** —— 监控集群级指标
- **[Node Dashboard](../05-monitoring-dashboards/node-dashboard.md)** —— 监控单节点指标
- **[系统日志内省](./system-log-introspection.md)** —— 所有系统日志工具概览
- **[system.ddl_distribution_queue 内省](./system-ddl-distributed-queue.md)** —— 监控分布式 DDL 操作
- **[system.query_log 内省](./system-query-log.md)** —— 分析查询执行日志
- **[system.query_views_log 内省](./system-query-views-log.md)** —— 监控查询视图执行
- **[system.zookeeper 内省](./system-zookeeper.md)** —— 浏览 ZooKeeper 数据
