import { describe, expect, it } from "vitest";
import {
  bindDashboardQueryCluster,
  dashboardNodeScopeValue,
  normalizeDynamicColumnAggregates,
  resolveDashboardQueryExecution,
  scopeDashboardQueryToCluster,
  scopeDashboardQueryToLocalNode,
} from "./dashboard-scope";

describe("dashboard cluster scope", () => {
  it("binds built-in dashboard queries to the selected connection cluster", () => {
    const sql = "FROM clusterAllReplicas(default, merge('system', '^metric_log'))";

    expect(bindDashboardQueryCluster(sql, "production_cluster")).toBe(
      "FROM clusterAllReplicas('production_cluster', merge('system', '^metric_log'))"
    );
  });

  it("uses default when the selected connection has no configured cluster", () => {
    expect(
      bindDashboardQueryCluster(
        "FROM clusterAllReplicas({cluster:String}, system.metric_log)",
        "  "
      )
    ).toBe("FROM clusterAllReplicas('default', system.metric_log)");
  });

  it("escapes configured cluster names before inserting a SQL literal", () => {
    expect(bindDashboardQueryCluster("SELECT clusterAllReplicas(default, system.one)", "a'b")).toBe(
      "SELECT clusterAllReplicas('a\\'b', system.one)"
    );
  });

  it("unwraps clusterAllReplicas when a dashboard targets the local node", () => {
    expect(
      scopeDashboardQueryToLocalNode(
        "SELECT * FROM clusterAllReplicas('default', merge('system', '^metric_log'))"
      )
    ).toBe("SELECT * FROM merge('system', '^metric_log')");
    expect(
      scopeDashboardQueryToLocalNode(
        "SELECT * FROM clusterAllReplicas('default', system.asynchronous_metric_log)"
      )
    ).toBe("SELECT * FROM system.asynchronous_metric_log");
  });

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

  const localNode = {
    hostName: "local",
    hostAddress: "127.0.0.1:9000",
    shardNumber: 1,
    replicaNumber: 1,
    isLocal: true,
  };
  const remoteNode = {
    hostName: "remote",
    hostAddress: "10.0.0.2:9000",
    shardNumber: 1,
    replicaNumber: 2,
    isLocal: false,
  };

  it("uses the configured endpoint directly for a standalone local node", () => {
    expect(
      resolveDashboardQueryExecution(
        "SELECT * FROM clusterAllReplicas('default', system.metric_log)",
        "default",
        undefined,
        [localNode],
        dashboardNodeScopeValue(localNode)
      )
    ).toEqual({ sql: "SELECT * FROM system.metric_log", direct: true, targetNode: undefined });
  });

  it("targets a selected remote replica without cluster fan-out", () => {
    expect(
      resolveDashboardQueryExecution(
        "SELECT * FROM clusterAllReplicas('default', system.metric_log)",
        "default",
        undefined,
        [localNode, remoteNode],
        dashboardNodeScopeValue(remoteNode)
      )
    ).toEqual({
      sql: "SELECT * FROM system.metric_log",
      direct: false,
      targetNode: "10.0.0.2:9000",
    });
  });

  it("fans out a multi-node aggregate using an auto-detected cluster", () => {
    expect(
      resolveDashboardQueryExecution(
        "SELECT * FROM clusterAllReplicas(default, system.metric_log)",
        "",
        "production",
        [localNode, remoteNode],
        "cluster"
      )
    ).toEqual({
      sql: "SELECT * FROM clusterAllReplicas('production', system.metric_log)",
      direct: true,
    });
  });

  it("fans out a local metric source across every member of a multi-node cluster", () => {
    expect(
      resolveDashboardQueryExecution(
        "SELECT * FROM system.metric_log",
        undefined,
        "production",
        [localNode, remoteNode],
        "cluster"
      )
    ).toEqual({
      sql: "SELECT * FROM clusterAllReplicas('production', system.metric_log)",
      direct: true,
    });
  });

  it("uses a collision-free scope key for replicas sharing a host name", () => {
    expect(dashboardNodeScopeValue(localNode)).not.toBe(
      dashboardNodeScopeValue({ ...localNode, replicaNumber: 2 })
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
