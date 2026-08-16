package io.github.ccweixiao.datastoria.common.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Safely parses the catalog fields shared by seeded and database-authored Skill markdown.
 *
 * <p>SnakeYAML {@link Yaml} instances are not thread-safe, and concurrent run preparations parse
 * several skills at once, so each thread uses its own parser.
 */
@Component
public class SkillMetadataParser {

  private static final int MAX_SKILL_MARKDOWN_CHARS = 1024 * 1024;
  private static final Pattern FRONTMATTER =
      Pattern.compile("\\A---[ \\t]*\\R([\\s\\S]*?)\\R---[ \\t]*(?:\\R|\\z)");

  private final ThreadLocal<Yaml> yaml =
      ThreadLocal.withInitial(() -> new Yaml(new SafeConstructor(loaderOptions())));

  public ParsedSkillMetadata parse(String content, String fallbackName) {
    if (content == null || content.length() > MAX_SKILL_MARKDOWN_CHARS) {
      throw new IllegalArgumentException("Skill markdown exceeds 1 MiB");
    }
    Matcher frontmatter = FRONTMATTER.matcher(content);
    if (!frontmatter.find()) {
      throw new IllegalArgumentException("Skill markdown requires YAML frontmatter");
    }
    Object parsed;
    try {
      parsed = yaml.get().load(frontmatter.group(1));
    } catch (RuntimeException error) {
      throw new IllegalArgumentException("Skill frontmatter is invalid YAML", error);
    }
    if (!(parsed instanceof Map<?, ?> root)) {
      throw new IllegalArgumentException("Skill frontmatter must be a mapping");
    }
    String body = content.substring(frontmatter.end()).trim();
    String summary =
        body.lines()
            .filter(line -> !line.isBlank() && !line.startsWith("#"))
            .findFirst()
            .orElse("");
    String name = scalar(root.get("name"), fallbackName);
    String description = scalar(root.get("description"), summary);
    Map<?, ?> metadata =
        root.get("metadata") instanceof Map<?, ?> metadataMap ? metadataMap : Map.of();
    return new ParsedSkillMetadata(
        name,
        description,
        summary,
        booleanValue(metadata, "disable-slash-command", "disableSlashCommand"),
        booleanValue(metadata, "show-in-sql-editor-quick-action", "showInSqlEditorQuickAction"),
        stringValue(metadata.get("author")),
        stringValue(metadata.get("url")),
        tools(root, metadata));
  }

  private static List<String> tools(Map<?, ?> root, Map<?, ?> metadata) {
    Object value = root.get("required-tools");
    if (value == null) {
      value = root.get("requiredTools");
    }
    if (value == null) {
      value = metadata.get("tools");
    }
    if (value == null) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    if (value instanceof String text) {
      for (String tool : text.split(",")) {
        if (!tool.isBlank()) {
          result.add(tool.trim());
        }
      }
    } else if (value instanceof List<?> values) {
      for (Object tool : values) {
        if (!(tool instanceof String text) || text.isBlank()) {
          throw new IllegalArgumentException("Skill required-tools must contain names");
        }
        result.add(text.trim());
      }
    } else {
      throw new IllegalArgumentException("Skill required-tools must be a list or string");
    }
    return List.copyOf(result);
  }

  private static boolean booleanValue(Map<?, ?> values, String kebab, String camel) {
    Object value = values.containsKey(kebab) ? values.get(kebab) : values.get(camel);
    return value instanceof Boolean bool
        ? bool
        : value instanceof String text && Boolean.parseBoolean(text);
  }

  private static String scalar(Object value, String fallback) {
    String text = stringValue(value);
    return text == null || text.isBlank() ? fallback : text.trim();
  }

  private static String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static LoaderOptions loaderOptions() {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(10);
    options.setCodePointLimit(MAX_SKILL_MARKDOWN_CHARS);
    return options;
  }

  public record ParsedSkillMetadata(
      String name,
      String description,
      String summary,
      boolean disableSlashCommand,
      boolean showInSqlEditorQuickAction,
      String author,
      String url,
      List<String> requiredTools) {}
}
