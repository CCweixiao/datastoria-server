"use client";

import { refreshModelCatalog } from "@/components/settings/models/model-config-bootstrap";
import { ProviderLogo } from "@/components/shared/provider-logo";
import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { fetchAvailableModels } from "@/lib/ai/llm/available-models-client";
import type { ModelProps } from "@/lib/ai/llm/llm-provider-factory";
import { toastManager } from "@/lib/toast";
import {
  createUserModel,
  createUserProvider,
  deleteUserModel,
  deleteUserProvider,
  listUserModels,
  listUserProviders,
  updateUserProvider,
  type UserModel,
  type UserProvider,
  type UserProviderInput,
} from "@/lib/user-model-client";
import {
  Bot,
  ChevronDown,
  KeyRound,
  Loader2,
  LockKeyhole,
  Plus,
  Save,
  Server,
  Settings2,
  Trash2,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

const PROVIDER_PRESETS: Array<UserProviderInput> = [
  {
    providerKey: "openai-compatible",
    displayName: "OpenAI Compatible",
    baseUrl: "https://api.openai.com/v1",
  },
  {
    providerKey: "anthropic",
    displayName: "Anthropic",
    baseUrl: "https://api.anthropic.com",
  },
  {
    providerKey: "openrouter",
    displayName: "OpenRouter",
    baseUrl: "https://openrouter.ai/api/v1",
  },
  {
    providerKey: "kimi",
    displayName: "Kimi",
    baseUrl: "https://api.moonshot.cn/v1",
  },
  {
    providerKey: "minimax",
    displayName: "MiniMax",
    baseUrl: "https://api.minimax.io/v1",
  },
  {
    providerKey: "deepseek",
    displayName: "DeepSeek",
    baseUrl: "https://api.deepseek.com",
  },
  {
    providerKey: "custom",
    displayName: "Custom Provider",
    baseUrl: "https://",
  },
];

export function UserModelsEdit() {
  const { t } = useUiPreferences();
  const [systemModels, setSystemModels] = useState<ModelProps[]>([]);
  const [userModels, setUserModels] = useState<UserModel[]>([]);
  const [providers, setProviders] = useState<UserProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [providerDraft, setProviderDraft] = useState<UserProviderInput>(PROVIDER_PRESETS[0]);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    const [catalogResult, modelsResult, providersResult] = await Promise.allSettled([
      fetchAvailableModels(),
      listUserModels(),
      listUserProviders(),
    ]);
    if (catalogResult.status === "fulfilled") {
      setSystemModels(catalogResult.value.systemModels.filter((model) => model.source !== "user"));
    }
    if (modelsResult.status === "fulfilled") {
      setUserModels(modelsResult.value);
    }
    if (providersResult.status === "fulfilled") {
      setProviders(providersResult.value);
    }
    const failure = [catalogResult, modelsResult, providersResult].find(
      (result) => result.status === "rejected"
    );
    if (failure?.status === "rejected") {
      toastManager.show(errorMessage(failure.reason, t("models.loadFailed")), "error");
    }
    setLoading(false);
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  const systemGroups = useMemo(() => {
    const groups = new Map<string, ModelProps[]>();
    for (const model of systemModels) {
      const group = groups.get(model.provider) ?? [];
      group.push(model);
      groups.set(model.provider, group);
    }
    return [...groups.entries()];
  }, [systemModels]);

  const createProvider = async () => {
    if (
      !providerDraft.providerKey.trim() ||
      !providerDraft.displayName.trim() ||
      !isHttpUrl(providerDraft.baseUrl) ||
      !providerDraft.apiKey?.trim()
    ) {
      return;
    }
    setCreating(true);
    try {
      await createUserProvider(providerDraft);
      setCreateOpen(false);
      setProviderDraft(PROVIDER_PRESETS[0]);
      await load();
      toastManager.show(t("models.providerAdded"), "success");
    } catch (error) {
      toastManager.show(errorMessage(error, t("models.saveFailed")), "error");
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="h-full overflow-y-auto bg-muted/10">
      <div className="border-b bg-background px-5 py-4 sm:px-6">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-semibold tracking-tight">{t("models.workspaceTitle")}</h2>
            <p className="mt-0.5 text-sm text-muted-foreground">{t("models.workspaceHelpV2")}</p>
          </div>
          <Button size="sm" onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" />
            {t("models.addProvider")}
          </Button>
        </div>
      </div>
      <div className="mx-auto max-w-6xl space-y-6 px-5 py-5 sm:px-6">
        {loading ? (
          <div className="flex min-h-48 items-center justify-center text-sm text-muted-foreground">
            <Loader2 className="mr-2 h-4 w-4 animate-spin" /> {t("models.loading")}
          </div>
        ) : (
          <>
            <section className="space-y-3">
              <SectionHeading
                icon={<LockKeyhole className="h-4 w-4" />}
                title={t("models.systemModels")}
                help={t("models.systemModelsGroupedHelp")}
              />
              <div className="space-y-3">
                {systemGroups.map(([provider, models]) => (
                  <SystemProviderCard key={provider} provider={provider} models={models} />
                ))}
              </div>
            </section>

            <section className="space-y-3">
              <SectionHeading
                icon={<Settings2 className="h-4 w-4" />}
                title={t("models.myProviders")}
                help={t("models.myProvidersHelp")}
              />
              <div className="space-y-3">
                {providers.map((provider) => (
                  <PrivateProviderCard
                    key={provider.id}
                    provider={provider}
                    models={userModels.filter((model) => model.providerId === provider.id)}
                    reload={load}
                  />
                ))}
                {providers.length === 0 ? (
                  <button
                    type="button"
                    onClick={() => setCreateOpen(true)}
                    className="flex min-h-28 w-full flex-col items-center justify-center rounded-xl border border-dashed bg-background text-sm text-muted-foreground transition-colors hover:border-primary/50 hover:text-foreground"
                  >
                    <Plus className="mb-2 h-5 w-5" />
                    {t("models.createFirstProvider")}
                  </button>
                ) : null}
              </div>
            </section>
          </>
        )}
      </div>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent
          className="!z-[10020] max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-xl"
          overlayClassName="!z-[10010]"
        >
          <DialogHeader>
            <DialogTitle>{t("models.addProvider")}</DialogTitle>
            <DialogDescription>{t("models.addProviderHelp")}</DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-4">
            {PROVIDER_PRESETS.map((preset) => (
              <button
                type="button"
                key={preset.providerKey}
                onClick={() => setProviderDraft({ ...preset, apiKey: "" })}
                aria-pressed={providerDraft.providerKey === preset.providerKey}
                className={`flex h-12 min-w-0 items-center gap-2 rounded-md border px-2.5 text-left transition-colors hover:border-primary/50 ${
                  providerDraft.providerKey === preset.providerKey
                    ? "border-primary bg-primary/5 ring-1 ring-primary/20"
                    : "bg-background"
                }`}
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-muted text-foreground">
                  <ProviderLogo
                    provider={preset.displayName}
                    className="h-4 w-4"
                    fallback={<Server aria-hidden="true" className="h-4 w-4" />}
                  />
                </span>
                <span className="truncate text-xs font-medium sm:text-sm">
                  {preset.displayName}
                </span>
              </button>
            ))}
          </div>
          <div className="grid gap-3 py-1 sm:grid-cols-2">
            <Field label={t("models.displayName")}>
              <Input
                className="h-9"
                value={providerDraft.displayName}
                onChange={(event) =>
                  setProviderDraft((current) => ({ ...current, displayName: event.target.value }))
                }
              />
            </Field>
            <Field label={t("models.providerKey")}>
              <Input
                className="h-9"
                value={providerDraft.providerKey}
                onChange={(event) =>
                  setProviderDraft((current) => ({ ...current, providerKey: event.target.value }))
                }
              />
            </Field>
            <div className="sm:col-span-2">
              <Field label="Base URL">
                <Input
                  className="h-9"
                  value={providerDraft.baseUrl}
                  onChange={(event) =>
                    setProviderDraft((current) => ({ ...current, baseUrl: event.target.value }))
                  }
                  placeholder="https://api.example.com/v1"
                />
              </Field>
            </div>
            <div className="sm:col-span-2">
              <Field label="API Key">
                <Input
                  className="h-9"
                  type="password"
                  autoComplete="new-password"
                  value={providerDraft.apiKey ?? ""}
                  onChange={(event) =>
                    setProviderDraft((current) => ({ ...current, apiKey: event.target.value }))
                  }
                />
              </Field>
            </div>
          </div>
          <DialogFooter>
            <Button size="sm" variant="outline" onClick={() => setCreateOpen(false)}>
              {t("common.cancel")}
            </Button>
            <Button
              size="sm"
              onClick={() => void createProvider()}
              disabled={
                creating ||
                !providerDraft.displayName.trim() ||
                !providerDraft.providerKey.trim() ||
                !isHttpUrl(providerDraft.baseUrl) ||
                !providerDraft.apiKey?.trim()
              }
            >
              {creating ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              {t("common.save")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function SystemProviderCard({ provider, models }: { provider: string; models: ModelProps[] }) {
  const { t } = useUiPreferences();
  const [open, setOpen] = useState(false);
  return (
    <Collapsible
      open={open}
      onOpenChange={setOpen}
      className="overflow-hidden rounded-lg border bg-background"
    >
      <CollapsibleTrigger className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-muted/20">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md border bg-background">
          <ProviderLogo
            provider={provider}
            className="h-4 w-4"
            fallback={<Server aria-hidden="true" className="h-4 w-4" />}
          />
        </span>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium">{provider}</div>
          <div className="text-xs text-muted-foreground">
            {t("models.modelCount", { count: models.length })}
          </div>
        </div>
        <Badge variant="secondary" className="h-5 px-2 text-[10px]">
          <LockKeyhole className="mr-1 h-3 w-3" /> {t("models.readOnly")}
        </Badge>
        <ChevronDown className={`h-4 w-4 transition-transform ${open ? "rotate-180" : ""}`} />
      </CollapsibleTrigger>
      <CollapsibleContent className="border-t">
        <div className="divide-y">
          {models.map((model) => (
            <div
              key={model.configId ?? model.modelId}
              className="flex min-h-11 items-center gap-3 px-4 py-2 hover:bg-muted/10"
            >
              <Bot className="h-4 w-4 shrink-0 text-muted-foreground" />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium">{model.modelId}</div>
                {model.description ? (
                  <div className="truncate text-xs text-muted-foreground">{model.description}</div>
                ) : null}
              </div>
              {model.supportsReasoning ? (
                <Badge variant="outline" className="h-5 px-1.5 text-[10px]">
                  {t("models.reasoning")}
                </Badge>
              ) : null}
              {model.supportsImageInput ? (
                <Badge variant="outline" className="h-5 px-1.5 text-[10px]">
                  {t("models.image")}
                </Badge>
              ) : null}
              <Badge variant="secondary" className="h-5 px-1.5 text-[10px]">
                {t("models.readOnly")}
              </Badge>
            </div>
          ))}
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}

function PrivateProviderCard({
  provider,
  models,
  reload,
}: {
  provider: UserProvider;
  models: UserModel[];
  reload: () => Promise<void>;
}) {
  const { t } = useUiPreferences();
  const [open, setOpen] = useState(true);
  const [draft, setDraft] = useState<UserProviderInput>({
    providerKey: provider.providerKey,
    displayName: provider.displayName,
    baseUrl: provider.baseUrl,
    apiKey: "",
  });
  const [modelKey, setModelKey] = useState("");
  const [busy, setBusy] = useState(false);

  const save = async () => {
    if (!draft.displayName.trim() || !draft.providerKey.trim() || !isHttpUrl(draft.baseUrl)) return;
    setBusy(true);
    try {
      await updateUserProvider(provider, draft);
      await Promise.all([reload(), refreshModelCatalog()]);
      toastManager.show(t("models.providerUpdated"), "success");
    } catch (error) {
      toastManager.show(errorMessage(error, t("models.saveFailed")), "error");
    } finally {
      setBusy(false);
    }
  };

  const addModel = async () => {
    const key = modelKey.trim();
    if (!key) return;
    setBusy(true);
    try {
      await createUserModel({
        providerId: provider.id,
        modelKey: key,
        displayName: key,
        description: "",
      });
      setModelKey("");
      await Promise.all([reload(), refreshModelCatalog()]);
      toastManager.show(t("models.modelAdded"), "success");
    } catch (error) {
      toastManager.show(errorMessage(error, t("models.saveFailed")), "error");
    } finally {
      setBusy(false);
    }
  };

  const removeModel = async (model: UserModel) => {
    if (!window.confirm(t("models.confirmDeleteModel", { name: model.displayName }))) return;
    await deleteUserModel(model);
    await Promise.all([reload(), refreshModelCatalog()]);
  };

  const removeProvider = async () => {
    if (models.length > 0) {
      toastManager.show(t("models.deleteModelsFirst"), "error");
      return;
    }
    if (!window.confirm(t("models.confirmDeleteProvider", { name: provider.displayName }))) return;
    await deleteUserProvider(provider);
    await reload();
  };

  return (
    <Collapsible
      open={open}
      onOpenChange={setOpen}
      className="overflow-hidden rounded-lg border bg-background"
    >
      <CollapsibleTrigger className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-muted/20">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md border bg-background text-primary">
          <ProviderLogo
            provider={provider.displayName}
            className="h-4 w-4"
            fallback={<Server aria-hidden="true" className="h-4 w-4" />}
          />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="truncate text-sm font-medium">{provider.displayName}</span>
            <Badge variant="outline" className="h-5 px-2 text-[10px]">
              {t("models.privateBadge")}
            </Badge>
          </div>
          <div className="flex min-w-0 items-center gap-1.5 text-xs text-muted-foreground">
            <span className="shrink-0">{t("models.modelCount", { count: models.length })}</span>
            <span>·</span>
            <span className="truncate" title={provider.baseUrl}>
              {provider.baseUrl}
            </span>
          </div>
        </div>
        <ChevronDown className={`h-4 w-4 transition-transform ${open ? "rotate-180" : ""}`} />
      </CollapsibleTrigger>
      <CollapsibleContent className="border-t bg-muted/10 p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="API Key">
            <div className="relative">
              <KeyRound className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                type="password"
                autoComplete="new-password"
                className="h-9 pl-9"
                value={draft.apiKey ?? ""}
                placeholder={provider.maskedHint ?? t("models.keepKeyPlaceholder")}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, apiKey: event.target.value }))
                }
              />
            </div>
          </Field>
          <Field label="Base URL">
            <Input
              className="h-9"
              value={draft.baseUrl}
              onChange={(event) =>
                setDraft((current) => ({ ...current, baseUrl: event.target.value }))
              }
            />
          </Field>
          <Field label={t("models.providerKey")}>
            <Input
              className="h-9"
              value={draft.providerKey}
              onChange={(event) =>
                setDraft((current) => ({ ...current, providerKey: event.target.value }))
              }
            />
          </Field>
          <Field label={t("models.displayName")}>
            <Input
              className="h-9"
              value={draft.displayName}
              onChange={(event) =>
                setDraft((current) => ({ ...current, displayName: event.target.value }))
              }
            />
          </Field>
        </div>
        <div className="mt-3 flex justify-end">
          <Button
            size="sm"
            onClick={() => void save()}
            disabled={busy || !isHttpUrl(draft.baseUrl)}
          >
            <Save className="h-4 w-4" /> {t("models.saveConfiguration")}
          </Button>
        </div>

        <div className="mt-4 overflow-hidden rounded-md border bg-background">
          <div className="border-b bg-muted/20 px-3 py-2 text-xs font-medium text-muted-foreground">
            {t("models.modelsInProvider")}
          </div>
          <div className="divide-y">
            {models.map((model) => (
              <div key={model.id} className="flex min-h-10 items-center gap-3 px-3 py-1.5">
                <Bot className="h-4 w-4 shrink-0 text-muted-foreground" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">{model.displayName}</div>
                  {model.displayName !== model.modelKey ? (
                    <code className="block truncate text-[11px] text-muted-foreground">
                      {model.modelKey}
                    </code>
                  ) : null}
                </div>
                <Badge variant="outline" className="h-5 px-1.5 text-[10px]">
                  {t("models.enabled")}
                </Badge>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 text-destructive hover:text-destructive"
                  aria-label={t("models.confirmDeleteModel", { name: model.displayName })}
                  onClick={() => void removeModel(model)}
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              </div>
            ))}
          </div>
          <div className="flex gap-2 border-t bg-muted/10 p-2">
            <div className="relative flex-1">
              <Server className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="h-9 pl-9"
                value={modelKey}
                onChange={(event) => setModelKey(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") void addModel();
                }}
                placeholder={t("models.modelIdPlaceholder")}
              />
            </div>
            <Button
              size="sm"
              variant="outline"
              onClick={() => void addModel()}
              disabled={busy || !modelKey.trim()}
            >
              <Plus className="h-4 w-4" /> {t("models.addModel")}
            </Button>
          </div>
        </div>
        <div className="mt-3 flex justify-end">
          <Button
            size="sm"
            variant="ghost"
            className="text-destructive"
            onClick={() => void removeProvider()}
          >
            <Trash2 className="h-4 w-4" /> {t("models.deleteProvider")}
          </Button>
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}

function SectionHeading({
  icon,
  title,
  help,
}: {
  icon: React.ReactNode;
  title: string;
  help: string;
}) {
  return (
    <div className="flex items-start gap-2">
      <div className="mt-0.5 rounded-md bg-primary/10 p-1 text-primary">{icon}</div>
      <div>
        <h3 className="text-sm font-medium">{title}</h3>
        <p className="text-xs text-muted-foreground">{help}</p>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label className="text-xs">{label}</Label>
      {children}
    </div>
  );
}

function isHttpUrl(value: string): boolean {
  return /^https?:\/\/\S+$/i.test(value.trim());
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim() ? error.message : fallback;
}
