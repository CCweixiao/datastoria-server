import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { CopyButton } from "@/components/ui/copy-button";
import { parseEnumType } from "./schema-tree-utils";

export function ColumnTooltip({
  column,
}: {
  column: {
    name: string;
    type: string;
    comment?: string | null;
  };
}) {
  const { t } = useUiPreferences();
  const columnName = String(column.name || t("schema.unknown"));
  const columnType = String(column.type || "");
  const columnComment = column.comment || null;

  // Check if it's an Enum type
  const enumInfo = parseEnumType(columnType);

  const hasEnumPairs = enumInfo && enumInfo.pairs.length > 0;
  const hasComment = !!columnComment;

  return (
    <div className="text-xs space-y-1 max-w-[400px]">
      <div className="grid grid-cols-[auto_1fr] gap-x-2 gap-y-1">
        <div className="font-medium text-muted-foreground">{t("schema.column")}</div>
        <div className="text-foreground break-all flex items-center gap-1 min-w-0">
          <span>{columnName}</span>
          <CopyButton
            value={columnName}
            className="relative top-0 right-0 h-4 w-4 shrink-0 [&_svg]:h-2.5 [&_svg]:w-2.5"
          />
        </div>
        <div className="font-medium text-muted-foreground">{t("schema.type")}</div>
        <div className="text-foreground break-all min-w-0">{columnType}</div>
      </div>

      {/* Enum info */}
      {hasEnumPairs && (
        <div className="pt-1 mt-1 border-t space-y-1">
          <div className="font-medium text-muted-foreground">{enumInfo.baseType}</div>
          <div className="space-y-1">
            {enumInfo.pairs.map(([key, value]) => (
              <div key={`${key}:${value}`} className="font-mono break-words">
                <span className="text-muted-foreground break-all">{key}</span>
                <span className="mx-2">=</span>
                <span className="break-all">{value}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Comment */}
      {hasComment && (
        <div className="pt-1 mt-1 border-t">
          <div className="text-foreground whitespace-pre-wrap break-words">{columnComment}</div>
        </div>
      )}
    </div>
  );
}

export function TableTooltip({
  table,
}: {
  table: {
    database: string;
    table: string;
    tableEngine: string;
    fullTableEngine: string;
    tableComment?: string | null;
  };
}) {
  const { t } = useUiPreferences();
  const tableName = String(table.table || t("schema.unknown"));
  const databaseName = String(table.database || t("schema.unknown"));
  const fullName = `${databaseName}.${tableName}`;
  const tableComment = table.tableComment || null;

  return (
    <div className="text-xs space-y-1 max-w-[400px]">
      <div className="grid grid-cols-[auto_1fr] gap-x-2 gap-y-1 items-center">
        <div className="font-medium text-muted-foreground">{t("schema.table")}</div>
        <div className="text-foreground break-all flex items-center gap-1 min-w-0">
          <span>{fullName}</span>
          <CopyButton
            value={fullName}
            className="relative top-0 right-0 h-4 w-4 shrink-0 [&_svg]:h-2.5 [&_svg]:w-2.5"
          />
        </div>
        <div className="font-medium text-muted-foreground">{t("schema.engine")}</div>
        <div className="text-foreground break-all min-w-0">
          {table.fullTableEngine || table.tableEngine}
        </div>
      </div>
      {tableComment && (
        <div className="pt-1 mt-1 border-t">
          <div className="text-foreground whitespace-pre-wrap break-words">{tableComment}</div>
        </div>
      )}
    </div>
  );
}

export function DatabaseTooltip({
  db,
}: {
  db: {
    name: string;
    engine: string;
    comment?: string | null;
    tableCount: number;
  };
}) {
  const { t } = useUiPreferences();
  const dbName = String(db.name || t("schema.unknown"));

  return (
    <div className="text-xs space-y-1 max-w-[400px]">
      <div className="grid grid-cols-[auto_1fr] gap-x-2 gap-y-1 items-center">
        <div className="font-medium text-muted-foreground">{t("schema.database")}</div>
        <div className="text-foreground break-all flex items-center gap-1 min-w-0">
          <span>{dbName}</span>
          <CopyButton
            value={dbName}
            className="relative top-0 right-0 h-4 w-4 shrink-0 [&_svg]:h-2.5 [&_svg]:w-2.5"
          />
        </div>
        <div className="font-medium text-muted-foreground">{t("schema.engine")}</div>
        <div className="text-foreground break-all min-w-0">{db.engine}</div>
        <div className="font-medium text-muted-foreground">{t("schema.tables")}</div>
        <div className="text-foreground min-w-0">{db.tableCount}</div>
      </div>
      {db.comment && (
        <div className="pt-1 mt-1 border-t">
          <div className="text-foreground whitespace-pre-wrap break-words">{db.comment}</div>
        </div>
      )}
    </div>
  );
}

export function HostTooltip({
  connection,
  fullServerName,
  databaseCount,
  tableCount,
}: {
  connection: {
    name: string;
    url: string;
    user: string;
  };
  fullServerName: string;
  databaseCount: number;
  tableCount: number;
}) {
  const { t } = useUiPreferences();
  return (
    <div className="text-xs space-y-1 max-w-[400px]">
      <div className="font-medium text-muted-foreground">{connection.name}</div>
      <div className="grid grid-cols-[auto_1fr] gap-x-2 gap-y-1 items-center">
        <div className="font-medium text-muted-foreground">URL</div>
        <div className="text-foreground break-all flex items-center gap-1 min-w-0">
          <span>{connection.url}</span>
          <CopyButton
            value={connection.url}
            className="relative top-0 right-0 h-4 w-4 shrink-0 [&_svg]:h-2.5 [&_svg]:w-2.5"
          />
        </div>
        <div className="font-medium text-muted-foreground">{t("connection.user")}</div>
        <div className="text-foreground break-all min-w-0">{connection.user}</div>
        <div className="font-medium text-muted-foreground">{t("schema.currentNode")}</div>
        <div className="text-foreground break-all min-w-0">{fullServerName}</div>
        <div className="col-span-2 pt-1 mt-1 border-t" />
        <div className="font-medium text-muted-foreground">{t("schema.databases")}</div>
        <div className="text-foreground">{databaseCount}</div>
        <div className="font-medium text-muted-foreground">{t("schema.tables")}</div>
        <div className="text-foreground">{tableCount}</div>
      </div>
    </div>
  );
}
