import type { AvailableModelsResponse } from "@/lib/ai/llm/available-models-client";
import type { ModelProps } from "@/lib/ai/llm/llm-provider-factory";
import { BasePath } from "@/lib/base-path";

export type ConfigurationBackend = "node" | "java";
export type ServerModelProps = ModelProps & { configId?: string };

export interface AiConfigurationGateway {
  listAvailableModels(tokens?: { githubToken?: string }): Promise<AvailableModelsResponse>;
  getModelPreference(): Promise<string | undefined>;
  setModelPreference(model: ServerModelProps): Promise<void>;
  saveProviderCredential(provider: string, credential: string): Promise<void>;
  clearProviderCredential(provider: string): Promise<void>;
}

function backend(): ConfigurationBackend {
  return process.env.NEXT_PUBLIC_DATASTORIA_CONFIG_BACKEND === "java" ? "java" : "node";
}

function javaUrl(path: string): string {
  const base = (process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "").replace(/\/+$/, "");
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

class NodeConfigurationGateway implements AiConfigurationGateway {
  async listAvailableModels(tokens?: { githubToken?: string }): Promise<AvailableModelsResponse> {
    const body = tokens?.githubToken ? { github: { token: tokens.githubToken } } : {};
    return checkedJson(
      await fetch(BasePath.getURL("/api/ai/models/available"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
      "Load available models"
    );
  }

  async getModelPreference(): Promise<string | undefined> {
    return undefined;
  }

  async setModelPreference(): Promise<void> {
    // Node mode retains the existing local ModelManager persistence.
  }

  async saveProviderCredential(): Promise<void> {
    throw new Error("Server credential storage is only available with the Java backend");
  }

  async clearProviderCredential(): Promise<void> {
    throw new Error("Server credential storage is only available with the Java backend");
  }
}

class JavaConfigurationGateway implements AiConfigurationGateway {
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
}

const gateway: AiConfigurationGateway =
  backend() === "java" ? new JavaConfigurationGateway() : new NodeConfigurationGateway();

export function getAiConfigurationGateway(): AiConfigurationGateway {
  return gateway;
}

export function isJavaConfigurationBackend(): boolean {
  return backend() === "java";
}
