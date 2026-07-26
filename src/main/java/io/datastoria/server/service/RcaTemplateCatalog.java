package io.datastoria.server.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Read-only access to enabled, revisioned RCA templates persisted by the A27 bootstrap. */
@Service
public class RcaTemplateCatalog {

  private final JdbcClient jdbc;

  public RcaTemplateCatalog(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, String> enabledSources() {
    Map<String, String> templates = new LinkedHashMap<>();
    jdbc.sql(
            "SELECT template_key, source_yaml FROM ds_rca_template"
                + " WHERE enabled = TRUE ORDER BY template_key")
        .query(
            rs -> {
              while (rs.next()) {
                templates.put(rs.getString("template_key"), rs.getString("source_yaml"));
              }
              return templates;
            });
    return templates;
  }

  public Optional<TemplateSnapshot> findEnabled(String key) {
    return jdbc.sql(
            "SELECT template_key, revision, source_yaml FROM ds_rca_template"
                + " WHERE template_key = :key AND enabled = TRUE")
        .param("key", key)
        .query(
            (rs, rowNum) -> {
              String source = rs.getString("source_yaml");
              return new TemplateSnapshot(
                  rs.getString("template_key"), rs.getLong("revision"), sha256(source));
            })
        .optional();
  }

  public TemplateSnapshot requireEnabled(String key) {
    return findEnabled(key)
        .orElseThrow(() -> new IllegalStateException("Enabled RCA template not found: " + key));
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

  public record TemplateSnapshot(String key, long revision, String checksum) {}
}
