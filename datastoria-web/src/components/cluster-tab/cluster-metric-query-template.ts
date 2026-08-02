import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";

type ClusterMetricLogTimeseriesOptions = Omit<TimeseriesDescriptor, "datasource"> & {
  metricExpression: string;
  metricAlias?: string;
  groupBy?: string;
  orderBy?: string;
};

/**
 * Builds the common cluster-wide system.metric_log time-series query without changing the
 * dashboard descriptor consumed by the existing rendering and execution pipeline.
 */
export function clusterMetricLogTimeseries({
  metricExpression,
  metricAlias = "metric",
  groupBy = "t, server",
  orderBy = "t",
  ...descriptor
}: ClusterMetricLogTimeseriesOptions): TimeseriesDescriptor {
  return {
    ...descriptor,
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  FQDN() as server,
  ${metricExpression} as ${metricAlias}
FROM {clusterAllReplicas:system.metric_log}
WHERE {filterExpression:String}
  AND event_date >= toDate({from:String})
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String}
  AND event_time < {to:String}
GROUP BY ${groupBy}
ORDER BY ${orderBy} WITH FILL STEP {rounding:UInt32}`,
    },
  };
}

type ClusterMetricLogRollupOptions = Omit<TimeseriesDescriptor, "datasource"> & {
  innerMetricExpression: string;
  outerAggregation?: "avg" | "max" | "sum";
};

/** Builds a two-stage query for counters that must first be sampled per event_time and replica. */
export function clusterMetricLogRollupTimeseries({
  innerMetricExpression,
  outerAggregation = "avg",
  ...descriptor
}: ClusterMetricLogRollupOptions): TimeseriesDescriptor {
  return {
    ...descriptor,
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  server,
  ${outerAggregation}(metric) as metric
FROM (
  SELECT event_time, FQDN() as server, ${innerMetricExpression} AS metric
  FROM {clusterAllReplicas:system.metric_log}
  WHERE {filterExpression:String}
  AND event_date >= toDate({from:String})
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String}
  AND event_time < {to:String}
  GROUP BY event_time, server)
GROUP BY t, server
ORDER BY t WITH FILL STEP {rounding:UInt32}`,
    },
  };
}
