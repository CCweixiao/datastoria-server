import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { nodeMetricLogTimeseries } from "./node-metric-query-template";

const commonQueryChart: Pick<TimeseriesDescriptor, "type" | "gridPos" | "legendOption"> = {
  type: "line",
  gridPos: { w: 6, h: 6 },
  legendOption: { placement: "none", values: ["min", "max", "last"] },
};

export const nodeQueryMetrics: TimeseriesDescriptor[] = [
  nodeMetricLogTimeseries({
    ...commonQueryChart,
    titleOption: {
      title: "Queries/second",
      descriptionKey: "monitor.node.queries.description",
      align: "center",
    },
    metricExpression: "avg(ProfileEvent_Query)",
    metricAlias: "query_qps",
  }),
  nodeMetricLogTimeseries({
    ...commonQueryChart,
    titleOption: {
      title: "Queries Running",
      descriptionKey: "monitor.node.queriesRunning.description",
      align: "center",
    },
    metricExpression: "avg(CurrentMetric_Query)",
    metricAlias: "queries_running",
  }),
  nodeMetricLogTimeseries({
    ...commonQueryChart,
    titleOption: {
      title: "Selected Bytes/second",
      descriptionKey: "monitor.node.selectedBytes.description",
      align: "center",
    },
    fieldOptions: { selected_bytes: { format: "binary_size" } },
    metricExpression: "avg(ProfileEvent_SelectedBytes)",
    metricAlias: "selected_bytes",
  }),
  nodeMetricLogTimeseries({
    ...commonQueryChart,
    titleOption: {
      title: "Selected Rows/second",
      descriptionKey: "monitor.node.selectedRows.description",
      align: "center",
    },
    metricExpression: "avg(ProfileEvent_SelectedRows)",
    metricAlias: "selected_rows_per_second",
  }),
];
