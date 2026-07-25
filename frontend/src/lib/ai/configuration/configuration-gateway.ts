import type { AvailableModelsResponse } from "@/lib/ai/llm/available-models-client";
import type { ModelProps } from "@/lib/ai/llm/llm-provider-factory";

export type ServerModelProps = ModelProps & { configId?: string };
export interface ServerProvider {
  id: string;
  providerKey: string;
  displayName: string;
  credentialConfigured: boolean;
  maskedHint?: string | null;
}

export interface AiConfigurationGateway {
  listAvailableModels(): Promise<AvailableModelsResponse>;
  listProviders(): Promise<ServerProvider[]>;
  getModelPreference(): Promise<string | undefined>;
  setModelPreference(model: ServerModelProps): Promise<void>;
  saveProviderCredential(provider: string, credential: string): Promise<void>;
  clearProviderCredential(provider: string): Promise<void>;
  setModelEnabled(model: ServerModelProps, enabled: boolean): Promise<void>;
}

function javaUrl(path: string): string {
  const base = (
    process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "http://127.0.0.1:8080"
  ).replace(/\/+$/, "");
  return `${base}${path}`;
}

function identityHeaders(): HeadersInit {
  const email = process.env.NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL;
  return email ? { "x-datastoria-user-email": email } : {};
}

async function checkedJson<T>(response: Response, operation: string): Promise<T> {
  if (!response.ok) {
    throw new Error(`${operation} failed: ${response.status}`);
  }
  return (await response.json()) as T;
}

class SpringConfigurationGateway implements AiConfigurationGateway {
  private providerKey(provider: string): string {
    return provider
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-|-$/g, "");
  }

  private async findOrCreateProvider(provider: string): Promise<{ id: string }> {
    const providerKey = this.providerKey(provider);
    const providers = await checkedJson<Array<{ id: string; providerKey: string }>>(
      await fetch(javaUrl("/api/admin/ai/providers"), { headers: identityHeaders() }),
      "Load providers"
    );
    const existing = providers.find((candidate) => candidate.providerKey === providerKey);
    if (existing) return existing;
    return checkedJson(
      await fetch(javaUrl("/api/admin/ai/providers"), {
        method: "POST",
        headers: { "Content-Type": "application/json", ...identityHeaders() },
        body: JSON.stringify({
          providerKey,
          displayName: provider,
          authType: "api_key",
          enabled: true,
          configJson: "{}",
        }),
      }),
      "Create provider"
    );
  }
  async listAvailableModels(): Promise<AvailableModelsResponse> {
    return checkedJson(
      await fetch(javaUrl("/api/ai/models/available"), {
        method: "POST",
        headers: { "Content-Type": "application/json", ...identityHeaders() },
        body: "{}",
      }),
      "Load Java model catalog"
    );
  }

  async listProviders(): Promise<ServerProvider[]> {
    return checkedJson(
      await fetch(javaUrl("/api/admin/ai/providers"), { headers: identityHeaders() }),
      "Load providers"
    );
  }

  async getModelPreference(): Promise<string | undefined> {
    const result = await checkedJson<{ selectedModelId: string | null }>(
      await fetch(javaUrl("/api/me/ai/model-preference"), {
        headers: identityHeaders(),
      }),
      "Load model preference"
    );
    return result.selectedModelId ?? undefined;
  }

  async setModelPreference(model: ServerModelProps): Promise<void> {
    if (!model.configId) {
      throw new Error("The selected server model is missing configId");
    }
    await checkedJson(
      await fetch(javaUrl("/api/me/ai/model-preference"), {
        method: "PUT",
        headers: { "Content-Type": "application/json", ...identityHeaders() },
        body: JSON.stringify({ modelConfigId: model.configId }),
      }),
      "Save model preference"
    );
  }

  async saveProviderCredential(provider: string, credential: string): Promise<void> {
    const configured = await this.findOrCreateProvider(provider);
    const response = await fetch(javaUrl(`/api/admin/ai/providers/${configured.id}/credential`), {
      method: "PUT",
      headers: { "Content-Type": "application/json", ...identityHeaders() },
      body: JSON.stringify({ secretKind: "api_key", value: credential }),
    });
    if (!response.ok) {
      throw new Error(`Save provider credential failed: ${response.status}`);
    }
  }

  async clearProviderCredential(provider: string): Promise<void> {
    const providerKey = this.providerKey(provider);
    const providers = await checkedJson<
      Array<{ id: string; providerKey: string; credentialConfigured: boolean }>
    >(
      await fetch(javaUrl("/api/admin/ai/providers"), { headers: identityHeaders() }),
      "Load providers"
    );
    const existing = providers.find((candidate) => candidate.providerKey === providerKey);
    if (!existing?.credentialConfigured) return;
    const response = await fetch(javaUrl(`/api/admin/ai/providers/${existing.id}/credential`), {
      method: "DELETE",
      headers: identityHeaders(),
    });
    if (!response.ok) {
      throw new Error(`Clear provider credential failed: ${response.status}`);
    }
  }

  async setModelEnabled(model: ServerModelProps, enabled: boolean): Promise<void> {
    if (!model.configId) {
      throw new Error("The server model is missing configId");
    }
    const currentResponse = await fetch(javaUrl(`/api/admin/ai/models/${model.configId}`), {
      headers: identityHeaders(),
    });
    const current = await checkedJson<{
      displayName: string;
      description?: string | null;
      source: string;
      isFree: boolean;
      capabilitiesJson?: string | null;
      generationDefaultsJson?: string | null;
      revision: number;
    }>(currentResponse, "Load model");
    await checkedJson(
      await fetch(javaUrl(`/api/admin/ai/models/${model.configId}`), {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          "If-Match": `"${current.revision}"`,
          ...identityHeaders(),
        },
        body: JSON.stringify({
          displayName: current.displayName,
          description: current.description ?? null,
          source: current.source,
          enabled,
          isFree: current.isFree,
          capabilitiesJson: current.capabilitiesJson ?? null,
          generationDefaultsJson: current.generationDefaultsJson ?? null,
        }),
      }),
      "Update model"
    );
  }
}

const gateway: AiConfigurationGateway = new SpringConfigurationGateway();

export function getAiConfigurationGateway(): AiConfigurationGateway {
  return gateway;
}

export function isJavaConfigurationBackend(): boolean {
  return true;
}
