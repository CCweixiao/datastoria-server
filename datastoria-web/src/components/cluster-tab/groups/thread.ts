import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import type { MessageKey } from "@/lib/i18n/messages/en";

function threadMetric(
  title: string,
  currentMetric: string,
  descriptionKey: MessageKey
): TimeseriesDescriptor {
  return {
    type: "line",
    titleOption: { title, descriptionKey, align: "center" },
    gridPos: { w: 6, h: 6 },
    legendOption: { placement: "bottom", values: ["min", "max", "last"] },
    fieldOptions: { metric: { format: "short_number" } },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  server,
  max(metric) as metric
FROM (
  SELECT event_time, FQDN() as server, sum(CurrentMetric_${currentMetric}) AS metric
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

export const threadMetricsDashboard: TimeseriesDescriptor[] = [
  threadMetric(
    "CurrentMetric_GlobalThread",
    "GlobalThread",
    "monitor.cluster.globalThread.description"
  ),
  threadMetric(
    "CurrentMetric_GlobalThreadActive",
    "GlobalThreadActive",
    "monitor.cluster.globalThreadActive.description"
  ),
];
