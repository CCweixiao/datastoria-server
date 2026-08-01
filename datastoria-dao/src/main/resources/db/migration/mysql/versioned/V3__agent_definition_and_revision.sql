-- V3__agent_definition_and_revision.sql (MySQL dialect)
--
-- Agent definitions and their immutable revisions.

CREATE TABLE ds_agent_definition (
    id                      varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id               varchar(64)  NOT NULL,
    agent_key               varchar(64)  NOT NULL,
    name                    varchar(255) NOT NULL,
    description             longtext     NULL,
    status                  varchar(32)  NOT NULL,
    published_revision_id   varchar(64)  NULL,
    revision                bigint       NOT NULL DEFAULT 0,
    created_by              varchar(255) NOT NULL,
    updated_by              varchar(255) NOT NULL,
    created_at              datetime(6)  NOT NULL,
    updated_at              datetime(6)  NOT NULL,
    deleted_at              datetime(6)  NULL,
    active_key              tinyint GENERATED ALWAYS AS
                            (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    CONSTRAINT chk_agent_status CHECK (status IN ('draft','published','disabled')),
    CONSTRAINT chk_agent_revision CHECK (revision >= 0),
    UNIQUE KEY uk_agent_active (tenant_id, agent_key, active_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_agent_definition_tenant
    ON ds_agent_definition (tenant_id, status, deleted_at);

CREATE TABLE ds_agent_revision (
    id                      varchar(64)  NOT NULL PRIMARY KEY,
    agent_id                varchar(64)  NOT NULL,
    version                 int          NOT NULL,
    model_id                varchar(64)  NULL,
    system_prompt           longtext     NOT NULL,
    prompt_checksum         varchar(128) NOT NULL,
    runtime_config_json     json         NULL,
    tool_policy_json        json         NULL,
    skill_policy_json       json         NULL,
    created_by              varchar(255) NOT NULL,
    created_at              datetime(6)  NOT NULL,
    CONSTRAINT chk_revision_version CHECK (version >= 1),
    CONSTRAINT fk_revision_agent FOREIGN KEY (agent_id) REFERENCES ds_agent_definition(id),
    UNIQUE KEY uk_agent_version (agent_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_agent_revision_agent
    ON ds_agent_revision (agent_id, created_at);
