import {
  getAiConfigurationGateway,
  type ServerModelProps,
} from "@/lib/ai/configuration/configuration-gateway";
import type { ModelProps } from "@/lib/ai/llm/llm-provider-factory";

export interface ModelSetting {
  modelId: string;
  provider: string;
  disabled: boolean;
  free: boolean;
}

export interface ProviderSetting {
  provider: string;
  providerId: string;
  credentialConfigured: boolean;
  maskedHint?: string | null;
}

export const MODEL_CONFIG_UPDATED_EVENT = "MODEL_CONFIG_UPDATED";

class ModelManager {
  private static instance: ModelManager;

  private static createAutoModel(): ModelProps {
    return {
      provider: "System",
      modelId: "Auto",
      description: `Use the server-side default model configuration. 
Rate limit on request/token will apply.
If you have your API keys, you can configure your models in the settings.`,
    };
  }

  private systemModels: ModelProps[] = [];
  private systemModelsHydrated = false;
  private serverSelectedModel: ModelProps | undefined;
  private modelSettings: ModelSetting[] = [];
  private providerSettings: ProviderSetting[] = [];

  public static getInstance(): ModelManager {
    if (!ModelManager.instance) {
      ModelManager.instance = new ModelManager();
    }
    return ModelManager.instance;
  }

  public setSystemModels(models: ModelProps[], notify = true): void {
    const normalized = models.map((model) => ({
      ...model,
      source: "system" as const,
    }));
    const previous = JSON.stringify(this.systemModels);
    const next = JSON.stringify(normalized);
    if (previous === next) {
      return;
    }

    this.systemModels = normalized;
    this.systemModelsHydrated = true;
    if (notify) {
      this.notify();
    }
  }

  public hasSystemModelsHydrated(): boolean {
    return this.systemModelsHydrated;
  }

  /**
   * Get the server-provided model catalog.
   */
  public getAllModels(): ModelProps[] {
    return [...this.systemModels];
  }

  /**
   * Notify listeners that the model configuration has changed
   */
  private notify() {
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent(MODEL_CONFIG_UPDATED_EVENT));
    }
  }

  /**
   * Get the model selected in the backend preference.
   * @returns The selected model configuration or undefined
   */
  public getSelectedModel(): ModelProps | undefined {
    return this.serverSelectedModel;
  }

  /**
   * Save the selected model configuration to the backend.
   * @param model - The model configuration to select
   */
  public setSelectedModel(model: { provider: string; modelId: string }): void {
    const selected = this.getAllModels().find(
      (candidate) => candidate.provider === model.provider && candidate.modelId === model.modelId
    ) as ServerModelProps | undefined;
    if (!selected) return;
    this.serverSelectedModel = selected;
    void getAiConfigurationGateway()
      .setModelPreference(selected)
      .catch((error) => console.error("Failed to persist server model preference:", error));
    this.notify();
  }

  public setServerSelectedModel(configId: string | undefined): void {
    this.serverSelectedModel = configId
      ? this.getAllModels().find((model) => (model as ServerModelProps).configId === configId)
      : undefined;
    this.notify();
  }

  /**
   * Get the current server-derived model settings cache.
   * @returns Array of model settings
   */
  public getModelSettings(): ModelSetting[] {
    return this.modelSettings;
  }

  /**
   * Update the in-memory view cache. Persistence is performed through backend model APIs.
   * @param settings - Array of model settings to save
   */
  public setModelSettings(settings: ModelSetting[]): void {
    this.modelSettings = settings;
    this.notify();
  }

  /**
   * Get transient provider UI state. Credentials are never stored in the browser.
   * @returns Array of provider settings
   */
  public getProviderSettings(): ProviderSetting[] {
    return this.providerSettings;
  }

  /**
   * Update transient provider UI state only.
   * @param settings - Array of provider settings to save
   */
  public setProviderSettings(settings: ProviderSetting[]): void {
    this.providerSettings = settings.map((setting) => ({ ...setting }));
    this.notify();
  }

  /**
   * Get a specific model setting by modelId
   * @param modelId - The model ID to look up
   * @returns The model setting or undefined if not found
   */
  public getModelSetting(modelId: string): ModelSetting | undefined {
    const settings = this.getModelSettings();
    return settings.find((s) => s.modelId === modelId);
  }

  /**
   * Update a specific model setting
   * @param provider - The provider name for the model
   * @param modelId - The model ID to update
   * @param updates - Partial updates to apply
   */
  public updateModelSetting(
    provider: string,
    modelId: string,
    updates: Partial<Omit<ModelSetting, "modelId" | "provider">>
  ): void {
    const settings = this.getModelSettings();
    const index = settings.findIndex((s) => s.modelId === modelId && s.provider === provider);

    if (index >= 0) {
      settings[index] = { ...settings[index], ...updates };
    } else {
      // If model doesn't exist, create a new one
      settings.push({
        modelId,
        provider,
        disabled: false,
        free: false,
        ...updates,
      });
    }

    this.setModelSettings(settings);
  }

  /**
   * Update a specific provider setting
   * @param provider - The provider name to update
   * @param updates - Partial updates to apply
   */
  public updateProviderSetting(
    provider: string,
    updates: Partial<Omit<ProviderSetting, "provider" | "providerId">>
  ): void {
    const settings = this.getProviderSettings();
    const index = settings.findIndex((s) => s.provider === provider);

    if (index >= 0) {
      settings[index] = { ...settings[index], ...updates };
    } else {
      // If provider doesn't exist, create a new one
      settings.push({
        provider,
        providerId: "",
        credentialConfigured: false,
        ...updates,
      });
    }

    this.setProviderSettings(settings);
  }

  /**
   * Delete a provider setting
   * @param provider - The provider name to delete
   */
  public deleteProviderSetting(provider: string): void {
    const settings = this.getProviderSettings();
    const filtered = settings.filter((s) => s.provider !== provider);
    this.setProviderSettings(filtered);
  }

  /**
   * Get all available models that are enabled and have an API key configured.
   * Includes a special 'Auto' model representing the server-side default if available.
   * @returns Array of available models
   */
  public getAvailableModels(): ModelProps[] {
    const modelSettings = this.getModelSettings();
    const providerSettings = this.getProviderSettings();

    const userModels = this.getAllModels().flatMap((model) => {
      // Filter out models that are disabled in settings
      const setting = modelSettings.find(
        (s) => s.modelId === model.modelId && s.provider === model.provider
      );
      if (setting ? setting.disabled : model.disabled) return [];

      const providerSetting = providerSettings.find((p) => p.provider === model.provider);
      if (!providerSetting?.credentialConfigured) return [];

      return [model];
    });

    // Auto is useful only when at least one credential-backed server model can be selected.
    if (userModels.some((model) => model.autoSelectable !== false)) {
      return [ModelManager.createAutoModel(), ...userModels];
    }

    return userModels;
  }
}

export { ModelManager };
