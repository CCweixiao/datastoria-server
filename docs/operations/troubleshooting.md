# 故障排查

## 启动

### `concurrently: command not found`

前端依赖未完整安装：

```bash
cd frontend
npm install
npm run dev
```

### `Can't resolve '@/lib/code-search/bootstrap'`

确认代码与 submodule 完整，停止前端后清理构建缓存：

```bash
git submodule update --init --recursive
rm -rf frontend/.next
cd frontend && npm run typecheck
```

### macOS Netty DNS native library警告

若服务可以正常解析地址，这是 Netty 回退到系统 DNS 的告警；若确有解析异常，再核对当前 CPU
架构与 `netty-resolver-dns-native-macos` 依赖。不要把 favicon 404 当成服务启动失败。

## ClickHouse

### 连接测试成功，但 AI 提示没有连接

新建会话，确认会话绑定的是实际连接 ID，而不是无连接占位值。连接元数据仍在加载时，聊天
面板会等待；旧的无连接会话不会被自动改绑，以免把历史上下文发往错误数据库。

### `clusterAllReplicas 被禁用`

连接的 Cluster 字段必须与 ClickHouse `system.clusters.cluster` 完全一致。服务端仅允许
字面量集群名且必须匹配已保存配置，动态表达式或其他集群名会被拒绝。

### `array aggregation ... type Nothing`

部分 ClickHouse 版本没有 Dashboard 正则匹配的缓存指标列。当前前端会把动态列数组显式转换
为 `Array(Float64)`，空匹配返回 0。若仍出现错误，清理前端缓存并确认运行版本包含该兼容逻辑。

### `system.opentelemetry_span_log does not exist`

该系统日志表取决于 ClickHouse 配置。未启用时相应页面应显示不可用，不影响其他功能。需要
该能力时在 ClickHouse 配置中启用 OpenTelemetry span log 并重启实例。

## 模型与 AI

### `CLIENT_SECRET_NOT_ALLOWED`

请求体包含了浏览器端 secret。模型凭据必须在系统设置中由 Java API 保存，聊天请求只引用
供应商/模型 ID。

### 供应商保存成功但模型列表为空

依次检查 Base URL、API Key、供应商测试，再执行“发现模型”。部分 OpenAI 兼容服务不提供
模型列表 API，此时由管理员手工添加模型 ID、上下文窗口和多模态能力。

## 日志收集

统一包：

```bash
bin/datastoria status
bin/datastoria logs 300
```

提交问题时附版本、profile、浏览器版本、ClickHouse 版本、稳定错误码和脱敏后的最小复现。
不要附数据库密码、API Key、Authorization Header 或完整业务查询结果。
