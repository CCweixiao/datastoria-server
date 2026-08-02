import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { nodeAsynchronousMetricTimeseries } from "./node-metric-query-template";

export const nodeSystemMetrics: TimeseriesDescriptor[] = [
  nodeAsynchronousMetricTimeseries({
    type: "line",
    titleOption: {
      title: "Load Average (15 minutes)",
      descriptionKey: "monitor.node.loadAverage.description",
      align: "center",
    },
    gridPos: { w: 6, h: 6 },
    legendOption: { placement: "none", values: ["min", "max", "last"] },
    metric: "LoadAverage15",
  }),
  {
    type: "line",
    titleOption: {
      title: "Concurrent network connections",
      descriptionKey: "monitor.node.networkConnections.description",
      align: "center",
    },
    gridPos: { w: 6, h: 6 },
    tooltipOption: { sortValue: "desc" },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  max(CurrentMetric_TCPConnection) AS TCP_Connections,
  max(CurrentMetric_MySQLConnection) AS MySQL_Connections,
  max(CurrentMetric_HTTPConnection) AS HTTP_Connections,
  max(CurrentMetric_InterserverConnection) AS Interserver_Connections
FROM system.metric_log
WHERE event_date >= toDate({from:String})
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String}
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}`,
    },
  },
];
