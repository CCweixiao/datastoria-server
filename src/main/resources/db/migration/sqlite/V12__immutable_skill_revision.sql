ALTER TABLE ds_agent_skill
    ADD COLUMN published_revision INTEGER;

ALTER TABLE ds_agent_skill
    ADD COLUMN draft_revision INTEGER;

CREATE TABLE ds_skill_revision (
    tenant_id           TEXT NOT NULL,
    skill_id            TEXT NOT NULL,
    revision            INTEGER NOT NULL CHECK (revision >= 0),
    version             TEXT,
    name                TEXT NOT NULL,
    description         TEXT NOT NULL,
    summary             TEXT NOT NULL,
    skill_md            TEXT NOT NULL,
    metadata_json       TEXT NOT NULL CHECK (json_valid(metadata_json)),
    required_tools_json TEXT NOT NULL CHECK (json_valid(required_tools_json)),
    content_checksum    TEXT NOT NULL,
    review_status       TEXT NOT NULL
                            CHECK (review_status IN ('pending','passed','failed','not_required')),
    created_by          TEXT NOT NULL,
    created_at          TEXT NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, revision),
    FOREIGN KEY (tenant_id, skill_id) REFERENCES ds_agent_skill(tenant_id, id)
        ON DELETE CASCADE
);

CREATE TABLE ds_skill_resource (
    tenant_id        TEXT NOT NULL,
    skill_id         TEXT NOT NULL,
    skill_revision   INTEGER NOT NULL,
    resource_path    TEXT NOT NULL,
    media_type       TEXT NOT NULL,
    content          TEXT NOT NULL,
    size_bytes       INTEGER NOT NULL CHECK (size_bytes >= 0),
    checksum         TEXT NOT NULL,
    PRIMARY KEY (tenant_id, skill_id, skill_revision, resource_path),
    FOREIGN KEY (tenant_id, skill_id, skill_revision)
        REFERENCES ds_skill_revision(tenant_id, skill_id, revision) ON DELETE CASCADE
);

CREATE TABLE ds_agent_run_skill (
    tenant_id       TEXT NOT NULL,
    run_id          TEXT NOT NULL,
    skill_id        TEXT NOT NULL,
    skill_revision  INTEGER NOT NULL,
    content_checksum TEXT NOT NULL,
    PRIMARY KEY (tenant_id, run_id, skill_id),
    FOREIGN KEY (tenant_id, run_id) REFERENCES ds_agent_run(tenant_id, id)
        ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, skill_id, skill_revision)
        REFERENCES ds_skill_revision(tenant_id, skill_id, revision)
);

CREATE INDEX idx_agent_run_skill_revision
    ON ds_agent_run_skill (tenant_id, skill_id, skill_revision);

INSERT INTO ds_skill_revision
    (tenant_id, skill_id, revision, version, name, description, summary, skill_md,
     metadata_json, required_tools_json, content_checksum, review_status, created_by, created_at)
SELECT tenant_id, id, revision, version, id, id, '', content, '{}', '[]',
       COALESCE(bundle_checksum, 'legacy-unverified'), 'not_required', owner_user_id, created_at
FROM ds_agent_skill;

INSERT INTO ds_skill_resource
    (tenant_id, skill_id, skill_revision, resource_path, media_type, content, size_bytes, checksum)
SELECT r.tenant_id, r.skill_id, s.revision, r.resource_path, 'text/plain', r.content,
       length(CAST(r.content AS BLOB)), 'legacy-unverified'
FROM ds_agent_skill_resource r
JOIN ds_agent_skill s ON s.tenant_id = r.tenant_id AND s.id = r.skill_id;

UPDATE ds_agent_skill
SET published_revision = CASE WHEN state = 'published' THEN revision ELSE NULL END,
    draft_revision = CASE WHEN state = 'draft' THEN revision ELSE NULL END;
