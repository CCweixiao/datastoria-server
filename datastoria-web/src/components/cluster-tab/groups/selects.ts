import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import type { FormatName } from "@/lib/formatter";
import type { MessageKey } from "@/lib/i18n/messages/en";
import {
  clusterMetricLogRollupTimeseries,
  clusterMetricLogTimeseries,
} from "../cluster-metric-query-template";

const commonSelectChart: Pick<TimeseriesDescriptor, "type" | "gridPos" | "legendOption"> = {
  type: "line",
  gridPos: { w: 6, h: 6 },
  legendOption: { placement: "bottom", values: ["min", "max", "last"] },
};

function selectRollupMetric(
  title: string,
  profileEvent: string,
  descriptionKey: MessageKey,
  format?: FormatName
): TimeseriesDescriptor {
  return clusterMetricLogRollupTimeseries({
    ...commonSelectChart,
    titleOption: { title, descriptionKey, align: "center" },
    fieldOptions: format ? { metric: { format } } : undefined,
    innerMetricExpression: `sum(ProfileEvent_${profileEvent})`,
  });
}

function selectRateMetric(
  title: string,
  profileEvent: string,
  descriptionKey: MessageKey,
  format: FormatName
): TimeseriesDescriptor {
  return clusterMetricLogTimeseries({
    ...commonSelectChart,
    titleOption: { title, descriptionKey, align: "center" },
    fieldOptions: { metric: { format } },
    metricExpression: `sum(ProfileEvent_${profileEvent}) / {rounding:UInt32}`,
  });
}

export const selectMetricsDashboard: TimeseriesDescriptor[] = [
  {
    ...selectRollupMetric(
      "Select Queries Per Second",
      "SelectQuery",
      "monitor.cluster.selectQueries.description"
    ),
    tooltipOption: { sortValue: "desc" },
  },
  {
    ...selectRollupMetric(
      "Failed Queries Per Second",
      "FailedQuery",
      "monitor.cluster.failedQueries.description"
    ),
    tooltipOption: { sortValue: "none" },
  },
  selectRateMetric(
    "SelectedRows Per Second",
    "SelectedRows",
    "monitor.cluster.selectedRows.description",
    "rate"
  ),
  selectRateMetric(
    "SelectedBytes Per Second",
    "SelectedBytes",
    "monitor.cluster.selectedBytes.description",
    "binary_size_per_second"
  ),
  selectRollupMetric(
    "SelectedParts",
    "SelectedParts",
    "monitor.cluster.selectedParts.description",
    "short_number"
  ),
  selectRollupMetric(
    "SelectedPartsTotal",
    "SelectedPartsTotal",
    "monitor.cluster.selectedPartsTotal.description",
    "short_number"
  ),
  selectRollupMetric(
    "SelectedRanges",
    "SelectedRanges",
    "monitor.cluster.selectedRanges.description",
    "short_number"
  ),
  selectRollupMetric(
    "SelectedMarks",
    "SelectedMarks",
    "monitor.cluster.selectedMarks.description",
    "short_number"
  ),
  selectRollupMetric(
    "SelectedMarksTotal",
    "SelectedMarksTotal",
    "monitor.cluster.selectedMarksTotal.description",
    "short_number"
  ),
];
