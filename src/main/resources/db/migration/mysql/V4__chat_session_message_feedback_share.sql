-- V4__chat_session_message_feedback_share.sql (MySQL dialect)
--
-- P3 data ownership for chat product data: sessions, messages, feedback events
-- and server-side session share rows. Mirrors V4 SQLite with MySQL types:
-- varchar, json, datetime(6), boolean, bigint, tinyint. See the SQLite file
-- for design notes.

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

CREATE INDEX idx_chat_session_user_cursor
    ON ds_chat_session (tenant_id, user_id, updated_at, id);

CREATE INDEX idx_chat_session_user_connection_cursor
    ON ds_chat_session (tenant_id, user_id, connection_id, updated_at, id);

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

CREATE INDEX idx_feedback_event_tenant_source_updated
    ON ds_feedback_event (tenant_id, source, updated_at);

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

CREATE INDEX idx_session_share_session_active
    ON ds_session_share (tenant_id, session_id, revoked_at, expires_at);
