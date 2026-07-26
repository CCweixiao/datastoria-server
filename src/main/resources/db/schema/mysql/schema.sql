-- GENERATED FILE. DO NOT EDIT DIRECTLY.
-- Regenerate with: node scripts/generate-schema-snapshots.mjs
-- Deployment helper for a NEW MYSQL database at Flyway V15.
-- Application startup continues to use db/migration/mysql; this file is not auto-run.

-- Source: V1__identity_config_and_audit.sql
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

-- Source: V1__identity_config_and_audit.sql
CREATE INDEX idx_config_entry_scope
    ON ds_config_entry (tenant_id, scope_type, scope_id);

-- Source: V1__identity_config_and_audit.sql
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

-- Source: V1__identity_config_and_audit.sql
CREATE INDEX idx_audit_log_resource
    ON ds_audit_log (tenant_id, resource_type, resource_id);

-- Source: V1__identity_config_and_audit.sql
CREATE INDEX idx_audit_log_created_at
    ON ds_audit_log (created_at);

-- Source: V2__model_provider_and_secret.sql
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

-- Source: V2__model_provider_and_secret.sql
CREATE INDEX idx_model_provider_tenant
    ON ds_model_provider (tenant_id, enabled, deleted_at);

-- Source: V2__model_provider_and_secret.sql
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

-- Source: V2__model_provider_and_secret.sql
CREATE INDEX idx_secret_owner
    ON ds_secret (tenant_id, owner_user_id, secret_kind, deleted_at);

-- Source: V2__model_provider_and_secret.sql
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

-- Source: V2__model_provider_and_secret.sql
CREATE INDEX idx_model_tenant_provider
    ON ds_model (tenant_id, provider_id, enabled, deleted_at);

-- Source: V2__model_provider_and_secret.sql
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

-- Source: V3__agent_definition_and_revision.sql
CREATE TABLE ds_agent_definition (
    id                      varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id               varchar(64)  NOT NULL,
    agent_key               varchar(64)  NOT NULL,
    name                    varchar(255) NOT NULL,
    description             longtext     NULL,
    status                  varchar(32)  NOT NULL,
    published_revision_id   varchar(64)  NULL,
    revision                bigint       NOT NULL DEFAULT 0,
    created_by              varchar(255) NOT NULL,
    updated_by              varchar(255) NOT NULL,
    created_at              datetime(6)  NOT NULL,
    updated_at              datetime(6)  NOT NULL,
    deleted_at              datetime(6)  NULL,
    active_key              tinyint GENERATED ALWAYS AS
                            (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    CONSTRAINT chk_agent_status CHECK (status IN ('draft','published','disabled')),
    CONSTRAINT chk_agent_revision CHECK (revision >= 0),
    UNIQUE KEY uk_agent_active (tenant_id, agent_key, active_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V3__agent_definition_and_revision.sql
CREATE INDEX idx_agent_definition_tenant
    ON ds_agent_definition (tenant_id, status, deleted_at);

-- Source: V3__agent_definition_and_revision.sql
CREATE TABLE ds_agent_revision (
    id                      varchar(64)  NOT NULL PRIMARY KEY,
    agent_id                varchar(64)  NOT NULL,
    version                 int          NOT NULL,
    model_id                varchar(64)  NULL,
    system_prompt           longtext     NOT NULL,
    prompt_checksum         varchar(128) NOT NULL,
    runtime_config_json     json         NULL,
    tool_policy_json        json         NULL,
    skill_policy_json       json         NULL,
    created_by              varchar(255) NOT NULL,
    created_at              datetime(6)  NOT NULL,
    CONSTRAINT chk_revision_version CHECK (version >= 1),
    CONSTRAINT fk_revision_agent FOREIGN KEY (agent_id) REFERENCES ds_agent_definition(id),
    UNIQUE KEY uk_agent_version (agent_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V3__agent_definition_and_revision.sql
CREATE INDEX idx_agent_revision_agent
    ON ds_agent_revision (agent_id, created_at);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE TABLE ds_chat_session (
    id              varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id       varchar(64)  NOT NULL,
    user_id         varchar(255) NOT NULL,
    connection_id   varchar(255) NOT NULL,
    title           varchar(255) NULL,
    revision        bigint       NOT NULL DEFAULT 0,
    created_at      datetime(6)  NOT NULL,
    updated_at      datetime(6)  NOT NULL,
    CONSTRAINT chk_session_revision CHECK (revision >= 0),
    UNIQUE KEY uk_session_tenant_id (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V4__chat_session_message_feedback_share.sql
CREATE INDEX idx_chat_session_user_cursor
    ON ds_chat_session (tenant_id, user_id, updated_at, id);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE INDEX idx_chat_session_user_connection_cursor
    ON ds_chat_session (tenant_id, user_id, connection_id, updated_at, id);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE TABLE ds_chat_message (
    id              varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id       varchar(64)  NOT NULL,
    session_id      varchar(64)  NOT NULL,
    user_id         varchar(255) NOT NULL,
    role            varchar(32)  NOT NULL,
    parts_json      json         NOT NULL,
    metadata_json   json         NULL,
    sequence        bigint       NOT NULL,
    created_at      datetime(6)  NOT NULL,
    updated_at      datetime(6)  NOT NULL,
    CONSTRAINT chk_message_sequence CHECK (sequence > 0),
    CONSTRAINT fk_message_session FOREIGN KEY (tenant_id, session_id)
        REFERENCES ds_chat_session(tenant_id, id) ON DELETE CASCADE,
    UNIQUE KEY uk_message_session_sequence (tenant_id, session_id, sequence),
    UNIQUE KEY uk_message_session_id (tenant_id, session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V4__chat_session_message_feedback_share.sql
CREATE TABLE ds_feedback_event (
    id                      varchar(64)   NOT NULL PRIMARY KEY,
    tenant_id               varchar(64)   NOT NULL,
    user_id                 varchar(255)  NOT NULL,
    source                  varchar(32)   NOT NULL,
    session_id              varchar(64)   NOT NULL,
    message_id              varchar(255)  NOT NULL,
    solved                  boolean       NOT NULL,
    reason_code             varchar(64)   NULL,
    payload_json            json          NOT NULL,
    free_text               varchar(2000) NULL,
    recovery_action_taken   boolean       NOT NULL DEFAULT FALSE,
    created_at              datetime(6)   NOT NULL,
    updated_at              datetime(6)   NOT NULL,
    CONSTRAINT chk_feedback_source CHECK (source = 'auto_explain_error'),
    CONSTRAINT fk_feedback_session FOREIGN KEY (tenant_id, session_id)
        REFERENCES ds_chat_session(tenant_id, id) ON DELETE CASCADE,
    UNIQUE KEY uk_feedback_upsert (tenant_id, user_id, source, session_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V4__chat_session_message_feedback_share.sql
CREATE INDEX idx_feedback_event_tenant_source_updated
    ON ds_feedback_event (tenant_id, source, updated_at);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE TABLE ds_session_share (
    id              varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id       varchar(64)  NOT NULL,
    session_id      varchar(64)  NOT NULL,
    owner_user_id   varchar(255) NOT NULL,
    token_hash      varchar(128) NOT NULL,
    expires_at      datetime(6)  NOT NULL,
    revoked_at      datetime(6)  NULL,
    created_at      datetime(6)  NOT NULL,
    active_key      tinyint GENERATED ALWAYS AS
                    (CASE WHEN revoked_at IS NULL THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_share_session_active (tenant_id, session_id, active_key),
    UNIQUE KEY uk_share_token_hash (tenant_id, token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V4__chat_session_message_feedback_share.sql
CREATE INDEX idx_session_share_session_active
    ON ds_session_share (tenant_id, session_id, revoked_at, expires_at);

-- Source: V5__agent_run_and_checkpoint.sql
CREATE TABLE ds_agent_run (
    id                      varchar(64)   NOT NULL PRIMARY KEY,
    tenant_id               varchar(64)   NOT NULL,
    user_id                 varchar(255)  NOT NULL,
    session_id              varchar(64)   NOT NULL,
    message_id              varchar(255)  NULL,
    agent_revision_id       varchar(64)   NOT NULL,
    model_id                varchar(64)   NOT NULL,
    status                  varchar(32)   NOT NULL,
    idempotency_key         varchar(128)  NULL,
    request_id              varchar(128)  NULL,
    connection_id           varchar(255)  NULL,
    input_snapshot_json     json          NULL,
    usage_json              json          NULL,
    error_code              varchar(64)   NULL,
    safe_message            varchar(512)  NULL,
    revision                bigint        NOT NULL DEFAULT 0,
    started_at              datetime(6)   NULL,
    finished_at             datetime(6)   NULL,
    created_at              datetime(6)   NOT NULL,
    updated_at              datetime(6)   NOT NULL,
    CONSTRAINT chk_run_status CHECK (status IN (
        'queued','running','waiting_input','succeeded','failed','cancelled','expired')),
    CONSTRAINT chk_run_revision CHECK (revision >= 0),
    CONSTRAINT fk_run_session FOREIGN KEY (tenant_id, session_id)
        REFERENCES ds_chat_session(tenant_id, id) ON DELETE CASCADE,
    UNIQUE KEY uk_run_tenant_id (tenant_id, id),
    UNIQUE KEY uk_run_idempotency (tenant_id, user_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V5__agent_run_and_checkpoint.sql
CREATE INDEX idx_agent_run_user_cursor
    ON ds_agent_run (tenant_id, user_id, updated_at, id);

-- Source: V5__agent_run_and_checkpoint.sql
CREATE INDEX idx_agent_run_session
    ON ds_agent_run (tenant_id, session_id, created_at, id);

-- Source: V5__agent_run_and_checkpoint.sql
CREATE TABLE ds_agent_checkpoint (
    id              varchar(64)   NOT NULL PRIMARY KEY,
    tenant_id       varchar(64)   NOT NULL,
    run_id          varchar(64)   NOT NULL,
    sequence        bigint        NOT NULL,
    checkpoint_type varchar(32)   NOT NULL,
    state_json      json          NOT NULL,
    codec_version   varchar(32)   NOT NULL,
    checksum        varchar(64)   NOT NULL,
    created_at      datetime(6)   NOT NULL,
    updated_at      datetime(6)   NOT NULL,
    CONSTRAINT chk_checkpoint_sequence CHECK (sequence > 0),
    CONSTRAINT chk_checkpoint_type CHECK (checkpoint_type IN ('run_state','pending_action')),
    CONSTRAINT chk_checkpoint_checksum CHECK (checksum REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT fk_checkpoint_run FOREIGN KEY (tenant_id, run_id)
        REFERENCES ds_agent_run(tenant_id, id) ON DELETE CASCADE,
    UNIQUE KEY uk_checkpoint_run_sequence (tenant_id, run_id, sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V6__agent_event_replay.sql
CREATE TABLE ds_agent_event (
    id          VARCHAR(26) NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(26) NOT NULL,
    run_id      VARCHAR(26) NOT NULL,
    sequence    BIGINT NOT NULL,
    frame_text  LONGTEXT NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_agent_event_run FOREIGN KEY (tenant_id, run_id)
        REFERENCES ds_agent_run(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_agent_event_sequence UNIQUE (tenant_id, run_id, sequence),
    CONSTRAINT chk_agent_event_sequence CHECK (sequence > 0)
);

-- Source: V6__agent_event_replay.sql
CREATE INDEX idx_agent_event_replay
    ON ds_agent_event (tenant_id, run_id, sequence);

-- Source: V7__clickhouse_connection.sql
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

-- Source: V8__agent_skill.sql
CREATE TABLE ds_agent_skill (
    id              VARCHAR(255) NOT NULL,
    tenant_id       VARCHAR(128) NOT NULL,
    owner_user_id   VARCHAR(128) NOT NULL,
    content         LONGTEXT NOT NULL,
    state           ENUM('draft','published') NOT NULL,
    scope           ENUM('global','self') NOT NULL,
    version         VARCHAR(128),
    revision        BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    deleted_at      TIMESTAMP(6),
    PRIMARY KEY (tenant_id, id),
    KEY idx_agent_skill_visibility
        (tenant_id, owner_user_id, state, scope, deleted_at)
);

-- Source: V8__agent_skill.sql
CREATE TABLE ds_agent_skill_resource (
    tenant_id       VARCHAR(128) NOT NULL,
    skill_id        VARCHAR(255) NOT NULL,
    resource_path   VARCHAR(1024) NOT NULL,
    content         LONGTEXT NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, resource_path),
    CONSTRAINT fk_agent_skill_resource_skill
        FOREIGN KEY (tenant_id, skill_id) REFERENCES ds_agent_skill(tenant_id, id)
        ON DELETE CASCADE
);

-- Source: V9__user_state.sql
CREATE TABLE ds_user_state (
    tenant_id       VARCHAR(128) NOT NULL,
    user_id         VARCHAR(128) NOT NULL,
    namespace       VARCHAR(128) NOT NULL,
    state_key       VARCHAR(255) NOT NULL,
    value_json      JSON NOT NULL,
    revision        BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, user_id, namespace, state_key),
    KEY idx_user_state_namespace
        (tenant_id, user_id, namespace, updated_at)
);

-- Source: V10__rca_template.sql
CREATE TABLE ds_rca_template (
  id VARCHAR(36) PRIMARY KEY,
  template_key VARCHAR(191) NOT NULL UNIQUE,
  source_yaml LONGTEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  revision BIGINT NOT NULL DEFAULT 1,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

-- Source: V11__builtin_skill_bundle_metadata.sql
ALTER TABLE ds_agent_skill
    ADD COLUMN bundle_checksum CHAR(64),
    ADD COLUMN builtin BOOLEAN NOT NULL DEFAULT FALSE;

-- Source: V12__immutable_skill_revision.sql
ALTER TABLE ds_agent_skill
    ADD COLUMN published_revision BIGINT,
    ADD COLUMN draft_revision BIGINT;

-- Source: V12__immutable_skill_revision.sql
CREATE TABLE ds_skill_revision (
    tenant_id           VARCHAR(128) NOT NULL,
    skill_id            VARCHAR(255) NOT NULL,
    revision            BIGINT NOT NULL,
    version             VARCHAR(128),
    name                VARCHAR(255) NOT NULL,
    description         TEXT NOT NULL,
    summary             TEXT NOT NULL,
    skill_md            LONGTEXT NOT NULL,
    metadata_json       JSON NOT NULL,
    required_tools_json JSON NOT NULL,
    content_checksum    CHAR(64) NOT NULL,
    review_status       ENUM('pending','passed','failed','not_required') NOT NULL,
    created_by          VARCHAR(128) NOT NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, revision),
    CONSTRAINT fk_skill_revision_skill
        FOREIGN KEY (tenant_id, skill_id) REFERENCES ds_agent_skill(tenant_id, id)
        ON DELETE CASCADE
);

-- Source: V12__immutable_skill_revision.sql
CREATE TABLE ds_skill_resource (
    tenant_id       VARCHAR(128) NOT NULL,
    skill_id        VARCHAR(255) NOT NULL,
    skill_revision  BIGINT NOT NULL,
    resource_path   VARCHAR(512) NOT NULL,
    media_type      VARCHAR(255) NOT NULL,
    content         LONGBLOB NOT NULL,
    size_bytes      BIGINT NOT NULL,
    checksum        CHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, skill_revision, resource_path),
    CONSTRAINT fk_skill_resource_revision
        FOREIGN KEY (tenant_id, skill_id, skill_revision)
        REFERENCES ds_skill_revision(tenant_id, skill_id, revision) ON DELETE CASCADE
);

-- Source: V12__immutable_skill_revision.sql
CREATE TABLE ds_agent_run_skill (
    tenant_id        VARCHAR(128) NOT NULL,
    run_id           VARCHAR(26) NOT NULL,
    skill_id         VARCHAR(255) NOT NULL,
    skill_revision   BIGINT NOT NULL,
    content_checksum CHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id, run_id, skill_id),
    KEY idx_agent_run_skill_revision (tenant_id, skill_id, skill_revision),
    CONSTRAINT fk_agent_run_skill_run
        FOREIGN KEY (tenant_id, run_id) REFERENCES ds_agent_run(tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_agent_run_skill_revision
        FOREIGN KEY (tenant_id, skill_id, skill_revision)
        REFERENCES ds_skill_revision(tenant_id, skill_id, revision)
);

-- Source: V13__agent_pending_action.sql
CREATE TABLE ds_agent_pending_action (
    id                  varchar(64)   NOT NULL PRIMARY KEY,
    tenant_id           varchar(64)   NOT NULL,
    run_id              varchar(64)   NOT NULL,
    tool_call_id        varchar(128)  NOT NULL,
    action_type         varchar(32)   NOT NULL,
    request_json        json          NOT NULL,
    response_json       json          NULL,
    resolution_digest   varchar(64)   NULL,
    status              varchar(32)   NOT NULL,
    expires_at          datetime(6)   NOT NULL,
    resolved_by         varchar(255)  NULL,
    resolved_at         datetime(6)   NULL,
    revision            bigint        NOT NULL DEFAULT 0,
    created_at          datetime(6)   NOT NULL,
    updated_at          datetime(6)   NOT NULL,
    CONSTRAINT chk_pending_action_type CHECK (action_type IN ('question','approval')),
    CONSTRAINT chk_pending_action_status CHECK (status IN (
        'pending','responded','approved','denied','expired','cancelled')),
    CONSTRAINT chk_pending_action_digest CHECK (
        resolution_digest IS NULL OR resolution_digest REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_pending_action_revision CHECK (revision >= 0),
    CONSTRAINT fk_pending_action_run FOREIGN KEY (tenant_id, run_id)
        REFERENCES ds_agent_run(tenant_id, id) ON DELETE CASCADE,
    UNIQUE KEY uk_pending_action_tenant_id (tenant_id, id),
    UNIQUE KEY uk_pending_action_tool_call (tenant_id, run_id, tool_call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V13__agent_pending_action.sql
CREATE INDEX idx_pending_action_run_status
    ON ds_agent_pending_action (tenant_id, run_id, status, created_at, id);

-- Source: V13__agent_pending_action.sql
CREATE INDEX idx_pending_action_expiry
    ON ds_agent_pending_action (status, expires_at);

-- Source: V14__oauth_credential.sql
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
