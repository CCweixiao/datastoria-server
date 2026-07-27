CREATE TABLE ds_agent_event (
    id          VARCHAR(26) NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    run_id      VARCHAR(64) NOT NULL,
    sequence    BIGINT NOT NULL,
    frame_text  LONGTEXT NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_agent_event_run FOREIGN KEY (tenant_id, run_id)
        REFERENCES ds_agent_run(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_agent_event_sequence UNIQUE (tenant_id, run_id, sequence),
    CONSTRAINT chk_agent_event_sequence CHECK (sequence > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_agent_event_replay
    ON ds_agent_event (tenant_id, run_id, sequence);
