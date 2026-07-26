# JDBC → MyBatis-Plus Migration

This document records the migration of the relational data-access layer from
`JdbcClient`/`JdbcTemplate` to **MyBatis-Plus** (`mybatis-plus-spring-boot3-starter` 3.5.12).
SQLite and MySQL now share **one** Entity / Mapper / XML / Repository-adapter set; the active
datasource is selected purely by Spring profile. PostgreSQL support was removed.

## 1. Single-mapper design

```
io.datastoria.server
├── repository/                      # domain Repository interfaces — UNCHANGED (contracts preserved)
│   ├── ModelRepository, ChatSessionRepository, AgentRunRepository, …
│   ├── ModelProviderSecretRepository   # NEW: privileged credential-rotation port
│   └── SessionListCursor               # RELOCATED from repository.jdbc (was the only jdbc-package
│                                       #   type a domain interface depended on)
└── persistence/                     # NEW MyBatis-Plus layer
    ├── entity/        # mutable DB POJOs (one per table); @TableName / @TableId(IdType.INPUT|AUTO)
    ├── mapper/        # @MapperScan target; extends BaseMapper<Entity> + custom methods
    ├── repository/    # @Repository adapters implementing the domain interfaces
    └── typehandler/   # InstantTypeHandler (@MappedTypes(Instant.class))
resources/mapper/*.xml               # ONE XML namespace per mapper, loaded by both profiles
```

**Why one set works for both dialects**

- `application.yaml` carries the shared `mybatis-plus` config (mapper-locations,
  type-handlers-package, `map-underscore-to-camel-case`). Per-profile yaml only swaps the
  datasource (Flyway location + JDBC driver). No dynamic-datasource framework is introduced.
- `InstantTypeHandler` serialises every `Instant` to a millisecond-truncated ISO-8601 string and
  parses both ISO (SQLite TEXT) and `datetime(6)` (MySQL) forms — so TEXT lexicographic ordering and
  the opaque keyset cursor (`SessionListCursor`) stay correct on both databases.
- Boolean columns rely on MyBatis' default `BooleanTypeHandler` (`setBoolean`/`getBoolean`), which
  works on SQLite INTEGER(0/1) and MySQL TINYINT/BOOLEAN alike.
- JSON columns stay open strings (`setString`/`getString`), never dropping unknown fields.
- BLOB columns (`ds_secret`, `ds_clickhouse_connection`) declare an explicit
  `ByteArrayTypeHandler` in their result maps — SQLite JDBC does not implement `getBlob()`.

**Simple CRUD** (single-table reads, counts) uses `BaseMapper` + `LambdaQueryWrapper`.
**Complex SQL** (keyset pagination, revision-guarded CAS, joins, upserts, the run state machine,
the pending-action resolution CAS, the skill bundle, the importer) uses custom mapper XML.
Generated columns (`active_key`, `active_name`) are intentionally absent from the entities so they
never appear in INSERT/UPDATE column lists.

## 2. Layering rules (how writes are mapped)

| Concern | Mechanism |
|---|---|
| ULID / string primary keys | `@TableId(type = IdType.INPUT)` — app assigns |
| `ds_audit_log` auto-increment | `@TableId(type = IdType.AUTO)` + `useGeneratedKeys keyProperty="id"` |
| Optimistic lock / revision CAS | explicit `UPDATE … WHERE revision = #{expected} … revision = revision + 1` in XML |
| Upsert | dialect-neutral **UPDATE-then-INSERT**; insert race retries the UPDATE (catches `RuntimeException` for the SQLite `SQLITE_CONSTRAINT` paths, `DataIntegrityViolationException` for the race) |
| Soft delete / tenant scope | explicit `deleted_at IS NULL` / `tenant_id` predicates — no global TenantLine/LogicDelete plugins |
| Keyset pagination | `(updated_at DESC, id DESC)`, `LIMIT :limit+1`, opaque cursor; dynamic `<if>` guards so NULL is never bound into a comparison |
| Blocking discipline | adapters are blocking; WebFlux services still offload every call via `.subscribeOn(jdbcScheduler)` — Netty event loop is never blocked |

**No forbidden dialect SQL.** `MyBatisMigrationBoundaryTest` fails the build if any mapper XML
contains `ON CONFLICT`, `INSERT OR REPLACE`, `ON DUPLICATE KEY UPDATE`, `RETURNING`, or
`LAST_INSERT_ID`.

## 3. Migration / deletion checklist

**Added**
- `mybatis-plus-spring-boot3-starter` 3.5.12 (fixed version; raw `mybatis-spring-boot-starter` NOT added).
- `persistence/{entity,mapper,repository,typehandler}` packages and `resources/mapper/*.xml`.
- `MyBatisPlusConfig` (`@MapperScan`), `InstantTypeHandler`, 19 entities, 16 mappers + XML, 16 adapters.
- `ModelProviderSecretRepository` (new port so `ProviderService` no longer depends on a concrete repo).
- `MyBatisMigrationBoundaryTest` (architecture gate).

**Replaced**
- `RcaTemplateBootstrap`, `RcaTemplateCatalog`, `P3Importer`, `P3ImportRunner` → MyBatis mappers
  (`RcaTemplateMapper`, `P3ImportMapper`). P3Importer keeps its per-table `TransactionTemplate`
  rollback semantics; timestamps are bound as raw ISO strings to preserve the prior behaviour exactly.
- `ProviderService` now injects `ModelProviderSecretRepository` instead of `JdbcModelProviderRepository`.

**Relocated**
- `SessionListCursor` `repository.jdbc` → `repository` (and its test moved out of the `jdbc` test pkg).

**Deleted**
- All 21 `Jdbc*Repository`, `SqlTimestamps`, the `repository/jdbc` package.
- PostgreSQL artifacts: `flyway-database-postgresql`, `postgresql` driver, `testcontainers/postgresql`,
  the `postgres-it` Maven profile, `application-postgres(-it).yaml`, `PostgresRepositoryIT`,
  `db/migration/postgresql/`, `scripts/generate-postgres-migrations.mjs`, the CI `postgres-contract`
  job, and the `@Profile("postgres")` datasource guard + profile annotations.

**Kept (intentionally)**
- `spring-boot-starter-jdbc` — still provides the single `DataSource`, the
  `DataSourceTransactionManager` (backs every `TransactionTemplate` / `@Transactional`), and
  `JdbcClient` used by test helpers. MyBatis-Plus reuses the same `DataSource`; no R2DBC, no H2.

## 4. Verification

Java (JDK 17):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw spotless:check          # formatting
./mvnw test                    # 417 tests, 0 failures (SQLite: full migration + repository + API)
./mvnw -Pmysql-it verify       # MySQL Testcontainers contract (requires Docker)
```

Frontend (`frontend/`): `npm run format:check`, `npm run typecheck`, `npm run lint`, `npx vitest run`
(291 tests), `npm run build` — all green; no frontend/API contract was modified.

Local-profile runtime smoke (SQLite file):

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
curl localhost:8080/actuator/health                       # {"status":"UP", db: SQLite}
curl localhost:8080/api/admin/ai/providers                # 200
curl localhost:8080/api/admin/ai/models                   # 200
curl localhost:8080/api/ai/chat/sessions                  # 200
curl localhost:8080/api/connections                       # 200
```

## 5. Not run on this machine (and why)

- **`./mvnw -Pmysql-it verify`** — the MySQL Testcontainers contract requires Docker, which is not
  available in this environment. It is preserved and run in CI (`mysql-contract` job); the same
  `RelationalRepositoryContractIT` runs against the migrated mappers, and `SchemaParityTest`
  validates the SQLite↔MySQL schema equivalence under Docker.
- **Agent SSE end-to-end** — exercising a live agent run requires a configured LLM provider
  credential. The SSE path (`AgentRunController` + `AgentEventReplayService` + `RunLifecycleRecorder`)
  is covered by the green `AgentRunControllerTest`, replay/HITL, and run-lifecycle suites on SQLite.
