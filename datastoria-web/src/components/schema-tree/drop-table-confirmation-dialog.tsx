import type { FieldOption } from "@/components/shared/dashboard/dashboard-model";
import { DataTable } from "@/components/shared/dashboard/data-table";
import FloatingProgressBar from "@/components/shared/floating-progress-bar";
import { ThemedSyntaxHighlighter } from "@/components/shared/themed-syntax-highlighter";
import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Dialog } from "@/components/shared/use-dialog";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Connection, QueryError } from "@/lib/connection/connection";
import { HttpResponseLineReader } from "@/lib/http-response-line-reader";
import { normalizeLocale, translate } from "@/lib/i18n/i18n";
import "@/lib/number-utils";
import { SqlUtils } from "@/lib/sql-utils";
import { Loader2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

interface TableNodeData {
  type: "table";
  database: string;
  table: string;
  fullName: string;
  tableEngine: string;
  fullTableEngine: string;
}

interface DropTableConfirmationDialogProps {
  table: TableNodeData;
  connection: Connection;
  onSuccess: () => void;
  onCancel?: () => void;
}

interface DropTableOptions {
  dropAsync: boolean;
  skipSizeRestriction: boolean;
}

function buildDropTableSql(
  table: TableNodeData,
  connection: Connection,
  options: DropTableOptions
): string {
  const tableIdentifier = `\`${table.database.replaceAll("`", "``")}\`.\`${table.table.replaceAll("`", "``")}\``;
  const clusterClause =
    connection.cluster && connection.cluster.length > 0
      ? ` ON CLUSTER '${SqlUtils.escapeSqlString(connection.cluster)}'`
      : "";

  const settings: string[] = [];
  if (options.dropAsync) {
    settings.push("distributed_ddl_task_timeout = 0");
  }
  if (options.skipSizeRestriction) {
    settings.push("max_table_size_to_drop = 0");
  }

  const settingsClause = settings.length > 0 ? `\nSETTINGS ${settings.join(",\n")}` : "";
  return `DROP TABLE IF EXISTS ${tableIdentifier}${clusterClause}${settingsClause}`;
}

type DropDialogView = "confirmation" | "result";

function DropTableConfirmationDialogContent({
  table,
  connection,
  onSuccess,
  onCancel,
  isDroppingRef,
}: {
  table: TableNodeData;
  connection: Connection;
  onSuccess: () => void;
  onCancel?: () => void;
  isDroppingRef: React.MutableRefObject<boolean>;
}) {
  const { t } = useUiPreferences();
  const [options, setOptions] = useState<DropTableOptions>({
    dropAsync: false,
    skipSizeRestriction: false,
  });
  const [view, setView] = useState<DropDialogView>("confirmation");
  const [isDropping, setIsDropping] = useState(false);
  const [hostRecords, setHostRecords] = useState<Array<{ host: string; timestamp: number }>>([]);
  const [message, setMessages] = useState<string | null>(null);
  const [tableStats, setTableStats] = useState<{ totalRows: number; totalBytes: number } | null>(
    null
  );

  useEffect(() => {
    const fetchTableStats = async () => {
      const escapedDb = SqlUtils.escapeSqlString(table.database);
      const escapedTable = SqlUtils.escapeSqlString(table.table);
      const { response } = connection.query(`SELECT sum(total_rows) as total_rows, 
        sum(total_bytes) as total_bytes 
        FROM {clusterAllReplicas:system.tables}
        WHERE database = '${escapedDb}' AND table = '${escapedTable}'`);
      const res = await response;
      const data = await res.data.json<{ data: [number, number][] }>();
      const row = data.data?.at(0);
      if (row) {
        setTableStats({
          totalRows: Number(row[0]) || 0,
          totalBytes: Number(row[1]) || 0,
        });
      } else if (data.data !== undefined) {
        setTableStats({ totalRows: 0, totalBytes: 0 });
      }
    };
    fetchTableStats().catch(() => setTableStats(null));
  }, [table.database, table.table, connection]);

  const sql = useMemo(
    () => buildDropTableSql(table, connection, options),
    [table, connection, options]
  );

  const updateOption = (key: keyof DropTableOptions, value: boolean) => {
    setOptions((prev) => ({ ...prev, [key]: value }));
  };

  const handleDropTable = async () => {
    if (isDroppingRef.current) return;
    isDroppingRef.current = true;
    setIsDropping(true);
    setView("result");
    setHostRecords([]);
    setMessages(null);

    try {
      const { response } = connection.queryRawResponse(sql, {
        default_format: "TabSeparatedRaw",
      });
      const res = await response;
      const reader = res.body?.getReader();
      if (!reader) {
        throw new Error(t("schema.responseUnreadable"));
      }

      const exceptionTag = "__exception__";
      const exceptionLines: string[] = [];
      await HttpResponseLineReader.read(reader, (line) => {
        const exceptionIndex = line.indexOf(exceptionTag);
        if (exceptionIndex >= 0) {
          const message = line.slice(exceptionIndex + exceptionTag.length).trim();
          exceptionLines.push(message || t("schema.unknownError"));
          return;
        }
        if (exceptionLines.length > 0) {
          exceptionLines.push(line);
          return;
        }
        const hostName = line.split("\t")[0];
        if (hostName) {
          setHostRecords((prev) => [...prev, { host: hostName, timestamp: Date.now() }]);
        }
      });

      if (exceptionLines.length > 0) {
        throw new Error(exceptionLines.join("\n"));
      }

      if (options.dropAsync) {
        setMessages(t("schema.dropSubmitted"));
      } else {
        setMessages(t("schema.dropSucceeded"));
        onSuccess();
      }
    } catch (error) {
      let message = t("schema.dropFailed");
      if (error instanceof QueryError) {
        message += String(error.data || error.message);
      } else {
        message += error instanceof Error ? error.message : t("schema.unknownError");
      }
      setMessages(message);
    } finally {
      isDroppingRef.current = false;
      setIsDropping(false);
    }
  };

  const handleCancel = () => {
    onCancel?.();
    Dialog.close();
  };

  const handleOk = () => {
    Dialog.close();
  };

  const confirmationView = (
    <div className="flex min-h-0 flex-1 flex-col gap-4">
      <div className="min-h-0 flex-1 overflow-auto space-y-4">
        <p>{t("schema.dropConfirm", { name: `${table.database}.${table.table}` })}</p>
        <div>
          <p className="text-sm text-muted-foreground">{t("schema.tableInfo")}</p>
          <div className="rounded-md border p-3 text-sm">
            {tableStats ? (
              <>
                <span className="text-muted-foreground">{t("schema.totalRows")} </span>
                <span className="font-medium">{Number(tableStats.totalRows).toLocaleString()}</span>
                <span className="mx-2 text-muted-foreground">·</span>
                <span className="text-muted-foreground">{t("schema.size")} </span>
                <span className="font-medium">
                  {Number(tableStats.totalBytes).formatBinarySize()}
                </span>
              </>
            ) : (
              t("schema.loadingTableInfo")
            )}
          </div>
        </div>
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">{t("schema.dropSettings")}</p>
          <div className="space-y-3 rounded-md border p-3">
            <div className="flex items-start justify-between gap-3">
              <div className="space-y-1">
                <p className="text-sm font-medium">{t("schema.dropAsync")}</p>
                <p className="text-xs text-muted-foreground">{t("schema.dropAsyncHelp")}</p>
              </div>
              <Switch
                checked={options.dropAsync}
                onCheckedChange={(checked) => updateOption("dropAsync", checked)}
                aria-label={t("schema.dropAsync")}
              />
            </div>
            <div className="flex items-start justify-between gap-3">
              <div className="space-y-1">
                <p className="text-sm font-medium">{t("schema.skipDropRestriction")}</p>
                <p className="text-xs text-muted-foreground">
                  {t("schema.skipDropRestrictionHelp")}
                </p>
              </div>
              <Switch
                checked={options.skipSizeRestriction}
                onCheckedChange={(checked) => updateOption("skipSizeRestriction", checked)}
                aria-label={t("schema.skipDropRestriction")}
              />
            </div>
          </div>
        </div>
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">{t("schema.sqlToExecute")}</p>
          <div className="max-h-16 overflow-auto rounded-md border">
            <ThemedSyntaxHighlighter
              language="sql"
              customStyle={{ fontSize: "14px", margin: 0 }}
              wrapLongLines={true}
              showLineNumbers={false}
            >
              {sql}
            </ThemedSyntaxHighlighter>
          </div>
        </div>
      </div>
      <div className="flex shrink-0 justify-end gap-2">
        <Button type="button" variant="outline" size="sm" onClick={handleCancel}>
          {t("common.cancel")}
        </Button>
        <Button
          type="button"
          variant="destructive"
          size="sm"
          onClick={handleDropTable}
          disabled={isDropping}
        >
          {isDropping ? (
            <>
              <Loader2 className="mr-2 inline-block h-4 w-4 animate-spin" />
              {t("schema.dropping")}
            </>
          ) : (
            t("schema.dropTitle")
          )}
        </Button>
      </div>
    </div>
  );

  const resultView = (
    <div className="flex min-h-0 flex-1 flex-col animate-in fade-in slide-in-from-bottom-2 duration-300 gap-4">
      {isDropping && (
        <div className="relative shrink-0 rounded-md border p-3">
          <FloatingProgressBar show={isDropping} className="rounded-t-md" />
        </div>
      )}
      {!options.dropAsync && hostRecords.length > 0 && (
        <div className="relative flex min-h-0 flex-1 flex-col overflow-auto rounded-md border">
          <div className="min-h-0 flex-1">
            <DataTable
              data={hostRecords}
              meta={[
                { name: "host", type: "String" },
                { name: "timestamp", type: "Int64" },
              ]}
              fieldOptions={
                [
                  { name: "host", title: t("schema.executedHost") },
                  { name: "timestamp", title: t("schema.executedTime"), format: "MMddHHmmss" },
                ] as FieldOption[]
              }
              enableIndexColumn
            />
          </div>
        </div>
      )}
      {message && <pre className="overflow-auto text-sm">{message}</pre>}
      <div className="mt-auto flex shrink-0 justify-end">
        <Button type="button" size="sm" onClick={handleOk}>
          {t("common.ok")}
        </Button>
      </div>
    </div>
  );

  return (
    <div className="flex h-[460px] flex-col">
      {view === "confirmation" ? confirmationView : resultView}
    </div>
  );
}

export function showDropTableConfirmationDialog({
  table,
  connection,
  onSuccess,
  onCancel,
}: DropTableConfirmationDialogProps) {
  const isDroppingRef = { current: false };
  const locale = normalizeLocale(
    typeof document === "undefined" ? "en" : document.documentElement.lang
  );

  Dialog.showDialog({
    title: translate(locale, "schema.dropTitle"),
    mainContent: (
      <DropTableConfirmationDialogContent
        table={table}
        connection={connection}
        onSuccess={onSuccess}
        onCancel={onCancel}
        isDroppingRef={isDroppingRef}
      />
    ),
    className: "max-w-2xl",
    onCancel: () => onCancel?.(),
    canClose: () => !isDroppingRef.current,
    disableBackdrop: false,
  });
}
