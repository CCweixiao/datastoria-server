-- V5__agent_run_and_checkpoint.sql (MySQL dialect)
--
-- MySQL 5.7 is the schema baseline. CHECK clauses document invariants enforced by the application;
-- MySQL 5.7 parses but does not enforce them.

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

CREATE INDEX idx_agent_run_user_cursor
    ON ds_agent_run (tenant_id, user_id, updated_at, id);

CREATE INDEX idx_agent_run_session
    ON ds_agent_run (tenant_id, session_id, created_at, id);

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
