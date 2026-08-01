"use client";

import { refreshModelCatalog } from "@/components/settings/models/model-config-bootstrap";
import { ProviderLogo } from "@/components/shared/provider-logo";
import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import {
  getAiConfigurationGateway,
  type ModelInput,
  type ProviderInput,
  type ServerModel,
  type ServerProvider,
} from "@/lib/ai/configuration/configuration-gateway";
import type { MessageKey } from "@/lib/i18n/messages/en";
import { toastManager } from "@/lib/toast";
import {
  Bot,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Server,
  Trash2,
  WandSparkles,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

type ProviderPreset = ProviderInput & {
  hintKey: MessageKey;
  color: string;
};

const PROVIDER_PRESETS: ProviderPreset[] = [
  {
    providerKey: "zhipu",
    displayName: "智谱 GLM",
    baseUrl: "https://open.bigmodel.cn/api/paas/v4",
    hintKey: "models.presetZhipu",
    color: "from-blue-500/15 to-cyan-500/5",
  },
  {
    providerKey: "kimi",
    displayName: "Kimi / Moonshot",
    baseUrl: "https://api.moonshot.cn/v1",
    hintKey: "models.presetKimi",
    color: "from-violet-500/15 to-fuchsia-500/5",
  },
  {
    providerKey: "minimax",
    displayName: "MiniMax",
    baseUrl: "https://api.minimaxi.com/v1",
    hintKey: "models.presetMinimax",
    color: "from-orange-500/15 to-amber-500/5",
  },
  {
    providerKey: "dashscope",
    displayName: "阿里云百炼",
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    hintKey: "models.presetDashscope",
    color: "from-purple-500/15 to-indigo-500/5",
  },
  {
    providerKey: "deepseek",
    displayName: "DeepSeek",
    baseUrl: "https://api.deepseek.com",
    hintKey: "models.presetDeepseek",
    color: "from-sky-500/15 to-blue-500/5",
  },
  {
    providerKey: "openai-compatible",
    displayName: "OpenAI Compatible",
    baseUrl: "",
    hintKey: "models.presetCustom",
    color: "from-slate-500/15 to-zinc-500/5",
  },
];

const EMPTY_PROVIDER: ProviderInput = {
  providerKey: "",
  displayName: "",
  baseUrl: "",
  authType: "api_key",
  enabled: true,
};

const EMPTY_MODEL: ModelInput = {
  providerId: "",
  modelKey: "",
  displayName: "",
  description: "",
  enabled: true,
  isFree: false,
  supportsImageInput: false,
  supportsReasoning: false,
  tier: "balanced",
  contextWindowTokens: undefined,
  maxOutputTokens: undefined,
};

function toProviderInput(input: ProviderInput): ProviderInput {
  return {
    providerKey: input.providerKey,
    displayName: input.displayName,
    baseUrl: input.baseUrl,
    authType: input.authType ?? "api_key",
    enabled: input.enabled ?? true,
  };
}

function modelCapabilities(model: ServerModel) {
  try {
    return JSON.parse(model.capabilitiesJson ?? "{}") as {
      supportsImageInput?: boolean;
      supportsReasoning?: boolean;
      tier?: ModelInput["tier"];
      contextWindowTokens?: number | null;
      maxOutputTokens?: number | null;
    };
  } catch {
    return {};
  }
}

export function ModelsEdit() {
  const { t } = useUiPreferences();
  const gateway = getAiConfigurationGateway();
  const [providers, setProviders] = useState<ServerProvider[]>([]);
  const [models, setModels] = useState<ServerModel[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string>();
  const [search, setSearch] = useState("");
  const [providerDialog, setProviderDialog] = useState(false);
  const [editingProvider, setEditingProvider] = useState<ServerProvider>();
  const [providerDraft, setProviderDraft] = useState<ProviderInput>(EMPTY_PROVIDER);
  const [credential, setCredential] = useState("");
  const [showCredential, setShowCredential] = useState(false);
  const [modelDialog, setModelDialog] = useState(false);
  const [editingModel, setEditingModel] = useState<ServerModel>();
  const [modelDraft, setModelDraft] = useState<ModelInput>(EMPTY_MODEL);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [nextProviders, nextModels] = await Promise.all([
        gateway.listProviders(),
        gateway.listModels(),
      ]);
      setProviders(nextProviders.filter((provider) => provider.authType !== "oauth"));
      setModels(nextModels);
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : t("models.loadFailed"), "error");
    } finally {
      setLoading(false);
    }
  }, [gateway, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const visibleProviders = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return providers;
    return providers.filter((provider) => {
      const providerModels = models.filter((model) => model.providerId === provider.id);
      return (
        provider.displayName.toLowerCase().includes(query) ||
        provider.providerKey.toLowerCase().includes(query) ||
        providerModels.some(
          (model) =>
            model.modelKey.toLowerCase().includes(query) ||
            model.displayName.toLowerCase().includes(query)
        )
      );
    });
  }, [models, providers, search]);

  const openNewProvider = (preset: ProviderInput = EMPTY_PROVIDER) => {
    setEditingProvider(undefined);
    setProviderDraft(toProviderInput(preset));
    setCredential("");
    setShowCredential(false);
    setProviderDialog(true);
  };

  const openEditProvider = (provider: ServerProvider) => {
    setEditingProvider(provider);
    setProviderDraft({
      providerKey: provider.providerKey,
      displayName: provider.displayName,
      baseUrl: provider.baseUrl ?? "",
      authType: provider.authType === "none" ? "none" : "api_key",
      enabled: provider.enabled,
    });
    setCredential("");
    setShowCredential(false);
    setProviderDialog(true);
  };

  const saveProvider = async () => {
    if (!providerDraft.providerKey.trim() || !providerDraft.displayName.trim()) {
      toastManager.show(t("models.providerRequired"), "warning");
      return;
    }
    if (providerDraft.baseUrl && !/^https?:\/\//i.test(providerDraft.baseUrl)) {
      toastManager.show(t("models.invalidBaseUrl"), "warning");
      return;
    }
    setBusy("provider");
    try {
      if (editingProvider) {
        const saved = await gateway.updateProvider(editingProvider, providerDraft);
        if (credential.trim()) {
          await gateway.saveProviderCredentialById(saved.id, credential.trim());
        }
      } else {
        await gateway.createProvider(providerDraft, credential);
      }
      setProviderDialog(false);
      await Promise.all([load(), refreshModelCatalog()]);
      toastManager.show(
        editingProvider ? t("models.providerUpdated") : t("models.providerAdded"),
        "success"
      );
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : t("models.saveFailed"), "error");
    } finally {
      setBusy(undefined);
    }
  };

  const removeProvider = async (provider: ServerProvider) => {
    const providerModels = models.filter((model) => model.providerId === provider.id);
    if (providerModels.length) {
      toastManager.show(t("models.deleteModelsFirst"), "warning");
      return;
    }
    if (!window.confirm(t("models.confirmDeleteProvider", { name: provider.displayName }))) return;
    setBusy(provider.id);
    try {
      await gateway.deleteProvider(provider);
      await Promise.all([load(), refreshModelCatalog()]);
      toastManager.show(t("models.providerDeleted"), "success");
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : t("models.deleteFailed"), "error");
    } finally {
      setBusy(undefined);
    }
  };

  const clearCredential = async (provider: ServerProvider) => {
    if (!window.confirm(t("models.confirmClearKey", { name: provider.displayName }))) return;
    setBusy(`key:${provider.id}`);
    try {
      await gateway.clearProviderCredentialById(provider.id);
      await Promise.all([load(), refreshModelCatalog()]);
      setEditingProvider((current) =>
        current?.id === provider.id
          ? { ...current, credentialConfigured: false, maskedHint: null }
          : current
      );
      toastManager.show(t("models.keyCleared"), "success");
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : t("models.clearFailed"), "error");
    } finally {
      setBusy(undefined);
    }
  };

  const openNewModel = (provider: ServerProvider, modelKey = "", displayName = "") => {
    setEditingModel(undefined);
    setModelDraft({
      ...EMPTY_MODEL,
      providerId: provider.id,
      modelKey,
      displayName: displayName || modelKey,
    });
    setModelDialog(true);
  };

  const openEditModel = (model: ServerModel) => {
    const capabilities = modelCapabilities(model);
    setEditingModel(model);
    setModelDraft({
      providerId: model.providerId,
      modelKey: model.modelKey,
      displayName: model.displayName,
      description: model.description ?? "",
      enabled: model.enabled,
      isFree: model.isFree,
      supportsImageInput: capabilities.supportsImageInput ?? false,
      supportsReasoning: capabilities.supportsReasoning ?? false,
      tier: capabilities.tier ?? "balanced",
      contextWindowTokens: capabilities.contextWindowTokens ?? undefined,
      maxOutputTokens: capabilities.maxOutputTokens ?? undefined,
    });
    setModelDialog(true);
  };

  const saveModel = async () => {
    if (!modelDraft.modelKey.trim() || !modelDraft.displayName.trim()) {
      toastManager.show(t("models.modelRequired"), "warning");
      return;
    }
    setBusy("model");
    try {
      if (editingModel) {
        await gateway.updateModel(editingModel, modelDraft);
      } else {
        await gateway.createModel(modelDraft);
      }
      setModelDialog(false);
      await Promise.all([load(), refreshModelCatalog()]);
      toastManager.show(
        editingModel ? t("models.modelUpdated") : t("models.modelAdded"),
        "success"
      );
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : t("models.saveFailed"), "error");
    } finally {
      setBusy(undefined);
    }
  };

  const removeModel = async (model: ServerModel) => {
    if (!window.confirm(t("models.confirmDeleteModel", { name: model.displayName }))) return;
    setBusy(model.id);
    try {
      await gateway.deleteModel(model);
      await Promise.all([load(), refreshModelCatalog()]);
      toastManager.show(t("models.modelDeleted"), "success");
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : t("models.deleteFailed"), "error");
    } finally {
      setBusy(undefined);
    }
  };

  const toggleModel = async (model: ServerModel, enabled: boolean) => {
    const capabilities = modelCapabilities(model);
    setBusy(`model:${model.id}`);
    try {
      await gateway.updateModel(model, {
        providerId: model.providerId,
        modelKey: model.modelKey,
        displayName: model.displayName,
        description: model.description ?? "",
        enabled,
        isFree: model.isFree,
        supportsImageInput: capabilities.supportsImageInput ?? false,
        supportsReasoning: capabilities.supportsReasoning ?? false,
        tier: capabilities.tier ?? "balanced",
        contextWindowTokens: capabilities.contextWindowTokens ?? undefined,
        maxOutputTokens: capabilities.maxOutputTokens ?? undefined,
      });
      await Promise.all([load(), refreshModelCatalog()]);
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : t("models.updateFailed"), "error");
    } finally {
      setBusy(undefined);
    }
  };

  const discover = async (provider: ServerProvider) => {
    setBusy(`discover:${provider.id}`);
    try {
      const discovered = await gateway.discoverModels(provider.id);
      const existing = new Set(
        models.filter((model) => model.providerId === provider.id).map((model) => model.modelKey)
      );
      const additions = discovered.filter((model) => !existing.has(model.modelKey));
      await Promise.all(
        additions.map((model) =>
          gateway.createModel({
            ...EMPTY_MODEL,
            providerId: provider.id,
            modelKey: model.modelKey,
            displayName: model.displayName || model.modelKey,
            supportsReasoning: model.supportsReasoning,
            supportsImageInput: model.supportsImageInput,
            tier: model.tier,
            contextWindowTokens: model.contextWindowTokens ?? undefined,
            maxOutputTokens: model.maxOutputTokens ?? undefined,
            source: "discovered",
          })
        )
      );
      await Promise.all([load(), refreshModelCatalog()]);
      toastManager.show(
        additions.length
          ? t("models.syncedCount", { count: additions.length })
          : t("models.catalogCurrent"),
        "success"
      );
    } catch (error) {
      toastManager.show(
        error instanceof Error ? error.message : t("models.discoveryFailed"),
        "error"
      );
    } finally {
      setBusy(undefined);
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col bg-muted/10">
      <div className="border-b bg-background px-6 py-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold">{t("models.title")}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{t("models.securityHelp")}</p>
          </div>
          <Button onClick={() => openNewProvider()}>
            <Plus className="mr-2 h-4 w-4" />
            {t("models.addProvider")}
          </Button>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-2 lg:grid-cols-3 xl:grid-cols-6">
          {PROVIDER_PRESETS.map((preset) => (
            <button
              key={preset.providerKey}
              type="button"
              onClick={() => openNewProvider(preset)}
              className={`rounded-lg border bg-gradient-to-br ${preset.color} p-3 text-left transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-sm`}
            >
              <div className="flex items-center gap-2 text-sm font-medium">
                <ProviderLogo provider={preset.displayName} className="h-4 w-4" />
                {preset.displayName}
              </div>
              <div className="mt-1 truncate text-xs text-muted-foreground">{t(preset.hintKey)}</div>
            </button>
          ))}
        </div>
        <div className="relative mt-4 max-w-md">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={t("models.search")}
            className="pl-9"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-6">
        {loading ? (
          <div className="flex h-48 items-center justify-center text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            {t("models.loading")}
          </div>
        ) : visibleProviders.length === 0 ? (
          <div className="flex min-h-64 flex-col items-center justify-center rounded-xl border border-dashed bg-background p-10 text-center">
            <div className="rounded-full bg-primary/10 p-4">
              <Server className="h-7 w-7 text-primary" />
            </div>
            <h3 className="mt-4 font-semibold">
              {search ? t("models.noMatch") : t("models.noProviders")}
            </h3>
            <p className="mt-1 max-w-md text-sm text-muted-foreground">{t("models.emptyHelp")}</p>
            {!search && (
              <Button className="mt-4" variant="outline" onClick={() => openNewProvider()}>
                <Plus className="mr-2 h-4 w-4" />
                {t("models.createFirstProvider")}
              </Button>
            )}
          </div>
        ) : (
          <div className="space-y-4">
            {visibleProviders.map((provider) => {
              const providerModels = models.filter((model) => model.providerId === provider.id);
              return (
                <Card key={provider.id} className="overflow-hidden shadow-none">
                  <CardHeader className="border-b bg-muted/20 px-5 py-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div className="flex min-w-0 items-center gap-3">
                        <div className="rounded-lg border bg-background p-2">
                          <ProviderLogo provider={provider.displayName} className="h-5 w-5" />
                        </div>
                        <div className="min-w-0">
                          <CardTitle className="flex items-center gap-2 text-base">
                            {provider.displayName}
                            {!provider.enabled && (
                              <Badge variant="secondary">{t("models.disabled")}</Badge>
                            )}
                          </CardTitle>
                          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                            <code>{provider.providerKey}</code>
                            <span>·</span>
                            <span className="max-w-[420px] truncate">
                              {provider.baseUrl || t("models.baseUrlMissing")}
                            </span>
                          </div>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge
                          variant={provider.credentialConfigured ? "default" : "outline"}
                          className="gap-1"
                        >
                          {provider.credentialConfigured ? (
                            <CheckCircle2 className="h-3 w-3" />
                          ) : (
                            <KeyRound className="h-3 w-3" />
                          )}
                          {provider.credentialConfigured
                            ? provider.maskedHint || t("models.keyConfigured")
                            : t("models.keyMissing")}
                        </Badge>
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={!provider.credentialConfigured || !!busy}
                          onClick={() => void discover(provider)}
                        >
                          {busy === `discover:${provider.id}` ? (
                            <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <RefreshCw className="mr-1 h-3.5 w-3.5" />
                          )}
                          {t("models.sync")}
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => openEditProvider(provider)}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="text-destructive hover:text-destructive"
                          disabled={busy === provider.id}
                          onClick={() => void removeProvider(provider)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent className="p-0">
                    {providerModels.length === 0 ? (
                      <div className="flex items-center justify-between px-5 py-5">
                        <div className="text-sm text-muted-foreground">
                          {t("models.providerEmpty")}
                        </div>
                        <Button size="sm" variant="outline" onClick={() => openNewModel(provider)}>
                          <Plus className="mr-1 h-4 w-4" />
                          {t("models.addModel")}
                        </Button>
                      </div>
                    ) : (
                      <div className="divide-y">
                        {providerModels.map((model) => {
                          const capabilities = modelCapabilities(model);
                          return (
                            <div
                              key={model.id}
                              className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 hover:bg-muted/20"
                            >
                              <div className="flex min-w-0 items-center gap-3">
                                <Bot className="h-4 w-4 shrink-0 text-muted-foreground" />
                                <div className="min-w-0">
                                  <div className="flex items-center gap-2">
                                    <span className="truncate text-sm font-medium">
                                      {model.displayName}
                                    </span>
                                    <code className="text-xs text-muted-foreground">
                                      {model.modelKey}
                                    </code>
                                  </div>
                                  <div className="mt-1 flex gap-1.5">
                                    {capabilities.supportsReasoning && (
                                      <Badge variant="outline" className="h-5 text-[10px]">
                                        {t("models.reasoning")}
                                      </Badge>
                                    )}
                                    {capabilities.supportsImageInput && (
                                      <Badge variant="outline" className="h-5 text-[10px]">
                                        {t("models.image")}
                                      </Badge>
                                    )}
                                    <Badge variant="outline" className="h-5 text-[10px]">
                                      {capabilities.tier === "flagship"
                                        ? t("models.flagship")
                                        : capabilities.tier === "fast"
                                          ? t("models.fast")
                                          : capabilities.tier === "specialized"
                                            ? t("models.specialized")
                                            : t("models.balanced")}
                                    </Badge>
                                    {capabilities.contextWindowTokens && (
                                      <Badge variant="outline" className="h-5 text-[10px]">
                                        {capabilities.contextWindowTokens >= 1_000_000
                                          ? `${capabilities.contextWindowTokens / 1_000_000}M`
                                          : `${Math.round(capabilities.contextWindowTokens / 1024)}K`}{" "}
                                        {t("models.context")}
                                      </Badge>
                                    )}
                                    {model.isFree && (
                                      <Badge variant="secondary" className="h-5 text-[10px]">
                                        {t("models.free")}
                                      </Badge>
                                    )}
                                  </div>
                                </div>
                              </div>
                              <div className="flex items-center gap-2">
                                <Switch
                                  checked={model.enabled}
                                  disabled={busy === `model:${model.id}`}
                                  onCheckedChange={(enabled) => void toggleModel(model, enabled)}
                                />
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  onClick={() => openEditModel(model)}
                                >
                                  <Pencil className="h-4 w-4" />
                                </Button>
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  className="text-destructive hover:text-destructive"
                                  onClick={() => void removeModel(model)}
                                >
                                  <Trash2 className="h-4 w-4" />
                                </Button>
                              </div>
                            </div>
                          );
                        })}
                        <div className="px-5 py-3">
                          <Button size="sm" variant="ghost" onClick={() => openNewModel(provider)}>
                            <Plus className="mr-1 h-4 w-4" />
                            {t("models.addModel")}
                          </Button>
                        </div>
                      </div>
                    )}
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}
      </div>

      <Dialog open={providerDialog} onOpenChange={setProviderDialog}>
        <DialogContent
          className="!z-[10020] max-h-[calc(100dvh-2rem)] max-w-xl grid-rows-[auto_minmax(0,1fr)_auto] overflow-hidden"
          overlayClassName="!z-[10010]"
        >
          <DialogHeader>
            <DialogTitle>
              {editingProvider ? t("models.editProvider") : t("models.providerDialogTitle")}
            </DialogTitle>
            <DialogDescription>{t("models.providerDialogHelp")}</DialogDescription>
          </DialogHeader>
          <div className="-mx-2 min-h-0 overflow-y-auto overscroll-contain px-2">
            {!editingProvider && (
              <div className="grid grid-cols-2 gap-2">
                {PROVIDER_PRESETS.filter(
                  (preset) => preset.providerKey !== "openai-compatible"
                ).map((preset) => (
                  <button
                    type="button"
                    key={preset.providerKey}
                    onClick={() => setProviderDraft(toProviderInput(preset))}
                    className="rounded-md border p-2 text-left text-xs hover:border-primary/50 hover:bg-muted/40"
                  >
                    <div className="font-medium">{preset.displayName}</div>
                    <div className="mt-0.5 truncate text-muted-foreground">{preset.baseUrl}</div>
                  </button>
                ))}
              </div>
            )}
            <div className="grid gap-4 py-2">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-2">
                  <Label htmlFor="provider-name">{t("models.displayName")}</Label>
                  <Input
                    id="provider-name"
                    autoComplete="off"
                    value={providerDraft.displayName}
                    onChange={(event) =>
                      setProviderDraft((current) => ({
                        ...current,
                        displayName: event.target.value,
                      }))
                    }
                    placeholder={t("models.displayNameExample")}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="provider-key">{t("models.providerKey")}</Label>
                  <Input
                    id="provider-key"
                    autoComplete="off"
                    value={providerDraft.providerKey}
                    disabled={!!editingProvider}
                    onChange={(event) =>
                      setProviderDraft((current) => ({
                        ...current,
                        providerKey: event.target.value.toLowerCase().replace(/[^a-z0-9-]/g, "-"),
                      }))
                    }
                    placeholder="zhipu"
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="provider-base-url">Base URL</Label>
                <Input
                  id="provider-base-url"
                  autoComplete="off"
                  value={providerDraft.baseUrl}
                  onChange={(event) =>
                    setProviderDraft((current) => ({
                      ...current,
                      baseUrl: event.target.value,
                    }))
                  }
                  placeholder="https://example.com/v1"
                />
                <p className="text-xs text-muted-foreground">{t("models.baseUrlHelp")}</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="provider-api-key">
                  API Key
                  {editingProvider?.credentialConfigured && (
                    <span className="ml-2 font-normal text-muted-foreground">
                      {t("models.keySaved", { hint: editingProvider.maskedHint ?? "" })}
                    </span>
                  )}
                </Label>
                <div className="relative">
                  <Input
                    id="provider-api-key"
                    autoComplete="new-password"
                    type={showCredential ? "text" : "password"}
                    value={credential}
                    onChange={(event) => setCredential(event.target.value)}
                    placeholder={
                      editingProvider?.credentialConfigured
                        ? t("models.keepKeyPlaceholder")
                        : t("models.keyPlaceholder")
                    }
                    className="pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowCredential((value) => !value)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground"
                  >
                    {showCredential ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>
              <div className="flex items-center justify-between rounded-lg border p-3">
                <div>
                  <Label>{t("models.enableProvider")}</Label>
                  <p className="text-xs text-muted-foreground">{t("models.enableProviderHelp")}</p>
                </div>
                <Switch
                  checked={providerDraft.enabled}
                  onCheckedChange={(enabled) =>
                    setProviderDraft((current) => ({ ...current, enabled }))
                  }
                />
              </div>
            </div>
          </div>
          <DialogFooter className="items-center sm:justify-between">
            <div>
              {editingProvider?.credentialConfigured && (
                <Button
                  variant="ghost"
                  className="text-destructive hover:text-destructive"
                  disabled={busy === `key:${editingProvider.id}`}
                  onClick={() => void clearCredential(editingProvider)}
                >
                  <KeyRound className="mr-2 h-4 w-4" />
                  {t("models.clearKey")}
                </Button>
              )}
            </div>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setProviderDialog(false)}>
                {t("common.cancel")}
              </Button>
              <Button disabled={busy === "provider"} onClick={() => void saveProvider()}>
                {busy === "provider" && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                {t("common.save")}
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={modelDialog} onOpenChange={setModelDialog}>
        <DialogContent
          className="!z-[10020] max-h-[calc(100dvh-2rem)] max-w-lg grid-rows-[auto_minmax(0,1fr)_auto] overflow-hidden"
          overlayClassName="!z-[10010]"
        >
          <DialogHeader>
            <DialogTitle>
              {editingModel ? t("models.editModel") : t("models.modelDialogTitle")}
            </DialogTitle>
            <DialogDescription>{t("models.modelDialogHelp")}</DialogDescription>
          </DialogHeader>
          <div className="-mx-2 grid min-h-0 gap-4 overflow-y-auto overscroll-contain px-2 py-2">
            <div className="space-y-2">
              <Label htmlFor="model-key">{t("models.modelId")}</Label>
              <Input
                id="model-key"
                value={modelDraft.modelKey}
                disabled={!!editingModel}
                onChange={(event) =>
                  setModelDraft((current) => ({
                    ...current,
                    modelKey: event.target.value,
                    displayName:
                      current.displayName === current.modelKey
                        ? event.target.value
                        : current.displayName,
                  }))
                }
                placeholder={t("models.modelIdPlaceholder")}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="model-name">{t("models.displayName")}</Label>
              <Input
                id="model-name"
                value={modelDraft.displayName}
                onChange={(event) =>
                  setModelDraft((current) => ({
                    ...current,
                    displayName: event.target.value,
                  }))
                }
                placeholder={t("models.modelNamePlaceholder")}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="model-description">{t("models.description")}</Label>
              <Textarea
                id="model-description"
                value={modelDraft.description}
                onChange={(event) =>
                  setModelDraft((current) => ({
                    ...current,
                    description: event.target.value,
                  }))
                }
                placeholder={t("models.descriptionPlaceholder")}
                rows={3}
              />
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div className="space-y-2">
                <Label htmlFor="model-tier">{t("models.tier")}</Label>
                <select
                  id="model-tier"
                  value={modelDraft.tier}
                  onChange={(event) =>
                    setModelDraft((current) => ({
                      ...current,
                      tier: event.target.value as ModelInput["tier"],
                    }))
                  }
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
                >
                  <option value="flagship">{t("models.flagship")}</option>
                  <option value="balanced">{t("models.balanced")}</option>
                  <option value="fast">{t("models.fast")}</option>
                  <option value="specialized">{t("models.specialized")}</option>
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="model-context">{t("models.contextWindow")}</Label>
                <Input
                  id="model-context"
                  type="number"
                  min={1}
                  value={modelDraft.contextWindowTokens ?? ""}
                  onChange={(event) =>
                    setModelDraft((current) => ({
                      ...current,
                      contextWindowTokens: event.target.value
                        ? Number(event.target.value)
                        : undefined,
                    }))
                  }
                  placeholder={t("models.tokenCount")}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="model-output">{t("models.maxOutput")}</Label>
                <Input
                  id="model-output"
                  type="number"
                  min={1}
                  value={modelDraft.maxOutputTokens ?? ""}
                  onChange={(event) =>
                    setModelDraft((current) => ({
                      ...current,
                      maxOutputTokens: event.target.value ? Number(event.target.value) : undefined,
                    }))
                  }
                  placeholder={t("models.tokenCount")}
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              {[
                [
                  "supportsReasoning",
                  t("models.supportsReasoning"),
                  t("models.supportsReasoningHelp"),
                ],
                ["supportsImageInput", t("models.supportsImage"), t("models.supportsImageHelp")],
                ["isFree", t("models.freeModel"), t("models.freeModelHelp")],
                ["enabled", t("models.enableModel"), t("models.enableModelHelp")],
              ].map(([key, title, description]) => (
                <div key={key} className="flex items-center justify-between rounded-lg border p-3">
                  <div>
                    <div className="text-sm font-medium">{title}</div>
                    <div className="text-xs text-muted-foreground">{description}</div>
                  </div>
                  <Switch
                    checked={Boolean(modelDraft[key as keyof ModelInput])}
                    onCheckedChange={(checked) =>
                      setModelDraft((current) => ({ ...current, [key]: checked }))
                    }
                  />
                </div>
              ))}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModelDialog(false)}>
              {t("common.cancel")}
            </Button>
            <Button disabled={busy === "model"} onClick={() => void saveModel()}>
              {busy === "model" ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <WandSparkles className="mr-2 h-4 w-4" />
              )}
              {t("models.saveModel")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
