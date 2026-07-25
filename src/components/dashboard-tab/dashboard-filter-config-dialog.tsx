"use client";

import type {
  DateTimeFilterSpec,
  FilterSpec,
  SelectorFilterSpec,
} from "@/components/shared/dashboard/dashboard-model";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { memo, useCallback, useEffect, useState } from "react";

interface DashboardFilterConfigDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onAdd: (filter: FilterSpec) => void;
  editingFilter?: FilterSpec | null;
  onUpdate?: (filter: FilterSpec) => void;
}

function ToggleOption({
  selected,
  onClick,
  children,
}: {
  selected: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      className={cn(
        "px-3 py-1.5 rounded-md border text-sm transition-colors",
        selected ? "bg-primary text-primary-foreground border-primary" : "hover:bg-muted/50"
      )}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

const DashboardFilterConfigDialogComponent = ({
  open,
  onOpenChange,
  onAdd,
  editingFilter,
  onUpdate,
}: DashboardFilterConfigDialogProps) => {
  const isEditMode = !!editingFilter;
  const [filterType, setFilterType] = useState<"select" | "date_time">("select");
  const [displayName, setDisplayName] = useState("");
  const [columnName, setColumnName] = useState("");
  const [dataSourceType, setDataSourceType] = useState<"sql" | "inline">("sql");
  const [sqlQuery, setSqlQuery] = useState("");
  const [staticValues, setStaticValues] = useState("");
  const [defaultTimeSpan, setDefaultTimeSpan] = useState("Last 15 Mins");

  // Populate when editing
  useEffect(() => {
    if (editingFilter) {
      if (editingFilter.filterType === "select") {
        const sel = editingFilter as SelectorFilterSpec;
        setFilterType("select");
        setDisplayName(sel.displayText);
        setColumnName(sel.name);
        if (sel.datasource.type === "sql") {
          setDataSourceType("sql");
          setSqlQuery(sel.datasource.sql);
          setStaticValues("");
        } else {
          setDataSourceType("inline");
          setStaticValues(sel.datasource.values.map((v) => v.value).join(", "));
          setSqlQuery("");
        }
      } else {
        const dt = editingFilter as DateTimeFilterSpec;
        setFilterType("date_time");
        setDisplayName(dt.displayText);
        setColumnName(dt.timeColumn);
        setDefaultTimeSpan(dt.defaultTimeSpan || "Last 15 Mins");
      }
    } else {
      resetForm();
    }
  }, [editingFilter]);

  const resetForm = () => {
    setFilterType("select");
    setDisplayName("");
    setColumnName("");
    setDataSourceType("sql");
    setSqlQuery("");
    setStaticValues("");
    setDefaultTimeSpan("Last 15 Mins");
  };

  const handleClose = useCallback(() => {
    resetForm();
    onOpenChange(false);
  }, [onOpenChange]);

  const handleSubmit = useCallback(() => {
    if (!displayName.trim() || !columnName.trim()) return;

    let filter: FilterSpec;

    if (filterType === "select") {
      const datasource =
        dataSourceType === "sql"
          ? { type: "sql" as const, sql: sqlQuery.trim() }
          : {
              type: "inline" as const,
              values: staticValues
                .split(",")
                .map((v) => v.trim())
                .filter(Boolean)
                .map((v) => ({ label: v, value: v })),
            };

      filter = {
        filterType: "select",
        name: columnName.trim(),
        displayText: displayName.trim(),
        datasource,
      } satisfies SelectorFilterSpec;
    } else {
      filter = {
        filterType: "date_time",
        alias: columnName.trim(),
        displayText: displayName.trim(),
        timeColumn: columnName.trim(),
        defaultTimeSpan: defaultTimeSpan.trim() || "Last 15 Mins",
      } satisfies DateTimeFilterSpec;
    }

    if (isEditMode && onUpdate) {
      onUpdate(filter);
    } else {
      onAdd(filter);
    }

    handleClose();
  }, [
    filterType,
    displayName,
    columnName,
    dataSourceType,
    sqlQuery,
    staticValues,
    defaultTimeSpan,
    isEditMode,
    onAdd,
    onUpdate,
    handleClose,
  ]);

  const isValid =
    displayName.trim() &&
    columnName.trim() &&
    (filterType === "date_time" ||
      (dataSourceType === "sql" && sqlQuery.trim()) ||
      (dataSourceType === "inline" && staticValues.trim()));

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>{isEditMode ? "Edit Filter" : "Add Filter"}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Filter type */}
          <div className="space-y-2">
            <Label>Filter Type</Label>
            <div className="flex gap-2">
              <ToggleOption
                selected={filterType === "select"}
                onClick={() => setFilterType("select")}
              >
                Select
              </ToggleOption>
              <ToggleOption
                selected={filterType === "date_time"}
                onClick={() => setFilterType("date_time")}
              >
                Date/Time
              </ToggleOption>
            </div>
          </div>

          {/* Display name */}
          <div className="space-y-2">
            <Label htmlFor="filter-display-name">Display Name</Label>
            <Input
              id="filter-display-name"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="e.g. Database, Table, Type"
            />
          </div>

          {/* Column name */}
          <div className="space-y-2">
            <Label htmlFor="filter-column-name">
              {filterType === "date_time" ? "Time Column" : "Column Name"}
            </Label>
            <Input
              id="filter-column-name"
              value={columnName}
              onChange={(e) => setColumnName(e.target.value)}
              placeholder={filterType === "date_time" ? "e.g. event_time" : "e.g. database"}
            />
          </div>

          {/* Select-specific options */}
          {filterType === "select" && (
            <>
              <div className="space-y-2">
                <Label>Data Source</Label>
                <div className="flex gap-2">
                  <ToggleOption
                    selected={dataSourceType === "sql"}
                    onClick={() => setDataSourceType("sql")}
                  >
                    SQL Query
                  </ToggleOption>
                  <ToggleOption
                    selected={dataSourceType === "inline"}
                    onClick={() => setDataSourceType("inline")}
                  >
                    Static Values
                  </ToggleOption>
                </div>
              </div>

              {dataSourceType === "sql" ? (
                <div className="space-y-2">
                  <Label htmlFor="filter-sql">SQL Query</Label>
                  <Textarea
                    id="filter-sql"
                    value={sqlQuery}
                    onChange={(e) => setSqlQuery(e.target.value)}
                    placeholder="SELECT DISTINCT database FROM system.tables ORDER BY database"
                    className="font-mono text-sm min-h-[80px]"
                  />
                  <p className="text-xs text-muted-foreground">
                    Query should return a single column. First column values are used as options.
                  </p>
                </div>
              ) : (
                <div className="space-y-2">
                  <Label htmlFor="filter-static">Static Values (comma-separated)</Label>
                  <Input
                    id="filter-static"
                    value={staticValues}
                    onChange={(e) => setStaticValues(e.target.value)}
                    placeholder="value1, value2, value3"
                  />
                </div>
              )}
            </>
          )}

          {/* DateTime-specific options */}
          {filterType === "date_time" && (
            <div className="space-y-2">
              <Label htmlFor="filter-default-timespan">Default Time Span</Label>
              <Input
                id="filter-default-timespan"
                value={defaultTimeSpan}
                onChange={(e) => setDefaultTimeSpan(e.target.value)}
                placeholder="Last 15 Mins"
              />
              <p className="text-xs text-muted-foreground">
                Options: Last 1/5/15/30 Mins, Last 1/3/6/12 Hours, Last 1/3/5/7 Days, Today,
                Yesterday
              </p>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={!isValid}>
            {isEditMode ? "Update Filter" : "Add Filter"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

DashboardFilterConfigDialogComponent.displayName = "DashboardFilterConfigDialog";

export const DashboardFilterConfigDialog = memo(DashboardFilterConfigDialogComponent);
