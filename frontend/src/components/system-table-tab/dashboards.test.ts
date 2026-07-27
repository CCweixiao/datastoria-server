import { describe, expect, it } from "vitest";
import { normalizeDynamicColumnAggregates, scopeDashboardQueryToCluster } from "./dashboard-scope";

describe("dashboard cluster scope", () => {
  it("fans metric sources out to every configured replica", () => {
    expect(
      scopeDashboardQueryToCluster(`
        SELECT event_time, value
        FROM system.metric_log
        UNION ALL
        SELECT event_time, value
        FROM system.asynchronous_metric_log
      `)
    ).toContain("{clusterAllReplicas:system.metric_log}");
    expect(scopeDashboardQueryToCluster("SELECT * FROM system.asynchronous_metric_log")).toContain(
      "{clusterAllReplicas:system.asynchronous_metric_log}"
    );
  });

  it("does not rewrite unrelated system tables", () => {
    expect(scopeDashboardQueryToCluster("SELECT * FROM system.tables")).toBe(
      "SELECT * FROM system.tables"
    );
  });

  it("types empty dynamic metric arrays so ClickHouse 25.5 returns zero", () => {
    const sql =
      "SELECT arraySum([COLUMNS('CurrentMetric_.*CacheBytes') " +
      "EXCEPT 'CurrentMetric_FilesystemCache.*' APPLY avg]) AS cache_bytes";

    expect(normalizeDynamicColumnAggregates(sql)).toContain(
      "arraySum(CAST([COLUMNS('CurrentMetric_.*CacheBytes') " +
        "EXCEPT 'CurrentMetric_FilesystemCache.*' APPLY avg] AS Array(Float64)))"
    );
  });

  it("leaves ordinary array aggregates unchanged", () => {
    expect(normalizeDynamicColumnAggregates("SELECT arraySum([1, 2, 3])")).toBe(
      "SELECT arraySum([1, 2, 3])"
    );
  });
});
