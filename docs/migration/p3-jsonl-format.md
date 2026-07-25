# P3 JSONL Import Format

> Status: Stable as of P3.6
> Implementer: `io.datastoria.server.tools.importer.P3Importer`
> Tests: `src/test/java/io/datastoria/server/tools/importer/P3ImporterTest.java`

## 1. Purpose

P3 closes chat product data (sessions, messages, feedback, share rows) on the Java side. Before
decommissioning the Node route handlers we must move any rows still living in the Node SQLite /
MySQL store into the Java-side tables. The JSONL importer is the supported path; a Node-side
exporter that emits a compatible bundle is documented in §7 but lives in the frontend repo.

The importer is **idempotent**: running it twice on the same bundle produces identical row counts
and no duplicate primary keys. This satisfies the P3 acceptance criterion "导入重复执行".

## 2. Bundle Layout

A bundle is a directory containing `manifest.json` plus zero or more JSONL files:

```
bundle/
  manifest.json     # P3ImportManifest; checksum + provenance
  sessions.jsonl    # one SessionRow per line; optional
  messages.jsonl    # one MessageRow per line; optional
  feedback.jsonl    # one FeedbackRow per line; optional
  shares.jsonl      # one ShareRow per line; optional
```

Files that don't exist are silently skipped. The manifest's `expectedRowCounts` is the source of
truth for the checksum — the importer parses every JSONL line and compares the parsed total
against the manifest.

## 3. Manifest Schema

`manifest.json` is a single JSON object:

| Field                | Type                  | Notes                                                                 |
| -------------------- | --------------------- | --------------------------------------------------------------------- |
| `version`            | integer               | Required. Must equal `1`. Future schema changes bump this.            |
| `generatedAt`        | string (ISO-8601)     | When the bundle was produced. Informational only.                      |
| `sourceDialect`      | string                | `"sqlite"` / `"mysql"` / `"postgres"`. Informational only.             |
| `expectedRowCounts`  | object: table → long  | Keys: `sessions`, `messages`, `feedback`, `shares`. Verified.         |
| `expectedTenantCounts` | object: tenant → long | Informational only; importer does not enforce tenant-level counts.  |

Example:

```json
{
  "version": 1,
  "generatedAt": "2026-07-25T10:00:00Z",
  "sourceDialect": "sqlite",
  "expectedRowCounts": {
    "sessions": 412,
    "messages": 3127,
    "feedback": 184,
    "shares": 53
  },
  "expectedTenantCounts": {
    "tenant-a": 280,
    "tenant-b": 132
  }
}
```

## 4. Row Schemas

All four row types are flat JSON objects with camelCase keys. Timestamps are ISO-8601 strings
(`2026-07-25T10:15:30Z` or `2026-07-25T10:15:30.123Z`). NULL timestamps may be omitted or sent
as JSON `null`.

### 4.1 SessionRow (ds_chat_session)

| Field          | Type             | Required | Notes                                                |
| -------------- | ---------------- | -------- | ---------------------------------------------------- |
| `id`           | string (≤64)     | yes      | Natural primary key; preserved verbatim.             |
| `tenantId`     | string (≤64)     | yes      | Tenant isolation key.                                |
| `userId`       | string (≤255)    | yes      | Owner identity.                                      |
| `connectionId` | string (≤255)    | yes      | ClickHouse connection id.                            |
| `title`        | string (≤255)    | nullable | Display title.                                       |
| `revision`     | integer ≥ 0      | default 0| Optimistic-lock counter.                             |
| `createdAt`    | ISO-8601         | yes      | Preserved on UPDATE; refreshed on INSERT.            |
| `updatedAt`    | ISO-8601         | yes      | Overwritten with the supplied value on every import. |

### 4.2 MessageRow (ds_chat_message)

| Field          | Type             | Required | Notes                                                              |
| -------------- | ---------------- | -------- | ------------------------------------------------------------------ |
| `id`           | string (≤64)     | yes      | Natural primary key (within `(tenantId, sessionId)`).              |
| `tenantId`     | string (≤64)     | yes      | Tenant isolation key.                                              |
| `sessionId`    | string (≤64)     | yes      | Must reference an existing SessionRow in the bundle (or DB).       |
| `userId`       | string (≤255)    | yes      | Author identity.                                                   |
| `role`         | string (≤32)     | yes      | `"user"` / `"assistant"` (Node baseline).                          |
| `partsJson`    | string (JSON)    | yes      | Raw JSON array of message parts. Round-trips byte-for-byte.        |
| `metadataJson` | string (JSON)    | nullable | Optional metadata object.                                          |
| `sequence`     | integer > 0      | yes      | Unique within `(tenantId, sessionId)`. Order on read.              |
| `createdAt`    | ISO-8601         | yes      | Preserved on UPDATE.                                               |
| `updatedAt`    | ISO-8601         | yes      | Overwritten with supplied value.                                   |

### 4.3 FeedbackRow (ds_feedback_event)

| Field                  | Type             | Required | Notes                                                              |
| ---------------------- | ---------------- | -------- | ------------------------------------------------------------------ |
| `id`                   | string (≤64)     | yes      | Stable ULID.                                                       |
| `tenantId`             | string (≤64)     | yes      | Tenant isolation key.                                              |
| `userId`               | string (≤255)    | yes      | Submitter identity.                                                |
| `source`               | string           | yes      | Must equal `"auto_explain_error"` (DB CHECK constraint).           |
| `sessionId`            | string (≤64)     | yes      | Must reference an existing SessionRow.                             |
| `messageId`            | string (≤255)    | yes      | Target message id.                                                 |
| `solved`               | boolean          | default false | Resolved status.                                              |
| `reasonCode`           | string (≤64)     | nullable | Required when `solved=false`; one of `wrong_diagnosis`, `too_vague`, `unsafe_fix`, `missing_context`, `other`. |
| `payloadJson`          | string (JSON)    | yes      | `{queryId, errorCode?, sql?}` shape.                               |
| `freeText`             | string (≤2000)   | nullable | Free-text feedback.                                                |
| `recoveryActionTaken`  | boolean          | default false | True if a recovery action was attempted.                      |
| `createdAt`            | ISO-8601         | yes      | Preserved on UPDATE.                                               |
| `updatedAt`            | ISO-8601         | yes      | Overwritten with supplied value.                                   |

### 4.4 ShareRow (ds_session_share)

| Field          | Type             | Required | Notes                                                              |
| -------------- | ---------------- | -------- | ------------------------------------------------------------------ |
| `id`           | string (≤64)     | yes      | Stable ULID.                                                       |
| `tenantId`     | string (≤64)     | yes      | Tenant isolation key.                                              |
| `sessionId`    | string (≤64)     | yes      | Must reference an existing SessionRow.                             |
| `ownerUserId`  | string (≤255)    | yes      | Session owner identity (from SessionShare JWT `iss`).              |
| `tokenHash`    | string (≤128)    | yes      | SHA-256 hex of the share JWT. **Not** the JWT itself.              |
| `expiresAt`    | ISO-8601         | yes      | Refreshed on UPDATE.                                               |
| `revokedAt`    | ISO-8601         | nullable | `null` means active. Refreshed on UPDATE.                          |
| `createdAt`    | ISO-8601         | yes      | Preserved on UPDATE.                                               |

> The JWT itself is **never** stored and **never** imported — only its SHA-256 hash. After
> import, any visitor presenting the original JWT will continue to resolve to the imported row,
> but issuing new shares for the same session requires a fresh call to `POST
> /api/ai/sessions/{id}/share`.

## 5. Import Semantics

### 5.1 Idempotency

Every table uses lookup-then-upsert:

| Table               | Lookup key                                                       | Behaviour on existing row                       |
| ------------------- | ---------------------------------------------------------------- | ----------------------------------------------- |
| `ds_chat_session`   | `(tenant_id, id)`                                                | UPDATE mutable columns; preserve `created_at`.  |
| `ds_chat_message`   | `(tenant_id, session_id, id)`                                    | UPDATE role/parts/metadata/sequence; preserve `created_at`. |
| `ds_feedback_event` | `(tenant_id, user_id, source, session_id, message_id)`           | UPDATE solved/reason/payload/free_text; preserve `created_at`. |
| `ds_session_share`  | `(tenant_id, token_hash)`                                        | UPDATE expires_at/revoked_at; preserve `created_at`. Does **not** flip `token_hash`. |

Re-importing the same bundle yields: 0 new inserts, N updates, identical row counts.

### 5.2 Order

Sessions → messages → feedback → shares. Each table's JSONL file is consumed fully before the
next starts. Within a file, line order is preserved. The importer does not re-sort.

Sessions are wrapped in a single JDBC transaction; subsequent tables are committed per-row so a
single bad row does not roll back the entire table. This is a deliberate "best-effort + report"
choice — strict atomicity per table is a future option (`--strict-rows`).

### 5.3 Dry-Run Mode

`--p3.import.dry-run=true` parses every JSONL line, validates required fields, counts expected
rows per table, but does **not** issue any SQL. The result summary reports the counts the
subsequent live run would apply. Always dry-run before applying to a production database.

### 5.4 Checksum Verification

After import, the importer counts rows per table and compares against
`manifest.expectedRowCounts`. Any mismatch sets `result.checksum.matches() = false` and the run
is marked unsuccessful (exit code 1). Common causes:

- Extra lines in a JSONL file not accounted for in the manifest.
- Rows that failed validation (reported as errors and skipped).
- Rows whose INSERT hit a CHECK / UNIQUE constraint.

### 5.5 Tenant Isolation

The importer does not enforce tenant-level row counts — the manifest's `expectedTenantCounts`
is informational. The DB schema's `tenant_id` columns continue to scope every query, so imported
rows for `tenant-a` remain invisible to `tenant-b` queries (verified by
`P3ImporterTest.crossTenant`).

## 6. CLI Usage

```bash
# Dry-run
java -jar target/datastoria-server-*.jar \
  --spring.profiles.active=local \
  --p3.import.path=./bundle \
  --p3.import.dry-run=true

# Apply
java -jar target/datastoria-server-*.jar \
  --spring.profiles.active=local \
  --p3.import.path=./bundle

echo $?  # 0=success, 1=row/checksum errors, 2=io/manifest errors
```

The runner (`P3ImportRunner`) is gated by `--p3.import.path` so it is inert during normal server
operation. When the property is set, Spring Boot runs the importer and exits; the web server is
not started.

## 7. Companion Node Exporter (deferred)

A Node-side exporter that reads the existing `chat_sessions` / `chat_messages` /
`feedback_events` tables via Knex and emits a bundle matching this spec is **deferred** to P3.7
in the frontend repo. The exporter needs:

1. `knex.select('*').from('chat_sessions')` → write `sessions.jsonl`, mapping column names to
   camelCase (`tenant_id` → `tenantId`, etc.).
2. Compute SHA-256 of any existing share JWTs and persist as `shares.jsonl`. The Node store
   historically kept only the JWT, so this step requires a one-time backfill that re-signs or
   re-derives the hash. **Recommendation:** drop historical share rows during cutover and let
   users re-issue shares via A09; share JWTs expire far in the future but are scoped to specific
   sessions, so churn is bounded.
3. Generate `manifest.json` with exact row counts (use `COUNT(*)` per table).

Until that exporter exists, the importer can be exercised against synthetic bundles (see
`P3ImporterTest` for the format) or against manual exports from a Node dev database.

## 8. Versioning

Manifest `version` is `1`. Any future change to row schemas (new column, removed column, type
change) MUST:

1. Bump `version` to N+1.
2. Add a migration note in this document.
3. Keep the importer backward-compatible with version N for at least one release (warn + accept
   rows missing the new field with sensible defaults).

Removing a column or changing a type requires a hard cutover — record an ADR.
