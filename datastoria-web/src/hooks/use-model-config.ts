import {
  MODEL_CONFIG_UPDATED_EVENT,
  ModelManager,
} from "@/components/settings/models/model-manager";
import { useCallback, useEffect, useState } from "react";

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
  const [config, setConfig] = useState(snapshot);
  const refresh = useCallback(() => setConfig(snapshot()), [snapshot]);

  useEffect(() => {
    window.addEventListener(MODEL_CONFIG_UPDATED_EVENT, refresh);
    return () => window.removeEventListener(MODEL_CONFIG_UPDATED_EVENT, refresh);
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
