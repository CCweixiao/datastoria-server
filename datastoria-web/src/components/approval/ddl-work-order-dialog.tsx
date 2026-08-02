"use client";

import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { prepareDdlApproval, type ApprovalType } from "@/lib/approval-client";
import { useEffect, useMemo, useState } from "react";

function localizedName(value: string): string {
  try {
    const values = JSON.parse(value) as Record<string, string>;
    const language = document.documentElement.lang.toLowerCase().startsWith("zh") ? "zh-CN" : "en";
    return values[language] ?? values.en ?? "";
  } catch {
    return value;
  }
}

export function DdlWorkOrderDialog({
  open,
  onOpenChange,
  connectionId,
  cluster,
  types,
  onCreated,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  connectionId: string;
  cluster?: string;
  types: ApprovalType[];
  onCreated: (requestId: string) => void;
}) {
  const { t } = useUiPreferences();
  const [typeKey, setTypeKey] = useState("");
  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const [database, setDatabase] = useState("");
  const [table, setTable] = useState("");
  const [column, setColumn] = useState("");
  const [columnType, setColumnType] = useState("");
  const [columns, setColumns] = useState('[{"name":"id","type":"UInt64"}]');
  const [clusterName, setClusterName] = useState(cluster ?? "");
  const [orderBy, setOrderBy] = useState("id");
  const [shardingKey, setShardingKey] = useState("id");
  const [index, setIndex] = useState("");
  const [indexType, setIndexType] = useState("minmax");
  const [granularity, setGranularity] = useState("1");
  const [materialize, setMaterialize] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open && !typeKey && types[0]) setTypeKey(types[0].typeKey);
  }, [open, typeKey, types]);
  const isCreate = typeKey === "CLICKHOUSE_CREATE_TABLE";
  const isAddOrModify =
    typeKey === "CLICKHOUSE_ADD_COLUMN" || typeKey === "CLICKHOUSE_MODIFY_COLUMN";
  const isDrop = typeKey === "CLICKHOUSE_DROP_COLUMN";
  const isIndex = typeKey === "CLICKHOUSE_ADD_INDEX";
  const selectedType = useMemo(
    () => types.find((type) => type.typeKey === typeKey),
    [typeKey, types]
  );

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      const intent: Record<string, unknown> = { database, table };
      if (isCreate)
        Object.assign(intent, {
          cluster: clusterName,
          columns: JSON.parse(columns),
          orderBy: orderBy
            .split(",")
            .map((value) => value.trim())
            .filter(Boolean),
          shardingKey,
        });
      if (isAddOrModify) Object.assign(intent, { column, type: columnType });
      if (isDrop) Object.assign(intent, { column });
      if (isIndex)
        Object.assign(intent, {
          index,
          column,
          indexType,
          granularity: Number(granularity),
          materialize,
        });
      const result = await prepareDdlApproval({
        connectionId,
        workOrderTypeKey: typeKey,
        title,
        summary,
        intent,
      });
      onOpenChange(false);
      onCreated(result.requestId);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t("approval.error.action"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("approval.create.title")}</DialogTitle>
          <DialogDescription>{t("approval.create.description")}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-2">
          <div>
            <Label>{t("approval.create.type")}</Label>
            <select
              className="mt-1 h-9 w-full rounded-md border bg-background px-3 text-sm"
              value={typeKey}
              onChange={(event) => setTypeKey(event.target.value)}
            >
              {types.map((type) => (
                <option key={type.typeKey} value={type.typeKey}>
                  {localizedName(type.nameI18nJson)}
                </option>
              ))}
            </select>
            {selectedType && (
              <p className="mt-1 text-xs text-muted-foreground">
                {localizedName(selectedType.descriptionI18nJson)}
              </p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>{t("approval.create.titleField")}</Label>
              <Input value={title} onChange={(event) => setTitle(event.target.value)} />
            </div>
            <div>
              <Label>{t("approval.create.summary")}</Label>
              <Input value={summary} onChange={(event) => setSummary(event.target.value)} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>{t("approval.create.database")}</Label>
              <Input value={database} onChange={(event) => setDatabase(event.target.value)} />
            </div>
            <div>
              <Label>{t("approval.create.table")}</Label>
              <Input value={table} onChange={(event) => setTable(event.target.value)} />
            </div>
          </div>
          {isCreate && (
            <>
              <div>
                <Label>{t("approval.create.cluster")}</Label>
                <Input
                  value={clusterName}
                  onChange={(event) => setClusterName(event.target.value)}
                />
              </div>
              <div>
                <Label>{t("approval.create.columns")}</Label>
                <Textarea
                  className="min-h-28 font-mono text-xs"
                  value={columns}
                  onChange={(event) => setColumns(event.target.value)}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label>{t("approval.create.orderBy")}</Label>
                  <Input value={orderBy} onChange={(event) => setOrderBy(event.target.value)} />
                </div>
                <div>
                  <Label>{t("approval.create.shardingKey")}</Label>
                  <Input
                    value={shardingKey}
                    onChange={(event) => setShardingKey(event.target.value)}
                  />
                </div>
              </div>
            </>
          )}
          {(isAddOrModify || isDrop) && (
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>{t("approval.create.column")}</Label>
                <Input value={column} onChange={(event) => setColumn(event.target.value)} />
              </div>
              {isAddOrModify && (
                <div>
                  <Label>{t("approval.create.columnType")}</Label>
                  <Input
                    value={columnType}
                    onChange={(event) => setColumnType(event.target.value)}
                  />
                </div>
              )}
            </div>
          )}
          {isIndex && (
            <>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label>{t("approval.create.index")}</Label>
                  <Input value={index} onChange={(event) => setIndex(event.target.value)} />
                </div>
                <div>
                  <Label>{t("approval.create.column")}</Label>
                  <Input value={column} onChange={(event) => setColumn(event.target.value)} />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label>{t("approval.create.indexType")}</Label>
                  <Input value={indexType} onChange={(event) => setIndexType(event.target.value)} />
                </div>
                <div>
                  <Label>{t("approval.create.granularity")}</Label>
                  <Input
                    type="number"
                    value={granularity}
                    onChange={(event) => setGranularity(event.target.value)}
                  />
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Switch
                  id="materialize-index"
                  checked={materialize}
                  onCheckedChange={setMaterialize}
                />
                <Label htmlFor="materialize-index">{t("approval.create.materialize")}</Label>
              </div>
            </>
          )}
          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t("common.cancel")}
          </Button>
          <Button disabled={saving || !typeKey || !title.trim()} onClick={() => void save()}>
            {saving ? t("approval.create.saving") : t("approval.create.save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
