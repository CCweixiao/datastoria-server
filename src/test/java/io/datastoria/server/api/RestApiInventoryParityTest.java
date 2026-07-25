package io.datastoria.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    assertThat(actual).containsAll(expected);
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

  private static String normalize(String path) {
    return path.replaceAll("\\{[^}]+}", "{}");
  }

  private record Operation(String method, String path) {}
}
