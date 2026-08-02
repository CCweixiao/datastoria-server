import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import type { FormatName } from "@/lib/formatter";
import type { MessageKey } from "@/lib/i18n/messages/en";
import { clusterMetricLogTimeseries } from "../cluster-metric-query-template";

function networkMetric(
  title: string,
  profileEvent: string,
  descriptionKey: MessageKey,
  format: FormatName,
  perSecond = false
): TimeseriesDescriptor {
  const expression = `sum(ProfileEvent_${profileEvent})${perSecond ? " / {rounding:UInt32}" : ""}`;
  return clusterMetricLogTimeseries({
    type: "line",
    titleOption: { title, descriptionKey, align: "center" },
    gridPos: { w: 12, h: 6 },
    legendOption: { placement: "bottom", values: ["min", "max", "last"] },
    fieldOptions: { metric: { format } },
    metricExpression: expression,
  });
}

export const networkMetricsDashboard: TimeseriesDescriptor[] = [
  networkMetric(
    "Network Receive Bytes",
    "NetworkReceiveBytes",
    "monitor.cluster.networkReceiveBytes.description",
    "binary_size_per_second",
    true
  ),
  networkMetric(
    "Network Receive Elapsed Microseconds",
    "NetworkReceiveElapsedMicroseconds",
    "monitor.cluster.networkReceiveElapsed.description",
    "microsecond"
  ),
  networkMetric(
    "Network Send Bytes",
    "NetworkSendBytes",
    "monitor.cluster.networkSendBytes.description",
    "binary_size_per_second",
    true
  ),
  networkMetric(
    "Network Send Elapsed Microseconds",
    "NetworkSendElapsedMicroseconds",
    "monitor.cluster.networkSendElapsed.description",
    "microsecond"
  ),
];
