CREATE TABLE ds_agent_skill (
    id              VARCHAR(255) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    owner_user_id   VARCHAR(128) NOT NULL,
    content         LONGTEXT NOT NULL,
    state           ENUM('draft','published') NOT NULL,
    scope           ENUM('global','self') NOT NULL,
    version         VARCHAR(128),
    revision        BIGINT NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    deleted_at      DATETIME(6),
    PRIMARY KEY (tenant_id, id),
    KEY idx_agent_skill_visibility
        (tenant_id, owner_user_id, state, scope, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ds_agent_skill_resource (
    tenant_id       VARCHAR(64) NOT NULL,
    skill_id        VARCHAR(255) NOT NULL,
    resource_path   VARCHAR(440) NOT NULL,
    content         LONGTEXT NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, resource_path),
    CONSTRAINT fk_agent_skill_resource_skill
        FOREIGN KEY (tenant_id, skill_id) REFERENCES ds_agent_skill(tenant_id, id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
