/** Tool-event names used to select display components for server-produced SSE parts. */
export const CLICKHOUSE_TOOL_NAMES = {
  EXPLORE_SCHEMA: "explore_schema",
  GET_TABLES: "get_tables",
  EXECUTE_SQL: "execute_sql",
  VALIDATE_SQL: "validate_sql",
  COLLECT_SQL_OPTIMIZATION_EVIDENCE: "collect_sql_optimization_evidence",
  SEARCH_QUERY_LOG: "search_query_log",
  COLLECT_CLUSTER_STATUS: "collect_cluster_status",
  COLLECT_RCA_EVIDENCE: "collect_rca_evidence",
} as const;
