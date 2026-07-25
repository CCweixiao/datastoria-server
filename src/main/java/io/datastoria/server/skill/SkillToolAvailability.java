package io.datastoria.server.skill;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * P5 availability policy for Skill-declared tool dependencies.
 *
 * <p>The set contains only tools currently registered by the Java runtime. Browser executors are
 * intentionally excluded. P6+ contributors will replace this fixed baseline with the Toolkit
 * registry.
 */
@Component
public class SkillToolAvailability {

  private static final Set<String> JAVA_TOOLS =
      Set.of(
          "execute_sql",
          "get_tables",
          "explore_schema",
          "validate_sql",
          "collect_sql_optimization_evidence",
          "search_query_log",
          "collect_cluster_status",
          "collect_rca_evidence");

  private final SkillMetadataParser metadataParser;

  public SkillToolAvailability(SkillMetadataParser metadataParser) {
    this.metadataParser = metadataParser;
  }

  public boolean isAvailable(String skillMarkdown, String fallbackName) {
    return JAVA_TOOLS.containsAll(
        metadataParser.parse(skillMarkdown, fallbackName).requiredTools());
  }
}
