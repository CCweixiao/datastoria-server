function escapeClickHouseString(value: string): string {
  return value.replaceAll("\\", "\\\\").replaceAll("'", "\\'");
}

export function bindDashboardQueryCluster(sql: string, configuredCluster?: string | null): string {
  const cluster = configuredCluster?.trim() || "default";
  const clusterLiteral = `'${escapeClickHouseString(cluster)}'`;

  return sql
    .replace(/\{cluster:String\}/gi, clusterLiteral)
    .replace(
      /\bclusterAllReplicas\s*\(\s*(?:default|'default'|"default")\s*,/gi,
      `clusterAllReplicas(${clusterLiteral},`
    );
}

export function scopeDashboardQueryToCluster(sql: string, cluster?: string): string {
  // Official system.dashboards queries may already use clusterAllReplicas. Wrapping the table
  // argument again produces an invalid nested table function.
  if (/\bclusterAllReplicas\s*\(/i.test(sql)) {
    return sql;
  }
  return sql.replace(
    /\bsystem\.(metric_log|asynchronous_metric_log)\b/gi,
    (_match, tableName: string) =>
      cluster
        ? `clusterAllReplicas('${escapeClickHouseString(cluster)}', system.${tableName})`
        : `{clusterAllReplicas:system.${tableName}}`
  );
}

export interface DashboardClusterNode {
  hostName: string;
  hostAddress: string;
  shardNumber: number;
  replicaNumber: number;
  isLocal: boolean;
}

export interface DashboardQueryExecution {
  sql: string;
  direct: boolean;
  targetNode?: string;
}

export function dashboardNodeScopeValue(node: DashboardClusterNode): string {
  return `node:${node.shardNumber}:${node.replicaNumber}:${node.hostAddress}`;
}

export function resolveDashboardQueryExecution(
  sql: string,
  configuredCluster: string | null | undefined,
  detectedCluster: string | null | undefined,
  clusterNodes: DashboardClusterNode[],
  monitorScope: string
): DashboardQueryExecution {
  const effectiveCluster = configuredCluster?.trim() || detectedCluster?.trim() || "default";
  const boundSql = bindDashboardQueryCluster(sql, effectiveCluster);
  const selectedNode = clusterNodes.find((node) => dashboardNodeScopeValue(node) === monitorScope);

  // Cluster aggregation is meaningful only when the selected topology has multiple members.
  // Execute it through the configured endpoint so ClickHouse itself fans out to all replicas.
  if (monitorScope === "cluster" && clusterNodes.length > 1) {
    return {
      sql: scopeDashboardQueryToCluster(boundSql, effectiveCluster),
      direct: true,
    };
  }

  // Standalone servers and node-scoped views must not fan out. A local node uses the saved HTTP
  // connection; a remote topology member is addressed explicitly by the backend.
  return {
    sql: scopeDashboardQueryToLocalNode(boundSql),
    direct: !selectedNode || selectedNode.isLocal,
    targetNode: selectedNode && !selectedNode.isLocal ? selectedNode.hostAddress : undefined,
  };
}

export function scopeDashboardQueryToLocalNode(sql: string): string {
  return sql.replace(
    /\bclusterAllReplicas\s*\(\s*'(?:[^'\\]|\\.)*'\s*,\s*(merge\([^()]*\)|system\.[A-Za-z0-9_]+)\s*\)/gi,
    "$1"
  );
}

/**
 * ClickHouse infers an empty dynamic-column array as Array(Nothing). Array aggregations reject that
 * type on releases such as 25.5 when a COLUMNS(...) expression matches no metrics. Giving the
 * array an explicit numeric type preserves normal expansion and makes the empty case evaluate to 0.
 */
export function normalizeDynamicColumnAggregates(sql: string): string {
  return sql.replace(
    /\barraySum\(\[\s*(COLUMNS\('[^']*'\)(?:\s+EXCEPT\s+'[^']*')?(?:\s+APPLY\s+[A-Za-z_][A-Za-z0-9_]*)?)\s*\]\)/gi,
    "arraySum(CAST([$1] AS Array(Float64)))"
  );
}
