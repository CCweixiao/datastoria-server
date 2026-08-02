package io.github.ccweixiao.datastoria.service.approval;

import java.util.Set;

public record DdlSchemaSnapshot(Set<String> columns, Set<String> protectedColumns) {

  public static final DdlSchemaSnapshot EMPTY = new DdlSchemaSnapshot(Set.of(), Set.of());

  public DdlSchemaSnapshot {
    columns = Set.copyOf(columns);
    protectedColumns = Set.copyOf(protectedColumns);
  }
}
