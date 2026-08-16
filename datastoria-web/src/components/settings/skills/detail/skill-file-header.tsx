"use client";

import { Badge } from "@/components/ui/badge";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { FileText } from "lucide-react";
import { memo } from "react";

export const SkillFileHeader = memo(function SkillFileHeader({
  displayedFilename,
  currentSource,
  renderMode,
  showRenderToggle,
  onRenderModeChange,
}: {
  displayedFilename: string;
  currentSource: string | null;
  renderMode: "rendered" | "raw";
  showRenderToggle: boolean;
  onRenderModeChange: (value: "rendered" | "raw") => void;
}) {
  return (
    <div className="flex h-10 flex-shrink-0 items-center justify-between gap-2 border-b px-4">
      <div className="flex items-center gap-1.5 min-w-0">
        <FileText className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
        <span className="text-xs font-medium text-muted-foreground truncate">
          {displayedFilename}
        </span>
        {currentSource ? (
          <Badge variant="secondary" className="text-[10px] rounded-lg px-1.5 py-0 h-5 capitalize">
            {currentSource === "builtin" ? "Built-in" : currentSource}
          </Badge>
        ) : null}
      </div>
      <div className="flex items-center gap-1">
        <ToggleGroup
          type="single"
          value={renderMode}
          onValueChange={(value) => value && onRenderModeChange(value as "rendered" | "raw")}
          size="sm"
          variant="outline"
          className={showRenderToggle ? undefined : "invisible pointer-events-none"}
        >
          <ToggleGroupItem value="rendered" className="text-xs h-6 px-2">
            Preview
          </ToggleGroupItem>
          <ToggleGroupItem value="raw" className="text-xs h-6 px-2">
            Raw
          </ToggleGroupItem>
        </ToggleGroup>
      </div>
    </div>
  );
});
