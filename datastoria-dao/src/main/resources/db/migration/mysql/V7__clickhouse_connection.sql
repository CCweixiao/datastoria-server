CREATE TABLE ds_clickhouse_connection (
    id                   VARCHAR(26) NOT NULL PRIMARY KEY,
    tenant_id            VARCHAR(128) NOT NULL,
    owner_user_id        VARCHAR(128) NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    url                  TEXT NOT NULL,
    username             VARCHAR(255) NOT NULL,
    cluster_name         VARCHAR(255),
    password_cipher      LONGBLOB,
    password_nonce       VARBINARY(64),
    password_key_version VARCHAR(64),
    password_masked_hint VARCHAR(255),
    enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    revision             BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP(6) NOT NULL,
    updated_at           TIMESTAMP(6) NOT NULL,
    deleted_at           TIMESTAMP(6),
    active_name          TINYINT GENERATED ALWAYS AS
                         (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_clickhouse_connection_name
        (tenant_id, owner_user_id, name, active_name),
    UNIQUE KEY uk_clickhouse_connection_tenant_id (tenant_id, id),
    KEY idx_clickhouse_connection_owner
        (tenant_id, owner_user_id, enabled, deleted_at)
);
