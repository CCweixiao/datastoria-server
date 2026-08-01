CREATE TABLE ds_user_state (
    tenant_id       VARCHAR(128) NOT NULL,
    user_id         VARCHAR(128) NOT NULL,
    namespace       VARCHAR(128) NOT NULL,
    state_key       VARCHAR(255) NOT NULL,
    value_json      JSON NOT NULL,
    revision        BIGINT NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, user_id, namespace, state_key),
    KEY idx_user_state_namespace
        (tenant_id, user_id, namespace, updated_at)
);
