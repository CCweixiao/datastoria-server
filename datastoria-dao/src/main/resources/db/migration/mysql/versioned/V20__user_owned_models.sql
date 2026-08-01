-- Split the model catalog into tenant-wide system models and private per-user models.
-- NULL owner_user_id denotes a system model managed by administrators.

ALTER TABLE ds_model
    ADD COLUMN owner_user_id varchar(255) NULL AFTER tenant_id,
    ADD COLUMN owner_scope varchar(255) GENERATED ALWAYS AS
        (COALESCE(owner_user_id, '__system__')) STORED AFTER deleted_at;

ALTER TABLE ds_model DROP INDEX uk_model_active;

ALTER TABLE ds_model
    ADD UNIQUE KEY uk_model_active
        (tenant_id, provider_id, model_key, owner_scope, active_key);

CREATE INDEX idx_model_owner
    ON ds_model (tenant_id, owner_user_id, enabled, deleted_at);
