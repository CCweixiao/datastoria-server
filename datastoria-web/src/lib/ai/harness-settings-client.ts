import { backendApiFetch, backendApiHeaders, backendApiUrl } from "@/lib/backend-api";

/**
 * Tenant-level agent harness runtime overrides managed by administrators. Overrides replace the
 * `datastoria.agent.*` process defaults for every run in the tenant; a null field keeps the
 * process default. The server clamps values to the absolute bounds regardless of source.
 */
export interface HarnessSettingsKnobs {
  maxIters?: number | null;
  toolResultEvictionChars?: number | null;
  compactionTriggerRatio?: number | null;
  compactionFallbackContextTokens?: number | null;
}

export interface HarnessSettingsResponse {
  defaults: HarnessSettingsKnobs;
  overrides: HarnessSettingsKnobs;
  effective: HarnessSettingsKnobs;
  revision: number;
}

export async function loadHarnessSettings(): Promise<HarnessSettingsResponse> {
  const response = await backendApiFetch(backendApiUrl("/api/admin/ai/harness-settings"), {
    headers: backendApiHeaders(),
  });
  if (!response.ok) {
    throw new Error(`Failed to load agent runtime settings: ${response.status}`);
  }
  return (await response.json()) as HarnessSettingsResponse;
}

export async function saveHarnessSettings(
  overrides: HarnessSettingsKnobs,
  revision: number
): Promise<HarnessSettingsResponse> {
  const response = await backendApiFetch(backendApiUrl("/api/admin/ai/harness-settings"), {
    method: "PUT",
    headers: backendApiHeaders({
      "Content-Type": "application/json",
      "If-Match": String(revision),
    }),
    body: JSON.stringify({
      maxIters: overrides.maxIters ?? null,
      toolResultEvictionChars: overrides.toolResultEvictionChars ?? null,
      compactionTriggerRatio: overrides.compactionTriggerRatio ?? null,
      compactionFallbackContextTokens: overrides.compactionFallbackContextTokens ?? null,
    }),
  });
  if (!response.ok) {
    throw new Error(`Failed to save agent runtime settings: ${response.status}`);
  }
  return (await response.json()) as HarnessSettingsResponse;
}
