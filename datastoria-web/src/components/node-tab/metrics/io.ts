import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { nodeMetricLogTimeseries } from "./node-metric-query-template";

const commonIoChart: Pick<TimeseriesDescriptor, "type" | "gridPos" | "legendOption"> = {
  type: "line",
  gridPos: { w: 6, h: 6 },
  legendOption: { placement: "none", values: ["min", "max", "last"] },
};

export const nodeIoMetrics: TimeseriesDescriptor[] = [
  nodeMetricLogTimeseries({
    ...commonIoChart,
    titleOption: {
      title: "IO Wait",
      descriptionKey: "monitor.node.ioWait.description",
      align: "center",
    },
    metricExpression: "avg(ProfileEvent_OSIOWaitMicroseconds) / 1000000",
    metricAlias: "io_wait",
  }),
  nodeMetricLogTimeseries({
    ...commonIoChart,
    titleOption: {
      title: "Read From Disk",
      descriptionKey: "monitor.node.readDisk.description",
      align: "center",
    },
    fieldOptions: { OSReadBytes: { format: "binary_size" } },
    metricExpression: "avg(ProfileEvent_OSReadBytes)",
    metricAlias: "OSReadBytes",
  }),
  nodeMetricLogTimeseries({
    ...commonIoChart,
    titleOption: {
      title: "Read From Filesystem",
      descriptionKey: "monitor.node.readFilesystem.description",
      align: "center",
    },
    metricExpression: "avg(ProfileEvent_OSReadChars)",
    metricAlias: "OSReadChars",
  }),
];
