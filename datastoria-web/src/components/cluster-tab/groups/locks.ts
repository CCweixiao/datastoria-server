import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { clusterMetricLogTimeseries } from "../cluster-metric-query-template";

const commonLockChart: Pick<TimeseriesDescriptor, "type" | "gridPos" | "legendOption"> = {
  type: "line",
  gridPos: { w: 6, h: 6 },
  legendOption: { placement: "bottom", values: ["min", "max", "last"] },
};

export const lockMetricsDashboard: TimeseriesDescriptor[] = [
  clusterMetricLogTimeseries({
    ...commonLockChart,
    titleOption: {
      title: "ContextLockWait",
      descriptionKey: "monitor.cluster.contextLockWait.description",
      align: "center",
    },
    fieldOptions: { metric: { format: "short_number" } },
    metricExpression: "sum(CurrentMetric_ContextLockWait)",
  }),
  clusterMetricLogTimeseries({
    ...commonLockChart,
    titleOption: {
      title: "ContextLockWaitMicroseconds",
      descriptionKey: "monitor.cluster.contextLockWaitMicroseconds.description",
      align: "center",
    },
    fieldOptions: { metric: { format: "microsecond" } },
    metricExpression: "sum(ProfileEvent_ContextLockWaitMicroseconds)",
  }),
  clusterMetricLogTimeseries({
    ...commonLockChart,
    titleOption: {
      title: "ProcessSelectListLock",
      titleKey: "monitor.cluster.processSelectListLock.title",
      descriptionKey: "monitor.cluster.processSelectListLock.description",
      align: "center",
    },
    fieldOptions: { metric: { format: "short_number" } },
    metricExpression: "sum(ProfileEvent_ProcessSelectListLock)",
  }),
  clusterMetricLogTimeseries({
    ...commonLockChart,
    titleOption: {
      title: "RWLockAcquiredReadLocks",
      descriptionKey: "monitor.cluster.rwLockAcquiredReadLocks.description",
      align: "center",
    },
    fieldOptions: { metric: { format: "short_number" } },
    metricExpression: "sum(ProfileEvent_RWLockAcquiredReadLocks)",
  }),
  clusterMetricLogTimeseries({
    ...commonLockChart,
    titleOption: {
      title: "RWLockAcquiredWriteLocks",
      descriptionKey: "monitor.cluster.rwLockAcquiredWriteLocks.description",
      align: "center",
    },
    fieldOptions: { metric: { format: "short_number" } },
    metricExpression: "sum(ProfileEvent_RWLockAcquiredWriteLocks)",
  }),
  clusterMetricLogTimeseries({
    ...commonLockChart,
    titleOption: {
      title: "RWLockReadersWaitMilliseconds",
      descriptionKey: "monitor.cluster.rwLockReadersWaitMilliseconds.description",
      align: "center",
    },
    fieldOptions: { metric: { format: "millisecond" } },
    metricExpression: "sum(ProfileEvent_RWLockReadersWaitMilliseconds)",
  }),
  clusterMetricLogTimeseries({
    ...commonLockChart,
    titleOption: {
      title: "RWLockWritersWaitMilliseconds",
      descriptionKey: "monitor.cluster.rwLockWritersWaitMilliseconds.description",
      align: "center",
    },
    fieldOptions: { metric: { format: "millisecond" } },
    metricExpression: "sum(ProfileEvent_RWLockWritersWaitMilliseconds)",
  }),
];
