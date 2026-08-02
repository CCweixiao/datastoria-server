import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import type { FormatName } from "@/lib/formatter";
import type { MessageKey } from "@/lib/i18n/messages/en";
import {
  clusterMetricLogRollupTimeseries,
  clusterMetricLogTimeseries,
} from "../cluster-metric-query-template";

const commonInsertChart: Pick<TimeseriesDescriptor, "type" | "gridPos" | "legendOption"> = {
  type: "line",
  gridPos: { w: 6, h: 6 },
  legendOption: { placement: "bottom", values: ["min", "max", "last"] },
};

function insertRateMetric(
  title: string,
  profileEvent: string,
  descriptionKey: MessageKey,
  format: FormatName
): TimeseriesDescriptor {
  return clusterMetricLogTimeseries({
    ...commonInsertChart,
    titleOption: { title, descriptionKey, align: "center" },
    fieldOptions: { metric: { format } },
    metricExpression: `sum(ProfileEvent_${profileEvent}) / {rounding:UInt32}`,
  });
}

export const insertMetricsDashboard: TimeseriesDescriptor[] = [
  clusterMetricLogRollupTimeseries({
    ...commonInsertChart,
    titleOption: {
      title: "Insert Queries Per Second",
      descriptionKey: "monitor.cluster.insertQueries.description",
      align: "center",
    },
    innerMetricExpression: "sum(ProfileEvent_InsertQuery)",
  }),
  insertRateMetric(
    "Insert Rows Per Second",
    "InsertedRows",
    "monitor.cluster.insertRows.description",
    "short_number"
  ),
  insertRateMetric(
    "Insert Bytes Per Second",
    "InsertedBytes",
    "monitor.cluster.insertBytes.description",
    "binary_size_per_second"
  ),
  clusterMetricLogRollupTimeseries({
    ...commonInsertChart,
    titleOption: {
      title: "InsertQueryTimeMicroseconds",
      descriptionKey: "monitor.cluster.insertQueryTime.description",
      align: "center",
    },
    fieldOptions: { metric: { format: "microsecond" } },
    innerMetricExpression: "sum(ProfileEvent_InsertQueryTimeMicroseconds)",
  }),
  // Keep the original result-column name for this panel; it intentionally has no metric alias.
  {
    ...commonInsertChart,
    titleOption: {
      title: "AsyncInsertQuery",
      descriptionKey: "monitor.cluster.asyncInsertQuery.description",
      align: "center",
    },
    fieldOptions: { metric: { format: "short_number" } },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  FQDN() as server,
  sum(ProfileEvent_AsyncInsertQuery)
FROM {clusterAllReplicas:system.metric_log}
WHERE {filterExpression:String}
  AND event_date >= toDate({from:String})
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String}
  AND event_time < {to:String}
GROUP BY t, server
ORDER BY t WITH FILL STEP {rounding:UInt32}`,
    },
  },
  insertRateMetric(
    "AsyncInsertBytes",
    "AsyncInsertBytes",
    "monitor.cluster.asyncInsertBytes.description",
    "binary_size_per_second"
  ),
  insertRateMetric(
    "AsyncInsertRows Per Second",
    "AsyncInsertRows",
    "monitor.cluster.asyncInsertRows.description",
    "short_number"
  ),
];
