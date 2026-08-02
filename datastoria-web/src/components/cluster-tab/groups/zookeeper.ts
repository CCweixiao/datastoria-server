import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import type { FormatName } from "@/lib/formatter";
import type { MessageKey } from "@/lib/i18n/messages/en";
import { clusterMetricLogTimeseries } from "../cluster-metric-query-template";

interface ZooKeeperMetricDefinition {
  title: string;
  event: string;
  descriptionKey: MessageKey;
  format?: FormatName;
  perSecond?: boolean;
  wide?: boolean;
  showLegend?: boolean;
}

function zookeeperMetric(definition: ZooKeeperMetricDefinition): TimeseriesDescriptor {
  const expression = `sum(ProfileEvent_${definition.event})${
    definition.perSecond ? " / {rounding:UInt32}" : ""
  }`;
  return clusterMetricLogTimeseries({
    type: "line",
    titleOption: {
      title: definition.title,
      descriptionKey: definition.descriptionKey,
      align: "center",
    },
    gridPos: { w: definition.wide ? 12 : 6, h: 6 },
    legendOption: definition.showLegend
      ? { placement: "bottom", values: ["min", "max", "last"] }
      : { placement: "none" },
    fieldOptions: definition.format ? { metric: { format: definition.format } } : undefined,
    metricExpression: expression,
  });
}

const operation = (
  title: string,
  event: string,
  descriptionKey: MessageKey
): ZooKeeperMetricDefinition => ({ title, event, descriptionKey });

export const clusterZkMetricsDashboard: TimeseriesDescriptor[] = [
  zookeeperMetric({
    title: "ZooKeeper Bytes Sent",
    event: "ZooKeeperBytesSent",
    descriptionKey: "monitor.cluster.zookeeperBytesSent.description",
    format: "binary_size_per_second",
    perSecond: true,
    wide: true,
    showLegend: true,
  }),
  zookeeperMetric({
    title: "ZooKeeper Bytes Received",
    event: "ZooKeeperBytesReceived",
    descriptionKey: "monitor.cluster.zookeeperBytesReceived.description",
    format: "binary_size_per_second",
    perSecond: true,
    wide: true,
    showLegend: true,
  }),
  zookeeperMetric({
    title: "ZooKeeper Transactions",
    event: "ZooKeeperTransactions",
    descriptionKey: "monitor.cluster.zookeeperTransactions.description",
    showLegend: true,
  }),
  zookeeperMetric({
    title: "ZooKeeper Wait Microseconds",
    event: "ZooKeeperWaitMicroseconds",
    descriptionKey: "monitor.cluster.zookeeperWait.description",
    format: "microsecond",
    showLegend: true,
  }),
  ...[
    operation("ZooKeeper Check", "ZooKeeperCheck", "monitor.cluster.zookeeperCheck.description"),
    operation("ZooKeeper Close", "ZooKeeperClose", "monitor.cluster.zookeeperClose.description"),
    operation("ZooKeeper Create", "ZooKeeperCreate", "monitor.cluster.zookeeperCreate.description"),
    operation("ZooKeeper Exists", "ZooKeeperExists", "monitor.cluster.zookeeperExists.description"),
    operation("ZooKeeper Get", "ZooKeeperGet", "monitor.cluster.zookeeperGet.description"),
    operation(
      "ZooKeeper Hardware Exceptions",
      "ZooKeeperHardwareExceptions",
      "monitor.cluster.zookeeperHardwareExceptions.description"
    ),
    operation("ZooKeeper Init", "ZooKeeperInit", "monitor.cluster.zookeeperInit.description"),
    operation("ZooKeeper List", "ZooKeeperList", "monitor.cluster.zookeeperList.description"),
    operation("ZooKeeper Multi", "ZooKeeperMulti", "monitor.cluster.zookeeperMulti.description"),
    operation(
      "ZooKeeper Multi Read",
      "ZooKeeperMultiRead",
      "monitor.cluster.zookeeperMultiRead.description"
    ),
    operation(
      "ZooKeeper Multi Write",
      "ZooKeeperMultiWrite",
      "monitor.cluster.zookeeperMultiWrite.description"
    ),
    operation(
      "ZooKeeper Other Exceptions",
      "ZooKeeperOtherExceptions",
      "monitor.cluster.zookeeperOtherExceptions.description"
    ),
    operation(
      "ZooKeeper Reconfig",
      "ZooKeeperReconfig",
      "monitor.cluster.zookeeperReconfig.description"
    ),
    operation("ZooKeeper Remove", "ZooKeeperRemove", "monitor.cluster.zookeeperRemove.description"),
    operation("ZooKeeper Set", "ZooKeeperSet", "monitor.cluster.zookeeperSet.description"),
    operation("ZooKeeper Sync", "ZooKeeperSync", "monitor.cluster.zookeeperSync.description"),
    operation(
      "ZooKeeper User Exceptions",
      "ZooKeeperUserExceptions",
      "monitor.cluster.zookeeperUserExceptions.description"
    ),
    operation(
      "ZooKeeper Watch Response",
      "ZooKeeperWatchResponse",
      "monitor.cluster.zookeeperWatchResponse.description"
    ),
  ].map(zookeeperMetric),
];
