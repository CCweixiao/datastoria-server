import { describe, expect, it } from "vitest";
import {
  nodeAsynchronousMetricTimeseries,
  nodeMetricLogTimeseries,
} from "./node-metric-query-template";

describe("node metric query templates", () => {
  it("builds a node-local metric_log query", () => {
    const descriptor = nodeMetricLogTimeseries({
      type: "line",
      metricExpression: "avg(ProfileEvent_Query)",
      metricAlias: "query_qps",
    });
    expect(descriptor.datasource.sql).toContain("avg(ProfileEvent_Query) AS query_qps");
    expect(descriptor.datasource.sql).toContain("FROM system.metric_log");
    expect(descriptor.datasource.sql).not.toContain("clusterAllReplicas");
  });

  it("builds a node-local asynchronous metric query", () => {
    const descriptor = nodeAsynchronousMetricTimeseries({
      type: "line",
      metric: "MaxPartCountForPartition",
      aggregation: "max",
    });
    expect(descriptor.datasource.sql).toContain("max(value) AS MaxPartCountForPartition");
    expect(descriptor.datasource.sql).toContain("metric = 'MaxPartCountForPartition'");
  });
});
