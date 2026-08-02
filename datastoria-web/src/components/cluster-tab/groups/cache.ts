import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import type { FormatName } from "@/lib/formatter";
import type { MessageKey } from "@/lib/i18n/messages/en";
import { clusterMetricLogTimeseries } from "../cluster-metric-query-template";

function cacheMetric(
  title: string,
  profileEvent: string,
  descriptionKey: MessageKey,
  format: FormatName = "short_number"
): TimeseriesDescriptor {
  return clusterMetricLogTimeseries({
    type: "line",
    titleOption: { title, descriptionKey, align: "center" },
    gridPos: { w: 6, h: 6 },
    legendOption: { placement: "bottom", values: ["min", "max", "last"] },
    fieldOptions: { metric: { format } },
    metricExpression: `sum(ProfileEvent_${profileEvent})`,
  });
}

export const cacheMetricsDashboard: TimeseriesDescriptor[] = [
  cacheMetric(
    "UncompressedCacheHits",
    "UncompressedCacheHits",
    "monitor.cluster.uncompressedCacheHits.description"
  ),
  cacheMetric(
    "UncompressedCacheMisses",
    "UncompressedCacheMisses",
    "monitor.cluster.uncompressedCacheMisses.description"
  ),
  cacheMetric(
    "UncompressedCacheWeightLost",
    "UncompressedCacheWeightLost",
    "monitor.cluster.uncompressedCacheWeightLost.description",
    "binary_size"
  ),
  cacheMetric("MarkCacheHits", "MarkCacheHits", "monitor.cluster.markCacheHits.description"),
  cacheMetric("MarkCacheMisses", "MarkCacheMisses", "monitor.cluster.markCacheMisses.description"),
  cacheMetric("QueryCacheHits", "QueryCacheHits", "monitor.cluster.queryCacheHits.description"),
  cacheMetric(
    "QueryCacheMisses",
    "QueryCacheMisses",
    "monitor.cluster.queryCacheMisses.description"
  ),
];
