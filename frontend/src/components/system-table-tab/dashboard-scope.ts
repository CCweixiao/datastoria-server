export function scopeDashboardQueryToCluster(sql: string): string {
  return sql.replace(
    /\bsystem\.(metric_log|asynchronous_metric_log)\b/gi,
    (_match, tableName: string) => `{clusterAllReplicas:system.${tableName}}`
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
