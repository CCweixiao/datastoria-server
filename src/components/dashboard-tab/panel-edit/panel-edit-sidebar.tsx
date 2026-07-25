"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";
import { ChevronDown, ChevronRight } from "lucide-react";
import { memo, useState } from "react";
import {
  CHART_TYPE_LABELS,
  type ChartType,
  type GaugeOptions,
  type PieOptions,
  type StatOptions,
  type TableOptions,
  type TimeseriesOptions,
} from "./use-panel-edit-state";

// --- Toggle Option Button ---
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
        "px-2.5 py-1 rounded-md border text-xs transition-colors",
        selected ? "bg-primary text-primary-foreground border-primary" : "hover:bg-muted/50"
      )}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

// --- Collapsible Section ---
function Section({
  title,
  children,
  defaultOpen = true,
}: {
  title: string;
  children: React.ReactNode;
  defaultOpen?: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-b last:border-b-0">
      <button
        type="button"
        className="flex items-center gap-1.5 w-full px-3 py-2 text-xs font-medium text-muted-foreground uppercase tracking-wider hover:bg-muted/30 transition-colors"
        onClick={() => setOpen(!open)}
      >
        {open ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
        {title}
      </button>
      {open && <div className="px-3 pb-3 space-y-3">{children}</div>}
    </div>
  );
}

// --- Select dropdown ---
function SimpleSelect({
  value,
  options,
  onChange,
}: {
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-full h-8 rounded-md border bg-background px-2 text-xs"
    >
      {options.map((opt) => (
        <option key={opt.value} value={opt.value}>
          {opt.label}
        </option>
      ))}
    </select>
  );
}

// --- Main Sidebar Props ---
interface PanelEditSidebarProps {
  title: string;
  onTitleChange: (title: string) => void;
  chartType: ChartType;
  onChartTypeChange: (type: ChartType) => void;
  gridW: number;
  gridH: number;
  onGridSizeChange: (w: number, h: number) => void;
  // Type-specific options
  statOptions: StatOptions;
  onStatOptionsChange: (partial: Partial<StatOptions>) => void;
  timeseriesOptions: TimeseriesOptions;
  onTimeseriesOptionsChange: (partial: Partial<TimeseriesOptions>) => void;
  pieOptions: PieOptions;
  onPieOptionsChange: (partial: Partial<PieOptions>) => void;
  gaugeOptions: GaugeOptions;
  onGaugeOptionsChange: (partial: Partial<GaugeOptions>) => void;
  tableOptions: TableOptions;
  onTableOptionsChange: (partial: Partial<TableOptions>) => void;
}

function PanelEditSidebarComponent({
  title,
  onTitleChange,
  chartType,
  onChartTypeChange,
  gridW,
  gridH,
  onGridSizeChange,
  statOptions,
  onStatOptionsChange,
  timeseriesOptions,
  onTimeseriesOptionsChange,
  pieOptions,
  onPieOptionsChange,
  gaugeOptions,
  onGaugeOptionsChange,
  tableOptions,
  onTableOptionsChange,
}: PanelEditSidebarProps) {
  const isTitleEmpty = title.trim().length === 0;

  return (
    <div className="flex flex-col h-full">
      {/* Sidebar header */}
      <div className="flex items-center px-3 py-1.5 border-b bg-muted/30 shrink-0">
        <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
          Panel Options
        </span>
      </div>

      <ScrollArea className="flex-1">
        <div className="divide-y">
          {/* Panel title */}
          <Section title="Panel Title">
            <div>
              <Input
                value={title}
                onChange={(e) => onTitleChange(e.target.value)}
                placeholder="Enter panel name"
                className={cn(
                  "h-8 text-sm",
                  isTitleEmpty && "border-destructive focus-visible:ring-destructive"
                )}
              />
              {isTitleEmpty && (
                <p className="text-[11px] text-destructive mt-1">
                  Required — Apply is disabled without a name
                </p>
              )}
            </div>
          </Section>

          {/* Visualization type */}
          <Section title="Visualization">
            <div className="flex flex-wrap gap-1.5">
              {(Object.entries(CHART_TYPE_LABELS) as [ChartType, string][]).map(([type, label]) => (
                <ToggleOption
                  key={type}
                  selected={chartType === type}
                  onClick={() => onChartTypeChange(type)}
                >
                  {label}
                </ToggleOption>
              ))}
            </div>
          </Section>

          {/* Type-specific options */}
          {chartType === "stat" && (
            <StatOptionsSection options={statOptions} onChange={onStatOptionsChange} />
          )}
          {(chartType === "line" || chartType === "bar" || chartType === "area") && (
            <TimeseriesOptionsSection
              chartType={chartType}
              options={timeseriesOptions}
              onChange={onTimeseriesOptionsChange}
            />
          )}
          {chartType === "pie" && (
            <PieOptionsSection options={pieOptions} onChange={onPieOptionsChange} />
          )}
          {chartType === "gauge" && (
            <GaugeOptionsSection options={gaugeOptions} onChange={onGaugeOptionsChange} />
          )}
          {chartType === "table" && (
            <TableOptionsSection options={tableOptions} onChange={onTableOptionsChange} />
          )}

          {/* Grid size */}
          <Section title="Panel Size">
            <div className="space-y-2">
              <div>
                <Label className="text-xs">Width</Label>
                <div className="flex flex-wrap gap-1.5 mt-1">
                  {[
                    { value: 6, label: "Quarter" },
                    { value: 8, label: "Third" },
                    { value: 12, label: "Half" },
                    { value: 16, label: "2/3" },
                    { value: 24, label: "Full" },
                  ].map((opt) => (
                    <ToggleOption
                      key={opt.value}
                      selected={gridW === opt.value}
                      onClick={() => onGridSizeChange(opt.value, gridH)}
                    >
                      {opt.label}
                    </ToggleOption>
                  ))}
                </div>
              </div>
              <div>
                <Label className="text-xs">Height</Label>
                <div className="flex flex-wrap gap-1.5 mt-1">
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
                      onClick={() => onGridSizeChange(gridW, opt.value)}
                    >
                      {opt.label}
                    </ToggleOption>
                  ))}
                </div>
              </div>
            </div>
          </Section>

          {/* Template variables reference */}
          <Section title="Variable Reference" defaultOpen={false}>
            <div className="space-y-1.5 text-[11px]">
              <VarRow name="{timeFilter}" desc="Time range filter expression" />
              <VarRow name="{filterExpression}" desc="Combined filter conditions" />
              <VarRow name="{from:String}" desc="Start datetime string" />
              <VarRow name="{to:String}" desc="End datetime string" />
              <VarRow name="{rounding:UInt32}" desc="Time bucket size in seconds" />
              <VarRow name="{seconds:UInt32}" desc="Total time range in seconds" />
            </div>
          </Section>
        </div>
      </ScrollArea>
    </div>
  );
}

// --- Variable Reference Row ---
function VarRow({ name, desc }: { name: string; desc: string }) {
  return (
    <div className="flex items-start gap-2">
      <code className="text-[10px] bg-muted/50 px-1 py-0.5 rounded shrink-0 text-muted-foreground">
        {name}
      </code>
      <span className="text-muted-foreground/80">{desc}</span>
    </div>
  );
}

// --- Stat Options ---
function StatOptionsSection({
  options,
  onChange,
}: {
  options: StatOptions;
  onChange: (partial: Partial<StatOptions>) => void;
}) {
  return (
    <Section title="Stat Options">
      <div className="space-y-2">
        <div>
          <Label className="text-xs">Minimap</Label>
          <div className="flex gap-1.5 mt-1">
            {(["line", "area", "none"] as const).map((type) => (
              <ToggleOption
                key={type}
                selected={options.minimapType === type}
                onClick={() => onChange({ minimapType: type })}
              >
                {type === "none" ? "None" : type.charAt(0).toUpperCase() + type.slice(1)}
              </ToggleOption>
            ))}
          </div>
        </div>
        <div>
          <Label className="text-xs">Reducer</Label>
          <SimpleSelect
            value={options.reducer}
            options={[
              { value: "last", label: "Last" },
              { value: "first", label: "First" },
              { value: "avg", label: "Average" },
              { value: "sum", label: "Sum" },
              { value: "min", label: "Min" },
              { value: "max", label: "Max" },
              { value: "count", label: "Count" },
            ]}
            onChange={(v) => onChange({ reducer: v as StatOptions["reducer"] })}
          />
        </div>
        <div>
          <Label className="text-xs">Value Format</Label>
          <FormatSelect value={options.format} onChange={(v) => onChange({ format: v })} />
        </div>
      </div>
    </Section>
  );
}

// --- Timeseries Options ---
function TimeseriesOptionsSection({
  chartType,
  options,
  onChange,
}: {
  chartType: "line" | "bar" | "area";
  options: TimeseriesOptions;
  onChange: (partial: Partial<TimeseriesOptions>) => void;
}) {
  return (
    <Section title="Chart Options">
      <div className="space-y-2">
        <div>
          <Label className="text-xs">Legend</Label>
          <div className="flex gap-1.5 mt-1">
            {(["bottom", "inside", "none"] as const).map((p) => (
              <ToggleOption
                key={p}
                selected={options.legendPlacement === p}
                onClick={() => onChange({ legendPlacement: p })}
              >
                {p.charAt(0).toUpperCase() + p.slice(1)}
              </ToggleOption>
            ))}
          </div>
        </div>
        <div>
          <Label className="text-xs">Y-Axis Format</Label>
          <FormatSelect
            value={options.yAxisFormat}
            onChange={(v) => onChange({ yAxisFormat: v })}
          />
        </div>
        {chartType === "bar" && (
          <div className="flex items-center justify-between">
            <Label className="text-xs">Stacked</Label>
            <Switch
              checked={options.stacked}
              onCheckedChange={(checked) => onChange({ stacked: checked })}
            />
          </div>
        )}
      </div>
    </Section>
  );
}

// --- Pie Options ---
function PieOptionsSection({
  options,
  onChange,
}: {
  options: PieOptions;
  onChange: (partial: Partial<PieOptions>) => void;
}) {
  return (
    <Section title="Pie Options">
      <div className="space-y-2">
        <div>
          <Label className="text-xs">Legend</Label>
          <div className="flex flex-wrap gap-1.5 mt-1">
            {(["bottom", "inside", "right", "none"] as const).map((p) => (
              <ToggleOption
                key={p}
                selected={options.legendPlacement === p}
                onClick={() => onChange({ legendPlacement: p })}
              >
                {p.charAt(0).toUpperCase() + p.slice(1)}
              </ToggleOption>
            ))}
          </div>
        </div>
        <div>
          <Label className="text-xs">Label Format</Label>
          <SimpleSelect
            value={options.labelFormat}
            options={[
              { value: "name", label: "Name" },
              { value: "value", label: "Value" },
              { value: "percent", label: "Percent" },
              { value: "name-value", label: "Name + Value" },
              { value: "name-percent", label: "Name + Percent" },
            ]}
            onChange={(v) =>
              onChange({
                labelFormat: v as PieOptions["labelFormat"],
              })
            }
          />
        </div>
      </div>
    </Section>
  );
}

// --- Gauge Options ---
function GaugeOptionsSection({
  options,
  onChange,
}: {
  options: GaugeOptions;
  onChange: (partial: Partial<GaugeOptions>) => void;
}) {
  return (
    <Section title="Gauge Options">
      <div className="space-y-2">
        <div className="grid grid-cols-2 gap-2">
          <div>
            <Label className="text-xs">Min</Label>
            <Input
              type="number"
              value={options.min}
              onChange={(e) => onChange({ min: Number(e.target.value) })}
              className="h-7 text-xs mt-1"
            />
          </div>
          <div>
            <Label className="text-xs">Max</Label>
            <Input
              type="number"
              value={options.max}
              onChange={(e) => onChange({ max: Number(e.target.value) })}
              className="h-7 text-xs mt-1"
            />
          </div>
        </div>
        <div>
          <Label className="text-xs">Value Format</Label>
          <FormatSelect
            value={options.valueFormat}
            onChange={(v) => onChange({ valueFormat: v })}
          />
        </div>
      </div>
    </Section>
  );
}

// --- Table Options ---
function TableOptionsSection({
  options,
  onChange,
}: {
  options: TableOptions;
  onChange: (partial: Partial<TableOptions>) => void;
}) {
  return (
    <Section title="Table Options">
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <Label className="text-xs">Sticky Header</Label>
          <Switch
            checked={options.stickyHeader}
            onCheckedChange={(checked) => onChange({ stickyHeader: checked })}
          />
        </div>
        <div className="flex items-center justify-between">
          <Label className="text-xs">Server-side Sorting</Label>
          <Switch
            checked={options.serverSideSorting}
            onCheckedChange={(checked) => onChange({ serverSideSorting: checked })}
          />
        </div>
      </div>
    </Section>
  );
}

// --- Format Select (common for value formats) ---
function FormatSelect({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <SimpleSelect
      value={value}
      options={[
        { value: "", label: "Auto" },
        { value: "short_number", label: "Short Number" },
        { value: "comma_number", label: "Comma Number" },
        { value: "percentage", label: "Percentage" },
        { value: "binary_size", label: "Binary Size" },
        { value: "binary_size_per_second", label: "Binary Size/s" },
        { value: "millisecond", label: "Milliseconds" },
        { value: "microsecond", label: "Microseconds" },
        { value: "nanosecond", label: "Nanoseconds" },
        { value: "seconds", label: "Seconds" },
        { value: "rate", label: "Rate" },
        { value: "byte_rate", label: "Byte Rate" },
        { value: "timeDuration", label: "Duration" },
      ]}
      onChange={onChange}
    />
  );
}

PanelEditSidebarComponent.displayName = "PanelEditSidebar";

export const PanelEditSidebar = memo(PanelEditSidebarComponent);
