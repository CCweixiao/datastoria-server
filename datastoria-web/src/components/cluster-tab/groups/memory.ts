import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { clusterMetricLogTimeseries } from "../cluster-metric-query-template";

export const memoryMetricsDashboard: TimeseriesDescriptor[] = [
  clusterMetricLogTimeseries({
    type: "line",
    titleOption: {
      title: "CurrentMetric_MemoryTracking",
      descriptionKey: "monitor.cluster.memoryTracking.description",
      align: "center",
    },
    gridPos: { w: 12, h: 6 },
    legendOption: { placement: "bottom", values: ["min", "max", "last"] },
    fieldOptions: { metric: { format: "binary_size" } },
    metricExpression: "avg(CurrentMetric_MemoryTracking)",
  }),
];
