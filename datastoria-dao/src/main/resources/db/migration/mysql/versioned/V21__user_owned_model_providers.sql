-- Private model providers owned by ordinary users.
ALTER TABLE ds_model_provider
    ADD COLUMN owner_user_id varchar(255) NULL AFTER tenant_id,
    ADD COLUMN owner_scope varchar(255) GENERATED ALWAYS AS
        (COALESCE(owner_user_id, '__system__')) STORED AFTER owner_user_id;

ALTER TABLE ds_model_provider DROP INDEX uk_provider_active;
ALTER TABLE ds_model_provider
    ADD UNIQUE KEY uk_provider_active (tenant_id, owner_scope, provider_key, active_key),
    ADD INDEX idx_provider_owner (tenant_id, owner_user_id, enabled, deleted_at);
