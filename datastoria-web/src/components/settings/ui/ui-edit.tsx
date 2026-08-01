"use client";

import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { useEffect, useState } from "react";

export function UiEdit() {
  const { theme, setTheme, language, setLanguage, t } = useUiPreferences();
  const [isDark, setIsDark] = useState(false);

  useEffect(() => {
    const root = window.document.documentElement;

    const syncTheme = () => {
      setIsDark(root.classList.contains("dark"));
    };

    syncTheme();

    const observer = new MutationObserver(syncTheme);
    observer.observe(root, {
      attributes: true,
      attributeFilter: ["class"],
    });

    return () => observer.disconnect();
  }, [theme]);

  return (
    <div className="h-full flex flex-col">
      <div className="px-4 py-2 grid gap-2">
        <div className="grid grid-cols-[200px_300px_1fr] gap-8 items-start">
          <div className="space-y-1 pt-2">
            <Label>{t("settings.ui.darkMode")}</Label>
          </div>
          <div className="flex items-center h-10">
            <Switch
              checked={isDark}
              onCheckedChange={(checked) => setTheme(checked ? "dark" : "light")}
              aria-label={t("settings.ui.darkModeAria")}
            />
          </div>
          <div className="text-sm text-muted-foreground pt-2">{t("settings.ui.darkModeHelp")}</div>
        </div>

        <Separator />

        <div className="grid grid-cols-[200px_300px_1fr] gap-8 items-start">
          <div className="space-y-1 pt-2">
            <Label htmlFor="interface-language">{t("settings.ui.language")}</Label>
          </div>
          <div className="flex items-center h-10">
            <select
              id="interface-language"
              value={language}
              onChange={(event) => setLanguage(event.target.value as "system" | "en" | "zh-CN")}
              className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
            >
              <option value="system">{t("common.system")}</option>
              <option value="en">{t("common.english")}</option>
              <option value="zh-CN">{t("common.chinese")}</option>
            </select>
          </div>
          <div className="text-sm text-muted-foreground pt-2">{t("settings.ui.languageHelp")}</div>
        </div>

        <Separator />
      </div>
    </div>
  );
}
