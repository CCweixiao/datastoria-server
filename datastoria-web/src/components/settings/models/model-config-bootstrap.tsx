"use client";

import { AgentConfigurationManager } from "@/components/settings/agent/agent-manager";
import { ModelManager } from "@/components/settings/models/model-manager";
import { getAiConfigurationGateway } from "@/lib/ai/configuration/configuration-gateway";
import { fetchAvailableModels } from "@/lib/ai/llm/available-models-client";
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

interface ModelConfigBootstrapContextValue {
  /** True once the initial model catalog fetch has completed (or failed). */
  isReady: boolean;
}

const ModelConfigBootstrapContext = createContext<ModelConfigBootstrapContextValue>({
  isReady: false,
});

let bootstrapCatalog:
  | {
      key: string;
      promise: Promise<void>;
    }
  | undefined;

async function bootstrapModelCatalog(): Promise<boolean> {
  const manager = ModelManager.getInstance();

  try {
    const [{ systemModels }] = await Promise.all([
      fetchAvailableModels(),
      AgentConfigurationManager.hydrate(),
    ]);

    manager.setSystemModels(systemModels, false);
    manager.setProviderSettings(
      Array.from(
        new Map(
          systemModels.map((model) => [
            model.provider,
            {
              provider: model.provider,
              providerId: "",
              credentialConfigured: true,
            },
          ])
        ).values()
      )
    );
    const selectedModelId = await getAiConfigurationGateway().getModelPreference();
    manager.setServerSelectedModel(selectedModelId);
    return true;
  } catch (error) {
    console.error("Failed to bootstrap model catalog:", error);
    return false;
  }
}

export async function refreshModelCatalog(): Promise<void> {
  bootstrapCatalog = undefined;
  await getBootstrapCatalogPromise();
}

function getBootstrapCatalogPromise(): Promise<void> {
  const key = "spring";

  if (!bootstrapCatalog || bootstrapCatalog.key !== key) {
    const promise = bootstrapModelCatalog().then((success) => {
      if (!success && bootstrapCatalog?.key === key) {
        bootstrapCatalog = undefined;
      }
    });

    bootstrapCatalog = {
      key,
      promise,
    };
  }

  return bootstrapCatalog.promise;
}

/** Returns whether the initial model catalog has been bootstrapped. */
export function useModelConfigBootstrap(): ModelConfigBootstrapContextValue {
  return useContext(ModelConfigBootstrapContext);
}

export function ModelConfigBootstrap({ children }: { children: ReactNode }) {
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setIsReady(false);

    void (async () => {
      await getBootstrapCatalogPromise();
      if (!cancelled) {
        setIsReady(true);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <ModelConfigBootstrapContext.Provider value={{ isReady }}>
      {children}
    </ModelConfigBootstrapContext.Provider>
  );
}
