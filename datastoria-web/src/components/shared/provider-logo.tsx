import { PROVIDERS } from "@/lib/ai/llm/llm-provider-factory";
import { BasePath } from "@/lib/base-path";
import { cn } from "@/lib/utils";
import type { ReactNode } from "react";

export function getProviderLogoUrl(provider: string): string | undefined {
  const normalized = provider.trim().toLowerCase();
  const definition = Object.entries(PROVIDERS).find(
    ([name]) => name.toLowerCase() === normalized
  )?.[1];
  const logo = definition?.logo;
  return logo ? BasePath.getURL(`/provider-logos/${logo}`) : undefined;
}

export function ProviderLogo({
  provider,
  className,
  fallback,
}: {
  provider: string;
  className?: string;
  fallback?: ReactNode;
}) {
  const url = getProviderLogoUrl(provider);

  if (!url) {
    return fallback ?? null;
  }

  return (
    <span
      aria-hidden="true"
      className={cn("inline-block shrink-0 bg-current", className ?? "h-4 w-4")}
      style={{
        mask: `url("${url}") center / contain no-repeat`,
        WebkitMask: `url("${url}") center / contain no-repeat`,
      }}
    />
  );
}
