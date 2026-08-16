-- V27: Skills are static assets shipped in the jar under classpath:/skills (see
-- SkillBundleProvider / SkillCatalog). The user-authored skill lifecycle and its
-- per-tenant provisioning pipeline are gone, so the five skill tables are dropped.
-- Skill content never lives in MySQL again.
DROP TABLE IF EXISTS ds_agent_run_skill;
DROP TABLE IF EXISTS ds_skill_resource;
DROP TABLE IF EXISTS ds_skill_revision;
DROP TABLE IF EXISTS ds_agent_skill_resource;
DROP TABLE IF EXISTS ds_agent_skill;
