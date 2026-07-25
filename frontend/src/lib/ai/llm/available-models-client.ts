import { getAiConfigurationGateway } from "@/lib/ai/configuration/configuration-gateway";
import type { ModelProps } from "@/lib/ai/llm/llm-provider-factory";

export interface AvailableModelsResponse {
  systemModels: ModelProps[];
  githubModels: ModelProps[];
}

const inFlightRequests = new Map<string, Promise<AvailableModelsResponse>>();

export async function fetchAvailableModels(tokens?: {
  githubToken?: string;
}): Promise<AvailableModelsResponse> {
  const requestBody = tokens
    ? {
        ...(tokens.githubToken
          ? {
              github: {
                token: tokens.githubToken,
              },
            }
          : {}),
      }
    : {};
  const requestKey = JSON.stringify(requestBody);
  const existing = inFlightRequests.get(requestKey);
  if (existing) {
    return existing;
  }

  const request = (async () => {
    return getAiConfigurationGateway().listAvailableModels(tokens);
  })().finally(() => {
    inFlightRequests.delete(requestKey);
  });

  inFlightRequests.set(requestKey, request);
  return request;
}
