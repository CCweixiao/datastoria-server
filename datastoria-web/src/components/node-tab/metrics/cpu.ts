import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import {
  nodeAsynchronousMetricTimeseries,
  nodeMetricLogTimeseries,
} from "./node-metric-query-template";

const commonCpuChart: Pick<TimeseriesDescriptor, "type" | "gridPos" | "legendOption"> = {
  type: "line",
  gridPos: { w: 6, h: 6 },
  legendOption: { placement: "none", values: ["min", "max", "last"] },
};

export const nodeCpuMetrics: TimeseriesDescriptor[] = [
  nodeMetricLogTimeseries({
    ...commonCpuChart,
    titleOption: {
      title: "CPU Usage (cores)",
      descriptionKey: "monitor.node.cpuUsage.description",
      align: "center",
    },
    metricExpression: "avg(ProfileEvent_OSCPUVirtualTimeMicroseconds) / 1000000",
    metricAlias: "cpu_cores",
  }),
  nodeMetricLogTimeseries({
    ...commonCpuChart,
    titleOption: {
      title: "CPU Wait",
      descriptionKey: "monitor.node.cpuWait.description",
      align: "center",
    },
    metricExpression: "avg(ProfileEvent_OSCPUWaitMicroseconds) / 1000000",
    metricAlias: "cpu_wait",
  }),
  nodeAsynchronousMetricTimeseries({
    ...commonCpuChart,
    titleOption: {
      title: "OS CPU Usage (Userspace)",
      descriptionKey: "monitor.node.cpuUserspace.description",
      align: "center",
    },
    metric: "OSUserTimeNormalized",
  }),
  nodeAsynchronousMetricTimeseries({
    ...commonCpuChart,
    titleOption: {
      title: "OS CPU Usage (Kernel)",
      descriptionKey: "monitor.node.cpuKernel.description",
      align: "center",
    },
    metric: "OSSystemTimeNormalized",
  }),
];
