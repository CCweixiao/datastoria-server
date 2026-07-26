import type { AvailableModelsResponse } from "@/lib/ai/llm/available-models-client";
import type { ModelProps } from "@/lib/ai/llm/llm-provider-factory";
import { backendApiFetch } from "@/lib/backend-api";

export type ServerModelProps = ModelProps & { configId?: string };
export interface ServerProvider {
  id: string;
  providerKey: string;
  displayName: string;
  baseUrl?: string | null;
  authType: "api_key" | "oauth" | "none";
  enabled: boolean;
  configJson?: string | null;
  revision: number;
  credentialConfigured: boolean;
  maskedHint?: string | null;
}

export interface ServerModel {
  id: string;
  providerId: string;
  modelKey: string;
  displayName: string;
  description?: string | null;
  source: "system" | "discovered" | "custom";
  enabled: boolean;
  isFree: boolean;
  capabilitiesJson?: string | null;
  generationDefaultsJson?: string | null;
  revision: number;
}

export interface ProviderInput {
  providerKey: string;
  displayName: string;
  baseUrl: string;
  authType?: "api_key" | "none";
  enabled?: boolean;
}

export interface ModelInput {
  providerId: string;
  modelKey: string;
  displayName: string;
  description?: string;
  enabled?: boolean;
  isFree?: boolean;
  supportsImageInput?: boolean;
  supportsReasoning?: boolean;
  tier?: "flagship" | "balanced" | "fast" | "specialized";
  contextWindowTokens?: number;
  maxOutputTokens?: number;
  source?: "discovered" | "custom";
}

export interface DiscoveredModel {
  modelKey: string;
  displayName: string;
  providerKey: string;
  tier: "flagship" | "balanced" | "fast" | "specialized";
  supportsReasoning: boolean;
  supportsImageInput: boolean;
  contextWindowTokens?: number | null;
  maxOutputTokens?: number | null;
}

export interface AiConfigurationGateway {
  listAvailableModels(): Promise<AvailableModelsResponse>;
  listProviders(): Promise<ServerProvider[]>;
  getModelPreference(): Promise<string | undefined>;
  setModelPreference(model: ServerModelProps): Promise<void>;
  saveProviderCredential(provider: string, credential: string): Promise<void>;
  saveProviderCredentialById(providerId: string, credential: string): Promise<void>;
  clearProviderCredentialById(providerId: string): Promise<void>;
  clearProviderCredential(provider: string): Promise<void>;
  setModelEnabled(model: ServerModelProps, enabled: boolean): Promise<void>;
  listModels(): Promise<ServerModel[]>;
  createProvider(input: ProviderInput, credential?: string): Promise<ServerProvider>;
  updateProvider(provider: ServerProvider, input: ProviderInput): Promise<ServerProvider>;
  deleteProvider(provider: ServerProvider): Promise<void>;
  createModel(input: ModelInput): Promise<ServerModel>;
  updateModel(model: ServerModel, input: ModelInput): Promise<ServerModel>;
  deleteModel(model: ServerModel): Promise<void>;
  discoverModels(providerId: string): Promise<DiscoveredModel[]>;
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
      await backendApiFetch(javaUrl("/api/admin/ai/providers"), { headers: identityHeaders() }),
      "Load providers"
    );
    const existing = providers.find((candidate) => candidate.providerKey === providerKey);
    if (existing) return existing;
    return checkedJson(
      await backendApiFetch(javaUrl("/api/admin/ai/providers"), {
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
      await backendApiFetch(javaUrl("/api/ai/models/available"), {
        method: "POST",
        headers: { "Content-Type": "application/json", ...identityHeaders() },
        body: "{}",
      }),
      "Load Java model catalog"
    );
  }

  async listProviders(): Promise<ServerProvider[]> {
    return checkedJson(
      await backendApiFetch(javaUrl("/api/admin/ai/providers"), { headers: identityHeaders() }),
      "Load providers"
    );
  }

  async listModels(): Promise<ServerModel[]> {
    return checkedJson(
      await backendApiFetch(javaUrl("/api/admin/ai/models"), { headers: identityHeaders() }),
      "Load models"
    );
  }

  async createProvider(input: ProviderInput, credential?: string): Promise<ServerProvider> {
    const provider = await checkedJson<ServerProvider>(
      await backendApiFetch(javaUrl("/api/admin/ai/providers"), {
        method: "POST",
        headers: { "Content-Type": "application/json", ...identityHeaders() },
        body: JSON.stringify({
          ...input,
          authType: input.authType ?? "api_key",
          enabled: input.enabled ?? true,
          configJson: "{}",
        }),
      }),
      "Create provider"
    );
    if (credential?.trim()) {
      await this.saveCredentialById(provider.id, credential.trim());
    }
    return provider;
  }

  async updateProvider(provider: ServerProvider, input: ProviderInput): Promise<ServerProvider> {
    return checkedJson(
      await backendApiFetch(javaUrl(`/api/admin/ai/providers/${provider.id}`), {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          "If-Match": `"${provider.revision}"`,
          ...identityHeaders(),
        },
        body: JSON.stringify({
          displayName: input.displayName,
          baseUrl: input.baseUrl,
          authType: input.authType ?? "api_key",
          enabled: input.enabled ?? true,
          configJson: provider.configJson ?? "{}",
        }),
      }),
      "Update provider"
    );
  }

  async deleteProvider(provider: ServerProvider): Promise<void> {
    const response = await backendApiFetch(javaUrl(`/api/admin/ai/providers/${provider.id}`), {
      method: "DELETE",
      headers: { "If-Match": `"${provider.revision}"`, ...identityHeaders() },
    });
    if (!response.ok) throw new Error(`Delete provider failed: ${response.status}`);
  }

  async createModel(input: ModelInput): Promise<ServerModel> {
    return checkedJson(
      await backendApiFetch(javaUrl("/api/admin/ai/models"), {
        method: "POST",
        headers: { "Content-Type": "application/json", ...identityHeaders() },
        body: JSON.stringify({
          providerId: input.providerId,
          modelKey: input.modelKey,
          displayName: input.displayName,
          description: input.description ?? "",
          source: input.source ?? "custom",
          enabled: input.enabled ?? true,
          isFree: input.isFree ?? false,
          capabilitiesJson: JSON.stringify({
            supportedEndpoints: ["chat"],
            autoSelectable: true,
            supportsImageInput: input.supportsImageInput ?? false,
            supportsTemperature: true,
            supportsReasoning: input.supportsReasoning ?? false,
            reasoningLevels: input.supportsReasoning ? ["low", "medium", "high"] : [],
            tier: input.tier ?? "balanced",
            contextWindowTokens: input.contextWindowTokens ?? null,
            maxOutputTokens: input.maxOutputTokens ?? null,
          }),
          generationDefaultsJson: "{}",
        }),
      }),
      "Create model"
    );
  }

  async updateModel(model: ServerModel, input: ModelInput): Promise<ServerModel> {
    return checkedJson(
      await backendApiFetch(javaUrl(`/api/admin/ai/models/${model.id}`), {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          "If-Match": `"${model.revision}"`,
          ...identityHeaders(),
        },
        body: JSON.stringify({
          displayName: input.displayName,
          description: input.description ?? "",
          source: model.source,
          enabled: input.enabled ?? true,
          isFree: input.isFree ?? false,
          capabilitiesJson: JSON.stringify({
            supportedEndpoints: ["chat"],
            autoSelectable: true,
            supportsImageInput: input.supportsImageInput ?? false,
            supportsTemperature: true,
            supportsReasoning: input.supportsReasoning ?? false,
            reasoningLevels: input.supportsReasoning ? ["low", "medium", "high"] : [],
            tier: input.tier ?? "balanced",
            contextWindowTokens: input.contextWindowTokens ?? null,
            maxOutputTokens: input.maxOutputTokens ?? null,
          }),
          generationDefaultsJson: model.generationDefaultsJson ?? "{}",
        }),
      }),
      "Update model"
    );
  }

  async deleteModel(model: ServerModel): Promise<void> {
    const response = await backendApiFetch(javaUrl(`/api/admin/ai/models/${model.id}`), {
      method: "DELETE",
      headers: { "If-Match": `"${model.revision}"`, ...identityHeaders() },
    });
    if (!response.ok) throw new Error(`Delete model failed: ${response.status}`);
  }

  async discoverModels(providerId: string): Promise<DiscoveredModel[]> {
    return checkedJson(
      await backendApiFetch(javaUrl(`/api/admin/ai/providers/${providerId}/models:discover`), {
        method: "POST",
        headers: identityHeaders(),
      }),
      "Discover models"
    );
  }

  async getModelPreference(): Promise<string | undefined> {
    const result = await checkedJson<{ selectedModelId: string | null }>(
      await backendApiFetch(javaUrl("/api/me/ai/model-preference"), {
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
      await backendApiFetch(javaUrl("/api/me/ai/model-preference"), {
        method: "PUT",
        headers: { "Content-Type": "application/json", ...identityHeaders() },
        body: JSON.stringify({ modelConfigId: model.configId }),
      }),
      "Save model preference"
    );
  }

  async saveProviderCredential(provider: string, credential: string): Promise<void> {
    const configured = await this.findOrCreateProvider(provider);
    await this.saveCredentialById(configured.id, credential);
  }

  async saveProviderCredentialById(providerId: string, credential: string): Promise<void> {
    await this.saveCredentialById(providerId, credential);
  }

  async clearProviderCredentialById(providerId: string): Promise<void> {
    const response = await backendApiFetch(
      javaUrl(`/api/admin/ai/providers/${providerId}/credential`),
      { method: "DELETE", headers: identityHeaders() }
    );
    if (!response.ok) {
      throw new Error(`Clear provider credential failed: ${response.status}`);
    }
  }

  private async saveCredentialById(providerId: string, credential: string): Promise<void> {
    const response = await backendApiFetch(
      javaUrl(`/api/admin/ai/providers/${providerId}/credential`),
      {
        method: "PUT",
        headers: { "Content-Type": "application/json", ...identityHeaders() },
        body: JSON.stringify({ secretKind: "api_key", value: credential }),
      }
    );
    if (!response.ok) {
      throw new Error(`Save provider credential failed: ${response.status}`);
    }
  }

  async clearProviderCredential(provider: string): Promise<void> {
    const providerKey = this.providerKey(provider);
    const providers = await checkedJson<
      Array<{ id: string; providerKey: string; credentialConfigured: boolean }>
    >(
      await backendApiFetch(javaUrl("/api/admin/ai/providers"), { headers: identityHeaders() }),
      "Load providers"
    );
    const existing = providers.find((candidate) => candidate.providerKey === providerKey);
    if (!existing?.credentialConfigured) return;
    const response = await backendApiFetch(
      javaUrl(`/api/admin/ai/providers/${existing.id}/credential`),
      {
        method: "DELETE",
        headers: identityHeaders(),
      }
    );
    if (!response.ok) {
      throw new Error(`Clear provider credential failed: ${response.status}`);
    }
  }

  async setModelEnabled(model: ServerModelProps, enabled: boolean): Promise<void> {
    if (!model.configId) {
      throw new Error("The server model is missing configId");
    }
    const currentResponse = await backendApiFetch(
      javaUrl(`/api/admin/ai/models/${model.configId}`),
      {
        headers: identityHeaders(),
      }
    );
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
      await backendApiFetch(javaUrl(`/api/admin/ai/models/${model.configId}`), {
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
