"use client";

import { loadEffectiveConfiguration, saveConfiguration } from "@/lib/configuration-client";
import {
  resolveLocale,
  translate,
  type LanguagePreference,
  type SupportedLocale,
  type TranslationParams,
} from "@/lib/i18n/i18n";
import type { MessageKey } from "@/lib/i18n/messages/en";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

export type Theme = "dark" | "light" | "system";

type UiPreferences = {
  theme: Theme;
  language: LanguagePreference;
};

type UiPreferencesContextValue = UiPreferences & {
  locale: SupportedLocale;
  setTheme: (theme: Theme) => void;
  setLanguage: (language: LanguagePreference) => void;
  t: (key: MessageKey, params?: TranslationParams) => string;
};

const CONFIG_KEY = "settings.ui";
const DEFAULT_PREFERENCES: UiPreferences = { theme: "dark", language: "system" };
const DEFAULT_CONTEXT: UiPreferencesContextValue = {
  ...DEFAULT_PREFERENCES,
  locale: "en",
  setTheme: () => undefined,
  setLanguage: () => undefined,
  t: (key, params) => translate("en", key, params),
};
const UiPreferencesContext = createContext<UiPreferencesContextValue>(DEFAULT_CONTEXT);

function isTheme(value: unknown): value is Theme {
  return value === "dark" || value === "light" || value === "system";
}

function isLanguage(value: unknown): value is LanguagePreference {
  return value === "system" || value === "en" || value === "zh-CN";
}

export function UiPreferencesProvider({ children }: { children: React.ReactNode }) {
  const [preferences, setPreferences] = useState<UiPreferences>(DEFAULT_PREFERENCES);
  const preferencesRef = useRef(preferences);

  useEffect(() => {
    preferencesRef.current = preferences;
  }, [preferences]);

  useEffect(() => {
    let cancelled = false;
    void loadEffectiveConfiguration()
      .then((configuration) => {
        const raw = configuration.entries[CONFIG_KEY];
        const stored = raw ? (JSON.parse(raw) as Partial<UiPreferences>) : {};
        if (!cancelled) {
          setPreferences({
            theme: isTheme(stored.theme) ? stored.theme : DEFAULT_PREFERENCES.theme,
            language: isLanguage(stored.language) ? stored.language : DEFAULT_PREFERENCES.language,
          });
        }
      })
      .catch((error) => console.error("Failed to load UI preferences:", error));
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const root = window.document.documentElement;
    root.classList.remove("light", "dark");
    const effectiveTheme =
      preferences.theme === "system"
        ? window.matchMedia("(prefers-color-scheme: dark)").matches
          ? "dark"
          : "light"
        : preferences.theme;
    root.classList.add(effectiveTheme);
  }, [preferences.theme]);

  const locale = resolveLocale(
    preferences.language,
    typeof navigator === "undefined" ? "en" : navigator.language
  );

  useEffect(() => {
    window.document.documentElement.lang = locale;
  }, [locale]);

  const updatePreferences = useCallback((updates: Partial<UiPreferences>) => {
    const next = { ...preferencesRef.current, ...updates };
    preferencesRef.current = next;
    setPreferences(next);
    void saveConfiguration(CONFIG_KEY, next).catch((error) =>
      console.error("Failed to save UI preferences:", error)
    );
  }, []);

  const value = useMemo<UiPreferencesContextValue>(
    () => ({
      ...preferences,
      locale,
      setTheme: (theme) => updatePreferences({ theme }),
      setLanguage: (language) => updatePreferences({ language }),
      t: (key, params) => translate(locale, key, params),
    }),
    [locale, preferences, updatePreferences]
  );

  return <UiPreferencesContext.Provider value={value}>{children}</UiPreferencesContext.Provider>;
}

export function useUiPreferences(): UiPreferencesContextValue {
  return useContext(UiPreferencesContext);
}
