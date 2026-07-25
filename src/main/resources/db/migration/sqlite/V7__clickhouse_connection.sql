CREATE TABLE ds_clickhouse_connection (
    id                  TEXT NOT NULL PRIMARY KEY,
    tenant_id           TEXT NOT NULL,
    owner_user_id       TEXT NOT NULL,
    name                TEXT NOT NULL,
    url                 TEXT NOT NULL,
    username            TEXT NOT NULL,
    cluster_name        TEXT,
    password_cipher     BLOB,
    password_nonce      BLOB,
    password_key_version TEXT,
    password_masked_hint TEXT,
    enabled             INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1)),
    revision            INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL,
    deleted_at          TEXT,
    active_name         INTEGER GENERATED ALWAYS AS
                        (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    UNIQUE (tenant_id, owner_user_id, name, active_name),
    UNIQUE (tenant_id, id)
);

CREATE INDEX idx_clickhouse_connection_owner
    ON ds_clickhouse_connection (tenant_id, owner_user_id, enabled, deleted_at);
