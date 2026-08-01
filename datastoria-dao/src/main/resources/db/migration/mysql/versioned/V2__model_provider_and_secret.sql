-- V2__model_provider_and_secret.sql (MySQL dialect)
--
-- Model providers, encrypted secrets, model catalog and per-user model
-- preferences using MySQL 5.7 types: varchar, boolean, json,
-- datetime(6), mediumblob, varbinary.

CREATE TABLE ds_model_provider (
    id              varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id       varchar(64)  NOT NULL,
    provider_key    varchar(64)  NOT NULL,
    display_name    varchar(128) NOT NULL,
    base_url        varchar(1024) NULL,
    auth_type       varchar(32)  NOT NULL,
    enabled         boolean      NOT NULL,
    config_json     json         NULL,
    secret_id       varchar(64)  NULL,
    revision        bigint       NOT NULL DEFAULT 0,
    created_by      varchar(255) NOT NULL,
    updated_by      varchar(255) NOT NULL,
    created_at      datetime(6)  NOT NULL,
    updated_at      datetime(6)  NOT NULL,
    deleted_at      datetime(6)  NULL,
    active_key      tinyint GENERATED ALWAYS AS
                    (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    CONSTRAINT chk_provider_auth CHECK (auth_type IN ('api_key','oauth','none')),
    CONSTRAINT chk_provider_revision CHECK (revision >= 0),
    UNIQUE KEY uk_provider_active (tenant_id, provider_key, active_key),
    UNIQUE KEY uk_provider_tenant_id (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_model_provider_tenant
    ON ds_model_provider (tenant_id, enabled, deleted_at);

CREATE TABLE ds_secret (
    id              varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id       varchar(64)  NOT NULL,
    owner_user_id   varchar(255) NULL,
    secret_kind     varchar(32)  NOT NULL,
    cipher_text     mediumblob   NOT NULL,
    key_version     varchar(64)  NOT NULL,
    nonce           varbinary(32) NOT NULL,
    masked_hint     varchar(32)  NOT NULL,
    expires_at      datetime(6)  NULL,
    created_at      datetime(6)  NOT NULL,
    updated_at      datetime(6)  NOT NULL,
    deleted_at      datetime(6)  NULL,
    CONSTRAINT chk_secret_kind CHECK (secret_kind IN ('api_key','access_token','refresh_token')),
    UNIQUE KEY uk_secret_tenant_id (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_secret_owner
    ON ds_secret (tenant_id, owner_user_id, secret_kind, deleted_at);

CREATE TABLE ds_model (
    id                          varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id                   varchar(64)  NOT NULL,
    provider_id                 varchar(64)  NOT NULL,
    model_key                   varchar(255) NOT NULL,
    display_name                varchar(255) NOT NULL,
    description                 longtext     NULL,
    source                      varchar(32)  NOT NULL,
    enabled                     boolean      NOT NULL,
    is_free                     boolean      NOT NULL DEFAULT FALSE,
    capabilities_json           json         NULL,
    generation_defaults_json    json         NULL,
    secret_id                   varchar(64)  NULL,
    revision                    bigint       NOT NULL DEFAULT 0,
    created_at                  datetime(6)  NOT NULL,
    updated_at                  datetime(6)  NOT NULL,
    deleted_at                  datetime(6)  NULL,
    active_key                  tinyint GENERATED ALWAYS AS
                                (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    CONSTRAINT chk_model_source CHECK (source IN ('system','discovered','custom')),
    CONSTRAINT chk_model_revision CHECK (revision >= 0),
    CONSTRAINT fk_model_provider FOREIGN KEY (tenant_id, provider_id)
        REFERENCES ds_model_provider(tenant_id, id),
    CONSTRAINT fk_model_secret FOREIGN KEY (tenant_id, secret_id)
        REFERENCES ds_secret(tenant_id, id),
    UNIQUE KEY uk_model_active (tenant_id, provider_id, model_key, active_key),
    UNIQUE KEY uk_model_tenant_id (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_model_tenant_provider
    ON ds_model (tenant_id, provider_id, enabled, deleted_at);

CREATE TABLE ds_user_model_preference (
    id                  varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id           varchar(64)  NOT NULL,
    user_id             varchar(255) NOT NULL,
    selected_model_id   varchar(64)  NOT NULL,
    preference_json     json         NULL,
    revision            bigint       NOT NULL DEFAULT 0,
    created_at          datetime(6)  NOT NULL,
    updated_at          datetime(6)  NOT NULL,
    CONSTRAINT chk_pref_revision CHECK (revision >= 0),
    CONSTRAINT fk_pref_model FOREIGN KEY (tenant_id, selected_model_id)
        REFERENCES ds_model(tenant_id, id),
    UNIQUE KEY uk_user_model_pref (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
