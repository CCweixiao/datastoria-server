"use client";

import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Button } from "@/components/ui/button";
import type { ConnectionConfig } from "@/lib/connection/connection-config";
import { cn } from "@/lib/utils";
import { Pencil } from "lucide-react";

interface ConnectionDetailPanelProps {
  conn: ConnectionConfig | null;
  onEdit?: (c: ConnectionConfig) => void;
}

interface ConnectionDetailContentProps {
  conn: ConnectionConfig | null;
  className?: string;
}

export function ConnectionDetailContent({ conn, className }: ConnectionDetailContentProps) {
  const { t } = useUiPreferences();
  if (!conn) {
    return null;
  }

  return (
    <div className={cn("px-2 py-2 text-[10px] text-popover-foreground", className)}>
      <div className="flex flex-col gap-y-2">
        <div>
          <div className="text-xs text-muted-foreground">{t("connection.name")}</div>
          <div className="text-xs font-medium break-all">{conn.name}</div>
        </div>

        <div>
          <div className="text-xs text-muted-foreground">{t("connection.url")}</div>
          <div className="text-xs break-all">{conn.url}</div>
        </div>

        <div>
          <div className="text-xs text-muted-foreground">{t("connection.user")}</div>
          <div className="text-xs break-all">{conn.user}</div>
        </div>

        <div>
          <div className="text-xs text-muted-foreground">{t("connection.password")}</div>
          <div className="text-xs">{t("connection.passwordStored")}</div>
        </div>

        <div>
          <div className="text-xs text-muted-foreground">{t("connection.cluster")}</div>
          <div className="text-xs break-all">{conn.cluster || t("common.notAvailable")}</div>
        </div>
      </div>
    </div>
  );
}

export function ConnectionDetailPanel({ conn, onEdit }: ConnectionDetailPanelProps) {
  const { t } = useUiPreferences();
  if (!conn) {
    return null;
  }

  return (
    <div
      data-panel="right"
      className="w-[260px] h-full min-h-0 flex-shrink-0 flex flex-col p-0 bg-popover rounded-sm text-popover-foreground shadow-md"
    >
      <ConnectionDetailContent conn={conn} className="flex-1 min-h-0 overflow-auto" />
      <div className="h-px bg-border" />
      <div className="h-9 shrink-0 flex items-center">
        <Button
          variant="ghost"
          size="sm"
          className="px-2 font-normal text-sm w-full h-9 rounded-none"
          onClick={() => onEdit?.(conn)}
        >
          <Pencil className="h-4 w-4 mr-2" />
          {t("connection.edit")}
        </Button>
      </div>
    </div>
  );
}
