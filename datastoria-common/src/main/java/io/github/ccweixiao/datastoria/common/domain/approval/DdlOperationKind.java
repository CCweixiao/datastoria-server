package io.github.ccweixiao.datastoria.common.domain.approval;

/** Stable operation codes emitted by the server-side DDL compiler. */
public enum DdlOperationKind {
  CREATE_TABLE,
  ALTER_TABLE_ADD_COLUMN,
  ALTER_TABLE_MODIFY_COLUMN,
  ALTER_TABLE_DROP_COLUMN,
  ALTER_TABLE_ADD_INDEX,
  ALTER_TABLE_MATERIALIZE_INDEX
}
