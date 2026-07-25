/**
 * Tool-event names and display-only payload types.
 *
 * The browser neither defines nor executes tools. Java AgentScope owns schemas, validation and
 * execution; these types only describe SSE payloads already produced by the server.
 */
import type {
  CollectSqlOptimizationEvidenceInput,
  ExploreSchemaInput,
  ExploreSchemaOutput,
  GetTablesInput,
  GetTablesOutput,
} from "./clickhouse-tool-contracts";

export type ValidateSqlToolInput = { sql: string };
export type ValidateSqlToolOutput = { success: boolean; error?: string };

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

export type ClickHouseToolName =
  (typeof CLICKHOUSE_TOOL_NAMES)[keyof typeof CLICKHOUSE_TOOL_NAMES];

export type ClickHouseUITools = {
  explore_schema: { input: ExploreSchemaInput; output: ExploreSchemaOutput };
  get_tables: { input: GetTablesInput; output: GetTablesOutput };
  execute_sql: { input: { sql: string }; output: unknown };
  validate_sql: { input: ValidateSqlToolInput; output: ValidateSqlToolOutput };
  collect_sql_optimization_evidence: {
    input: CollectSqlOptimizationEvidenceInput;
    output: unknown;
  };
  search_query_log: { input: unknown; output: unknown };
  collect_cluster_status: { input: unknown; output: unknown };
  collect_rca_evidence: { input: unknown; output: unknown };
};
