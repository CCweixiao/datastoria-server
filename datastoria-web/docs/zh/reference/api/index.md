# HTTP API 参考

DataStoria 的浏览器应用通过 JSON HTTP API 和 Server-Sent Events 与 Spring Boot 后端通信。
OpenAPI 契约在文档站每次构建时校验，并由后端测试与 Java controller 及前端调用清单交叉核对。

## OpenAPI 规范

- [下载当前 OpenAPI YAML](/api/openapi.yaml)
- [AI 交互指南](/zh/manual/02-ai-features/ask-ai-for-help)

OpenAPI 文件由 `docs/api/openapi-baseline.yaml` 权威契约生成。不要编辑文档输出目录下的
发布副本。

## 兼容性检查

当出现以下情况时，文档构建流程会拒绝变更：

- OpenAPI 文档无效或违反配置的 Redocly 规则；
- 流协议 fixture 不符合其 JSON Schema；
- VitePress 无法构建某个文档链接或页面。

Java 测试另行验证 controller 路由、前端调用、流事件顺序和 golden fixture 与已发布契约
保持一致。
