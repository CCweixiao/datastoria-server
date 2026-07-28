package io.github.ccweixiao.datastoria.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelCatalogRemovalMigrationTest {

  @TempDir Path tempDir;

  @Test
  void v15RemovesProvisionedAndOauthCatalogsButKeepsAdminProvider() throws Exception {
    String url = "jdbc:sqlite:" + tempDir.resolve("catalog-removal.db");
    Flyway.configure()
        .dataSource(url, "", "")
        .locations("classpath:db/migration/sqlite")
        .target(MigrationVersion.fromVersion("14"))
        .load()
        .migrate();

    try (var connection = DriverManager.getConnection(url);
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO ds_model_provider
            (id, tenant_id, provider_key, display_name, base_url, auth_type, enabled,
             config_json, revision, created_by, updated_by, created_at, updated_at)
          VALUES
            ('seed-provider', 'tenant', 'openai', 'OpenAI', 'https://api.openai.com/v1',
             'api_key', 1, '{}', 0, 'system:model-catalog', 'system:model-catalog',
             '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'),
            ('oauth-provider', 'tenant', 'openai-codex', 'OpenAI Codex', NULL,
             'oauth', 1, '{}', 0, 'system', 'system',
             '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'),
            ('admin-provider', 'tenant', 'zhipu', '智谱 GLM',
             'https://open.bigmodel.cn/api/paas/v4', 'api_key', 1, '{}', 0,
             'admin@example.com', 'admin@example.com',
             '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
          """);
      statement.executeUpdate(
          """
          INSERT INTO ds_model
            (id, tenant_id, provider_id, model_key, display_name, source, enabled, is_free,
             capabilities_json, generation_defaults_json, revision, created_at, updated_at)
          VALUES
            ('seed-model', 'tenant', 'seed-provider', 'gpt-seed', 'Seed', 'system', 1, 0,
             '{}', '{}', 0, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'),
            ('oauth-model', 'tenant', 'oauth-provider', 'codex-seed', 'Codex', 'discovered', 1, 0,
             '{}', '{}', 0, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
          """);
      statement.executeUpdate(
          """
          INSERT INTO ds_user_model_preference
            (id, tenant_id, user_id, selected_model_id, preference_json, revision,
             created_at, updated_at)
          VALUES
            ('preference', 'tenant', 'user@example.com', 'seed-model', '{}', 0,
             '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
          """);
    }

    Flyway.configure()
        .dataSource(url, "", "")
        .locations("classpath:db/migration/sqlite")
        .load()
        .migrate();

    try (var connection = DriverManager.getConnection(url);
        var statement = connection.createStatement()) {
      assertThat(count(statement, "ds_model_provider")).isEqualTo(1);
      assertThat(count(statement, "ds_model")).isZero();
      assertThat(count(statement, "ds_user_model_preference")).isZero();
      try (var result =
          statement.executeQuery("SELECT provider_key FROM ds_model_provider LIMIT 1")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("zhipu");
      }
    }
  }

  private static int count(java.sql.Statement statement, String table) throws Exception {
    try (var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      return result.next() ? result.getInt(1) : 0;
    }
  }
}
