/**
 * Serializable tool-event contracts rendered by the browser.
 *
 * Execution belongs to the Java AgentScope runtime; this module intentionally contains no
 * connection, SQL, filesystem, or provider code.
 */
export type GetTablesInput = {
  name_pattern?: string;
  database?: string;
  engine?: string;
  partition_key?: string;
  limit?: number;
};

export type GetTablesOutput = Array<{
  database: string;
  table: string;
  engine: string;
  partition_key?: string;
}>;

export type TableSchemaInput = {
  table: string;
  columns?: string[];
};

export type ExploreSchemaInput = {
  tables: TableSchemaInput[];
};

export type ExploreSchemaOutput = Array<{
  database: string;
  table: string;
  columns: Array<{ name: string; type: string; comment?: string }>;
  primaryKey: string;
  partitionBy: string;
  engine: string;
  sortingKey: string;
  totalColumns: number;
  truncated: boolean;
  guidance?: string;
}>;

type TableStatsEvidence = {
  rows?: number;
  bytes?: number;
  parts?: number;
  partitions?: number;
};

type TableStructureEvidence = {
  columns: Array<[string, string]>;
  engine?: string;
  partition_key?: string | null;
  primary_key?: string | null;
  sorting_key?: string | null;
  secondary_indexes?: string[];
};

type OptimizationTargetEvidence = TableStructureEvidence & {
  database: string;
  table: string;
  cluster?: string;
  stats?: TableStatsEvidence;
};

type ExplainPruningMetric = {
  selected: number;
  total: number;
  ratio: number;
};

type ExplainIndexEvidence = {
  table?: string;
  indexes: Array<{
    name: string;
    keys: string[];
    index_name?: string;
    description?: string;
    condition?: string;
    parts?: ExplainPruningMetric;
    granules?: ExplainPruningMetric;
    search_algorithm?: string;
  }>;
  summary: string[];
  raw_text?: string;
};

export interface EvidenceContext {
  goal: string;
  sql?: string;
  query_id?: string;
  symptoms?: {
    latency_ms?: number;
    read_rows?: number;
    read_bytes?: number;
    result_rows?: number;
    peak_memory_bytes?: number;
    spilled?: boolean;
    errors?: string | null;
  };
  tables?: Array<{ database: string; table: string; engine: string }>;
  table_schema?: Record<
    string,
    TableStructureEvidence & { optimization_target?: OptimizationTargetEvidence }
  >;
  table_stats?: Record<string, TableStatsEvidence>;
  explain_index?: ExplainIndexEvidence;
  explain_pipeline?: {
    max_parallelism?: number;
    operators: string[];
    summary: string[];
    raw_text?: string;
  };
  query_log?: {
    duration_ms?: number;
    read_rows?: number;
    read_bytes?: number;
    memory_usage?: number;
    result_rows?: number;
    exception?: string | null;
    resource_summary?: Record<string, number>;
    profile_events?: Record<string, number>;
  };
  settings?: Record<string, string | number>;
  constraints?: string[];
  cluster?: { mode?: string; shards?: number; replicas?: number };
}

export type CollectSqlOptimizationEvidenceInput = {
  sql?: string;
  query_id?: string;
  goal?: "latency" | "memory" | "bytes" | "dashboard" | "other";
  mode?: "light" | "full";
  time_window?: number;
  time_range?: { from: string; to: string };
  requested?: { required?: string[]; optional?: string[] };
};
