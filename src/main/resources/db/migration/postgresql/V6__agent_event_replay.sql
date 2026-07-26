CREATE TABLE ds_agent_event (
    id          TEXT NOT NULL PRIMARY KEY,
    tenant_id   TEXT NOT NULL,
    run_id      TEXT NOT NULL,
    sequence    BIGINT NOT NULL CHECK (sequence > 0),
    frame_text  TEXT NOT NULL,
    created_at  TEXT NOT NULL,
    FOREIGN KEY (tenant_id, run_id) REFERENCES ds_agent_run(tenant_id, id) ON DELETE CASCADE,
    UNIQUE (tenant_id, run_id, sequence)
);

CREATE INDEX idx_agent_event_replay
    ON ds_agent_event (tenant_id, run_id, sequence);
