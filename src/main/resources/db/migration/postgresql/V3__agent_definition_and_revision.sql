-- V3__agent_definition_and_revision.sql (PostgreSQL dialect)
--
-- Agent definitions and their immutable revisions. Publishing is an atomic
-- UPDATE of ds_agent_definition.published_revision_id; existing runs always
-- reference the revision id captured at run-creation time.

CREATE TABLE ds_agent_definition (
    id                      TEXT NOT NULL PRIMARY KEY,
    tenant_id               TEXT NOT NULL,
    agent_key               TEXT NOT NULL,
    name                    TEXT NOT NULL,
    description             TEXT,
    status                  TEXT NOT NULL CHECK (status IN ('draft','published','disabled')),
    published_revision_id   TEXT,
    revision                BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_by              TEXT NOT NULL,
    updated_by              TEXT NOT NULL,
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL,
    deleted_at              TEXT,
    active_key              BIGINT GENERATED ALWAYS AS
                            (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    UNIQUE (tenant_id, agent_key, active_key)
);

CREATE INDEX idx_agent_definition_tenant
    ON ds_agent_definition (tenant_id, status, deleted_at);

CREATE TABLE ds_agent_revision (
    id                      TEXT NOT NULL PRIMARY KEY,
    agent_id                TEXT NOT NULL,
    version                 BIGINT NOT NULL CHECK (version >= 1),
    model_id                TEXT,
    system_prompt           TEXT NOT NULL,
    prompt_checksum         TEXT NOT NULL,
    runtime_config_json     TEXT CHECK (runtime_config_json IS NULL OR (runtime_config_json::jsonb IS NOT NULL)),
    tool_policy_json        TEXT CHECK (tool_policy_json IS NULL OR (tool_policy_json::jsonb IS NOT NULL)),
    skill_policy_json       TEXT CHECK (skill_policy_json IS NULL OR (skill_policy_json::jsonb IS NOT NULL)),
    created_by              TEXT NOT NULL,
    created_at              TEXT NOT NULL,
    FOREIGN KEY (agent_id) REFERENCES ds_agent_definition(id),
    UNIQUE (agent_id, version)
);

CREATE INDEX idx_agent_revision_agent
    ON ds_agent_revision (agent_id, created_at);
