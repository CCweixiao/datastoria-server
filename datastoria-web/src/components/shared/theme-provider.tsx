import { UiPreferencesProvider, useUiPreferences, type Theme } from "./ui-preferences-provider";

type ThemeProviderProps = {
  children: React.ReactNode;
  defaultTheme?: Theme;
};

export function ThemeProvider({
  children,
  defaultTheme: _defaultTheme = "dark",
}: Omit<ThemeProviderProps, "storageKey">) {
  return <UiPreferencesProvider>{children}</UiPreferencesProvider>;
}

export const useTheme = useUiPreferences;
