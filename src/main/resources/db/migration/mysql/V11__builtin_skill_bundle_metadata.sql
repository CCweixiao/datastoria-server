ALTER TABLE ds_agent_skill
    ADD COLUMN bundle_checksum CHAR(64),
    ADD COLUMN builtin BOOLEAN NOT NULL DEFAULT FALSE;
