import { useConnection } from "@/components/connection/connection-context";
import type { Dashboard, DashboardGroup } from "@/components/shared/dashboard/dashboard-model";
import DashboardPage from "@/components/shared/dashboard/dashboard-page";
import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { memo, useMemo } from "react";
import { nodeMergeDashboard } from "./dashboards/node-merge";
import { nodeOverviewDashboard } from "./dashboards/node-overview";
import { nodeReplicationDashboard } from "./dashboards/node-replication";
import { nodeZkMetricsDashboard } from "./dashboards/node-zk-metrics";
import { queryDashboard } from "./dashboards/query";
import { nodeCpuMetrics } from "./metrics/cpu";
import { nodeInsertMetrics } from "./metrics/insert";
import { nodeIoMetrics } from "./metrics/io";
import { nodeMemoryMetrics } from "./metrics/memory";
import { nodeMergeMetrics } from "./metrics/merge";
import { filterUnsupportedNodeMetrics } from "./metrics/node-metric-support";
import { nodeQueryMetrics } from "./metrics/query";
import { nodeSystemMetrics } from "./metrics/system";

interface NodeTabProps {
  host: string;
}

export const NodeTab = memo((_props: NodeTabProps) => {
  const { connection } = useConnection();
  const { t } = useUiPreferences();

  const dashboard = useMemo<Dashboard | null>(() => {
    if (!connection) return null;
    const supported = filterUnsupportedNodeMetrics;
    const profileEvents = connection.metadata.profileEvents;
    const group = (title: string, charts: DashboardGroup["charts"], collapsed = false) => ({
      title,
      collapsed,
      charts,
    });

    return {
      version: 3,
      filter: {},
      charts: [
        group(t("monitor.node.group.overview"), nodeOverviewDashboard),
        group(t("monitor.node.group.queries"), queryDashboard),
        group(t("monitor.node.group.merges"), supported(nodeMergeDashboard, profileEvents)),
        group(t("monitor.node.group.replication"), nodeReplicationDashboard),
        group(t("monitor.node.group.system"), supported(nodeSystemMetrics, profileEvents)),
        group(t("monitor.node.group.cpu"), supported(nodeCpuMetrics, profileEvents)),
        group(
          t("monitor.node.group.memoryAndIo"),
          supported([...nodeMemoryMetrics, ...nodeIoMetrics], profileEvents)
        ),
        group(t("monitor.node.group.queryMetrics"), supported(nodeQueryMetrics, profileEvents)),
        group(
          t("monitor.node.group.mergeMetrics"),
          supported([...nodeInsertMetrics, ...nodeMergeMetrics], profileEvents)
        ),
        group(
          t("monitor.node.group.zookeeper"),
          supported(nodeZkMetricsDashboard, profileEvents),
          true
        ),
      ],
    };
  }, [connection, t]);

  if (!connection || !dashboard) {
    return (
      <div className="flex h-full items-center justify-center px-4 text-sm text-muted-foreground">
        Connect a ClickHouse cluster to view node dashboards.
      </div>
    );
  }

  return (
    <div className="flex flex-col" style={{ height: "calc(100vh - 49px)" }}>
      <DashboardPage dashboardId="node-overview" panels={dashboard} headerActions={null} />
    </div>
  );
});
