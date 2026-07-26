-- V4__chat_session_message_feedback_share.sql (PostgreSQL dialect)
--
-- P3 data ownership for chat product data: sessions, messages, feedback events
-- and server-side session share rows. Mirrors the Node chat_sessions /
-- chat_messages / feedback_events tables with two additions:
--   * tenant_id on every row for cross-tenant isolation;
--   * ds_session_share persists a server-side hash of the share JWT so that
--     revocation is possible (see docs/adr/0001-session-share-permissions.md).
--
-- Soft delete is NOT used here: sessions/messages/feedback are hard-deleted to
-- match Node's product semantics; shares keep an audit row by setting
-- revoked_at (active_key flips to NULL) instead of deleting.

CREATE TABLE ds_chat_session (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    connection_id   TEXT NOT NULL,
    title           TEXT,
    revision        BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    UNIQUE (tenant_id, id)
);

CREATE INDEX idx_chat_session_user_cursor
    ON ds_chat_session (tenant_id, user_id, updated_at, id);

CREATE INDEX idx_chat_session_user_connection_cursor
    ON ds_chat_session (tenant_id, user_id, connection_id, updated_at, id);

CREATE TABLE ds_chat_message (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    session_id      TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    role            TEXT NOT NULL,
    parts_json      TEXT NOT NULL CHECK ((parts_json::jsonb IS NOT NULL)),
    metadata_json   TEXT CHECK (metadata_json IS NULL OR (metadata_json::jsonb IS NOT NULL)),
    sequence        BIGINT NOT NULL CHECK (sequence > 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    FOREIGN KEY (tenant_id, session_id) REFERENCES ds_chat_session(tenant_id, id)
        ON DELETE CASCADE,
    UNIQUE (tenant_id, session_id, sequence),
    UNIQUE (tenant_id, session_id, id)
);

CREATE TABLE ds_feedback_event (
    id                      TEXT NOT NULL PRIMARY KEY,
    tenant_id               TEXT NOT NULL,
    user_id                 TEXT NOT NULL,
    source                  TEXT NOT NULL CHECK (source = 'auto_explain_error'),
    session_id              TEXT NOT NULL,
    message_id              TEXT NOT NULL,
    solved                  BOOLEAN NOT NULL,
    reason_code             TEXT,
    payload_json            TEXT NOT NULL CHECK ((payload_json::jsonb IS NOT NULL)),
    free_text               TEXT,
    recovery_action_taken   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL,
    FOREIGN KEY (tenant_id, session_id) REFERENCES ds_chat_session(tenant_id, id)
        ON DELETE CASCADE,
    UNIQUE (tenant_id, user_id, source, session_id, message_id)
);

CREATE INDEX idx_feedback_event_tenant_source_updated
    ON ds_feedback_event (tenant_id, source, updated_at);

CREATE TABLE ds_session_share (
    id              TEXT NOT NULL PRIMARY KEY,
    tenant_id       TEXT NOT NULL,
    session_id      TEXT NOT NULL,
    owner_user_id   TEXT NOT NULL,
    token_hash      TEXT NOT NULL,
    expires_at      TEXT NOT NULL,
    revoked_at      TEXT,
    created_at      TEXT NOT NULL,
    active_key      BIGINT GENERATED ALWAYS AS
                    (CASE WHEN revoked_at IS NULL THEN 1 ELSE NULL END) STORED,
    UNIQUE (tenant_id, session_id, active_key),
    UNIQUE (tenant_id, token_hash)
);

CREATE INDEX idx_session_share_session_active
    ON ds_session_share (tenant_id, session_id, revoked_at, expires_at);
