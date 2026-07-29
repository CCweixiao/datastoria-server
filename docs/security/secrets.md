# 密钥与敏感信息

## 敏感数据分类

以下内容不得进入 Git、截图、录屏、Issue、聊天记录或普通日志：

- 模型 API Key、OAuth Client Secret、Refresh Token；
- ClickHouse/MySQL 密码和完整连接串中的凭据；
- `DATASTORIA_MASTER_KEY`、Cookie、Authorization Header；
- 包含客户数据的 SQL 结果、Prompt、会话内容和导出文件。

## 存储与传输

- 浏览器只向 Java 提交一次凭据，不从读取接口取回明文；
- Java 使用 AES-256-GCM 加密供应商和连接凭据；
- 生产主密钥来自环境变量或密钥管理系统；
- 外部模型、MySQL、ClickHouse 和用户入口在生产环境使用 TLS；
- 失败信息返回稳定错误码与安全描述，原始供应商响应只在受控调试中查看。

## 文档素材规范

1. 使用 `demo@example.com`、`sk-demo-••••` 等不可用占位数据；
2. 截图前清空浏览器自动填充并隐藏个人书签、通知和其他标签页；
3. 录屏只录应用窗口，关闭开发者工具中可能包含 Header 的面板；
4. 保存素材后用 OCR/人工检查 `sk-`、`Bearer`、`password`、邮箱和内网域名；
5. 提交前执行仓库敏感信息扫描。

建议扫描：

```bash
rg -n --hidden \
  '(sk-[A-Za-z0-9_-]{12,}|Bearer[[:space:]]+[A-Za-z0-9._-]+|api[_-]?key[[:space:]]*[:=][[:space:]]*[^<${[:space:]]+)' \
  --glob '!data/**' --glob '!target/**' --glob '!datastoria-web/.next/**' .
```

命中示例、fixture 或正则本身需要人工判断；任何真实值都必须先撤销/轮换，再清理 Git 历史。
