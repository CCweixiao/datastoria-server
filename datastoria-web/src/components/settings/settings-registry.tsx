import { AgentEdit } from "@/components/settings/agent/agent-edit";
import { ModelsEdit } from "@/components/settings/models/models-edit";
import { QueryContextEdit } from "@/components/settings/query-context/query-context-edit";
import { SkillsEdit } from "@/components/settings/skills/skills-edit";
import { UiEdit } from "@/components/settings/ui/ui-edit";
import type { MessageKey } from "@/lib/i18n/messages/en";

export type SettingsSection = "query-context" | "ui" | "models" | "agent" | "skills";

export interface SettingsPageConfig {
  titleKey: MessageKey;
  descriptionKey: MessageKey;
  component: React.ComponentType<Record<string, unknown>>;
}

export const SETTINGS_REGISTRY: Record<SettingsSection, SettingsPageConfig> = {
  "query-context": {
    titleKey: "settings.queryContext.title",
    descriptionKey: "settings.queryContext.description",
    component: QueryContextEdit,
  },
  ui: {
    titleKey: "settings.ui.title",
    descriptionKey: "settings.ui.description",
    component: UiEdit,
  },
  models: {
    titleKey: "settings.models.title",
    descriptionKey: "settings.models.description",
    component: ModelsEdit,
  },
  agent: {
    titleKey: "settings.agent.title",
    descriptionKey: "settings.agent.description",
    component: AgentEdit,
  },
  skills: {
    titleKey: "settings.skills.title",
    descriptionKey: "settings.skills.description",
    component: SkillsEdit,
  },
};
