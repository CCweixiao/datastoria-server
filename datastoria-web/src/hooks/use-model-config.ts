import {
  MODEL_CONFIG_UPDATED_EVENT,
  ModelManager,
} from "@/components/settings/models/model-manager";
import { useCallback, useEffect, useState } from "react";

const modelConfigListeners = new Set<() => void>();

function notifyModelConfigListeners() {
  modelConfigListeners.forEach((listener) => listener());
}

function subscribeToModelConfig(listener: () => void): () => void {
  if (modelConfigListeners.size === 0) {
    window.addEventListener(MODEL_CONFIG_UPDATED_EVENT, notifyModelConfigListeners);
  }
  modelConfigListeners.add(listener);

  return () => {
    modelConfigListeners.delete(listener);
    if (modelConfigListeners.size === 0) {
      window.removeEventListener(MODEL_CONFIG_UPDATED_EVENT, notifyModelConfigListeners);
    }
  };
}

export function useModelConfig() {
  const manager = ModelManager.getInstance();
  const snapshot = useCallback(
    () => ({
      allModels: manager.getAllModels(),
      availableModels: manager.getAvailableModels(),
      selectedModel: manager.getSelectedModel(),
      modelSettings: manager.getModelSettings(),
      providerSettings: manager.getProviderSettings(),
    }),
    [manager]
  );
  const [config, setConfig] = useState(() => snapshot());
  const refresh = useCallback(() => setConfig(snapshot()), [snapshot]);

  useEffect(() => {
    return subscribeToModelConfig(refresh);
  }, [refresh]);

  return {
    ...config,
    isLoading: false,
    copilotModelsLoaded: true,
    setSelectedModel: (model: { provider: string; modelId: string }) =>
      manager.setSelectedModel(model),
    fetchDynamicModels: async () => undefined,
    refresh,
  };
}
