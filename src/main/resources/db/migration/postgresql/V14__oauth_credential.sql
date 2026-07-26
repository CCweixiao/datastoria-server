CREATE TABLE ds_oauth_credential (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    provider_key    TEXT NOT NULL CHECK (provider_key IN ('codex','github')),
    secret_id       TEXT NOT NULL,
    token_type      TEXT,
    scope           TEXT,
    expires_at      TEXT,
    revision        BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    FOREIGN KEY (tenant_id, secret_id) REFERENCES ds_secret(tenant_id, id),
    UNIQUE (tenant_id, user_id, provider_key),
    UNIQUE (tenant_id, id)
);

CREATE INDEX idx_oauth_credential_owner
    ON ds_oauth_credential (tenant_id, user_id, provider_key);
