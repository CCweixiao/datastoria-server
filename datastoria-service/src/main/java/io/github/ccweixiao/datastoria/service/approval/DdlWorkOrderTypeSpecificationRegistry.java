package io.github.ccweixiao.datastoria.service.approval;

import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DdlWorkOrderTypeSpecificationRegistry {

  private static final String RESOURCE = "approval-types/clickhouse-ddl.json";
  private final List<DdlWorkOrderTypeSpecification> specifications;

  public DdlWorkOrderTypeSpecificationRegistry(ObjectMapper mapper) {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("Missing built-in DDL work-order manifest: " + RESOURCE);
      }
      specifications =
          List.copyOf(
              mapper.readValue(input, new TypeReference<List<DdlWorkOrderTypeSpecification>>() {}));
      if (specifications.isEmpty()
          || specifications.stream().map(DdlWorkOrderTypeSpecification::typeKey).distinct().count()
              != specifications.size()
          || specifications.stream()
                  .map(DdlWorkOrderTypeSpecification::generatorKey)
                  .distinct()
                  .count()
              != specifications.size()) {
        throw new IllegalStateException("Duplicate or empty built-in DDL work-order manifest");
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid built-in DDL work-order manifest", exception);
    }
  }

  public List<DdlWorkOrderTypeSpecification> all() {
    return specifications;
  }
}
