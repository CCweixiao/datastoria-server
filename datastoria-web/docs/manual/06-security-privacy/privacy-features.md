---
title: Security & Privacy
description: DataStoria credential, query and AI data boundaries.
---

# Security & Privacy

## Stored data

Spring Boot stores application configuration, users, ClickHouse connections, model providers,
sessions, messages, feedback and Agent Run state in MySQL 5.7 in both development and production.
ClickHouse passwords and model API keys are encrypted with AES-256-GCM and are never returned to
the browser after saving.

## Query and AI data

SQL is sent to Java, which executes it against the selected ClickHouse connection. AI workflows may
send prompts, schema, errors, SQL or tool evidence to the configured model provider. Administrators
must choose providers and retention policies appropriate for their data classification.

DataStoria does not make a blanket “no data leaves the machine” promise: behavior depends on the
ClickHouse endpoint, model provider and deployment topology configured by the operator.

## Operator responsibilities

- Provide HTTPS for the user-facing endpoint and TLS to databases/providers.
- Restrict access to `datastoria.master-key`, database passwords and OAuth secrets.
- Use least-privilege ClickHouse accounts and restrict Java network access.
- Configure backups, log retention and provider data policies.
- Never include credentials or production rows in screenshots and issue reports.

See the repository
[security guide](https://github.com/CCweixiao/datastoria-server/blob/master/docs/security/secrets.md).
