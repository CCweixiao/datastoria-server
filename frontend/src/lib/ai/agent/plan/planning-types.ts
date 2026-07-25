import type { LanguageModelUsage } from "@/lib/ai/ai-types";

export type Intent = "generator" | "optimizer" | "visualizer" | "general";

/**
 * The output of the "tool"
 */
export interface PlanToolOutput {
  intent: Intent;
  title: string | undefined;
  usage: LanguageModelUsage | undefined;
  reasoning: string | undefined;
}

/**
 * Metadata attached to chat messages for planner intent and token usage
 */
export type PlannerMetadata = {
  intent: Intent;
  usage: LanguageModelUsage;
};
