import { useConnection } from "@/components/connection/connection-context";
import { DashboardGroupSection } from "@/components/shared/dashboard/dashboard-group-section";
import type {
  Dashboard,
  DashboardGroup,
  TimeseriesDescriptor,
} from "@/components/shared/dashboard/dashboard-model";
import DashboardPage from "@/components/shared/dashboard/dashboard-page";
import { ThemedSyntaxHighlighter } from "@/components/shared/themed-syntax-highlighter";
import { Dialog } from "@/components/shared/use-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { type JSONFormatResponse, type QueryResponse } from "@/lib/connection/connection";
import { hostNameManager } from "@/lib/host-name-manager";
import { AlertTriangle, EllipsisVertical, Network, Server } from "lucide-react";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { scopeDashboardQueryToCluster } from "./dashboard-scope";

interface DashboardRow {
  dashboard: string;
  title: string;
  query: string;
}

interface SkippedDashboard {
  dashboard: string;
  title: string;
  query: string;
  reason: string;
}

interface DashboardsProps {
  database: string;
  table: string;
}

export const Dashboards = memo(({ database, table }: DashboardsProps) => {
  const { connection } = useConnection();
  const clusterNodes = useMemo(
    () => connection?.metadata.clusterNodes ?? [],
    [connection?.metadata.clusterNodes]
  );
  const [monitorScope, setMonitorScope] = useState("cluster");
  const [dashboard, setDashboard] = useState<Dashboard>({
    version: 3,
    filter: {},
    charts: [],
  });
  const [error, setError] = useState<string | null>(null);
  const [skippedDashboards, setSkippedDashboards] = useState<SkippedDashboard[]>([]);
  const previousConnectionRef = useRef<string | null>(null);
  const selectedNode =
    monitorScope === "cluster"
      ? undefined
      : clusterNodes.find((node) => node.hostName === monitorScope);

  useEffect(() => {
    if (clusterNodes.length <= 1) {
      setMonitorScope(clusterNodes[0]?.hostName ?? "cluster");
    } else if (
      monitorScope !== "cluster" &&
      !clusterNodes.some((node) => node.hostName === monitorScope)
    ) {
      setMonitorScope("cluster");
    }
  }, [clusterNodes, monitorScope]);

  const scopeDashboardSql = useCallback(
    (sql: string) => {
      if (
        monitorScope !== "cluster" ||
        !(connection?.cluster || connection?.metadata.detectedCluster) ||
        clusterNodes.length <= 1
      ) {
        return sql;
      }
      return scopeDashboardQueryToCluster(sql);
    },
    [clusterNodes.length, connection?.cluster, connection?.metadata.detectedCluster, monitorScope]
  );

  const fetchDashboards = useCallback(
    (hasMetricLogTable: boolean, hasAsynchronousMetricLogTable: boolean) => {
      // Fetch dashboard definitions from system.dashboards (without predefined dashboard)
      if (!connection) return;

      connection
        .query(
          "SELECT dashboard, title, query FROM system.dashboards ORDER BY dashboard, title",
          {
            default_format: "JSON",
            output_format_json_quote_64bit_integers: 0,
          },
          {
            "Content-Type": "text/plain",
          }
        )
        .response.then((response: QueryResponse) => {
          try {
            const responseData = response.data.json<JSONFormatResponse>();
            const rows = responseData.data || [];
            const meta = responseData.meta || [];

            // Build column map
            const columnMap = new Map<string, number>();
            meta.forEach((colMeta: { name: string }, index: number) => {
              columnMap.set(colMeta.name, index);
            });

            // Group rows by dashboard category
            const dashboardMap = new Map<string, DashboardRow[]>();

            // Check if rows are arrays or objects
            const firstRow = rows[0];
            const isArrayFormat = Array.isArray(firstRow);

            rows.forEach((row: unknown) => {
              let dashboardName: string;
              let title: string;
              let query: string;

              if (isArrayFormat) {
                // Array format: row is [value1, value2, ...]
                const rowArray = row as unknown[];
                const dashboardIndex = columnMap.get("dashboard");
                const titleIndex = columnMap.get("title");
                const queryIndex = columnMap.get("query");

                if (
                  dashboardIndex === undefined ||
                  titleIndex === undefined ||
                  queryIndex === undefined
                ) {
                  return;
                }

                dashboardName = String(rowArray[dashboardIndex] ?? "");
                title = String(rowArray[titleIndex] ?? "");
                query = String(rowArray[queryIndex] ?? "");
              } else {
                // Object format: row is {column1: value1, column2: value2, ...}
                const rowObject = row as Record<string, unknown>;
                dashboardName = String(rowObject["dashboard"] ?? "");
                title = String(rowObject["title"] ?? "");
                query = String(rowObject["query"] ?? "");
              }

              // Validate extracted values
              if (!dashboardName || dashboardName === "undefined" || !title || !query) {
                return;
              }

              if (!dashboardMap.has(dashboardName)) {
                dashboardMap.set(dashboardName, []);
              }
              dashboardMap.get(dashboardName)!.push({ dashboard: dashboardName, title, query });
            });

            // Convert each row to a timeseries chart, grouped by dashboard name
            const dashboardGroups: DashboardGroup[] = [];
            const skipped: SkippedDashboard[] = [];

            dashboardMap.forEach((dashboardRows, dashboardName) => {
              const groupCharts: TimeseriesDescriptor[] = [];

              dashboardRows.forEach((row, index) => {
                // Validate row data
                if (!row.title || !row.query) {
                  return;
                }

                // Check if query references metric_log or asynchronous_metric_log
                const queryLower = row.query.toLowerCase();
                const referencesMetricLog =
                  queryLower.includes("metric_log") &&
                  !queryLower.includes("asynchronous_metric_log");
                const referencesAsynchronousMetricLog =
                  queryLower.includes("asynchronous_metric_log");

                // Skip if query references metric_log but table doesn't exist
                if (referencesMetricLog && !hasMetricLogTable) {
                  skipped.push({
                    dashboard: row.dashboard,
                    title: row.title,
                    query: row.query,
                    reason: "metric_log table not available",
                  });
                  return;
                }

                // Skip if query references asynchronous_metric_log but table doesn't exist
                if (referencesAsynchronousMetricLog && !hasAsynchronousMetricLogTable) {
                  skipped.push({
                    dashboard: row.dashboard,
                    title: row.title,
                    query: row.query,
                    reason: "asynchronous_metric_log table not available",
                  });
                  return;
                }

                const chartTitle = row.title || `Chart ${index}`;

                groupCharts.push({
                  type: "line" as const,
                  titleOption: {
                    title: chartTitle,
                  },
                  gridPos: { w: 6, h: 6 }, // Default size for charts
                  collapsed: false,
                  yAxis: [{}], // Default y-axis
                  datasource: {
                    sql: scopeDashboardSql(row.query),
                    targetNode: selectedNode?.hostAddress,
                  },
                });
              });

              // Create a group for this dashboard name if it has charts
              if (groupCharts.length > 0) {
                dashboardGroups.push({
                  title: dashboardName,
                  charts: groupCharts,
                  collapsed: true,
                });
              }
            });

            // Track skipped dashboards in state for separate rendering
            setSkippedDashboards(skipped);

            const mergedDashboard: Dashboard = {
              name: "dashboard",
              version: 3,
              filter: {},
              charts: dashboardGroups,
            };

            setDashboard(mergedDashboard);
            setError(null);
          } catch (err) {
            console.error("Error processing dashboard data:", err);
            setError(null);
          }
        })
        .catch((error) => {
          console.error("Error fetching dashboard data:", error);
          setError(null);
        });
    },
    [connection, scopeDashboardSql, selectedNode?.hostAddress]
  );

  useEffect(() => {
    if (!connection) {
      return;
    }

    // Skip if connection hasn't changed
    const connectionId = `${connection.connectionId}:${monitorScope}`;
    if (previousConnectionRef.current === connectionId) {
      return;
    }
    previousConnectionRef.current = connectionId;

    // First, check if metric_log and asynchronous_metric_log tables exist
    connection
      .query(
        `SELECT name FROM system.tables WHERE database = 'system' AND (name LIKE 'metric_log%' OR name LIKE 'asynchronous_metric_log%')`,
        {
          default_format: "JSON",
          output_format_json_quote_64bit_integers: 0,
        },
        {
          "Content-Type": "text/plain",
        }
      )
      .response.then((response: QueryResponse) => {
        try {
          const responseData = response.data.json<JSONFormatResponse>();
          const rows = responseData.data || [];
          const meta = responseData.meta || [];

          // Build column map
          const columnMap = new Map<string, number>();
          meta.forEach((colMeta: { name: string }, index: number) => {
            columnMap.set(colMeta.name, index);
          });

          // Check row format
          const firstRow = rows[0];
          const isArrayFormat = Array.isArray(firstRow);

          let hasMetricLogTable = false;
          let hasAsynchronousMetricLogTable = false;

          rows.forEach((row: unknown) => {
            let tableName: string;

            if (isArrayFormat) {
              const rowArray = row as unknown[];
              const nameIndex = columnMap.get("name");
              if (nameIndex !== undefined) {
                tableName = String(rowArray[nameIndex] ?? "");
              } else {
                return;
              }
            } else {
              const rowObject = row as Record<string, unknown>;
              tableName = String(rowObject["name"] ?? "");
            }

            if (tableName.startsWith("metric_log")) {
              hasMetricLogTable = true;
            }
            if (tableName.startsWith("asynchronous_metric_log")) {
              hasAsynchronousMetricLogTable = true;
            }
          });

          // Now fetch dashboard definitions
          fetchDashboards(hasMetricLogTable, hasAsynchronousMetricLogTable);
        } catch (err) {
          console.error("Error checking metric_log tables:", err);
          // Continue with fetching dashboards anyway
          fetchDashboards(false, false);
        }
      })
      .catch((error) => {
        console.error("Error checking metric_log tables:", error);
        // Continue with fetching dashboards anyway
        fetchDashboards(false, false);
      });
  }, [connection, fetchDashboards, database, table, monitorScope]);

  const headerActions =
    clusterNodes.length > 1 ? (
      <div className="flex items-center gap-2 rounded-lg border bg-background/80 px-2 py-1 shadow-sm">
        {monitorScope === "cluster" ? (
          <Network className="h-4 w-4 text-primary" />
        ) : (
          <Server className="h-4 w-4 text-primary" />
        )}
        <div className="hidden min-w-0 sm:block">
          <div className="text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
            Monitoring scope
          </div>
          <div className="truncate text-xs font-medium">
            {monitorScope === "cluster"
              ? `${connection?.cluster || connection?.metadata.detectedCluster} · All replicas`
              : hostNameManager.getShortHostname(selectedNode?.hostName ?? monitorScope)}
          </div>
        </div>
        <select
          aria-label="Dashboard monitoring scope"
          value={monitorScope}
          onChange={(event) => setMonitorScope(event.target.value)}
          className="h-8 max-w-[240px] rounded-md border border-input bg-background px-2 text-xs shadow-xs outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <option value="cluster">集群汇总 · 全部分片与副本</option>
          {clusterNodes.map((node) => (
            <option
              key={`${node.shardNumber}-${node.replicaNumber}-${node.hostName}`}
              value={node.hostName}
            >
              分片 {node.shardNumber} · 副本 {node.replicaNumber} ·{" "}
              {hostNameManager.getShortHostname(node.hostName)}
            </option>
          ))}
        </select>
        <Badge variant="secondary" className="hidden whitespace-nowrap md:inline-flex">
          {clusterNodes.length} 节点
        </Badge>
      </div>
    ) : null;

  if (error) {
    return (
      <div className="px-2 pt-2">
        <div className="flex items-center justify-center h-screen">
          <div className="text-destructive">
            <p className="font-semibold">Error loading dashboard:</p>
            <p className="text-sm">{error}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col" style={{ height: "calc(100vh - 49px)" }}>
      <DashboardPage
        dashboardId="system-dashboards"
        panels={dashboard}
        headerActions={headerActions}
      >
        {/* Render the skipped dashboards if any at the bottom of the container */}
        {skippedDashboards.length > 0 && (
          <DashboardGroupSection
            title={
              <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400">
                <AlertTriangle className="h-4 w-4" />
                <span className="font-semibold">Skipped Dashboards</span>
              </div>
            }
            defaultOpen={false}
          >
            <div className="card-container flex flex-wrap gap-1">
              {skippedDashboards.map((s, i) => (
                <div
                  key={`skipped-${i}`}
                  style={{ width: `calc(${(1 / 4) * 100}% - ${(3 * 0.25) / 4}rem)` }}
                >
                  <Card className="relative">
                    <CardHeader className="p-0">
                      <div className="flex items-center p-2 bg-muted/50 transition-colors gap-2">
                        <div className="flex-1 text-left">
                          <CardTitle className="m-0 text-left text-base">{s.title}</CardTitle>
                        </div>
                        <div className="pr-2">
                          <Button
                            variant="outline"
                            size="icon"
                            className="h-6 w-6 p-0 flex items-center justify-center hover:ring-2 hover:ring-foreground/20"
                            title="Show query"
                            aria-label="Show query"
                            onClick={() => {
                              Dialog.showDialog({
                                title: s.title || "Query",
                                description: s.dashboard ? `Dashboard: ${s.dashboard}` : undefined,
                                className: "max-w-[800px] max-h-[80vh]",
                                mainContent: (
                                  <div className="mt-2 overflow-x-auto">
                                    <ThemedSyntaxHighlighter
                                      language="sql"
                                      customStyle={{ margin: 0, borderRadius: "0.375rem" }}
                                    >
                                      {s.query || ""}
                                    </ThemedSyntaxHighlighter>
                                  </div>
                                ),
                                dialogButtons: [
                                  { text: "OK", onClick: async () => true, default: true },
                                ],
                              });
                            }}
                          >
                            <EllipsisVertical className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    </CardHeader>
                    <CardContent className="text-sm ">
                      <div className="pt-6">Reason: {s.reason}</div>
                    </CardContent>
                  </Card>
                </div>
              ))}
            </div>
          </DashboardGroupSection>
        )}
      </DashboardPage>
    </div>
  );
});
