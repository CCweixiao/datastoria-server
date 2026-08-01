import { en, type MessageKey } from "./messages/en";
import { zhCN } from "./messages/zh-CN";

export type SupportedLocale = "en" | "zh-CN";
export type LanguagePreference = "system" | SupportedLocale;

const catalogs: Record<SupportedLocale, Record<MessageKey, string>> = {
  en,
  "zh-CN": zhCN,
};

export function normalizeLocale(language?: string | null): SupportedLocale {
  return language?.toLowerCase().startsWith("zh") ? "zh-CN" : "en";
}

export function resolveLocale(
  preference: LanguagePreference,
  systemLanguage?: string | null
): SupportedLocale {
  return preference === "system" ? normalizeLocale(systemLanguage) : preference;
}

export type TranslationParams = Record<string, string | number>;

export function translate(
  locale: SupportedLocale,
  key: MessageKey,
  params?: TranslationParams
): string {
  const message = catalogs[locale][key] ?? en[key];
  if (!params) return message;
  return message.replace(/\{(\w+)\}/g, (token, name: string) =>
    Object.prototype.hasOwnProperty.call(params, name) ? String(params[name]) : token
  );
}
