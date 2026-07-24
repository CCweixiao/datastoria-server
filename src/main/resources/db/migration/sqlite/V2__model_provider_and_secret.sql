-- V2__model_provider_and_secret.sql (SQLite dialect)
--
-- Model providers, encrypted secrets, model catalog and per-user model
-- preferences. Secrets store only AES-GCM envelope cipher text + nonce; the
-- plaintext master key lives only in the application environment.
-- Soft-delete uniqueness uses (tenant_id, ..., deleted_at) so a single active
-- row (deleted_at IS NULL) coexists with archived copies.

CREATE TABLE ds_model_provider (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    provider_key    TEXT NOT NULL,
    display_name    TEXT NOT NULL,
    base_url        TEXT,
    auth_type       TEXT NOT NULL CHECK (auth_type IN ('api_key','oauth','none')),
    enabled         INTEGER NOT NULL CHECK (enabled IN (0,1)),
    config_json     TEXT CHECK (config_json IS NULL OR json_valid(config_json)),
    secret_id       TEXT,
    revision        INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_by      TEXT NOT NULL,
    updated_by      TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    deleted_at      TEXT,
    active_key      INTEGER GENERATED ALWAYS AS
                    (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    UNIQUE (tenant_id, provider_key, active_key),
    UNIQUE (tenant_id, id)
);

CREATE INDEX idx_model_provider_tenant
    ON ds_model_provider (tenant_id, enabled, deleted_at);

CREATE TABLE ds_secret (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    owner_user_id   TEXT,
    secret_kind     TEXT NOT NULL CHECK (secret_kind IN ('api_key','access_token','refresh_token')),
    cipher_text     BLOB NOT NULL,
    key_version     TEXT NOT NULL,
    nonce           BLOB NOT NULL,
    masked_hint     TEXT NOT NULL,
    expires_at      TEXT,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    deleted_at      TEXT,
    UNIQUE (tenant_id, id)
);

CREATE INDEX idx_secret_owner
    ON ds_secret (tenant_id, owner_user_id, secret_kind, deleted_at);

CREATE TABLE ds_model (
    id                          TEXT NOT NULL PRIMARY KEY,
    tenant_id                   TEXT NOT NULL,
    provider_id                 TEXT NOT NULL,
    model_key                   TEXT NOT NULL,
    display_name                TEXT NOT NULL,
    description                 TEXT,
    source                      TEXT NOT NULL CHECK (source IN ('system','discovered','custom')),
    enabled                     INTEGER NOT NULL CHECK (enabled IN (0,1)),
    is_free                     INTEGER NOT NULL CHECK (is_free IN (0,1)) DEFAULT 0,
    capabilities_json           TEXT CHECK (capabilities_json IS NULL OR json_valid(capabilities_json)),
    generation_defaults_json    TEXT CHECK (generation_defaults_json IS NULL OR json_valid(generation_defaults_json)),
    secret_id                   TEXT,
    revision                    INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at                  TEXT NOT NULL,
    updated_at                  TEXT NOT NULL,
    deleted_at                  TEXT,
    active_key                  INTEGER GENERATED ALWAYS AS
                                (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    FOREIGN KEY (tenant_id, provider_id) REFERENCES ds_model_provider(tenant_id, id),
    FOREIGN KEY (tenant_id, secret_id) REFERENCES ds_secret(tenant_id, id),
    UNIQUE (tenant_id, provider_id, model_key, active_key),
    UNIQUE (tenant_id, id)
);

CREATE INDEX idx_model_tenant_provider
    ON ds_model (tenant_id, provider_id, enabled, deleted_at);

CREATE TABLE ds_user_model_preference (
    id                  TEXT NOT NULL PRIMARY KEY,
    tenant_id           TEXT NOT NULL,
    user_id             TEXT NOT NULL,
    selected_model_id   TEXT NOT NULL,
    preference_json     TEXT CHECK (preference_json IS NULL OR json_valid(preference_json)),
    revision            INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL,
    FOREIGN KEY (tenant_id, selected_model_id) REFERENCES ds_model(tenant_id, id),
    UNIQUE (tenant_id, user_id)
);
