-- V25: Plan 版本化一等公民（V3 P1，见 docs/design/ddl-approval-v3-p1-implementation.md）
--
-- 将 Plan 从 content_json 中的一坨提升为带 plan_version / plan_hash 的版本化一等公民。
-- Plan 物理上并入 ds_approval_request（不建独立表）；content_json 仍持有结构化 Plan 快照，
-- content_digest 仍用于执行前逐字节匹配。plan_hash 为语义内容哈希，驱动 change classifier。
--
-- 列类型对齐 V22：env_snapshot_json 用 JSON（同 content_json）；plan_hash 用 CHAR(64)（同 content_digest）。
ALTER TABLE ds_approval_request
    ADD COLUMN plan_version       INT          NOT NULL DEFAULT 1,
    ADD COLUMN plan_hash          CHAR(64)     NULL     DEFAULT NULL,
    ADD COLUMN env_snapshot_json  JSON         NULL,
    ADD COLUMN policy_version_ref VARCHAR(128) NULL;

-- 不为 plan_version 建索引：它总是随单个 request（按 PK id）读取，无独立检索需求。
-- 既有 DRAFT 行的 plan_hash 回填由代码侧重算（开发库通常 0 行），SQL 层只保证列与默认值。
