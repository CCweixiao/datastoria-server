import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import type { FormatName } from "@/lib/formatter";
import type { MessageKey } from "@/lib/i18n/messages/en";
import {
  clusterMetricLogRollupTimeseries,
  clusterMetricLogTimeseries,
} from "../cluster-metric-query-template";

const commonMergeChart: Pick<TimeseriesDescriptor, "type" | "legendOption"> = {
  type: "line",
  legendOption: { placement: "bottom", values: ["min", "max", "last"] },
};

function mergeRateMetric(
  title: string,
  profileEvent: string,
  descriptionKey: MessageKey,
  format: FormatName
): TimeseriesDescriptor {
  return clusterMetricLogTimeseries({
    ...commonMergeChart,
    titleOption: { title, descriptionKey, align: "center" },
    gridPos: { w: 8, h: 6 },
    fieldOptions: { metric: { format } },
    metricExpression: `sum(ProfileEvent_${profileEvent}) / {rounding:UInt32}`,
    groupBy: "1, 2",
    orderBy: "1",
  });
}

function backgroundPoolMetric(
  title: string,
  currentMetric: string,
  descriptionKey: MessageKey
): TimeseriesDescriptor {
  return clusterMetricLogRollupTimeseries({
    ...commonMergeChart,
    titleOption: { title, descriptionKey, align: "center" },
    gridPos: { w: 6, h: 6 },
    fieldOptions: { metric: { format: "short_number" } },
    innerMetricExpression: `sum(CurrentMetric_${currentMetric})`,
  });
}

function replicationMetric(
  title: string,
  profileEvent: string,
  descriptionKey: MessageKey
): TimeseriesDescriptor {
  return clusterMetricLogTimeseries({
    ...commonMergeChart,
    titleOption: { title, descriptionKey, align: "center" },
    gridPos: { w: 6, h: 6 },
    fieldOptions: { metric: { format: "short_number" } },
    metricExpression: `sum(ProfileEvent_${profileEvent})`,
  });
}

export const mergeMutationMetricsDashboard: TimeseriesDescriptor[] = [
  clusterMetricLogTimeseries({
    ...commonMergeChart,
    titleOption: {
      title: "MergeSourceParts",
      titleKey: "monitor.cluster.mergeSourceParts.title",
      descriptionKey: "monitor.cluster.mergeSourceParts.description",
      align: "center",
    },
    gridPos: { w: 8, h: 6 },
    fieldOptions: { metric: { format: "short_number" } },
    metricExpression: "sum(ProfileEvent_MergeSourceParts)",
    groupBy: "1, 2",
    orderBy: "1",
  }),
  mergeRateMetric(
    "MergedRows Per Second",
    "MergedRows",
    "monitor.cluster.mergedRows.description",
    "rate"
  ),
  mergeRateMetric(
    "Uncompressed Bytes Read For Merge Per Second",
    "MergedUncompressedBytes",
    "monitor.cluster.mergedBytes.description",
    "binary_size_per_second"
  ),
  backgroundPoolMetric(
    "BackgroundFetchesPoolTask",
    "BackgroundFetchesPoolTask",
    "monitor.cluster.backgroundFetchesPoolTask.description"
  ),
  backgroundPoolMetric(
    "BackgroundFetchesPoolSize",
    "BackgroundFetchesPoolSize",
    "monitor.cluster.backgroundFetchesPoolSize.description"
  ),
  backgroundPoolMetric(
    "BackgroundMessageBrokerSchedulePoolTask",
    "BackgroundMessageBrokerSchedulePoolTask",
    "monitor.cluster.backgroundMessageBrokerPoolTask.description"
  ),
  backgroundPoolMetric(
    "BackgroundMergesAndMutationsPoolSize",
    "BackgroundMergesAndMutationsPoolSize",
    "monitor.cluster.backgroundMergeMutationPoolSize.description"
  ),
  replicationMetric(
    "ReplicatedPartFailedFetches",
    "ReplicatedPartFailedFetches",
    "monitor.cluster.replicatedPartFailedFetches.description"
  ),
  replicationMetric(
    "ReplicatedPartFetches",
    "ReplicatedPartFetches",
    "monitor.cluster.replicatedPartFetches.description"
  ),
  replicationMetric(
    "ReplicatedPartFetchesOfMerged",
    "ReplicatedPartFetchesOfMerged",
    "monitor.cluster.replicatedPartFetchesOfMerged.description"
  ),
  replicationMetric(
    "ReplicatedPartMerges",
    "ReplicatedPartMerges",
    "monitor.cluster.replicatedPartMerges.description"
  ),
  replicationMetric(
    "ReplicatedPartMutations",
    "ReplicatedPartMutations",
    "monitor.cluster.replicatedPartMutations.description"
  ),
];
