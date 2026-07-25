CREATE TABLE ds_rca_template (
  id TEXT PRIMARY KEY,
  template_key TEXT NOT NULL UNIQUE,
  source_yaml TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  revision INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
