-- V1__identity_config_and_audit.sql (MySQL dialect)
--
-- Layered configuration entries (system < tenant < user) and the append-only
-- audit log. Mirrors V1 SQLite with MySQL types: varchar, datetime(6), bigint
-- auto_increment for the audit sequence.

CREATE TABLE ds_config_entry (
    id              varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id       varchar(64)  NOT NULL,
    scope_type      varchar(32)  NOT NULL,
    scope_id        varchar(255) NOT NULL,
    config_key      varchar(128) NOT NULL,
    value_json      json         NOT NULL,
    schema_version  varchar(32)  NOT NULL DEFAULT '1',
    revision        bigint       NOT NULL DEFAULT 0,
    created_at      datetime(6)  NOT NULL,
    updated_at      datetime(6)  NOT NULL,
    deleted_at      datetime(6)  NULL,
    CONSTRAINT chk_config_scope CHECK (scope_type IN ('system','tenant','user')),
    CONSTRAINT chk_config_revision CHECK (revision >= 0),
    UNIQUE KEY uk_config_scope (tenant_id, scope_type, scope_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_config_entry_scope
    ON ds_config_entry (tenant_id, scope_type, scope_id);

CREATE TABLE ds_audit_log (
    id              bigint       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id       varchar(64)  NOT NULL,
    actor           varchar(255) NULL,
    action          varchar(64)  NOT NULL,
    resource_type   varchar(64)  NOT NULL,
    resource_id     varchar(64)  NULL,
    request_id      varchar(64)  NULL,
    safe_diff       longtext     NULL,
    result          varchar(32)  NOT NULL,
    created_at      datetime(6)  NOT NULL,
    CONSTRAINT chk_audit_result CHECK (result IN ('success','failure'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_log_resource
    ON ds_audit_log (tenant_id, resource_type, resource_id);
CREATE INDEX idx_audit_log_created_at
    ON ds_audit_log (created_at);
