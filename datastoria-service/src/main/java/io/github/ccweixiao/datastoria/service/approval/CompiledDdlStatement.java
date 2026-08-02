package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

public record CompiledDdlStatement(
    int ordinal,
    DdlOperationKind operationKind,
    String sql,
    List<String> objectRefs,
    String riskLevel,
    List<String> warnings,
    String idempotencyStrategy) {

  public CompiledDdlStatement {
    objectRefs = List.copyOf(objectRefs);
    warnings = List.copyOf(warnings);
  }
}
