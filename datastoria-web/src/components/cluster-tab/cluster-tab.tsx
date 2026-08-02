import { useConnection } from "@/components/connection/connection-context";
import type {
  DashboardGroup,
  SelectorFilterSpec,
  StatDescriptor,
  TableDescriptor,
  TimeseriesDescriptor,
} from "@/components/shared/dashboard/dashboard-model";
import DashboardPage from "@/components/shared/dashboard/dashboard-page";
import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import type { MessageKey } from "@/lib/i18n/messages/en";
import { memo, useMemo } from "react";
import { filterUnsupportedProfileEventMetrics } from "./cluster-metric-support";
import { cacheMetricsDashboard } from "./groups/cache";
import { cpuMetricsDashboard } from "./groups/cpu";
import { insertMetricsDashboard } from "./groups/inserts";
import { ioMetricsDashboard } from "./groups/io";
import { lockMetricsDashboard } from "./groups/locks";
import { memoryMetricsDashboard } from "./groups/memory";
import { mergeMutationMetricsDashboard } from "./groups/merge-mutation";
import { networkMetricsDashboard } from "./groups/network";
import { selectMetricsDashboard } from "./groups/selects";
import { threadMetricsDashboard } from "./groups/thread";
import { clusterZkMetricsDashboard } from "./groups/zookeeper";

const clusterStatusDashboard: StatDescriptor[] = [
  //
  // Shards
  //
  {
    type: "stat",
    titleOption: {
      title: "Shards",
    },
    gridPos: { w: 4, h: 4 },
    description: "Number of shards in the cluster",
    datasource: {
      sql: `
SELECT 
countDistinct(shard_num) as shard_count
FROM system.clusters
WHERE cluster = '{cluster}'
`,
    },
  } as StatDescriptor,

  //
  // Server Count
  //
  {
    type: "stat",
    titleOption: {
      title: "Server Count",
    },
    gridPos: { w: 4, h: 4 },
    description: "Number of servers in the cluster",
    datasource: {
      sql: `
SELECT 
  count() 
FROM system.clusters
WHERE cluster = '{cluster}'
`,
    },
    drilldown: {
      main: {
        type: "table",
        titleOption: {
          title: "Server Count",
        },
        gridPos: { w: 24, h: 12 },
        miscOption: { enableIndexColumn: true },
        datasource: {
          sql: `SELECT * FROM system.clusters WHERE cluster = '{cluster}'`,
        },
        fieldOptions: {
          host: {
            title: "Host",
          },
        },
      } as TableDescriptor,
    },
  } as StatDescriptor,

  //
  // Total Data Size
  //
  {
    type: "stat",
    titleOption: {
      title: "Total Data Size",
    },
    gridPos: { w: 4, h: 4 },
    description: "Total data size in the cluster",
    datasource: {
      sql: `
SELECT 
sum(bytes_on_disk) as bytes_on_disk
FROM {clusterAllReplicas:system.parts}
WHERE active
`,
    },
    valueOption: {
      format: "binary_size",
    },

    drilldown: {
      main: {
        type: "table",
        titleOption: {
          title: "Disk Space Usage By Server",
        },
        gridPos: { w: 24, h: 12 },
        description: "Number of servers in the cluster",
        datasource: {
          sql: `
SELECT
  FQDN() as host,
  sum(bytes_on_disk) AS bytes_on_disk,
  count(1) as part_count,
  sum(rows) as rows
FROM {clusterAllReplicas:system.parts}
WHERE active
GROUP BY host
ORDER BY host
    `,
        },
        fieldOptions: {
          bytes_on_disk: {
            format: "binary_size",
          },
        },
        sortOption: {
          initialSort: {
            column: "host",
            direction: "asc",
          },
        },
      } as TableDescriptor,
    },
  } as StatDescriptor,

  //
  // Disk Quota
  //
  {
    type: "stat",
    titleOption: {
      title: "Disk Quota",
    },
    gridPos: { w: 4, h: 4 },
    description: "Total data size in the cluster",
    datasource: {
      sql: `
SELECT sum(total_space) FROM {clusterAllReplicas:system.disks}
`,
    },
    valueOption: {
      format: "binary_size",
    },
    drilldown: {
      main: {
        type: "table",
        titleOption: {
          title: "Disk Quota",
        },
        gridPos: { w: 24, h: 12 },
        datasource: {
          sql: `SELECT FQDN() as server, round(free_space * 100 / total_space, 2) as free_percentage, * FROM {clusterAllReplicas:system.disks} ORDER BY server`,
        },
        fieldOptions: {
          free_percentage: {
            format: "percentage_bar",
            // server, name, path
            position: 3,
          },
          free_space: {
            format: "compact_number",
          },
          total_space: {
            format: "compact_number",
          },
          unreserved_space: {
            format: "compact_number",
          },
          keep_free_space: {
            format: "compact_number",
          },
        },
      },
    },
  } as StatDescriptor,

  //
  // Utilized Disk Space
  //
  {
    type: "stat",
    titleOption: {
      title: "Utilized Disk Space",
    },
    gridPos: { w: 4, h: 4 },
    description: "The percentage of utilized disk space of the cluster",
    datasource: {
      sql: `
SELECT 1 - (sum(free_space) / sum(total_space)) FROM {clusterAllReplicas:system.disks}
`,
    },
    valueOption: {
      format: "percentage_0_1",
    },
  } as StatDescriptor,
];

function createClusterDashboardGroups(
  t: (key: MessageKey) => string,
  profileEvents?: ReadonlySet<string>
): DashboardGroup[] {
  const supported = (charts: TimeseriesDescriptor[]) =>
    filterUnsupportedProfileEventMetrics(charts, profileEvents);

  return [
    {
      title: t("monitor.cluster.group.status"),
      collapsed: false,
      charts: clusterStatusDashboard,
    },
    {
      title: t("monitor.cluster.group.cpu"),
      collapsed: false,
      charts: supported(cpuMetricsDashboard),
    },
    {
      title: t("monitor.cluster.group.memory"),
      collapsed: false,
      charts: supported(memoryMetricsDashboard),
    },
    {
      title: t("monitor.cluster.group.io"),
      collapsed: false,
      charts: supported(ioMetricsDashboard),
    },
    {
      title: t("monitor.cluster.group.thread"),
      collapsed: false,
      charts: supported(threadMetricsDashboard),
    },
    {
      title: t("monitor.cluster.group.selects"),
      collapsed: false,
      charts: supported(selectMetricsDashboard),
    },
    {
      title: t("monitor.cluster.group.inserts"),
      collapsed: false,
      charts: supported(insertMetricsDashboard),
    },
    {
      title: t("monitor.cluster.group.locks"),
      collapsed: false,
      charts: supported(lockMetricsDashboard),
    },
    {
      title: t("monitor.cluster.group.cache"),
      collapsed: false,
      charts: supported(cacheMetricsDashboard),
    },
    {
      title: t("monitor.cluster.group.mergeMutation"),
      collapsed: false,
      charts: supported(mergeMutationMetricsDashboard),
    },
    {
      title: t("monitor.cluster.group.network"),
      collapsed: false,
      charts: supported(networkMetricsDashboard),
    },
    {
      title: t("monitor.cluster.group.zookeeper"),
      collapsed: false,
      charts: supported(clusterZkMetricsDashboard),
    },
  ];
}

export const ClusterTab = memo(() => {
  const { t } = useUiPreferences();
  const { connection } = useConnection();
  const dashboardGroups = useMemo(
    () => createClusterDashboardGroups(t, connection?.metadata.profileEvents),
    [connection?.metadata.profileEvents, t]
  );

  return (
    <DashboardPage
      dashboardId="cluster-overview"
      filterSpecs={[
        {
          filterType: "select",
          name: "FQDN()",
          displayText: "FQDN()",
          onPreviousFilters: true,
          datasource: {
            type: "sql",
            sql: `select distinct host_name from system.clusters WHERE cluster = '{cluster}' order by host_name`,
          },
        } as SelectorFilterSpec,
      ]}
      panels={{
        version: 3,
        filter: {},
        charts: dashboardGroups,
      }}
    />
  );
});
