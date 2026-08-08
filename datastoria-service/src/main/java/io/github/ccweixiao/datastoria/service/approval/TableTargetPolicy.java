package io.github.ccweixiao.datastoria.service.approval;

/** How a table-level DDL maps one logical table name to physical ClickHouse tables. */
public enum TableTargetPolicy {
  LOGICAL_PAIR_LOCAL_FIRST,
  LOGICAL_PAIR_DISTRIBUTED_FIRST,
  LOCAL_ONLY
}
