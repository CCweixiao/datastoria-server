"use client";

import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  loadHarnessSettings,
  saveHarnessSettings,
  type HarnessSettingsKnobs,
} from "@/lib/ai/harness-settings-client";
import { toastManager } from "@/lib/toast";
import { useEffect, useState } from "react";

interface KnobDescriptor {
  key: keyof HarnessSettingsKnobs;
  labelKey: "agent.runtimeMaxIters" | "agent.runtimeEvictionChars" | "agent.runtimeTriggerRatio" | "agent.runtimeFallbackTokens";
  helpKey: "agent.runtimeMaxItersHelp" | "agent.runtimeEvictionCharsHelp" | "agent.runtimeTriggerRatioHelp" | "agent.runtimeFallbackTokensHelp";
  step: string;
}

const KNOBS: KnobDescriptor[] = [
  {
    key: "maxIters",
    labelKey: "agent.runtimeMaxIters",
    helpKey: "agent.runtimeMaxItersHelp",
    step: "1",
  },
  {
    key: "toolResultEvictionChars",
    labelKey: "agent.runtimeEvictionChars",
    helpKey: "agent.runtimeEvictionCharsHelp",
    step: "1024",
  },
  {
    key: "compactionTriggerRatio",
    labelKey: "agent.runtimeTriggerRatio",
    helpKey: "agent.runtimeTriggerRatioHelp",
    step: "0.05",
  },
  {
    key: "compactionFallbackContextTokens",
    labelKey: "agent.runtimeFallbackTokens",
    helpKey: "agent.runtimeFallbackTokensHelp",
    step: "10000",
  },
];

/**
 * Admin-only tenant overrides for the agent harness runtime knobs. Empty fields follow the
 * process (config-file) defaults shown as placeholders; saved values apply to every run in the
 * tenant and are clamped server-side to the absolute bounds.
 */
export function AgentRuntimeSettings() {
  const { t } = useUiPreferences();
  const [values, setValues] = useState<Record<string, string>>({});
  const [defaults, setDefaults] = useState<HarnessSettingsKnobs>({});
  const [revision, setRevision] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void loadHarnessSettings()
      .then((settings) => {
        if (cancelled) {
          return;
        }
        setDefaults(settings.defaults);
        setRevision(settings.revision);
        const initial: Record<string, string> = {};
        for (const knob of KNOBS) {
          const overridden = settings.overrides[knob.key];
          initial[knob.key] = overridden === null || overridden === undefined ? "" : String(overridden);
        }
        setValues(initial);
      })
      .catch(() => {
        // 404 = backend without the endpoint (older build); degrade to a hint instead of
        // surfacing a console error for administrators.
        if (!cancelled) {
          setUnavailable(true);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSave = () => {
    const overrides: HarnessSettingsKnobs = {};
    for (const knob of KNOBS) {
      const raw = (values[knob.key] ?? "").trim();
      if (raw === "") {
        continue;
      }
      const parsed = Number(raw);
      if (!Number.isFinite(parsed)) {
        toastManager.show(t("agent.runtimeInvalidNumber"), "error");
        return;
      }
      overrides[knob.key] = parsed;
    }
    setIsSaving(true);
    void saveHarnessSettings(overrides, revision)
      .then((settings) => {
        setDefaults(settings.defaults);
        setRevision(settings.revision);
        const next: Record<string, string> = {};
        for (const knob of KNOBS) {
          const overridden = settings.overrides[knob.key];
          next[knob.key] = overridden === null || overridden === undefined ? "" : String(overridden);
        }
        setValues(next);
        toastManager.show(t("agent.runtimeSaved"), "success");
      })
      .catch((error) => {
        console.error("Failed to save agent runtime settings:", error);
        toastManager.show(t("agent.runtimeSaveFailed"), "error");
      })
      .finally(() => {
        setIsSaving(false);
      });
  };

  return (
    <div className="border-t px-4 py-4">
      <div className="mb-1 text-sm font-medium">{t("agent.runtimeTitle")}</div>
      <div className="mb-3 text-xs text-muted-foreground">{t("agent.runtimeHelp")}</div>
      {unavailable && (
        <div className="mb-3 rounded-md border border-dashed px-3 py-2 text-xs text-muted-foreground">
          {t("agent.runtimeUnavailable")}
        </div>
      )}
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        {KNOBS.map((knob) => (
          <div key={knob.key} className="space-y-1">
            <Label className="text-xs">{t(knob.labelKey)}</Label>
            <Input
              type="number"
              min={0}
              step={knob.step}
              disabled={isLoading || isSaving || unavailable}
              value={values[knob.key] ?? ""}
              onChange={(event) =>
                setValues((current) => ({ ...current, [knob.key]: event.target.value }))
              }
              placeholder={
                defaults[knob.key] === null || defaults[knob.key] === undefined
                  ? t("agent.runtimeFollowDefault")
                  : String(defaults[knob.key])
              }
              className="h-8"
            />
            <div className="text-[11px] leading-4 text-muted-foreground">{t(knob.helpKey)}</div>
          </div>
        ))}
      </div>
      <div className="mt-3 flex justify-end">
        <Button size="sm" onClick={handleSave} disabled={isLoading || isSaving || unavailable}>
          {t("common.save")}
        </Button>
      </div>
    </div>
  );
}
