---
title: Installation & Setup
description: Install DataStoria from a release package or run it from source; configuration file and every runtime setting.
---

# Installation & Setup

DataStoria includes a Next.js frontend and a Spring Boot backend. Deploy both components; a
frontend-only process cannot store connections, models or sessions.

## Release package

Download `datastoria-<version>.tar.gz` and `SHA256SUMS` from the
[current repository releases](https://github.com/CCweixiao/datastoria-server/releases).

```bash
sha256sum -c SHA256SUMS
tar -xzf datastoria-<version>.tar.gz
cd datastoria-<version>
bin/datastoria init
bin/datastoria start
bin/datastoria status
```

Open `http://localhost:8080`. The unified package deploys as a single process: the Spring Boot
backend serves both the API and the statically exported frontend — no separate Node.js process is
required. The `dev` profile uses the same MySQL 5.7 schema as production and is intended for
development/evaluation; production uses the `prod` profile with credentials supplied via
environment variables (see below).

## Configuration file and settings

All runtime configuration lives in `conf/datastoria.env`: `bin/datastoria init` copies it from
`conf/datastoria.env.example` with 0600 permissions on first run; adjust it for the target
environment afterwards. Every variable carries a bilingual comment and its default value in the
template; omitted variables fall back to those defaults.

`bin/datastoria start` sources the file when launching the process; changes take effect after a
restart.

### Process

| Variable | Description | Default |
|---|---|---|
| `DATASTORIA_PROFILE` | Runtime profile: `dev` = local development defaults; `prod` = production (credentials supplied by this file) | `dev` |
| `SERVER_HOST` / `SERVER_PORT` | Backend listen address and port | `0.0.0.0` / `8080` |
| `JAVA_OPTS` | JVM startup options | `-Xms256m -Xmx1024m` |
| `JAVA_HOME` | Path to a JDK 17 install (required when `java` is not on PATH) | — |

### Database (MySQL 5.7)

| Variable | Description | Default |
|---|---|---|
| `DATASTORIA_DB_URL` | JDBC connection URL | local `datastoria` database |
| `DATASTORIA_DB_USERNAME` / `DATASTORIA_DB_PASSWORD` | Database credentials | `datastoria` / `datastoria` |

### Authentication

Sign-in is username + password issuing a JWT. The following variables bootstrap the initial
administrator on first start (skipped when the account already exists):

| Variable | Description | Default |
|---|---|---|
| `DATASTORIA_JWT_SECRET` | JWT signing secret (any non-empty string, SHA-256 derived server-side); **required in prod**, must be a strong random value | built-in dev value |
| `DATASTORIA_JWT_TTL_MINUTES` | JWT lifetime in minutes | `480` |
| `DATASTORIA_BOOTSTRAP_ADMIN_USERNAME` | Bootstrap admin username | `datastoria` |
| `DATASTORIA_BOOTSTRAP_ADMIN_PASSWORD` | Bootstrap admin password; **required in prod** | built-in dev value |
| `DATASTORIA_BOOTSTRAP_ADMIN_ROLE` / `_EMAIL` | Bootstrap admin role / email | `ADMIN` / empty |

### Credential encryption

ClickHouse connection passwords and AI provider API keys are stored with AES-256 envelope
encryption:

| Variable | Description | Default |
|---|---|---|
| `DATASTORIA_MASTER_KEY` | Master key (base64-encoded 32 bytes). Optional: when unset, a random key is generated into `data/master.key` (0600) on first start — **back that file up**, losing it makes stored credentials unrecoverable | built-in dev key / auto-generated |
| `DATASTORIA_MASTER_KEY_FILE` | Master key file path | `data/master.key` |
| `DATASTORIA_MASTER_KEY_LEGACY` | Legacy master keys (comma-separated, decrypt-only). After rotating the master key, keep the previous key here so existing ciphertexts stay readable | repository's historical keys |

### Tenancy and CORS

| Variable | Description | Default |
|---|---|---|
| `DATASTORIA_DEFAULT_TENANT` | Default tenant identifier | `default` |
| `DATASTORIA_CORS_ALLOWED_ORIGINS` | Only for a separately hosted frontend; the unified single-process deployment is same-origin and needs no CORS | empty = deny all cross-origin |

### ClickHouse query guardrails

Server-side limits shared by regular-user queries and agent queries; request parameters can only
tighten them, never loosen them. Admin queries are exempt:

| Variable | Description | Default |
|---|---|---|
| `DATASTORIA_QUERY_READ_ONLY` | Enforce read-only (maps to `readonly=2`); must stay `true` | `true` |
| `DATASTORIA_QUERY_ALLOW_DDL` | Allow DDL statements | `false` |
| `DATASTORIA_QUERY_ALLOW_INTROSPECTION_FUNCTIONS` | Allow introspection functions | `false` |
| `DATASTORIA_QUERY_MAX_EXECUTION_TIME` | Max query execution time in seconds | `30` |
| `DATASTORIA_QUERY_MAX_RESULT_ROWS` / `_BYTES` | Max result rows / bytes, truncated when exceeded | `10000` / `10000000` |
| `DATASTORIA_QUERY_MAX_ROWS_TO_READ` / `_BYTES` | Max rows / bytes to read, query aborted when exceeded | `10000000` / `1000000000` |
| `DATASTORIA_QUERY_MAX_MEMORY_USAGE` | Max memory usage per query in bytes | `1000000000` |
| `DATASTORIA_QUERY_MAX_THREADS` | Max execution threads per query | `4` |

### Metadata cache and agent

| Variable | Description | Default |
|---|---|---|
| `DATASTORIA_CLICKHOUSE_METADATA_CACHE_TTL` | Connection metadata cache TTL (ISO-8601 duration) | `PT5M` |
| `DATASTORIA_CLICKHOUSE_METADATA_CACHE_MAXIMUM_SIZE` | Max cached metadata entries | `1000` |
| `DATASTORIA_AGENT_REPOSITORY_ROOT` | Agent runtime repository root | process working directory |

### Required in prod

With `DATASTORIA_PROFILE=prod` you must set `DATASTORIA_DB_URL/USERNAME/PASSWORD`,
`DATASTORIA_JWT_SECRET` and `DATASTORIA_BOOTSTRAP_ADMIN_PASSWORD`; everything else is optional.

## Build from source

Requirements: JDK 17, Node.js 22, npm, Git and tar.

```bash
git clone --recurse-submodules https://github.com/CCweixiao/datastoria-server.git
cd datastoria-server
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bin/build-package.sh
```

For live development, follow the repository
[development guide](https://github.com/CCweixiao/datastoria-server/blob/master/docs/development/getting-started.md).

## Production

Configure MySQL, a stable master key (inject it from a secret store or back up the auto-generated
`data/master.key`), the JWT secret, TLS and backups before exposing the service. See the
[production guide](https://github.com/CCweixiao/datastoria-server/blob/master/docs/deployment/production.md).

## Next steps

1. [Create the first ClickHouse connection](./first-connection.md).
2. [Configure an AI model](../02-ai-features/ai-model-configuration.md).
