-- V5__agent_run_and_checkpoint.sql (SQLite dialect)
--
-- P4 Agent run lifecycle and checkpoint ownership. Two tables:
--   * ds_agent_run        — one row per agent run; status state machine + idempotency + usage.
--   * ds_agent_checkpoint — opaque, DataStoria-adapter-serialized run state keyed by (run, sequence).
--
-- Design notes (docs/design/database-data-model.md §8, docs/design/harness-agent.md §10):
--   * Product messages (ds_chat_message) and Agent run state stay strictly separated. AgentScope
--     internal state is NEVER written to ds_chat_message; it lives only in ds_agent_checkpoint as
--     an opaque DataStoria-adapter payload (state_json), never as AgentScope types.
--   * Every row carries tenant_id and every query/update filters by it (defense-in-depth, not only
--     enforced at the controller). run_id/message_id are globally-unique ULIDs.
--   * Optimistic lock via revision bigint; terminal transitions use a conditional UPDATE on
--     revision so concurrent complete/fail/cancel cannot overwrite each other.
--   * status is CHECK-constrained to the authoritative status set (database-data-model.md §8).
--   * FKs: run -> ds_chat_session(tenant_id,id) ON DELETE CASCADE; checkpoint -> run ON DELETE
--     CASCADE. agent_revision_id/model_id are logical references (app-validated), not hard FKs,
--     because those tables are soft-deleted.
--   * JSON columns use json_valid() CHECK; MySQL relies on its native json type (see mysql dialect).

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

CREATE INDEX idx_agent_run_user_cursor
    ON ds_agent_run (tenant_id, user_id, updated_at, id);

CREATE INDEX idx_agent_run_session
    ON ds_agent_run (tenant_id, session_id, created_at, id);

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
