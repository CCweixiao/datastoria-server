-- Pending action persistence using MySQL 5.7 types.
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

CREATE INDEX idx_pending_action_run_status
    ON ds_agent_pending_action (tenant_id, run_id, status, created_at, id);

CREATE INDEX idx_pending_action_expiry
    ON ds_agent_pending_action (status, expires_at);
