# 历史数据导入包格式

`P3Importer` 用于从受支持的 JSONL Bundle 导入会话、消息、分享和反馈。该工具主要服务于
离线数据迁入和测试，运行前应备份目标数据库。

## 目录

```text
bundle/
├── manifest.json
├── sessions.jsonl
├── messages.jsonl
├── sessions-share.jsonl
└── feedback.jsonl
```

每个 JSONL 文件一行一个 JSON 对象，UTF-8 编码，以换行结束。示例位于
`docs/fixtures/business/`。

`manifest.json` 至少记录格式版本与各文件行数。导入器会先校验 Manifest、必填字段、ID 引用
和重复项，再执行写入；计数不符时拒绝导入，避免部分数据被误认为完整。

## 安全

- Bundle 可能包含用户 Prompt、SQL 和反馈，应按生产数据加密存储和传输；
- Bundle 不包含模型 API Key、ClickHouse 密码、OAuth Token 或主密钥；
- 导入日志只记录计数与脱敏 ID，不输出完整消息；
- 导入前确认目标 Tenant，避免跨租户混入。
