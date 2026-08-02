import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import type { MessageKey } from "@/lib/i18n/messages/en";
import { clusterMetricLogTimeseries } from "../cluster-metric-query-template";

function ioRateMetric(
  title: string,
  profileEvent: string,
  descriptionKey: MessageKey
): TimeseriesDescriptor {
  return clusterMetricLogTimeseries({
    type: "line",
    titleOption: { title, descriptionKey, align: "center" },
    gridPos: { w: 6, h: 6 },
    legendOption: { placement: "bottom", values: ["min", "max", "last"] },
    fieldOptions: { metric: { format: "rate" } },
    metricExpression: `sum(ProfileEvent_${profileEvent}) / {rounding:UInt32}`,
  });
}

export const ioMetricsDashboard: TimeseriesDescriptor[] = [
  ioRateMetric(
    "Read From FileSystem Per Second",
    "OSReadChars",
    "monitor.cluster.readFileSystem.description"
  ),
  ioRateMetric("Read From Disk Per Second", "OSReadBytes", "monitor.cluster.readDisk.description"),
  ioRateMetric(
    "Write To FileSystem Per Second",
    "OSWriteChars",
    "monitor.cluster.writeFileSystem.description"
  ),
  ioRateMetric("Write To Disk Per Second", "OSWriteBytes", "monitor.cluster.writeDisk.description"),
  clusterMetricLogTimeseries({
    type: "line",
    titleOption: {
      title: "IO Wait",
      descriptionKey: "monitor.cluster.ioWait.description",
      align: "center",
    },
    gridPos: { w: 6, h: 6 },
    legendOption: { placement: "bottom", values: ["min", "max", "last"] },
    metricExpression: "avg(ProfileEvent_OSIOWaitMicroseconds) / 1000000",
    metricAlias: "io_wait",
  }),
];
