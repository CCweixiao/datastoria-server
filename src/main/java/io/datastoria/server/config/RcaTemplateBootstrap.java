package io.datastoria.server.config;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class RcaTemplateBootstrap implements ApplicationRunner {

  private final JdbcClient jdbc;

  public RcaTemplateBootstrap(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    Integer count =
        jdbc.sql("SELECT COUNT(*) FROM ds_rca_template WHERE template_key = :key")
            .param("key", "high_part_count")
            .query(Integer.class)
            .single();
    if (count != null && count > 0) {
      return;
    }
    String source =
        new ClassPathResource("rca/high-part-count.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
    long now = System.currentTimeMillis();
    jdbc.sql(
            "INSERT INTO ds_rca_template"
                + " (id, template_key, source_yaml, enabled, revision, created_at, updated_at)"
                + " VALUES (:id, :key, :source, 1, 1, :now, :now)")
        .param("id", UUID.randomUUID().toString())
        .param("key", "high_part_count")
        .param("source", source)
        .param("now", now)
        .update();
  }
}
