CREATE TABLE ds_agent_skill (
    id              TEXT NOT NULL,
    tenant_id       TEXT NOT NULL,
    owner_user_id   TEXT NOT NULL,
    content         TEXT NOT NULL,
    state           TEXT NOT NULL CHECK (state IN ('draft','published')),
    scope           TEXT NOT NULL CHECK (scope IN ('global','self')),
    version         TEXT,
    revision        BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    deleted_at      TEXT,
    PRIMARY KEY (tenant_id, id)
);

CREATE INDEX idx_agent_skill_visibility
    ON ds_agent_skill (tenant_id, owner_user_id, state, scope, deleted_at);

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
