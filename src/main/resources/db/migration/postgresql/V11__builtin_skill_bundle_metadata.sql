ALTER TABLE ds_agent_skill
    ADD COLUMN bundle_checksum TEXT;

ALTER TABLE ds_agent_skill
    ADD COLUMN builtin BOOLEAN NOT NULL DEFAULT FALSE;
