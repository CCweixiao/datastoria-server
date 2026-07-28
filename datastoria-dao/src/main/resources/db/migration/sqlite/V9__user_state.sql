CREATE TABLE ds_user_state (
    tenant_id       TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    namespace       TEXT NOT NULL,
    state_key       TEXT NOT NULL,
    value_json      TEXT NOT NULL CHECK (json_valid(value_json)),
    revision        INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    PRIMARY KEY (tenant_id, user_id, namespace, state_key)
);

CREATE INDEX idx_user_state_namespace
    ON ds_user_state (tenant_id, user_id, namespace, updated_at);
