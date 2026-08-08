package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Immutable built-in metadata loaded from the approval-type resource manifest. */
public record DdlWorkOrderTypeSpecification(
    String typeKey,
    String generatorKey,
    String nameEn,
    String nameZhCn,
    String descriptionEn,
    String descriptionZhCn,
    List<String> allowedOperationKinds,
    JsonNode defaultRules) {

  public DdlWorkOrderTypeSpecification {
    allowedOperationKinds = List.copyOf(allowedOperationKinds);
    defaultRules = defaultRules.deepCopy();
  }
}
