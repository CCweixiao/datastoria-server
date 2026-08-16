---
title: AI 模型配置
description: 配置由服务端管理的 AI 提供商、凭证以及模型目录条目。
---

# AI 模型配置

DataStoria 的模型目录与凭证由 Spring Boot 管理。

打开 **Settings → Models**（模型）可以：

- 查看 `/api/ai/models/available` 返回的已启用模型；
- 启用或禁用存储在数据库中的模型；
- 提交或轮换提供商凭证；
- 选择保存在后端用户偏好中的模型。

首次使用时，Spring 会将内置的提供商/模型目录物化到 `ds_model_provider` 和 `ds_model` 表中。管理员可以通过 `/api/admin/ai/providers` 和 `/api/admin/ai/models` 编辑或替换这些记录。

## 凭证安全

浏览器仅在保存请求完成之前持有新输入的凭证。Spring 会将其加密存入 `ds_secret`；API 响应中只包含一个"已配置"标志和掩码提示。聊天、Skill 审核或模型发现请求一律不接受凭证。

## 添加提供商或模型

使用后端管理 API 来创建提供商、保存其凭证、发现支持的模型，以及创建或更新目录条目。前端有意不包含任何提供商 SDK，也不包含硬编码的可执行模型目录。
