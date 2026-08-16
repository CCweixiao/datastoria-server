---
title: 安装与配置
description: 通过发布包安装 DataStoria，或从源码运行；配置文件与全部配置项说明。
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

打开 `http://localhost:8080`。统一安装包为单进程部署：Spring Boot 后端同时托管 API 和静态导出的前端，无需单独运行 Node.js。`dev` profile 使用与生产环境相同的 MySQL 5.7 schema，适用于开发/评估；生产环境使用 `prod` profile，凭据通过环境变量提供（见下文）。

## 配置文件与配置项

所有运行时配置集中在 `conf/datastoria.env`：`bin/datastoria init` 首次运行时从 `conf/datastoria.env.example` 复制并设置权限 0600，之后按目标环境调整。文件中每个变量都带有中英文注释和默认值；省略的变量自动使用默认值。

`bin/datastoria start` 读取该文件并启动进程，修改后重启生效。

### 进程

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DATASTORIA_PROFILE` | 运行 profile：`dev`=本地开发默认值；`prod`=生产（凭据由本文件提供） | `dev` |
| `SERVER_HOST` / `SERVER_PORT` | 后端监听地址与端口 | `0.0.0.0` / `8080` |
| `JAVA_OPTS` | JVM 启动参数 | `-Xms256m -Xmx1024m` |
| `JAVA_HOME` | JDK 17 安装目录（`java` 不在 PATH 时必填） | — |

### 数据库（MySQL 5.7）

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DATASTORIA_DB_URL` | JDBC 连接串 | 本地 `datastoria` 库 |
| `DATASTORIA_DB_USERNAME` / `DATASTORIA_DB_PASSWORD` | 数据库账号 | `datastoria` / `datastoria` |

### 认证

登录方式为用户名 + 密码（JWT）。首次启动会按以下变量引导创建初始管理员，账号已存在时跳过：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DATASTORIA_JWT_SECRET` | JWT 签名密钥（任意非空字符串，服务端 SHA-256 派生）；**prod 必填**，必须为强随机值 | dev 内置值 |
| `DATASTORIA_JWT_TTL_MINUTES` | JWT 有效期（分钟） | `480` |
| `DATASTORIA_BOOTSTRAP_ADMIN_USERNAME` | 初始管理员用户名 | `datastoria` |
| `DATASTORIA_BOOTSTRAP_ADMIN_PASSWORD` | 初始管理员密码；**prod 必填** | dev 内置值 |
| `DATASTORIA_BOOTSTRAP_ADMIN_ROLE` / `_EMAIL` | 初始管理员角色 / 邮箱 | `ADMIN` / 空 |

### 凭据加密

ClickHouse 连接密码和 AI 供应商 API key 使用 AES-256 信封加密存储：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DATASTORIA_MASTER_KEY` | 主密钥（base64 的 32 字节，可用 `openssl rand -base64 32` 生成）。可省略：未设置时首启自动生成随机 key 写入 `data/master.key`（权限 0600），**务必备份该文件**，丢失后已存凭据不可恢复 | dev 内置 key / 自动生成 |
| `DATASTORIA_MASTER_KEY_FILE` | 主密钥文件路径 | `data/master.key` |
| `DATASTORIA_MASTER_KEY_LEGACY` | 历史主密钥（逗号分隔，仅解密用）。轮换主密钥后把旧 key 放这里，存量密文仍可读取 | 仓库历史内置 key |

### 多租户与 CORS

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DATASTORIA_DEFAULT_TENANT` | 默认租户标识 | `default` |
| `DATASTORIA_CORS_ALLOWED_ORIGINS` | 仅前后端分离部署（前端独立域名）时配置；统一单进程部署同源访问，无需 CORS | 空=拒绝所有跨域 |

### ClickHouse 查询限制

普通用户 Query 与 Agent 查询共用的服务端安全上限；请求参数只能调小不能放宽，管理员查询不受限：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DATASTORIA_QUERY_READ_ONLY` | 强制只读（映射 `readonly=2`），必须保持 `true` | `true` |
| `DATASTORIA_QUERY_ALLOW_DDL` | 是否允许 DDL | `false` |
| `DATASTORIA_QUERY_ALLOW_INTROSPECTION_FUNCTIONS` | 是否允许内省函数 | `false` |
| `DATASTORIA_QUERY_MAX_EXECUTION_TIME` | 单条查询最长执行时间（秒） | `30` |
| `DATASTORIA_QUERY_MAX_RESULT_ROWS` / `_BYTES` | 返回结果最大行数 / 字节数，超限截断 | `10000` / `10000000` |
| `DATASTORIA_QUERY_MAX_ROWS_TO_READ` / `_BYTES` | 最多扫描行数 / 字节数，超限终止查询 | `10000000` / `1000000000` |
| `DATASTORIA_QUERY_MAX_MEMORY_USAGE` | 单条查询最大内存（字节） | `1000000000` |
| `DATASTORIA_QUERY_MAX_THREADS` | 单条查询最大执行线程数 | `4` |

### 元数据缓存与 Agent

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DATASTORIA_CLICKHOUSE_METADATA_CACHE_TTL` | 连接元数据缓存 TTL（ISO-8601 时长） | `PT5M` |
| `DATASTORIA_CLICKHOUSE_METADATA_CACHE_MAXIMUM_SIZE` | 元数据缓存最大条目数 | `1000` |
| `DATASTORIA_AGENT_REPOSITORY_ROOT` | Agent 运行时仓库根目录 | 进程工作目录 |
| `DATASTORIA_AGENT_MAX_ITERS` | Agent 单条消息内"推理-工具调用"循环轮数上限（设置 → AI → 智能体里的配置只能在该上限内调低） | `25` |

### prod 必填清单

`DATASTORIA_PROFILE=prod` 时必须显式配置：`DATASTORIA_DB_URL/USERNAME/PASSWORD`、`DATASTORIA_JWT_SECRET`、`DATASTORIA_BOOTSTRAP_ADMIN_PASSWORD`；其余变量均可选。

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

在对外暴露服务之前，请配置好 MySQL、稳定的 master key（建议从密钥系统注入或备份自动生成的 `data/master.key`）、JWT 密钥、TLS 以及备份。参见[生产环境指南](https://github.com/CCweixiao/datastoria-server/blob/master/docs/deployment/production.md)。

## 后续步骤

1. [创建第一个 ClickHouse 连接](./first-connection.md)。
2. [配置 AI 模型](../02-ai-features/ai-model-configuration.md)。
