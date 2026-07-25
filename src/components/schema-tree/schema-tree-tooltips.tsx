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
  const columnName = String(column.name || "Unknown");
  const columnType = String(column.type || "");
  const columnComment = column.comment || null;

  // Check if it's an Enum type
  const enumInfo = parseEnumType(columnType);

  const hasEnumPairs = enumInfo && enumInfo.pairs.length > 0;
  const hasComment = !!columnComment;

  return (
    <div className="text-xs space-y-1 max-w-[400px]">
      <div className="grid grid-cols-[auto_1fr] gap-x-2 gap-y-1">
        <div className="font-medium text-muted-foreground">Column</div>
        <div className="text-foreground break-all flex items-center gap-1 min-w-0">
          <span>{columnName}</span>
          <CopyButton
            value={columnName}
            className="relative top-0 right-0 h-4 w-4 shrink-0 [&_svg]:h-2.5 [&_svg]:w-2.5"
          />
        </div>
        <div className="font-medium text-muted-foreground">Type</div>
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
  const tableName = String(table.table || "Unknown");
  const databaseName = String(table.database || "Unknown");
  const fullName = `${databaseName}.${tableName}`;
  const tableComment = table.tableComment || null;

  return (
    <div className="text-xs space-y-1 max-w-[400px]">
      <div className="grid grid-cols-[auto_1fr] gap-x-2 gap-y-1 items-center">
        <div className="font-medium text-muted-foreground">Table</div>
        <div className="text-foreground break-all flex items-center gap-1 min-w-0">
          <span>{fullName}</span>
          <CopyButton
            value={fullName}
            className="relative top-0 right-0 h-4 w-4 shrink-0 [&_svg]:h-2.5 [&_svg]:w-2.5"
          />
        </div>
        <div className="font-medium text-muted-foreground">Engine</div>
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
  const dbName = String(db.name || "Unknown");

  return (
    <div className="text-xs space-y-1 max-w-[400px]">
      <div className="grid grid-cols-[auto_1fr] gap-x-2 gap-y-1 items-center">
        <div className="font-medium text-muted-foreground">Database</div>
        <div className="text-foreground break-all flex items-center gap-1 min-w-0">
          <span>{dbName}</span>
          <CopyButton
            value={dbName}
            className="relative top-0 right-0 h-4 w-4 shrink-0 [&_svg]:h-2.5 [&_svg]:w-2.5"
          />
        </div>
        <div className="font-medium text-muted-foreground">Engine</div>
        <div className="text-foreground break-all min-w-0">{db.engine}</div>
        <div className="font-medium text-muted-foreground">Tables</div>
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
        <div className="font-medium text-muted-foreground">User</div>
        <div className="text-foreground break-all min-w-0">{connection.user}</div>
        <div className="font-medium text-muted-foreground">Current Node</div>
        <div className="text-foreground break-all min-w-0">{fullServerName}</div>
        <div className="col-span-2 pt-1 mt-1 border-t" />
        <div className="font-medium text-muted-foreground">Databases</div>
        <div className="text-foreground">{databaseCount}</div>
        <div className="font-medium text-muted-foreground">Tables</div>
        <div className="text-foreground">{tableCount}</div>
      </div>
    </div>
  );
}
