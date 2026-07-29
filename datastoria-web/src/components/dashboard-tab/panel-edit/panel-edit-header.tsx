"use client";

import { Button } from "@/components/ui/button";
import { ArrowLeft, Play, X } from "lucide-react";
import { memo, useCallback, useEffect, useMemo } from "react";

interface PanelEditHeaderProps {
  onRunQuery: () => void;
  onApply: () => void;
  onDiscard: () => void;
  isDirty: boolean;
  isValid: boolean;
}

function PanelEditHeaderComponent({
  onRunQuery,
  onApply,
  onDiscard,
  isDirty,
  isValid,
}: PanelEditHeaderProps) {
  const handleDiscard = useCallback(() => {
    if (isDirty) {
      const confirmed = window.confirm("You have unsaved changes. Discard them?");
      if (!confirmed) return;
    }
    onDiscard();
  }, [isDirty, onDiscard]);

  // Escape key handler
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        handleDiscard();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [handleDiscard]);

  const runQueryLabel = useMemo(() => {
    if (typeof window === "undefined") return "Run Query (Ctrl+Enter)";
    const isMac =
      window.navigator.platform.toLowerCase().includes("mac") ||
      window.navigator.userAgent.toLowerCase().includes("mac");
    return isMac ? "Run Query (⌘↵)" : "Run Query (Ctrl+↵)";
  }, []);

  return (
    <div className="h-12 border-b bg-background flex items-center gap-2 px-3 shrink-0">
      {/* Back button */}
      <Button
        variant="ghost"
        size="sm"
        className="h-8 gap-1 text-muted-foreground"
        onClick={handleDiscard}
      >
        <ArrowLeft className="h-4 w-4" />
        Back
      </Button>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Run Query */}
      <Button
        variant="outline"
        size="sm"
        className="h-8 gap-1.5"
        onClick={onRunQuery}
        disabled={!isValid}
      >
        <Play className="h-3.5 w-3.5" />
        {runQueryLabel}
      </Button>

      {/* Apply */}
      <Button size="sm" className="h-8" onClick={onApply} disabled={!isValid}>
        Apply
      </Button>

      {/* Close */}
      <Button
        variant="ghost"
        size="icon"
        className="h-8 w-8 text-muted-foreground"
        onClick={handleDiscard}
      >
        <X className="h-4 w-4" />
      </Button>
    </div>
  );
}

PanelEditHeaderComponent.displayName = "PanelEditHeader";

export const PanelEditHeader = memo(PanelEditHeaderComponent);
