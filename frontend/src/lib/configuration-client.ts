import { backendApiHeaders, backendApiUrl } from "@/lib/backend-api";

export interface EffectiveConfiguration {
  entries: Record<string, string>;
  revision: number;
}

export async function loadEffectiveConfiguration(): Promise<EffectiveConfiguration> {
  const response = await fetch(backendApiUrl("/api/me/ai/preferences"), {
    headers: backendApiHeaders(),
  });
  if (!response.ok) {
    throw new Error(`Failed to load configuration: ${response.status}`);
  }
  return (await response.json()) as EffectiveConfiguration;
}

export async function saveConfiguration(configKey: string, value: unknown): Promise<void> {
  const response = await fetch(backendApiUrl("/api/me/ai/preferences"), {
    method: "PUT",
    headers: backendApiHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({
      configKey,
      valueJson: JSON.stringify(value),
    }),
  });
  if (!response.ok) {
    throw new Error(`Failed to persist configuration: ${response.status}`);
  }
}
