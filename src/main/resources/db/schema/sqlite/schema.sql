-- GENERATED FILE. DO NOT EDIT DIRECTLY.
-- Regenerate with: node scripts/generate-schema-snapshots.mjs
-- Deployment helper for a NEW SQLITE database at Flyway V15.
-- Application startup continues to use db/migration/sqlite; this file is not auto-run.

-- Source: V1__identity_config_and_audit.sql
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

-- Source: V1__identity_config_and_audit.sql
CREATE INDEX idx_config_entry_scope
    ON ds_config_entry (tenant_id, scope_type, scope_id);

-- Source: V1__identity_config_and_audit.sql
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

-- Source: V1__identity_config_and_audit.sql
CREATE INDEX idx_audit_log_resource
    ON ds_audit_log (tenant_id, resource_type, resource_id);

-- Source: V1__identity_config_and_audit.sql
CREATE INDEX idx_audit_log_created_at
    ON ds_audit_log (created_at);

-- Source: V2__model_provider_and_secret.sql
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

-- Source: V2__model_provider_and_secret.sql
CREATE INDEX idx_model_provider_tenant
    ON ds_model_provider (tenant_id, enabled, deleted_at);

-- Source: V2__model_provider_and_secret.sql
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

-- Source: V2__model_provider_and_secret.sql
CREATE INDEX idx_secret_owner
    ON ds_secret (tenant_id, owner_user_id, secret_kind, deleted_at);

-- Source: V2__model_provider_and_secret.sql
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

-- Source: V2__model_provider_and_secret.sql
CREATE INDEX idx_model_tenant_provider
    ON ds_model (tenant_id, provider_id, enabled, deleted_at);

-- Source: V2__model_provider_and_secret.sql
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

-- Source: V3__agent_definition_and_revision.sql
CREATE TABLE ds_agent_definition (
    id                      TEXT NOT NULL PRIMARY KEY,
    tenant_id               TEXT NOT NULL,
    agent_key               TEXT NOT NULL,
    name                    TEXT NOT NULL,
    description             TEXT,
    status                  TEXT NOT NULL CHECK (status IN ('draft','published','disabled')),
    published_revision_id   TEXT,
    revision                INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_by              TEXT NOT NULL,
    updated_by              TEXT NOT NULL,
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL,
    deleted_at              TEXT,
    active_key              INTEGER GENERATED ALWAYS AS
                            (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    UNIQUE (tenant_id, agent_key, active_key)
);

-- Source: V3__agent_definition_and_revision.sql
CREATE INDEX idx_agent_definition_tenant
    ON ds_agent_definition (tenant_id, status, deleted_at);

-- Source: V3__agent_definition_and_revision.sql
CREATE TABLE ds_agent_revision (
    id                      TEXT NOT NULL PRIMARY KEY,
    agent_id                TEXT NOT NULL,
    version                 INTEGER NOT NULL CHECK (version >= 1),
    model_id                TEXT,
    system_prompt           TEXT NOT NULL,
    prompt_checksum         TEXT NOT NULL,
    runtime_config_json     TEXT CHECK (runtime_config_json IS NULL OR json_valid(runtime_config_json)),
    tool_policy_json        TEXT CHECK (tool_policy_json IS NULL OR json_valid(tool_policy_json)),
    skill_policy_json       TEXT CHECK (skill_policy_json IS NULL OR json_valid(skill_policy_json)),
    created_by              TEXT NOT NULL,
    created_at              TEXT NOT NULL,
    FOREIGN KEY (agent_id) REFERENCES ds_agent_definition(id),
    UNIQUE (agent_id, version)
);

-- Source: V3__agent_definition_and_revision.sql
CREATE INDEX idx_agent_revision_agent
    ON ds_agent_revision (agent_id, created_at);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE TABLE ds_chat_session (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    connection_id   TEXT NOT NULL,
    title           TEXT,
    revision        INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    UNIQUE (tenant_id, id)
);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE INDEX idx_chat_session_user_cursor
    ON ds_chat_session (tenant_id, user_id, updated_at, id);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE INDEX idx_chat_session_user_connection_cursor
    ON ds_chat_session (tenant_id, user_id, connection_id, updated_at, id);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE TABLE ds_chat_message (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    session_id      TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    role            TEXT NOT NULL,
    parts_json      TEXT NOT NULL CHECK (json_valid(parts_json)),
    metadata_json   TEXT CHECK (metadata_json IS NULL OR json_valid(metadata_json)),
    sequence        INTEGER NOT NULL CHECK (sequence > 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    FOREIGN KEY (tenant_id, session_id) REFERENCES ds_chat_session(tenant_id, id)
        ON DELETE CASCADE,
    UNIQUE (tenant_id, session_id, sequence),
    UNIQUE (tenant_id, session_id, id)
);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE TABLE ds_feedback_event (
    id                      TEXT NOT NULL PRIMARY KEY,
    tenant_id               TEXT NOT NULL,
    user_id                 TEXT NOT NULL,
    source                  TEXT NOT NULL CHECK (source = 'auto_explain_error'),
    session_id              TEXT NOT NULL,
    message_id              TEXT NOT NULL,
    solved                  INTEGER NOT NULL CHECK (solved IN (0,1)),
    reason_code             TEXT,
    payload_json            TEXT NOT NULL CHECK (json_valid(payload_json)),
    free_text               TEXT,
    recovery_action_taken   INTEGER NOT NULL CHECK (recovery_action_taken IN (0,1)) DEFAULT 0,
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL,
    FOREIGN KEY (tenant_id, session_id) REFERENCES ds_chat_session(tenant_id, id)
        ON DELETE CASCADE,
    UNIQUE (tenant_id, user_id, source, session_id, message_id)
);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE INDEX idx_feedback_event_tenant_source_updated
    ON ds_feedback_event (tenant_id, source, updated_at);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE TABLE ds_session_share (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    session_id      TEXT NOT NULL,
    owner_user_id   TEXT NOT NULL,
    token_hash      TEXT NOT NULL,
    expires_at      TEXT NOT NULL,
    revoked_at      TEXT,
    created_at      TEXT NOT NULL,
    active_key      INTEGER GENERATED ALWAYS AS
                    (CASE WHEN revoked_at IS NULL THEN 1 ELSE NULL END) STORED,
    UNIQUE (tenant_id, session_id, active_key),
    UNIQUE (tenant_id, token_hash)
);

-- Source: V4__chat_session_message_feedback_share.sql
CREATE INDEX idx_session_share_session_active
    ON ds_session_share (tenant_id, session_id, revoked_at, expires_at);

-- Source: V5__agent_run_and_checkpoint.sql
CREATE TABLE ds_agent_run (
    id                      TEXT NOT NULL PRIMARY KEY,
    tenant_id               TEXT NOT NULL,
    user_id                 TEXT NOT NULL,
    session_id              TEXT NOT NULL,
    message_id              TEXT,
    agent_revision_id       TEXT NOT NULL,
    model_id                TEXT NOT NULL,
    status                  TEXT NOT NULL CHECK (status IN (
                                'queued','running','waiting_input',
                                'succeeded','failed','cancelled','expired')),
    idempotency_key         TEXT,
    request_id              TEXT,
    connection_id           TEXT,
    input_snapshot_json     TEXT CHECK (input_snapshot_json IS NULL OR json_valid(input_snapshot_json)),
    usage_json              TEXT CHECK (usage_json IS NULL OR json_valid(usage_json)),
    error_code              TEXT,
    safe_message            TEXT,
    revision                INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    started_at              TEXT,
    finished_at             TEXT,
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL,
    FOREIGN KEY (tenant_id, session_id) REFERENCES ds_chat_session(tenant_id, id)
        ON DELETE CASCADE,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, user_id, idempotency_key)
);

-- Source: V5__agent_run_and_checkpoint.sql
CREATE INDEX idx_agent_run_user_cursor
    ON ds_agent_run (tenant_id, user_id, updated_at, id);

-- Source: V5__agent_run_and_checkpoint.sql
CREATE INDEX idx_agent_run_session
    ON ds_agent_run (tenant_id, session_id, created_at, id);

-- Source: V5__agent_run_and_checkpoint.sql
CREATE TABLE ds_agent_checkpoint (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    run_id          TEXT NOT NULL,
    sequence        INTEGER NOT NULL CHECK (sequence > 0),
    checkpoint_type TEXT NOT NULL CHECK (checkpoint_type IN ('run_state','pending_action')),
    state_json      TEXT NOT NULL CHECK (json_valid(state_json)),
    codec_version   TEXT NOT NULL,
    checksum        TEXT NOT NULL CHECK (length(checksum) = 64 AND checksum NOT GLOB '*[^0-9a-f]*'),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    FOREIGN KEY (tenant_id, run_id) REFERENCES ds_agent_run(tenant_id, id)
        ON DELETE CASCADE,
    UNIQUE (tenant_id, run_id, sequence)
);

-- Source: V6__agent_event_replay.sql
CREATE TABLE ds_agent_event (
    id          TEXT NOT NULL PRIMARY KEY,
    tenant_id   TEXT NOT NULL,
    run_id      TEXT NOT NULL,
    sequence    INTEGER NOT NULL CHECK (sequence > 0),
    frame_text  TEXT NOT NULL,
    created_at  TEXT NOT NULL,
    FOREIGN KEY (tenant_id, run_id) REFERENCES ds_agent_run(tenant_id, id) ON DELETE CASCADE,
    UNIQUE (tenant_id, run_id, sequence)
);

-- Source: V6__agent_event_replay.sql
CREATE INDEX idx_agent_event_replay
    ON ds_agent_event (tenant_id, run_id, sequence);

-- Source: V7__clickhouse_connection.sql
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

-- Source: V7__clickhouse_connection.sql
CREATE INDEX idx_clickhouse_connection_owner
    ON ds_clickhouse_connection (tenant_id, owner_user_id, enabled, deleted_at);

-- Source: V8__agent_skill.sql
CREATE TABLE ds_agent_skill (
    id              TEXT NOT NULL,
    tenant_id       TEXT NOT NULL,
    owner_user_id   TEXT NOT NULL,
    content         TEXT NOT NULL,
    state           TEXT NOT NULL CHECK (state IN ('draft','published')),
    scope           TEXT NOT NULL CHECK (scope IN ('global','self')),
    version         TEXT,
    revision        INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    deleted_at      TEXT,
    PRIMARY KEY (tenant_id, id)
);

-- Source: V8__agent_skill.sql
CREATE INDEX idx_agent_skill_visibility
    ON ds_agent_skill (tenant_id, owner_user_id, state, scope, deleted_at);

-- Source: V8__agent_skill.sql
CREATE TABLE ds_agent_skill_resource (
    tenant_id       TEXT NOT NULL,
    skill_id        TEXT NOT NULL,
    resource_path   TEXT NOT NULL,
    content         TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, resource_path),
    FOREIGN KEY (tenant_id, skill_id) REFERENCES ds_agent_skill(tenant_id, id)
        ON DELETE CASCADE
);

-- Source: V9__user_state.sql
CREATE TABLE ds_user_state (
    tenant_id       TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    namespace       TEXT NOT NULL,
    state_key       TEXT NOT NULL,
    value_json      TEXT NOT NULL CHECK (json_valid(value_json)),
    revision        INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    PRIMARY KEY (tenant_id, user_id, namespace, state_key)
);

-- Source: V9__user_state.sql
CREATE INDEX idx_user_state_namespace
    ON ds_user_state (tenant_id, user_id, namespace, updated_at);

-- Source: V10__rca_template.sql
CREATE TABLE ds_rca_template (
  id TEXT PRIMARY KEY,
  template_key TEXT NOT NULL UNIQUE,
  source_yaml TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  revision INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

-- Source: V11__builtin_skill_bundle_metadata.sql
ALTER TABLE ds_agent_skill
    ADD COLUMN bundle_checksum TEXT;

-- Source: V11__builtin_skill_bundle_metadata.sql
ALTER TABLE ds_agent_skill
    ADD COLUMN builtin INTEGER NOT NULL DEFAULT 0 CHECK (builtin IN (0, 1));

-- Source: V12__immutable_skill_revision.sql
ALTER TABLE ds_agent_skill
    ADD COLUMN published_revision INTEGER;

-- Source: V12__immutable_skill_revision.sql
ALTER TABLE ds_agent_skill
    ADD COLUMN draft_revision INTEGER;

-- Source: V12__immutable_skill_revision.sql
CREATE TABLE ds_skill_revision (
    tenant_id           TEXT NOT NULL,
    skill_id            TEXT NOT NULL,
    revision            INTEGER NOT NULL CHECK (revision >= 0),
    version             TEXT,
    name                TEXT NOT NULL,
    description         TEXT NOT NULL,
    summary             TEXT NOT NULL,
    skill_md            TEXT NOT NULL,
    metadata_json       TEXT NOT NULL CHECK (json_valid(metadata_json)),
    required_tools_json TEXT NOT NULL CHECK (json_valid(required_tools_json)),
    content_checksum    TEXT NOT NULL,
    review_status       TEXT NOT NULL
                            CHECK (review_status IN ('pending','passed','failed','not_required')),
    created_by          TEXT NOT NULL,
    created_at          TEXT NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, revision),
    FOREIGN KEY (tenant_id, skill_id) REFERENCES ds_agent_skill(tenant_id, id)
        ON DELETE CASCADE
);

-- Source: V12__immutable_skill_revision.sql
CREATE TABLE ds_skill_resource (
    tenant_id        TEXT NOT NULL,
    skill_id         TEXT NOT NULL,
    skill_revision   INTEGER NOT NULL,
    resource_path    TEXT NOT NULL,
    media_type       TEXT NOT NULL,
    content          TEXT NOT NULL,
    size_bytes       INTEGER NOT NULL CHECK (size_bytes >= 0),
    checksum         TEXT NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, skill_revision, resource_path),
    FOREIGN KEY (tenant_id, skill_id, skill_revision)
        REFERENCES ds_skill_revision(tenant_id, skill_id, revision) ON DELETE CASCADE
);

-- Source: V12__immutable_skill_revision.sql
CREATE TABLE ds_agent_run_skill (
    tenant_id       TEXT NOT NULL,
    run_id          TEXT NOT NULL,
    skill_id        TEXT NOT NULL,
    skill_revision  INTEGER NOT NULL,
    content_checksum TEXT NOT NULL,
    PRIMARY KEY (tenant_id, run_id, skill_id),
    FOREIGN KEY (tenant_id, run_id) REFERENCES ds_agent_run(tenant_id, id)
        ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, skill_id, skill_revision)
        REFERENCES ds_skill_revision(tenant_id, skill_id, revision)
);

-- Source: V12__immutable_skill_revision.sql
CREATE INDEX idx_agent_run_skill_revision
    ON ds_agent_run_skill (tenant_id, skill_id, skill_revision);

-- Source: V13__agent_pending_action.sql
CREATE TABLE ds_agent_pending_action (
    id                  TEXT NOT NULL PRIMARY KEY,
    tenant_id           TEXT NOT NULL,
    run_id              TEXT NOT NULL,
    tool_call_id        TEXT NOT NULL,
    action_type         TEXT NOT NULL CHECK (action_type IN ('question','approval')),
    request_json        TEXT NOT NULL CHECK (json_valid(request_json)),
    response_json       TEXT CHECK (response_json IS NULL OR json_valid(response_json)),
    resolution_digest   TEXT CHECK (
                            resolution_digest IS NULL OR
                            (length(resolution_digest) = 64
                             AND resolution_digest NOT GLOB '*[^0-9a-f]*')),
    status              TEXT NOT NULL CHECK (status IN (
                            'pending','responded','approved','denied','expired','cancelled')),
    expires_at          TEXT NOT NULL,
    resolved_by         TEXT,
    resolved_at         TEXT,
    revision            INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL,
    FOREIGN KEY (tenant_id, run_id) REFERENCES ds_agent_run(tenant_id, id)
        ON DELETE CASCADE,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, run_id, tool_call_id)
);

-- Source: V13__agent_pending_action.sql
CREATE INDEX idx_pending_action_run_status
    ON ds_agent_pending_action (tenant_id, run_id, status, created_at, id);

-- Source: V13__agent_pending_action.sql
CREATE INDEX idx_pending_action_expiry
    ON ds_agent_pending_action (status, expires_at);

-- Source: V14__oauth_credential.sql
CREATE TABLE ds_oauth_credential (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    provider_key    TEXT NOT NULL CHECK (provider_key IN ('codex','github')),
    secret_id       TEXT NOT NULL,
    token_type      TEXT,
    scope           TEXT,
    expires_at      TEXT,
    revision        INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    FOREIGN KEY (tenant_id, secret_id) REFERENCES ds_secret(tenant_id, id),
    UNIQUE (tenant_id, user_id, provider_key),
    UNIQUE (tenant_id, id)
);

-- Source: V14__oauth_credential.sql
CREATE INDEX idx_oauth_credential_owner
    ON ds_oauth_credential (tenant_id, user_id, provider_key);
