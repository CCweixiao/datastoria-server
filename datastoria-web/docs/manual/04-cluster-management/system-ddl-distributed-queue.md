---
title: system.ddl_distribution_queue 内省
description: 监控 ClickHouse 集群的分布式 DDL 操作。跟踪 CREATE、ALTER、DROP 语句的执行，识别失败，并监控 DDL 在各节点上的进度。
head:
  - - meta
    - name: keywords
      content: ddl_distribution_queue, distributed DDL, DDL monitoring, cluster DDL, DDL operations, DDL tracking, CREATE ALTER DROP, DDL execution, cluster operations
---

# system.ddl_distribution_queue 内省

DDL 分发队列内省工具提供对 ClickHouse 集群上分布式 DDL 操作的洞察。它跟踪 DDL 语句（CREATE、ALTER、DROP 等）在集群节点上的分发与执行情况，帮助你监控 DDL 操作状态、识别失败并跟踪执行进度。

它提供多种视图和过滤器，帮助你理解 DDL 操作在整个集群中的分发和执行状态。

## 前置条件

> **注意**：
>
> 1. 使用该内省工具需要对 `system.distributed_ddl_queue` 表的读权限。请确保你的用户具备必要的系统表权限。
> 2. 数据库连接需配置为集群模式

## UI

<Video src="/manual/04-cluster-management/img/system-ddl-distributed-queue.webm" alt="系统 DDL 分发队列界面，展示各集群节点上 DDL 查询的执行状态和进度指示" />

## 使用场景

### DDL 操作监控

1. **跟踪 DDL 进度**：使用聚合条目（Aggregated Entries）视图查看所有主机上 DDL 操作的整体状态
2. **监控执行**：检查逐主机状态，识别哪些主机已完成、正在执行或排队中
3. **识别失败**：按状态过滤或查看详情面板，了解哪些主机失败及原因
4. **跟踪耗时**：监控查询耗时，识别缓慢的 DDL 操作

### 集群健康

1. **主机对比**：对比各主机的执行状态，识别有问题的节点
2. **失败分析**：使用详情面板查看失败操作的异常代码和错误信息
3. **执行模式**：使用图表查看 DDL 操作随时间的分布
4. **滞后检测**：识别在 DDL 执行上滞后的主机

### 问题排查

1. **失败的 DDL 操作**：点击失败的条目查看详细错误信息
2. **卡住的操作**：识别长时间停留在"Active"或"Queued"状态的 DDL 操作
3. **主机问题**：按主机过滤，查看特定节点上的所有 DDL 操作
4. **时序分析**：对比各主机上的查询创建时间和耗时

### DDL 管理

1. **操作跟踪**：在一个地方监控所有分布式 DDL 操作
2. **状态核验**：快速核验 DDL 操作已在所有主机上成功完成
3. **集群同步**：确保 DDL 操作在集群中正确分发和执行
4. **历史分析**：使用时间范围选择器回顾过往的 DDL 操作


## DDL 分发队列功能

Dashboard 提供对分布式 DDL 操作的全面可视化与分析：

### 图表

- **按主机统计的 DDL 队列条目（DDL Queue Entries By Host）**：堆叠柱状图，按主机分组展示 DDL 队列条目数量随时间的变化。帮助你直观了解 DDL 操作何时被处理，并识别可能滞后的主机。

### 视图

该工具提供两种分析 DDL 操作的视图：

#### 聚合条目视图（Aggregated Entries View）

该视图按条目 ID 对 DDL 操作分组，提供高层概览：

- **Entry**：DDL 操作的唯一标识
- **Query Create Time**：DDL 操作的创建时间
- **Cluster**：DDL 操作的目标集群
- **Query**：DDL SQL 语句（截断显示，悬停可查看完整查询）
- **Status**：汇总展示所有主机上各状态（Finished、Active、Queued、Failed）的百分比分布
- **Hosts**：参与该 DDL 操作的主机数量

**功能：**
- 点击任意条目可在侧面板中查看详细信息
- 状态汇总展示各主机执行状态的分布
- 默认按查询创建时间排序（最新在前）

#### 原始条目视图（Raw Entries View）

该视图展示所有未聚合的 DDL 队列记录：

- **Entry**：DDL 操作条目标识
- **Query Create Time**：DDL 操作的创建时间
- **Host**：正在执行该 DDL 的主机名
- **Status**：当前执行状态（Finished、Active、Queued、Failed）
- **Query**：DDL SQL 语句（截断显示，悬停可查看完整查询）
- **Query Duration**：执行耗时（毫秒）

**功能：**
- 查看逐主机的执行详情
- 跟踪每个 DDL 操作在各主机上的状态
- 识别哪些主机已完成、正在执行、排队或失败

### 详情面板

在聚合条目视图中点击某个条目后，会打开详情面板，展示：

#### 条目详情

- **Cluster**：目标集群名称
- **Create Time**：DDL 操作的创建时间
- **Entry Version**：DDL 条目的版本
- **Initiator Host**：发起该 DDL 操作的主机

#### 分布式 DDL 查询

带语法高亮的完整 DDL SQL 语句，便于阅读。

#### 逐主机 DDL 日志

展示每个主机执行状态的详细表格：

- **Host**：主机名
- **Status**：带彩色图标的执行状态：
  - ✅ **Finished**：绿色（成功完成）
  - ▶️ **Active**：蓝色（正在执行）
  - ⏰ **Queued**：琥珀色（等待执行）
  - ❌ **Failed**：红色（执行失败）
- **Query Create Time**：该 DDL 在此主机上的创建时间
- **Query Duration**：执行耗时（毫秒）
- **Exception 详情**：如果失败，在工具提示中展示异常代码和错误信息

## DDL 分发队列过滤

DDL 分发队列支持过滤：


## 下一步

- **[Node Dashboard](../05-monitoring-dashboards/node-dashboard.md)** —— 监控单节点指标
- **[系统日志内省](./system-log-introspection.md)** —— 所有系统日志工具概览
- **[system.part_log 内省](./system-part-log.md)** —— 监控 Part 级操作
- **[system.query_log 内省](./system-query-log.md)** —— 分析查询执行日志
- **[system.query_views_log 内省](./system-query-views-log.md)** —— 监控查询视图执行
- **[system.zookeeper 内省](./system-zookeeper.md)** —— 浏览 ZooKeeper 数据
