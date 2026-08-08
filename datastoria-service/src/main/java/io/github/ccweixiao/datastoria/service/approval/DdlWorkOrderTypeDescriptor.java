package io.github.ccweixiao.datastoria.service.approval;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;

public interface DdlWorkOrderTypeDescriptor {

  String generatorKey();

  void validateRules(JsonNode rules);

  CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema);
}
