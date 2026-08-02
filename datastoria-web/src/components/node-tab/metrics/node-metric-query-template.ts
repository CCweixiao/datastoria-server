import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";

type NodeMetricLogTimeseriesOptions = Omit<TimeseriesDescriptor, "datasource"> & {
  metricExpression: string;
  metricAlias: string;
};

export function nodeMetricLogTimeseries({
  metricExpression,
  metricAlias,
  ...descriptor
}: NodeMetricLogTimeseriesOptions): TimeseriesDescriptor {
  return {
    ...descriptor,
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  ${metricExpression} AS ${metricAlias}
FROM system.metric_log
WHERE event_date >= toDate({from:String})
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String}
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}`,
    },
  };
}

type NodeAsynchronousMetricTimeseriesOptions = Omit<TimeseriesDescriptor, "datasource"> & {
  metric: string;
  aggregation?: "avg" | "max";
  metricAlias?: string;
};

export function nodeAsynchronousMetricTimeseries({
  metric,
  aggregation = "avg",
  metricAlias = metric,
  ...descriptor
}: NodeAsynchronousMetricTimeseriesOptions): TimeseriesDescriptor {
  return {
    ...descriptor,
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  ${aggregation}(value) AS ${metricAlias}
FROM system.asynchronous_metric_log
WHERE event_date >= toDate({from:String})
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String}
  AND event_time < {to:String}
  AND metric = '${metric}'
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}`,
    },
  };
}
