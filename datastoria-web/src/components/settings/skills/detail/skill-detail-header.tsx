"use client";

import { Badge } from "@/components/ui/badge";
import type { SkillDetailResponse } from "@/lib/ai/skills/skill-provider";
import { ExternalLink } from "lucide-react";
import { memo } from "react";

export const SkillDetailHeader = memo(function SkillDetailHeader({
  detail,
}: {
  detail: SkillDetailResponse;
}) {
  return (
    <div className="flex min-w-0 flex-1 items-center justify-between gap-3">
      <div className="flex min-w-0 items-center gap-2">
        <span className="truncate text-sm font-semibold">{detail.name}</span>
        {detail.version ? (
          <Badge variant="secondary" className="shrink-0 px-1.5 py-0 text-xs">
            v{detail.version}
          </Badge>
        ) : null}
        {detail.author ? (
          <span className="flex shrink-0 items-center gap-1 text-xs text-muted-foreground">
            <span>author: {detail.author}</span>
            {detail.url ? (
              <a
                href={detail.url}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center text-muted-foreground transition-colors hover:text-foreground"
                aria-label={`Open ${detail.name} reference URL`}
                title={detail.url}
              >
                <ExternalLink className="h-3 w-3" />
              </a>
            ) : null}
          </span>
        ) : null}
      </div>
    </div>
  );
});
