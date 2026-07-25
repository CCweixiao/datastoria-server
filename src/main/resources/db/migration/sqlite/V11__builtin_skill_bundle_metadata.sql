ALTER TABLE ds_agent_skill
    ADD COLUMN bundle_checksum TEXT;

ALTER TABLE ds_agent_skill
    ADD COLUMN builtin INTEGER NOT NULL DEFAULT 0 CHECK (builtin IN (0, 1));
