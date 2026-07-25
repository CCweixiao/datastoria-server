CREATE TABLE ds_rca_template (
  id VARCHAR(36) PRIMARY KEY,
  template_key VARCHAR(191) NOT NULL UNIQUE,
  source_yaml LONGTEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  revision BIGINT NOT NULL DEFAULT 1,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);
