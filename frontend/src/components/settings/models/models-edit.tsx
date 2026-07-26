"use client";

import { refreshModelCatalog } from "@/components/settings/models/model-config-bootstrap";
import { ProviderLogo } from "@/components/shared/provider-logo";
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
  hint: string;
  color: string;
};

const PROVIDER_PRESETS: ProviderPreset[] = [
  {
    providerKey: "zhipu",
    displayName: "智谱 GLM",
    baseUrl: "https://open.bigmodel.cn/api/paas/v4",
    hint: "GLM 系列 · OpenAI 兼容",
    color: "from-blue-500/15 to-cyan-500/5",
  },
  {
    providerKey: "kimi",
    displayName: "Kimi / Moonshot",
    baseUrl: "https://api.moonshot.cn/v1",
    hint: "Moonshot 与 Kimi 系列",
    color: "from-violet-500/15 to-fuchsia-500/5",
  },
  {
    providerKey: "minimax",
    displayName: "MiniMax",
    baseUrl: "https://api.minimaxi.com/v1",
    hint: "MiniMax OpenAI 兼容接口",
    color: "from-orange-500/15 to-amber-500/5",
  },
  {
    providerKey: "dashscope",
    displayName: "阿里云百炼",
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    hint: "通义千问 · DashScope",
    color: "from-purple-500/15 to-indigo-500/5",
  },
  {
    providerKey: "deepseek",
    displayName: "DeepSeek",
    baseUrl: "https://api.deepseek.com",
    hint: "DeepSeek V4 · OpenAI 兼容",
    color: "from-sky-500/15 to-blue-500/5",
  },
  {
    providerKey: "openai-compatible",
    displayName: "OpenAI Compatible",
    baseUrl: "",
    hint: "自定义兼容服务",
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
      toastManager.show(error instanceof Error ? error.message : "模型配置加载失败", "error");
    } finally {
      setLoading(false);
    }
  }, [gateway]);

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
      toastManager.show("请填写供应商标识和名称", "warning");
      return;
    }
    if (providerDraft.baseUrl && !/^https?:\/\//i.test(providerDraft.baseUrl)) {
      toastManager.show("Base URL 必须以 http:// 或 https:// 开头", "warning");
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
      toastManager.show(editingProvider ? "供应商已更新" : "供应商已添加", "success");
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : "保存失败", "error");
    } finally {
      setBusy(undefined);
    }
  };

  const removeProvider = async (provider: ServerProvider) => {
    const providerModels = models.filter((model) => model.providerId === provider.id);
    if (providerModels.length) {
      toastManager.show("请先删除该供应商下的模型", "warning");
      return;
    }
    if (!window.confirm(`确定删除供应商“${provider.displayName}”吗？`)) return;
    setBusy(provider.id);
    try {
      await gateway.deleteProvider(provider);
      await Promise.all([load(), refreshModelCatalog()]);
      toastManager.show("供应商已删除", "success");
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : "删除失败", "error");
    } finally {
      setBusy(undefined);
    }
  };

  const clearCredential = async (provider: ServerProvider) => {
    if (!window.confirm(`确定清除“${provider.displayName}”的 API Key 吗？`)) return;
    setBusy(`key:${provider.id}`);
    try {
      await gateway.clearProviderCredentialById(provider.id);
      await Promise.all([load(), refreshModelCatalog()]);
      setEditingProvider((current) =>
        current?.id === provider.id
          ? { ...current, credentialConfigured: false, maskedHint: null }
          : current
      );
      toastManager.show("API Key 已清除", "success");
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : "清除失败", "error");
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
      toastManager.show("请填写模型 ID 和显示名称", "warning");
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
      toastManager.show(editingModel ? "模型已更新" : "模型已添加", "success");
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : "保存失败", "error");
    } finally {
      setBusy(undefined);
    }
  };

  const removeModel = async (model: ServerModel) => {
    if (!window.confirm(`确定删除模型“${model.displayName}”吗？`)) return;
    setBusy(model.id);
    try {
      await gateway.deleteModel(model);
      await Promise.all([load(), refreshModelCatalog()]);
      toastManager.show("模型已删除", "success");
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : "删除失败", "error");
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
      toastManager.show(error instanceof Error ? error.message : "更新模型失败", "error");
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
        additions.length ? `已同步 ${additions.length} 个新模型` : "模型目录已是最新",
        "success"
      );
    } catch (error) {
      toastManager.show(error instanceof Error ? error.message : "发现模型失败", "error");
    } finally {
      setBusy(undefined);
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col bg-muted/10">
      <div className="border-b bg-background px-6 py-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold">模型与供应商</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              API Key 由 Java 后端加密保存。新环境不自动创建任何供应商或模型。
            </p>
          </div>
          <Button onClick={() => openNewProvider()}>
            <Plus className="mr-2 h-4 w-4" />
            添加供应商
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
              <div className="mt-1 truncate text-xs text-muted-foreground">{preset.hint}</div>
            </button>
          ))}
        </div>
        <div className="relative mt-4 max-w-md">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="搜索供应商或模型..."
            className="pl-9"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-6">
        {loading ? (
          <div className="flex h-48 items-center justify-center text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            正在加载后端配置
          </div>
        ) : visibleProviders.length === 0 ? (
          <div className="flex min-h-64 flex-col items-center justify-center rounded-xl border border-dashed bg-background p-10 text-center">
            <div className="rounded-full bg-primary/10 p-4">
              <Server className="h-7 w-7 text-primary" />
            </div>
            <h3 className="mt-4 font-semibold">{search ? "没有匹配的配置" : "还没有模型供应商"}</h3>
            <p className="mt-1 max-w-md text-sm text-muted-foreground">
              使用上方预设快速接入国内模型平台，或添加任意 OpenAI 兼容服务。
            </p>
            {!search && (
              <Button className="mt-4" variant="outline" onClick={() => openNewProvider()}>
                <Plus className="mr-2 h-4 w-4" />
                创建第一个供应商
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
                            {!provider.enabled && <Badge variant="secondary">已停用</Badge>}
                          </CardTitle>
                          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                            <code>{provider.providerKey}</code>
                            <span>·</span>
                            <span className="max-w-[420px] truncate">
                              {provider.baseUrl || "未配置 Base URL"}
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
                            ? provider.maskedHint || "密钥已配置"
                            : "未配置密钥"}
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
                          同步模型
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
                          该供应商尚未添加模型。可手动添加，或配置密钥后同步目录。
                        </div>
                        <Button size="sm" variant="outline" onClick={() => openNewModel(provider)}>
                          <Plus className="mr-1 h-4 w-4" />
                          添加模型
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
                                        推理
                                      </Badge>
                                    )}
                                    {capabilities.supportsImageInput && (
                                      <Badge variant="outline" className="h-5 text-[10px]">
                                        图片
                                      </Badge>
                                    )}
                                    <Badge variant="outline" className="h-5 text-[10px]">
                                      {capabilities.tier === "flagship"
                                        ? "旗舰"
                                        : capabilities.tier === "fast"
                                          ? "高速"
                                          : capabilities.tier === "specialized"
                                            ? "专项"
                                            : "均衡"}
                                    </Badge>
                                    {capabilities.contextWindowTokens && (
                                      <Badge variant="outline" className="h-5 text-[10px]">
                                        {capabilities.contextWindowTokens >= 1_000_000
                                          ? `${capabilities.contextWindowTokens / 1_000_000}M`
                                          : `${Math.round(capabilities.contextWindowTokens / 1024)}K`}{" "}
                                        上下文
                                      </Badge>
                                    )}
                                    {model.isFree && (
                                      <Badge variant="secondary" className="h-5 text-[10px]">
                                        免费
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
                            添加模型
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
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>{editingProvider ? "编辑供应商" : "添加模型供应商"}</DialogTitle>
            <DialogDescription>
              配置 OpenAI 兼容端点。API Key 只会提交到 Java 后端并加密保存。
            </DialogDescription>
          </DialogHeader>
          {!editingProvider && (
            <div className="grid grid-cols-2 gap-2">
              {PROVIDER_PRESETS.filter((preset) => preset.providerKey !== "openai-compatible").map(
                (preset) => (
                  <button
                    type="button"
                    key={preset.providerKey}
                    onClick={() => setProviderDraft(toProviderInput(preset))}
                    className="rounded-md border p-2 text-left text-xs hover:border-primary/50 hover:bg-muted/40"
                  >
                    <div className="font-medium">{preset.displayName}</div>
                    <div className="mt-0.5 truncate text-muted-foreground">{preset.baseUrl}</div>
                  </button>
                )
              )}
            </div>
          )}
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="provider-name">显示名称</Label>
                <Input
                  id="provider-name"
                  value={providerDraft.displayName}
                  onChange={(event) =>
                    setProviderDraft((current) => ({
                      ...current,
                      displayName: event.target.value,
                    }))
                  }
                  placeholder="例如：智谱 GLM"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="provider-key">供应商标识</Label>
                <Input
                  id="provider-key"
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
                value={providerDraft.baseUrl}
                onChange={(event) =>
                  setProviderDraft((current) => ({
                    ...current,
                    baseUrl: event.target.value,
                  }))
                }
                placeholder="https://example.com/v1"
              />
              <p className="text-xs text-muted-foreground">
                填写兼容端点根地址，不要追加 /chat/completions。
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="provider-api-key">
                API Key
                {editingProvider?.credentialConfigured && (
                  <span className="ml-2 font-normal text-muted-foreground">
                    已保存 {editingProvider.maskedHint}
                  </span>
                )}
              </Label>
              <div className="relative">
                <Input
                  id="provider-api-key"
                  type={showCredential ? "text" : "password"}
                  value={credential}
                  onChange={(event) => setCredential(event.target.value)}
                  placeholder={
                    editingProvider?.credentialConfigured
                      ? "留空保留现有密钥，输入新值可轮换"
                      : "输入 API Key"
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
                <Label>启用供应商</Label>
                <p className="text-xs text-muted-foreground">
                  停用后其模型不会出现在模型选择器中。
                </p>
              </div>
              <Switch
                checked={providerDraft.enabled}
                onCheckedChange={(enabled) =>
                  setProviderDraft((current) => ({ ...current, enabled }))
                }
              />
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
                  清除密钥
                </Button>
              )}
            </div>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setProviderDialog(false)}>
                取消
              </Button>
              <Button disabled={busy === "provider"} onClick={() => void saveProvider()}>
                {busy === "provider" && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                保存
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={modelDialog} onOpenChange={setModelDialog}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingModel ? "编辑模型" : "添加模型"}</DialogTitle>
            <DialogDescription>
              模型 ID 必须与供应商 API 接受的 model 参数完全一致。
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="model-key">模型 ID</Label>
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
                placeholder="例如 glm-5 或 qwen-max"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="model-name">显示名称</Label>
              <Input
                id="model-name"
                value={modelDraft.displayName}
                onChange={(event) =>
                  setModelDraft((current) => ({
                    ...current,
                    displayName: event.target.value,
                  }))
                }
                placeholder="用户界面中显示的名称"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="model-description">说明</Label>
              <Textarea
                id="model-description"
                value={modelDraft.description}
                onChange={(event) =>
                  setModelDraft((current) => ({
                    ...current,
                    description: event.target.value,
                  }))
                }
                placeholder="模型用途、上下文限制或计费提示"
                rows={3}
              />
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div className="space-y-2">
                <Label htmlFor="model-tier">模型等级</Label>
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
                  <option value="flagship">旗舰</option>
                  <option value="balanced">均衡</option>
                  <option value="fast">高速</option>
                  <option value="specialized">专项</option>
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="model-context">上下文窗口</Label>
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
                  placeholder="Token 数"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="model-output">最大输出</Label>
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
                  placeholder="Token 数"
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              {[
                ["supportsReasoning", "支持推理", "展示推理强度选项"],
                ["supportsImageInput", "支持图片", "允许发送图片输入"],
                ["isFree", "免费模型", "仅作为价格提示"],
                ["enabled", "启用模型", "可在聊天中选择"],
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
              取消
            </Button>
            <Button disabled={busy === "model"} onClick={() => void saveModel()}>
              {busy === "model" ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <WandSparkles className="mr-2 h-4 w-4" />
              )}
              保存模型
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
