---
title: Installation & Setup
description: Install DataStoria from a release package or run it from source.
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

Open `http://localhost:3000`. The `dev` profile uses the same MySQL 5.7 schema as production and is
intended for
development/evaluation. Production uses MySQL and OAuth2/OIDC.

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

Configure MySQL, a stable master key, OAuth2/OIDC, TLS and backups before exposing the service.
See the
[production guide](https://github.com/CCweixiao/datastoria-server/blob/master/docs/deployment/production.md).

## Next steps

1. [Create the first ClickHouse connection](./first-connection.md).
2. [Configure an AI model](../02-ai-features/ai-model-configuration.md).
