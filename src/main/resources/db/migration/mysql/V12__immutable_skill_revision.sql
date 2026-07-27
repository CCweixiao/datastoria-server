ALTER TABLE ds_agent_skill
    ADD COLUMN published_revision BIGINT,
    ADD COLUMN draft_revision BIGINT;

CREATE TABLE ds_skill_revision (
    tenant_id           VARCHAR(64) NOT NULL,
    skill_id            VARCHAR(255) NOT NULL,
    revision            BIGINT NOT NULL,
    version             VARCHAR(128),
    name                VARCHAR(255) NOT NULL,
    description         TEXT NOT NULL,
    summary             TEXT NOT NULL,
    skill_md            LONGTEXT NOT NULL,
    metadata_json       JSON NOT NULL,
    required_tools_json JSON NOT NULL,
    content_checksum    CHAR(64) NOT NULL,
    review_status       ENUM('pending','passed','failed','not_required') NOT NULL,
    created_by          VARCHAR(128) NOT NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, revision),
    CONSTRAINT fk_skill_revision_skill
        FOREIGN KEY (tenant_id, skill_id) REFERENCES ds_agent_skill(tenant_id, id)
        ON DELETE CASCADE
);

CREATE TABLE ds_skill_resource (
    tenant_id       VARCHAR(64) NOT NULL,
    skill_id        VARCHAR(255) NOT NULL,
    skill_revision  BIGINT NOT NULL,
    resource_path   VARCHAR(512) NOT NULL,
    media_type      VARCHAR(255) NOT NULL,
    content         LONGBLOB NOT NULL,
    size_bytes      BIGINT NOT NULL,
    checksum        CHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, skill_revision, resource_path),
    CONSTRAINT fk_skill_resource_revision
        FOREIGN KEY (tenant_id, skill_id, skill_revision)
        REFERENCES ds_skill_revision(tenant_id, skill_id, revision) ON DELETE CASCADE
);

CREATE TABLE ds_agent_run_skill (
    tenant_id        VARCHAR(64) NOT NULL,
    run_id           VARCHAR(64) NOT NULL,
    skill_id         VARCHAR(255) NOT NULL,
    skill_revision   BIGINT NOT NULL,
    content_checksum CHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id, run_id, skill_id),
    KEY idx_agent_run_skill_revision (tenant_id, skill_id, skill_revision),
    CONSTRAINT fk_agent_run_skill_run
        FOREIGN KEY (tenant_id, run_id) REFERENCES ds_agent_run(tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_agent_run_skill_revision
        FOREIGN KEY (tenant_id, skill_id, skill_revision)
        REFERENCES ds_skill_revision(tenant_id, skill_id, revision)
);

INSERT INTO ds_skill_revision
    (tenant_id, skill_id, revision, version, name, description, summary, skill_md,
     metadata_json, required_tools_json, content_checksum, review_status, created_by, created_at)
SELECT tenant_id, id, revision, version, id, id, '', content, JSON_OBJECT(), JSON_ARRAY(),
       COALESCE(bundle_checksum, 'legacy-unverified'), 'not_required', owner_user_id, created_at
FROM ds_agent_skill;

INSERT INTO ds_skill_resource
    (tenant_id, skill_id, skill_revision, resource_path, media_type, content, size_bytes, checksum)
SELECT r.tenant_id, r.skill_id, s.revision, r.resource_path, 'text/plain',
       CONVERT(r.content USING utf8mb4), OCTET_LENGTH(r.content), 'legacy-unverified'
FROM ds_agent_skill_resource r
JOIN ds_agent_skill s ON s.tenant_id = r.tenant_id AND s.id = r.skill_id;

UPDATE ds_agent_skill
SET published_revision = CASE WHEN state = 'published' THEN revision ELSE NULL END,
    draft_revision = CASE WHEN state = 'draft' THEN revision ELSE NULL END;
