CREATE TABLE ds_oauth_credential (
    id              VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    user_id         VARCHAR(320) NOT NULL,
    provider_key    VARCHAR(32) NOT NULL,
    secret_id       VARCHAR(64) NOT NULL,
    token_type      VARCHAR(64) NULL,
    scope           VARCHAR(1024) NULL,
    expires_at      DATETIME(6) NULL,
    revision        BIGINT NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_oauth_provider CHECK (provider_key IN ('codex','github')),
    CONSTRAINT chk_oauth_revision CHECK (revision >= 0),
    CONSTRAINT fk_oauth_secret
        FOREIGN KEY (tenant_id, secret_id) REFERENCES ds_secret(tenant_id, id),
    UNIQUE KEY uk_oauth_owner_provider (tenant_id, user_id, provider_key),
    UNIQUE KEY uk_oauth_tenant_id (tenant_id, id),
    KEY idx_oauth_credential_owner (tenant_id, user_id, provider_key)
) ENGINE=InnoDB;
