import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { clusterMetricLogTimeseries } from "../cluster-metric-query-template";

const commonCpuChart: Pick<TimeseriesDescriptor, "type" | "gridPos" | "legendOption"> = {
  type: "line",
  gridPos: { w: 12, h: 6 },
  legendOption: { placement: "bottom", values: ["min", "max", "last"] },
};

export const cpuMetricsDashboard: TimeseriesDescriptor[] = [
  clusterMetricLogTimeseries({
    ...commonCpuChart,
    titleOption: {
      title: "CPU Usage (cores)",
      descriptionKey: "monitor.cluster.cpuUsage.description",
      align: "center",
    },
    metricExpression: "avg(ProfileEvent_OSCPUVirtualTimeMicroseconds) / 1000000",
  }),
  clusterMetricLogTimeseries({
    ...commonCpuChart,
    titleOption: {
      title: "CPU Wait",
      descriptionKey: "monitor.cluster.cpuWait.description",
      align: "center",
    },
    metricExpression: "avg(ProfileEvent_OSCPUWaitMicroseconds) / 1000000",
    metricAlias: "cpu_wait",
  }),
];
