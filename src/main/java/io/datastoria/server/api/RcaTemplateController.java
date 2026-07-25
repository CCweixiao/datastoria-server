package io.datastoria.server.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/rca/templates")
public class RcaTemplateController {

  private final JdbcClient jdbc;

  public RcaTemplateController(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping
  public Map<String, Object> list() {
    Map<String, String> templates = new LinkedHashMap<>();
    jdbc.sql(
            "SELECT template_key, source_yaml FROM ds_rca_template"
                + " WHERE enabled = 1 ORDER BY template_key")
        .query(
            rs -> {
              while (rs.next()) {
                templates.put(rs.getString("template_key"), rs.getString("source_yaml"));
              }
              return templates;
            });
    return Map.of("templates", templates);
  }
}
