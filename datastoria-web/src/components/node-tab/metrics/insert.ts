import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { nodeMetricLogTimeseries } from "./node-metric-query-template";

export const nodeInsertMetrics: TimeseriesDescriptor[] = [
  nodeMetricLogTimeseries({
    type: "line",
    titleOption: {
      title: "Inserted Rows/second",
      descriptionKey: "monitor.node.insertedRows.description",
      align: "center",
    },
    gridPos: { w: 6, h: 6 },
    legendOption: { placement: "none", values: ["min", "max", "last"] },
    metricExpression: "avg(ProfileEvent_InsertedRows)",
    metricAlias: "inserted_rows_per_second",
  }),
];
