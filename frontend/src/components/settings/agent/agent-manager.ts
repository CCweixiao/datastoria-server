import {
  DEFAULT_REASONING_LEVEL,
  normalizeReasoningLevel,
  type ReasoningLevel,
} from "@/lib/ai/reasoning-levels";
import { backendApiFetch, backendApiHeaders, backendApiUrl } from "@/lib/backend-api";

const CONFIG_KEY = "settings.ai.agent";
export const AGENT_CONFIG_UPDATED_EVENT = "AGENT_CONFIG_UPDATED";

// See clickhouse-error-code.ts
export const DEFAULT_AUTO_EXPLAIN_BLACKLIST = [
  "62", // SYNTAX_ERROR
  "194", // REQUIRED_PASSWORD
];

/** BCP-47 tags supported by AI response-language settings (default: English). */
export const AI_RESPONSE_LANGUAGE_OPTIONS = [
  { value: "en", label: "English" },
  { value: "zh-CN", label: "简体中文" },
  { value: "zh-TW", label: "繁體中文" },
  { value: "ja", label: "日本語" },
  { value: "ko", label: "한국어" },
  { value: "es", label: "Español" }, // Spanish (ISO 639-1)
  { value: "fr", label: "Français" },
  { value: "de", label: "Deutsch" },
] as const;

export type ResponseLanguage = (typeof AI_RESPONSE_LANGUAGE_OPTIONS)[number]["value"];
export type AIResponseLanguage = ResponseLanguage;

export const DEFAULT_AI_RESPONSE_LANGUAGE: AIResponseLanguage = "en";

export function normalizeAIResponseLanguage(raw: string | undefined): AIResponseLanguage {
  if (!raw) {
    return DEFAULT_AI_RESPONSE_LANGUAGE;
  }
  const option = AI_RESPONSE_LANGUAGE_OPTIONS.find((o) => o.value === raw);
  return option ? option.value : DEFAULT_AI_RESPONSE_LANGUAGE;
}

export const normalizeAutoExplainLanguage = normalizeAIResponseLanguage;
export const normalizeSqlReviewLanguage = normalizeAIResponseLanguage;

export type AgentConfiguration = {
  /** Whether to prune successful validate_sql tool calls from history. Default true. */
  pruneValidateSql?: boolean;
  /** Whether to request reasoning summaries from models that support them. Default true. */
  outputReasoning?: boolean;
  /** Preferred reasoning level for models that expose configurable reasoning. Defaults to DEFAULT_REASONING_LEVEL. */
  reasoningLevel?: ReasoningLevel;
  /** Whether eligible ClickHouse errors should auto-trigger an inline AI explanation. */
  autoExplainClickHouseErrors?: boolean;
  /** ClickHouse error codes that should never auto-trigger inline explanation. */
  autoExplainBlacklist?: string[];
  /** Language for AI responses in SQL editor actions (BCP-47). Default English. */
  aiResponseLanguage?: AIResponseLanguage;
  /** @deprecated use aiResponseLanguage */
  autoExplainLanguage?: ResponseLanguage;
  /** @deprecated use aiResponseLanguage */
  sqlReviewLanguage?: ResponseLanguage;
};

export class AgentConfigurationManager {
  private static configuration: AgentConfiguration | null = null;
  private static hydration: Promise<AgentConfiguration> | null = null;

  private static defaults(): AgentConfiguration {
    return {
      pruneValidateSql: true,
      outputReasoning: true,
      reasoningLevel: DEFAULT_REASONING_LEVEL,
      autoExplainClickHouseErrors: true,
      autoExplainBlacklist: DEFAULT_AUTO_EXPLAIN_BLACKLIST,
      aiResponseLanguage: DEFAULT_AI_RESPONSE_LANGUAGE,
    };
  }

  public static getConfiguration(): AgentConfiguration {
    if (!this.configuration) {
      const stored = this.defaults();
      this.configuration = {
        ...stored,
        reasoningLevel: normalizeReasoningLevel(stored.reasoningLevel),
        aiResponseLanguage: normalizeAIResponseLanguage(
          stored.aiResponseLanguage ?? stored.sqlReviewLanguage ?? stored.autoExplainLanguage
        ),
      };
    }
    return this.configuration!;
  }

  public static hydrate(): Promise<AgentConfiguration> {
    if (!this.hydration) {
      this.hydration = backendApiFetch(backendApiUrl("/api/me/ai/preferences"), {
        headers: backendApiHeaders(),
      })
        .then(async (response) => {
          if (!response.ok) {
            throw new Error(`Failed to load agent preferences: ${response.status}`);
          }
          const payload = (await response.json()) as { entries?: Record<string, string> };
          const stored = payload.entries?.[CONFIG_KEY];
          const parsed: AgentConfiguration & { mode?: unknown } = stored
            ? (JSON.parse(stored) as AgentConfiguration & { mode?: unknown })
            : this.defaults();
          const { mode: _legacyMode, ...current } = parsed;
          this.configuration = {
            ...this.defaults(),
            ...current,
            reasoningLevel: normalizeReasoningLevel(current.reasoningLevel),
            aiResponseLanguage: normalizeAIResponseLanguage(
              current.aiResponseLanguage ??
                current.sqlReviewLanguage ??
                current.autoExplainLanguage
            ),
          };
          return this.configuration;
        })
        .catch((error) => {
          this.hydration = null;
          throw error;
        });
    }
    return this.hydration;
  }

  public static setConfiguration(cfg: AgentConfiguration) {
    const {
      mode: _legacyMode,
      autoExplainLanguage: _legacyAutoExplain,
      sqlReviewLanguage: _legacySqlReview,
      ...rest
    } = cfg as AgentConfiguration & { mode?: unknown };
    const normalized = {
      ...rest,
      reasoningLevel: normalizeReasoningLevel(cfg.reasoningLevel),
      aiResponseLanguage: normalizeAIResponseLanguage(cfg.aiResponseLanguage),
    };
    this.configuration = normalized;
    void backendApiFetch(backendApiUrl("/api/me/ai/preferences"), {
      method: "PUT",
      headers: backendApiHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({
        configKey: CONFIG_KEY,
        valueJson: JSON.stringify(normalized),
      }),
    }).then((response) => {
      if (!response.ok) {
        console.error(`Failed to persist agent preferences: ${response.status}`);
      }
    });
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent(AGENT_CONFIG_UPDATED_EVENT));
    }
  }
}
