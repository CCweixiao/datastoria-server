"use client";

import type { PanelDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { DashboardVisualizationPanel } from "@/components/shared/dashboard/dashboard-visualization-panel";
import TimeSpanSelector, {
  BUILT_IN_TIME_SPAN_LIST,
  type DisplayTimeSpan,
  type TimeSpan,
} from "@/components/shared/dashboard/timespan-selector";
import { memo, useCallback, useRef, useState } from "react";

interface PanelEditPreviewProps {
  descriptor: PanelDescriptor | null;
  previewKey: number;
}

function PanelEditPreviewComponent({ descriptor, previewKey }: PanelEditPreviewProps) {
  // Default to "Last 15 Mins"
  const defaultTimeSpan = BUILT_IN_TIME_SPAN_LIST[2]; // "Last 15 Mins"
  const [timeSpan, setTimeSpan] = useState<TimeSpan>(() => defaultTimeSpan.getTimeSpan());
  const timeSpanSelectorRef = useRef<TimeSpanSelector>(null);

  const handleTimeSpanChange = useCallback((span: DisplayTimeSpan) => {
    setTimeSpan(span.getTimeSpan());
  }, []);

  return (
    <div className="flex flex-col h-full">
      {/* Preview header with time span selector */}
      <div className="flex items-center justify-between px-3 py-1.5 border-b bg-muted/30 shrink-0">
        <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
          Preview
        </span>
        <TimeSpanSelector
          ref={timeSpanSelectorRef}
          defaultTimeSpan={defaultTimeSpan}
          onSelectedSpanChanged={handleTimeSpanChange}
          showTimeSpanSelector={true}
          showRefresh={true}
          showAutoRefresh={false}
          size="sm"
        />
      </div>

      {/* Preview content */}
      <div className="flex-1 min-h-0 p-3 overflow-auto">
        {descriptor ? (
          <div className="h-full w-full rounded-md border bg-card">
            <DashboardVisualizationPanel
              key={previewKey}
              descriptor={descriptor}
              initialTimeSpan={timeSpan}
              initialFilterExpression="1=1"
              initialLoading={true}
            />
          </div>
        ) : (
          <div className="h-full w-full flex items-center justify-center text-muted-foreground">
            <div className="text-center">
              <p className="text-sm">Run a query to see the preview</p>
              <p className="text-xs mt-1 text-muted-foreground/60">
                Write SQL in the editor below and press Run Query
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

PanelEditPreviewComponent.displayName = "PanelEditPreview";

export const PanelEditPreview = memo(PanelEditPreviewComponent);
