import type { ReasoningLevel } from "@/lib/ai/reasoning-levels";

export type ModelSource = "user" | "system";

export interface ModelProps {
  /** Persistent Spring model configuration identifier used for AgentScope execution. */
  configId?: string;
  provider: string;
  modelId: string;
  description?: string;
  free?: boolean;
  autoSelectable?: boolean;
  disabled?: boolean;
  supportedEndpoints?: string[];
  supportsImageInput?: boolean;
  supportsTemperature?: boolean;
  supportsReasoning?: boolean;
  reasoningLevels?: readonly ReasoningLevel[];
  source?: ModelSource;
}

export interface ProviderDefinition {
  logo?: string;
}

export const PROVIDERS: Record<string, ProviderDefinition> = {
  OpenAI: { logo: "openai.svg" },
  Anthropic: { logo: "anthropic.svg" },
  Google: { logo: "google.svg" },
  OpenRouter: { logo: "openrouter.svg" },
  Groq: { logo: "groq.svg" },
  Cerebras: { logo: "cerebras.svg" },
  Nebius: { logo: "nebius.svg" },
};

export function resolveModelSupportsImageInput(
  model?: Pick<ModelProps, "supportsImageInput"> | null
): boolean {
  return model?.supportsImageInput ?? false;
}

export function resolveModelSupportsReasoning(
  model?: Pick<ModelProps, "provider" | "modelId" | "supportsReasoning" | "reasoningLevels"> | null
): boolean {
  return model?.supportsReasoning ?? Boolean(model?.reasoningLevels?.length);
}

export function resolveModelReasoningLevels(
  model?: Pick<ModelProps, "provider" | "modelId" | "reasoningLevels"> | null
): readonly ReasoningLevel[] {
  return model?.reasoningLevels ?? [];
}
