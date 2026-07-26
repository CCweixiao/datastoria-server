import { getAiConfigurationGateway } from "@/lib/ai/configuration/configuration-gateway";
import type { ModelProps } from "@/lib/ai/llm/llm-provider-factory";

export interface AvailableModelsResponse {
  systemModels: ModelProps[];
}

const inFlightRequests = new Map<string, Promise<AvailableModelsResponse>>();

export async function fetchAvailableModels(): Promise<AvailableModelsResponse> {
  const requestKey = "spring";
  const existing = inFlightRequests.get(requestKey);
  if (existing) {
    return existing;
  }

  const request = (async () => {
    return getAiConfigurationGateway().listAvailableModels();
  })().finally(() => {
    inFlightRequests.delete(requestKey);
  });

  inFlightRequests.set(requestKey, request);
  return request;
}
