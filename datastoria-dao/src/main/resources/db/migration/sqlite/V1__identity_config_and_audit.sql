-- V1__identity_config_and_audit.sql (SQLite dialect)
--
-- Layered configuration entries (system < tenant < user) and the append-only
-- audit log. These are the foundation tables shared by all later vertical
-- slices. Identifiers are application-generated ULIDs (varchar(64)); only the
-- audit log uses an auto-increment sequence number because it is never an
-- external resource id.

CREATE TABLE ds_config_entry (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    scope_type      TEXT NOT NULL CHECK (scope_type IN ('system','tenant','user')),
    scope_id        TEXT NOT NULL,
    config_key      TEXT NOT NULL,
    value_json      TEXT NOT NULL,
    schema_version  TEXT NOT NULL DEFAULT '1',
    revision        INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    deleted_at      TEXT,
    UNIQUE (tenant_id, scope_type, scope_id, config_key)
);

CREATE INDEX idx_config_entry_scope
    ON ds_config_entry (tenant_id, scope_type, scope_id);

CREATE TABLE ds_audit_log (
    id              INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    tenant_id       TEXT NOT NULL,
    actor           TEXT,
    action          TEXT NOT NULL,
    resource_type   TEXT NOT NULL,
    resource_id     TEXT,
    request_id      TEXT,
    safe_diff       TEXT,
    result          TEXT NOT NULL CHECK (result IN ('success','failure')),
    created_at      TEXT NOT NULL
);

CREATE INDEX idx_audit_log_resource
    ON ds_audit_log (tenant_id, resource_type, resource_id);
CREATE INDEX idx_audit_log_created_at
    ON ds_audit_log (created_at);
