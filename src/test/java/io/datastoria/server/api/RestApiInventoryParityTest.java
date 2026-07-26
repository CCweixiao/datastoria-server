package io.datastoria.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards the frozen A01-A29 Node REST inventory against accidental loss from the Spring mapping
 * surface. A28 is implemented by Spring Security filters plus explicit compatibility endpoints and
 * is covered separately.
 */
@SpringBootTest
@ActiveProfiles("test")
class RestApiInventoryParityTest {

  private static final Pattern API_LITERAL = Pattern.compile("/api/[A-Za-z0-9_./:${}-]+");

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Test
  void everyFrozenBusinessOperationHasASpringHandler() throws Exception {
    Set<Operation> actual = new HashSet<>();
    mappings
        .getHandlerMethods()
        .forEach(
            (mapping, ignored) -> {
              Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
              mapping
                  .getPatternsCondition()
                  .getPatterns()
                  .forEach(
                      pattern ->
                          methods.forEach(
                              method ->
                                  actual.add(
                                      new Operation(
                                          method.name(), normalize(pattern.getPatternString())))));
            });

    Set<Operation> expected = baselineOperations();
    expected.addAll(frontendOperations());
    assertThat(actual).containsAll(expected);
  }

  @Test
  void everyFrontendBackendApiLiteralIsInventoried() throws Exception {
    Set<String> inventoriedPrefixes =
        frontendOperations().stream()
            .map(Operation::path)
            .map(path -> path.split("\\{", 2)[0])
            .collect(java.util.stream.Collectors.toSet());
    try (Stream<Path> files = Files.walk(Path.of("frontend/src"))) {
      for (Path file :
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().matches(".*\\.tsx?$"))
              .filter(path -> !path.toString().matches(".*\\.(test|spec)\\.tsx?$"))
              .toList()) {
        for (String line : Files.readAllLines(file)) {
          if (!line.contains("/api/")
              || (!line.contains("Api")
                  && !line.contains("apiUrl")
                  && !line.contains("Fetch")
                  && !line.contains("fetch")
                  && !line.contains("SessionApiBase"))) {
            continue;
          }
          Matcher matcher = API_LITERAL.matcher(line);
          while (matcher.find()) {
            String literalPrefix = matcher.group().split("\\$", 2)[0];
            assertThat(inventoriedPrefixes)
                .as("%s contains an API path missing from the frontend inventory: %s", file, line)
                .anyMatch(
                    inventoryPrefix ->
                        inventoryPrefix.startsWith(literalPrefix)
                            || literalPrefix.startsWith(inventoryPrefix));
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private Set<Operation> baselineOperations() throws Exception {
    Map<String, Object> document;
    try (InputStream input = Files.newInputStream(Path.of("docs/api/openapi-baseline.yaml"))) {
      document = new Yaml().load(input);
    }
    Map<String, Object> paths = (Map<String, Object>) document.get("paths");
    Set<Operation> operations = new HashSet<>();
    paths.forEach(
        (path, rawItem) -> {
          if (path.startsWith("/api/auth/")) {
            operations.add(new Operation("GET", "/api/auth/providers"));
            operations.add(new Operation("GET", "/api/auth/session"));
            operations.add(new Operation("GET", "/api/auth/signin/{}"));
            operations.add(new Operation("POST", "/api/auth/signout"));
            return;
          }
          Map<String, Object> item = (Map<String, Object>) rawItem;
          for (String method : Set.of("get", "post", "put", "patch", "delete", "head", "options")) {
            if (item.containsKey(method)) {
              operations.add(new Operation(method.toUpperCase(Locale.ROOT), normalize(path)));
            }
          }
        });
    return operations;
  }

  @SuppressWarnings("unchecked")
  private Set<Operation> frontendOperations() throws Exception {
    Map<String, Object> document;
    try (InputStream input =
        Files.newInputStream(Path.of("docs/api/frontend-spring-call-inventory.yaml"))) {
      document = new Yaml().load(input);
    }
    Set<Operation> operations = new HashSet<>();
    for (Map<String, Object> item : (Iterable<Map<String, Object>>) document.get("operations")) {
      String path = String.valueOf(item.get("path"));
      Path callSite = Path.of("frontend/src").resolve(String.valueOf(item.get("callSite")));
      assertThat(callSite).isRegularFile();
      String staticPathPrefix = path.split("\\{", 2)[0];
      assertThat(Files.readString(callSite))
          .as("%s must still call %s", callSite, path)
          .contains(staticPathPrefix);
      operations.add(
          new Operation(
              String.valueOf(item.get("method")).toUpperCase(Locale.ROOT), normalize(path)));
    }
    return operations;
  }

  private static String normalize(String path) {
    return path.replaceAll("\\{[^}]+}", "{}");
  }

  private record Operation(String method, String path) {}
}
