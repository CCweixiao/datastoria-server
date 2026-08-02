import { describe, expect, it } from "vitest";
import {
  clusterMetricLogRollupTimeseries,
  clusterMetricLogTimeseries,
} from "./cluster-metric-query-template";

describe("clusterMetricLogTimeseries", () => {
  it("builds the existing cluster metric_log time-series query shape", () => {
    const descriptor = clusterMetricLogTimeseries({
      type: "line",
      titleOption: { title: "ProcessSelectListLock", align: "center" },
      gridPos: { w: 6, h: 6 },
      metricExpression: "sum(ProfileEvent_ProcessSelectListLock)",
    });

    expect(descriptor.datasource.sql).toBe(`
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  FQDN() as server,
  sum(ProfileEvent_ProcessSelectListLock) as metric
FROM {clusterAllReplicas:system.metric_log}
WHERE {filterExpression:String}
  AND event_date >= toDate({from:String})
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String}
  AND event_time < {to:String}
GROUP BY t, server
ORDER BY t WITH FILL STEP {rounding:UInt32}`);
  });

  it("preserves positional grouping used by an existing metric", () => {
    const descriptor = clusterMetricLogTimeseries({
      type: "line",
      titleOption: { title: "MergeSourceParts" },
      metricExpression: "sum(ProfileEvent_MergeSourceParts)",
      groupBy: "1, 2",
      orderBy: "1",
    });

    expect(descriptor.datasource.sql).toContain("GROUP BY 1, 2\nORDER BY 1 WITH FILL STEP");
  });

  it("preserves a custom metric alias", () => {
    const descriptor = clusterMetricLogTimeseries({
      type: "line",
      metricExpression: "avg(ProfileEvent_OSCPUWaitMicroseconds) / 1000000",
      metricAlias: "cpu_wait",
    });

    expect(descriptor.datasource.sql).toContain(
      "avg(ProfileEvent_OSCPUWaitMicroseconds) / 1000000 as cpu_wait"
    );
  });

  it("builds the existing two-stage per-replica rollup shape", () => {
    const descriptor = clusterMetricLogRollupTimeseries({
      type: "line",
      innerMetricExpression: "sum(ProfileEvent_SelectQuery)",
    });

    expect(descriptor.datasource.sql).toContain(
      "SELECT event_time, FQDN() as server, sum(ProfileEvent_SelectQuery) AS metric"
    );
    expect(descriptor.datasource.sql).toContain("avg(metric) as metric");
    expect(descriptor.datasource.sql).toContain("GROUP BY event_time, server)");
  });
});
