import { loadEffectiveConfiguration, saveConfiguration } from "@/lib/configuration-client";
import { createContext, useContext, useEffect, useState } from "react";

type Theme = "dark" | "light" | "system";

type ThemeProviderProps = {
  children: React.ReactNode;
  defaultTheme?: Theme;
};

type ThemeProviderState = {
  theme: Theme;
  setTheme: (theme: Theme) => void;
};

const initialState: ThemeProviderState = {
  theme: "dark",
  setTheme: () => null,
};

const ThemeProviderContext = createContext<ThemeProviderState>(initialState);
const CONFIG_KEY = "settings.ui";

export function ThemeProvider({
  children,
  defaultTheme = "dark",
  ...props
}: Omit<ThemeProviderProps, "storageKey">) {
  const [theme, setThemeState] = useState<Theme>(defaultTheme);

  useEffect(() => {
    let cancelled = false;
    void loadEffectiveConfiguration()
      .then((configuration) => {
        const raw = configuration.entries[CONFIG_KEY];
        const stored = raw ? (JSON.parse(raw) as { theme?: Theme }).theme : undefined;
        if (!cancelled && (stored === "dark" || stored === "light" || stored === "system")) {
          setThemeState(stored);
        }
      })
      .catch((error) => console.error("Failed to load theme:", error));
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const root = window.document.documentElement;

    root.classList.remove("light", "dark");

    if (theme === "system") {
      const systemTheme = window.matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light";

      root.classList.add(systemTheme);
      return;
    }

    root.classList.add(theme);
  }, [theme]);

  const setTheme = (theme: Theme) => {
    setThemeState(theme);
    void saveConfiguration(CONFIG_KEY, { theme }).catch((error) =>
      console.error("Failed to save theme:", error)
    );
  };

  const value = {
    theme,
    setTheme,
  };

  return (
    <ThemeProviderContext.Provider {...props} value={value}>
      {children}
    </ThemeProviderContext.Provider>
  );
}

export const useTheme = () => {
  const context = useContext(ThemeProviderContext);

  if (context === undefined) throw new Error("useTheme must be used within a ThemeProvider");

  return context;
};
