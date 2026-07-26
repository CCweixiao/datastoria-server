-- P8 durable HITL actions. Resolution is a revision-guarded CAS; resolution_digest makes
-- retries with the same semantic payload idempotent while rejecting a different retry.
CREATE TABLE ds_agent_pending_action (
    id                  TEXT NOT NULL PRIMARY KEY,
    tenant_id           TEXT NOT NULL,
    run_id              TEXT NOT NULL,
    tool_call_id        TEXT NOT NULL,
    action_type         TEXT NOT NULL CHECK (action_type IN ('question','approval')),
    request_json        TEXT NOT NULL CHECK ((request_json::jsonb IS NOT NULL)),
    response_json       TEXT CHECK (response_json IS NULL OR (response_json::jsonb IS NOT NULL)),
    resolution_digest   TEXT CHECK (
                            resolution_digest IS NULL OR
                            resolution_digest ~ '^[0-9a-f]{64}$'),
    status              TEXT NOT NULL CHECK (status IN (
                            'pending','responded','approved','denied','expired','cancelled')),
    expires_at          TEXT NOT NULL,
    resolved_by         TEXT,
    resolved_at         TEXT,
    revision            BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL,
    FOREIGN KEY (tenant_id, run_id) REFERENCES ds_agent_run(tenant_id, id)
        ON DELETE CASCADE,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, run_id, tool_call_id)
);

CREATE INDEX idx_pending_action_run_status
    ON ds_agent_pending_action (tenant_id, run_id, status, created_at, id);

CREATE INDEX idx_pending_action_expiry
    ON ds_agent_pending_action (status, expires_at);
