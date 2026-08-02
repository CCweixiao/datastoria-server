import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import {
  nodeAsynchronousMetricTimeseries,
  nodeMetricLogTimeseries,
} from "./node-metric-query-template";

const commonMergeChart: Pick<TimeseriesDescriptor, "type" | "gridPos" | "legendOption"> = {
  type: "line",
  gridPos: { w: 6, h: 6 },
  legendOption: { placement: "none", values: ["min", "max", "last"] },
};

export const nodeMergeMetrics: TimeseriesDescriptor[] = [
  nodeMetricLogTimeseries({
    ...commonMergeChart,
    titleOption: {
      title: "Merges Running",
      descriptionKey: "monitor.node.mergesRunning.description",
      align: "center",
    },
    metricExpression: "avg(CurrentMetric_Merge)",
    metricAlias: "merges_running",
  }),
  nodeMetricLogTimeseries({
    ...commonMergeChart,
    titleOption: {
      title: "Total MergeTree Parts",
      descriptionKey: "monitor.node.mergeSourceParts.description",
      align: "center",
    },
    metricExpression: "avg(ProfileEvent_MergeSourceParts)",
    metricAlias: "TotalPartsOfMergeTreeTables",
  }),
  nodeAsynchronousMetricTimeseries({
    ...commonMergeChart,
    titleOption: {
      title: "Max Parts For Partition",
      descriptionKey: "monitor.node.maxPartsForPartition.description",
      align: "center",
    },
    metric: "MaxPartCountForPartition",
    aggregation: "max",
  }),
];
