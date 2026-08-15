---
title: 安装与配置
description: 通过发布包安装 DataStoria，或从源码运行。
---

# 安装与配置

DataStoria 包含一个 Next.js 前端和一个 Spring Boot 后端。两个组件都需要部署；仅运行前端进程无法存储连接、模型或会话。

## 发布包

从[当前仓库的 releases](https://github.com/CCweixiao/datastoria-server/releases) 下载 `datastoria-<version>.tar.gz` 和 `SHA256SUMS`。

```bash
sha256sum -c SHA256SUMS
tar -xzf datastoria-<version>.tar.gz
cd datastoria-<version>
bin/datastoria init
bin/datastoria start
bin/datastoria status
```

打开 `http://localhost:3000`。`dev` profile 使用与生产环境相同的 MySQL 5.7 schema，适用于开发/评估。生产环境使用 MySQL 以及 OAuth2/OIDC。

## 从源码构建

环境要求：JDK 17、Node.js 22、npm、Git 和 tar。

```bash
git clone --recurse-submodules https://github.com/CCweixiao/datastoria-server.git
cd datastoria-server
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bin/build-package.sh
```

如需进行实时开发，请遵循仓库的[开发指南](https://github.com/CCweixiao/datastoria-server/blob/master/docs/development/getting-started.md)。

## 生产环境

在对外暴露服务之前，请配置好 MySQL、稳定的 master key、OAuth2/OIDC、TLS 以及备份。
参见[生产环境指南](https://github.com/CCweixiao/datastoria-server/blob/master/docs/deployment/production.md)。

## 后续步骤

1. [创建第一个 ClickHouse 连接](./first-connection.md)。
2. [配置 AI 模型](../02-ai-features/ai-model-configuration.md)。
