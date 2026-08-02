package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

public record CompiledDdlPlan(List<CompiledDdlStatement> statements, List<String> ruleSummaries) {

  public CompiledDdlPlan {
    statements = List.copyOf(statements);
    ruleSummaries = List.copyOf(ruleSummaries);
  }
}
