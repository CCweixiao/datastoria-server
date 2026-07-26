CREATE TABLE ds_rca_template (
  id TEXT PRIMARY KEY,
  template_key TEXT NOT NULL UNIQUE,
  source_yaml TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  revision BIGINT NOT NULL DEFAULT 1,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);
