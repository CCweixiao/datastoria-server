"use client";

import type {
  GaugeDescriptor,
  GridPos,
  PanelDescriptor,
  PieDescriptor,
  StatDescriptor,
  TimeseriesDescriptor,
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

type ChartType = "stat" | "line" | "bar" | "area" | "pie" | "gauge" | "table";

const CHART_TYPE_LABELS: Record<ChartType, string> = {
  stat: "Number (Stat)",
  line: "Line Chart",
  bar: "Bar Chart",
  area: "Area Chart",
  pie: "Pie Chart",
  gauge: "Gauge",
  table: "Table",
};

/**
 * Built-in widget templates with pre-filled SQL queries
 */
interface WidgetTemplate {
  name: string;
  description: string;
  chartType: ChartType;
  sql: string;
  gridW: number;
  gridH: number;
}

const WIDGET_TEMPLATES: WidgetTemplate[] = [
  {
    name: "Total Rows",
    description: "Count of rows in a table",
    chartType: "stat",
    sql: "SELECT count() AS value FROM system.parts WHERE active",
    gridW: 6,
    gridH: 4,
  },
  {
    name: "Query Rate",
    description: "Queries per second over time",
    chartType: "line",
    sql: `SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND) AS time,
  count() / {rounding:UInt32} AS qps
FROM system.query_log
WHERE {timeFilter}
  AND type IN ('QueryFinish', 'ExceptionWhileProcessing')
GROUP BY time
ORDER BY time`,
    gridW: 12,
    gridH: 6,
  },
  {
    name: "Top Tables by Size",
    description: "Largest tables by disk usage",
    chartType: "bar",
    sql: `SELECT
  concat(database, '.', table) AS name,
  sum(bytes_on_disk) AS size
FROM system.parts
WHERE active
GROUP BY database, table
ORDER BY size DESC
LIMIT 10`,
    gridW: 12,
    gridH: 6,
  },
  {
    name: "Database Size Distribution",
    description: "Pie chart of database sizes",
    chartType: "pie",
    sql: `SELECT
  database AS name,
  sum(bytes_on_disk) AS value
FROM system.parts
WHERE active
GROUP BY database
ORDER BY value DESC
LIMIT 10`,
    gridW: 8,
    gridH: 8,
  },
  {
    name: "Memory Usage",
    description: "Current memory usage percentage",
    chartType: "gauge",
    sql: `SELECT
  round(
    (SELECT value FROM system.asynchronous_metrics WHERE metric = 'OSMemoryTotal')
    /
    (SELECT total_value FROM system.asynchronous_metrics WHERE metric = 'OSMemoryTotal')
    * 100, 1
  ) AS value`,
    gridW: 6,
    gridH: 6,
  },
  {
    name: "Recent Queries",
    description: "Table of recent query log entries",
    chartType: "table",
    sql: `SELECT
  type,
  query_id,
  user,
  query_duration_ms,
  read_rows,
  result_rows,
  formatReadableSize(memory_usage) AS memory,
  substring(query, 1, 100) AS query_preview
FROM system.query_log
WHERE {timeFilter}
  AND type IN ('QueryFinish', 'ExceptionWhileProcessing')
ORDER BY event_time DESC
LIMIT 50`,
    gridW: 24,
    gridH: 8,
  },
];

interface PanelConfigDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (panel: PanelDescriptor) => void;
  editingPanel?: PanelDescriptor | null;
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

const PanelConfigDialogComponent = ({
  open,
  onOpenChange,
  onSave,
  editingPanel,
}: PanelConfigDialogProps) => {
  const isEditMode = !!editingPanel;

  const [chartType, setChartType] = useState<ChartType>("line");
  const [title, setTitle] = useState("");
  const [sql, setSql] = useState("");
  const [gridW, setGridW] = useState(12);
  const [gridH, setGridH] = useState(6);
  const [showTemplates, setShowTemplates] = useState(false);

  // Populate form when editing
  useEffect(() => {
    if (editingPanel) {
      setChartType(editingPanel.type as ChartType);
      setTitle(editingPanel.titleOption?.title ?? "");
      setSql(editingPanel.datasource?.sql ?? "");
      setGridW(editingPanel.gridPos?.w ?? 12);
      setGridH(editingPanel.gridPos?.h ?? 6);
      setShowTemplates(false);
    } else {
      resetForm();
    }
  }, [editingPanel]);

  const resetForm = () => {
    setChartType("line");
    setTitle("");
    setSql("");
    setGridW(12);
    setGridH(6);
    setShowTemplates(false);
  };

  const handleClose = useCallback(() => {
    resetForm();
    onOpenChange(false);
  }, [onOpenChange]);

  const applyTemplate = useCallback((template: WidgetTemplate) => {
    setChartType(template.chartType);
    setTitle(template.name);
    setSql(template.sql);
    setGridW(template.gridW);
    setGridH(template.gridH);
    setShowTemplates(false);
  }, []);

  const handleSave = useCallback(() => {
    if (!title.trim() || !sql.trim()) return;

    const gridPos: GridPos = { w: gridW, h: gridH };

    const base: PanelDescriptor = {
      type: chartType,
      titleOption: { title: title.trim() },
      gridPos,
      datasource: { sql: sql.trim() },
    };

    let panel: PanelDescriptor;

    switch (chartType) {
      case "stat":
        panel = {
          ...base,
          type: "stat",
          minimapOption: { type: "area" },
          valueOption: { reducer: "last", format: "short_number" },
        } as StatDescriptor;
        break;
      case "line":
      case "bar":
      case "area":
        panel = {
          ...base,
          type: chartType,
          legendOption: { placement: "bottom" },
          yAxis: [{ format: "short_number" }],
        } as TimeseriesDescriptor;
        break;
      case "pie":
        panel = {
          ...base,
          type: "pie",
          legendOption: { placement: "right" },
          labelOption: { show: true, format: "name-percent" },
        } as PieDescriptor;
        break;
      case "gauge":
        panel = {
          ...base,
          type: "gauge",
          gaugeOption: { min: 0, max: 100 },
        } as GaugeDescriptor;
        break;
      case "table":
        panel = {
          ...base,
          type: "table",
        };
        break;
      default:
        panel = base;
    }

    onSave(panel);
    handleClose();
  }, [chartType, title, sql, gridW, gridH, onSave, handleClose]);

  const isValid = title.trim() && sql.trim();

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[600px] max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEditMode ? "Edit Panel" : "Add Panel"}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Widget templates toggle */}
          {!isEditMode && (
            <div>
              <Button variant="outline" size="sm" onClick={() => setShowTemplates(!showTemplates)}>
                {showTemplates ? "Hide Templates" : "Use Built-in Template"}
              </Button>

              {showTemplates && (
                <div className="mt-2 grid grid-cols-2 gap-2">
                  {WIDGET_TEMPLATES.map((t) => (
                    <button
                      key={t.name}
                      type="button"
                      className="text-left p-2 rounded-md border hover:bg-muted/50 transition-colors"
                      onClick={() => applyTemplate(t)}
                    >
                      <div className="text-sm font-medium">{t.name}</div>
                      <div className="text-xs text-muted-foreground">{t.description}</div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Chart type */}
          <div className="space-y-2">
            <Label>Chart Type</Label>
            <div className="flex flex-wrap gap-2">
              {(Object.entries(CHART_TYPE_LABELS) as [ChartType, string][]).map(([type, label]) => (
                <ToggleOption
                  key={type}
                  selected={chartType === type}
                  onClick={() => setChartType(type)}
                >
                  {label}
                </ToggleOption>
              ))}
            </div>
          </div>

          {/* Title */}
          <div className="space-y-2">
            <Label htmlFor="panel-title">Title</Label>
            <Input
              id="panel-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Query Rate, Memory Usage"
            />
          </div>

          {/* SQL query */}
          <div className="space-y-2">
            <Label htmlFor="panel-sql">SQL Query</Label>
            <Textarea
              id="panel-sql"
              value={sql}
              onChange={(e) => setSql(e.target.value)}
              placeholder={`SELECT toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND) AS time, count() AS value\nFROM system.query_log\nWHERE {timeFilter}\nGROUP BY time\nORDER BY time`}
              className="font-mono text-sm min-h-[120px]"
            />
            <p className="text-xs text-muted-foreground">
              Supported template variables: {"{timeFilter}"}, {"{filterExpression}"},{" "}
              {"{from:String}"}, {"{to:String}"}, {"{rounding:UInt32}"}, {"{seconds:UInt32}"}
            </p>
          </div>

          {/* Grid size */}
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Width</Label>
              <div className="flex flex-wrap gap-2">
                {[
                  { value: 6, label: "Quarter" },
                  { value: 8, label: "Third" },
                  { value: 12, label: "Half" },
                  { value: 16, label: "Two-Thirds" },
                  { value: 24, label: "Full" },
                ].map((opt) => (
                  <ToggleOption
                    key={opt.value}
                    selected={gridW === opt.value}
                    onClick={() => setGridW(opt.value)}
                  >
                    {opt.label}
                  </ToggleOption>
                ))}
              </div>
            </div>
            <div className="space-y-2">
              <Label>Height</Label>
              <div className="flex flex-wrap gap-2">
                {[
                  { value: 3, label: "Small" },
                  { value: 4, label: "Compact" },
                  { value: 6, label: "Medium" },
                  { value: 8, label: "Large" },
                  { value: 10, label: "X-Large" },
                  { value: 12, label: "Tall" },
                ].map((opt) => (
                  <ToggleOption
                    key={opt.value}
                    selected={gridH === opt.value}
                    onClick={() => setGridH(opt.value)}
                  >
                    {opt.label}
                  </ToggleOption>
                ))}
              </div>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={!isValid}>
            {isEditMode ? "Update Panel" : "Add Panel"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

PanelConfigDialogComponent.displayName = "PanelConfigDialog";

export const PanelConfigDialog = memo(PanelConfigDialogComponent);
