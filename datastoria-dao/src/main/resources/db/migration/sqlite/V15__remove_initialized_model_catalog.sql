-- Model configuration is administrator-owned from V15 onward.
-- Remove only rows materialized by the retired catalog provisioner and the
-- two retired OAuth model catalogs. Custom/admin-created provider rows remain.

DELETE FROM ds_user_model_preference
WHERE selected_model_id IN (
    SELECT m.id
    FROM ds_model m
    JOIN ds_model_provider p
      ON p.tenant_id = m.tenant_id AND p.id = m.provider_id
    WHERE p.created_by = 'system:model-catalog'
       OR p.provider_key IN ('github-copilot', 'openai-codex')
);

DELETE FROM ds_model
WHERE provider_id IN (
    SELECT id
    FROM ds_model_provider
    WHERE created_by = 'system:model-catalog'
       OR provider_key IN ('github-copilot', 'openai-codex')
);

DELETE FROM ds_model_provider
WHERE created_by = 'system:model-catalog'
   OR provider_key IN ('github-copilot', 'openai-codex');
