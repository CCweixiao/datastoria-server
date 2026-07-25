package io.datastoria.server.skill;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads the version-controlled {@code classpath:/skills} baseline with strict path, size, UTF-8 and
 * frontmatter validation. Runtime Agents never read these files directly; the provisioner imports
 * the validated bundles into the active profile database.
 */
@Component
public class ClasspathSkillBundleLoader {

  static final int MAX_FILE_BYTES = 1024 * 1024;
  static final int MAX_BUNDLE_BYTES = 5 * 1024 * 1024;

  private static final List<String> BUILTIN_IDS =
      List.of(
          "clickhouse",
          "clickhouse-system-queries",
          "diagnose-clickhouse-clusters",
          "diagnose-clickhouse-errors",
          "optimize-clickhouse-sql",
          "source-code-inspection",
          "sql-expert",
          "visualization",
          "vizlayer");
  private static final Pattern FRONTMATTER =
      Pattern.compile("\\A---[ \\t]*\\R([\\s\\S]*?)\\R---[ \\t]*(?:\\R|\\z)");
  private static final Pattern SAFE_ID = Pattern.compile("[a-z][a-z0-9_-]{0,127}");

  private final PathMatchingResourcePatternResolver resolver =
      new PathMatchingResourcePatternResolver();
  private final Yaml yaml = new Yaml(new SafeConstructor(loaderOptions()));

  public List<SkillBundle> loadAll() {
    return BUILTIN_IDS.stream().map(this::load).toList();
  }

  SkillBundle load(String id) {
    validateId(id);
    TreeMap<String, byte[]> files = discover(id);
    byte[] skillBytes = files.remove("SKILL.md");
    if (skillBytes == null) {
      throw new IllegalStateException("Skill bundle is missing SKILL.md: " + id);
    }
    String skillMarkdown = decode(skillBytes, id + "/SKILL.md");
    Map<String, Object> frontmatter = parseFrontmatter(skillMarkdown, id);
    String name = requiredString(frontmatter, "name", id);
    String description = requiredString(frontmatter, "description", id);
    Map<String, String> metadata = stringMap(frontmatter.get("metadata"), id);
    String version = metadata.get("version");
    List<String> requiredTools = requiredTools(frontmatter, metadata, id);
    Map<String, String> resources = new LinkedHashMap<>();
    files.forEach((path, bytes) -> resources.put(path, decode(bytes, id + "/" + path)));
    return new SkillBundle(
        id,
        name,
        description,
        version,
        skillMarkdown,
        Map.copyOf(metadata),
        requiredTools,
        Map.copyOf(resources),
        checksum(skillBytes, files));
  }

  private TreeMap<String, byte[]> discover(String id) {
    TreeMap<String, byte[]> files = new TreeMap<>();
    int total = 0;
    try {
      for (Resource resource : resolver.getResources("classpath*:skills/" + id + "/**")) {
        if (!resource.isReadable() || resource.getFilename() == null) {
          continue;
        }
        String external = resource.getURL().toExternalForm();
        String marker = "skills/" + id + "/";
        int markerIndex = external.lastIndexOf(marker);
        if (markerIndex < 0) {
          throw new IllegalStateException("Unable to resolve Skill resource path: " + external);
        }
        String path = external.substring(markerIndex + marker.length());
        validateResourcePath(path);
        byte[] content = resource.getInputStream().readAllBytes();
        if (content.length > MAX_FILE_BYTES) {
          throw new IllegalStateException("Skill resource exceeds 1 MiB: " + id + "/" + path);
        }
        total += content.length;
        if (total > MAX_BUNDLE_BYTES) {
          throw new IllegalStateException("Skill bundle exceeds 5 MiB: " + id);
        }
        if (files.put(path, content) != null) {
          throw new IllegalStateException("Duplicate Skill resource: " + id + "/" + path);
        }
      }
    } catch (IOException error) {
      throw new IllegalStateException("Unable to scan Skill bundle: " + id, error);
    }
    return files;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseFrontmatter(String markdown, String id) {
    Matcher matcher = FRONTMATTER.matcher(markdown);
    if (!matcher.find()) {
      throw new IllegalStateException("Skill frontmatter is missing or malformed: " + id);
    }
    Object parsed;
    try {
      parsed = yaml.load(matcher.group(1));
    } catch (RuntimeException error) {
      throw new IllegalStateException("Skill frontmatter is invalid YAML: " + id, error);
    }
    if (!(parsed instanceof Map<?, ?> map)) {
      throw new IllegalStateException("Skill frontmatter must be a mapping: " + id);
    }
    return (Map<String, Object>) map;
  }

  private static String requiredString(Map<String, Object> values, String key, String id) {
    Object value = values.get(key);
    if (!(value instanceof String text) || text.isBlank()) {
      throw new IllegalStateException("Skill " + id + " requires non-empty " + key);
    }
    return text.trim();
  }

  private static Map<String, String> stringMap(Object value, String id) {
    if (value == null) {
      return Map.of();
    }
    if (!(value instanceof Map<?, ?> raw)) {
      throw new IllegalStateException("Skill metadata must be a mapping: " + id);
    }
    Map<String, String> result = new LinkedHashMap<>();
    raw.forEach(
        (key, item) -> {
          if (!(key instanceof String textKey)
              || !(item instanceof String || item instanceof Boolean || item instanceof Number)) {
            throw new IllegalStateException("Skill metadata must contain scalar values: " + id);
          }
          result.put(textKey, String.valueOf(item));
        });
    return result;
  }

  private static List<String> requiredTools(
      Map<String, Object> frontmatter, Map<String, String> metadata, String id) {
    Object value = frontmatter.get("required-tools");
    if (value == null) {
      value = frontmatter.get("requiredTools");
    }
    if (value == null && metadata.containsKey("tools")) {
      value = metadata.get("tools");
    }
    if (value == null) {
      return List.of();
    }
    List<String> tools = new ArrayList<>();
    if (value instanceof String text) {
      for (String tool : text.split(",")) {
        if (!tool.isBlank()) {
          tools.add(tool.trim());
        }
      }
    } else if (value instanceof List<?> values) {
      for (Object tool : values) {
        if (!(tool instanceof String text) || text.isBlank()) {
          throw new IllegalStateException("Skill required-tools must contain names: " + id);
        }
        tools.add(text.trim());
      }
    } else {
      throw new IllegalStateException("Skill required-tools must be a list or string: " + id);
    }
    return List.copyOf(tools);
  }

  private static String decode(byte[] content, String source) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(content))
          .toString();
    } catch (CharacterCodingException error) {
      throw new IllegalStateException("Skill resource is not valid UTF-8: " + source, error);
    }
  }

  private static String checksum(byte[] skillMarkdown, TreeMap<String, byte[]> resources) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      updateDigest(digest, "SKILL.md", skillMarkdown);
      resources.forEach((path, content) -> updateDigest(digest, path, content));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static void updateDigest(MessageDigest digest, String path, byte[] content) {
    digest.update(path.getBytes(StandardCharsets.UTF_8));
    digest.update((byte) 0);
    digest.update(content);
    digest.update((byte) 0);
  }

  private static void validateId(String id) {
    if (!SAFE_ID.matcher(id).matches()) {
      throw new IllegalArgumentException("Invalid Skill id: " + id);
    }
  }

  static void validateResourcePath(String path) {
    if (path.isBlank()
        || path.startsWith("/")
        || path.startsWith(".")
        || path.endsWith("/")
        || path.contains("..")
        || path.contains("\\")
        || path.indexOf('\0') >= 0) {
      throw new IllegalStateException("Unsafe Skill resource path: " + path);
    }
  }

  private static LoaderOptions loaderOptions() {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(10);
    options.setCodePointLimit(MAX_FILE_BYTES);
    return options;
  }
}
