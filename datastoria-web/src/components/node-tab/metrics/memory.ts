import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { nodeMetricLogTimeseries } from "./node-metric-query-template";

const commonMemoryChart: Pick<TimeseriesDescriptor, "type" | "gridPos" | "legendOption"> = {
  type: "line",
  gridPos: { w: 6, h: 6 },
  legendOption: { placement: "none", values: ["min", "max", "last"] },
};

export const nodeMemoryMetrics: TimeseriesDescriptor[] = [
  nodeMetricLogTimeseries({
    ...commonMemoryChart,
    titleOption: {
      title: "Memory (tracked)",
      descriptionKey: "monitor.node.memoryTracking.description",
      align: "center",
    },
    fieldOptions: { memory_tracking_bytes: { format: "binary_size" } },
    metricExpression: "avg(CurrentMetric_MemoryTracking)",
    metricAlias: "memory_tracking_bytes",
  }),
  nodeMetricLogTimeseries({
    ...commonMemoryChart,
    titleOption: {
      title: "In-Memory Caches (bytes)",
      descriptionKey: "monitor.node.memoryCaches.description",
      align: "center",
    },
    fieldOptions: { cache_bytes: { format: "binary_size" } },
    metricExpression:
      "arraySum([COLUMNS('CurrentMetric_.*CacheBytes') EXCEPT 'CurrentMetric_FilesystemCache.*' APPLY avg])",
    metricAlias: "cache_bytes",
  }),
];
