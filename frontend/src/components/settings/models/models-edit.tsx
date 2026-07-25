import { StatusPopover } from "@/components/connection/connection-edit-component";
import {
  ModelManager,
  type ModelSetting,
  type ProviderSetting,
} from "@/components/settings/models/model-manager";
import { ProviderLogo } from "@/components/shared/provider-logo";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useModelConfig } from "@/hooks/use-model-config";
import {
  getAiConfigurationGateway,
  isJavaConfigurationBackend,
} from "@/lib/ai/configuration/configuration-gateway";
import { resolveModelSupportsImageInput, type ModelProps } from "@/lib/ai/llm/llm-provider-factory";
import { TextHighlighter } from "@/lib/text-highlighter";
import { AlertCircle, Check, ChevronDown, Eye, EyeOff, Search, X } from "lucide-react";
import React, { useCallback, useEffect, useState } from "react";
import { CodexOAuthConnect } from "./codex-oauth-connect";
import { GitHubOAuthConnect } from "./github-oauth-connect";

export function ModelsEdit() {
  const { allModels, modelSettings, providerSettings } = useModelConfig();
  const modelManager = ModelManager.getInstance();

  const [searchQuery, setSearchQuery] = useState("");

  // Start with all providers collapsed (empty set) to show only provider headers by default
  const [expandedProviders, setExpandedProviders] = useState<Set<string>>(new Set());

  // Track which providers have visible API keys
  const [visibleApiKeys, setVisibleApiKeys] = useState<Set<string>>(new Set());
  const [clearConfirmProvider, setClearConfirmProvider] = useState<string | null>(null);
  const [serverCredentialDrafts, setServerCredentialDrafts] = useState<Record<string, string>>({});
  const javaConfiguration = isJavaConfigurationBackend();
  const providerSources = allModels.reduce(
    (acc, model) => {
      if (!acc[model.provider]) {
        acc[model.provider] = {
          hasSystemModels: false,
          hasUserModels: false,
        };
      }

      if (model.source === "system") {
        acc[model.provider].hasSystemModels = true;
      } else {
        acc[model.provider].hasUserModels = true;
      }

      return acc;
    },
    {} as Record<string, { hasSystemModels: boolean; hasUserModels: boolean }>
  );
  const modelCatalog = allModels.reduce(
    (acc, model) => {
      acc[`${model.provider}:${model.modelId}`] = model;
      return acc;
    },
    {} as Record<string, ModelProps>
  );

  const handleModelDisabled = useCallback(
    (provider: string, modelId: string, disabled: boolean) => {
      modelManager.updateModelSetting(provider, modelId, { disabled });
      const model = modelCatalog[`${provider}:${modelId}`];
      if (model) {
        void getAiConfigurationGateway()
          .setModelEnabled(model, !disabled)
          .catch((error) => console.error(`Failed to update ${provider}/${modelId}:`, error));
      }
    },
    [modelCatalog, modelManager]
  );

  const handleProviderApiKeyChange = useCallback((provider: string, apiKey: string) => {
    setServerCredentialDrafts((current) => ({ ...current, [provider]: apiKey }));
  }, []);

  const persistServerCredential = useCallback(
    async (provider: string) => {
      const credential = serverCredentialDrafts[provider]?.trim();
      if (!javaConfiguration || !credential) return;
      try {
        await getAiConfigurationGateway().saveProviderCredential(provider, credential);
        const refreshed = await getAiConfigurationGateway().listProviders();
        const configured = refreshed.find((candidate) => candidate.providerKey === provider);
        if (configured) {
          modelManager.updateProviderSetting(provider, {
            credentialConfigured: configured.credentialConfigured,
            maskedHint: configured.maskedHint,
          });
        }
        setServerCredentialDrafts((current) => ({ ...current, [provider]: "" }));
      } catch (error) {
        console.error(`Failed to save ${provider} credential:`, error);
      }
    },
    [javaConfiguration, modelManager, serverCredentialDrafts]
  );

  const [providers, setProviders] = useState<Array<[string, ModelSetting[]]>>([]);

  useEffect(() => {
    const queryLower = searchQuery.toLowerCase().trim();
    const currentModelSettings = allModels.map((model: ModelProps) => {
      const stored = modelSettings.find(
        (m: ModelSetting) => m.modelId === model.modelId && m.provider === model.provider
      );
      return (
        stored || {
          modelId: model.modelId,
          provider: model.provider,
          disabled: !!model.disabled,
          free: !!model.free,
        }
      );
    });

    const filtered = queryLower
      ? currentModelSettings.filter((model) => model.modelId.toLowerCase().includes(queryLower))
      : currentModelSettings;

    const grouped = filtered.reduce(
      (acc: Record<string, ModelSetting[]>, model: ModelSetting) => {
        const provider = model.provider;
        if (!acc[provider]) {
          acc[provider] = [];
        }
        acc[provider].push(model);
        return acc;
      },
      {} as Record<string, ModelSetting[]>
    );

    const entries = Object.entries(grouped);
    entries.sort(([a], [b]) => a.localeCompare(b));
    setProviders(entries);
  }, [allModels, modelSettings, searchQuery]);

  // Expand all providers when searching
  useEffect(() => {
    if (searchQuery.trim()) {
      const allProviders = providers.map(([provider]) => provider);
      if (allProviders.length > 0) {
        setExpandedProviders((prev) => {
          const next = new Set(prev);
          allProviders.forEach((p) => next.add(p));
          return next;
        });
      }
    }
  }, [providers, searchQuery]);

  const toggleProvider = useCallback((provider: string) => {
    setExpandedProviders((prev) => {
      const next = new Set(prev);
      if (next.has(provider)) {
        next.delete(provider);
      } else {
        next.add(provider);
      }
      return next;
    });
  }, []);

  const toggleApiKeyVisibility = useCallback((provider: string) => {
    setVisibleApiKeys((prev) => {
      const next = new Set(prev);
      if (next.has(provider)) {
        next.delete(provider);
      } else {
        next.add(provider);
      }
      return next;
    });
  }, []);

  const handleClearProviderKey = useCallback(
    (provider: string) => {
      if (javaConfiguration) {
        void getAiConfigurationGateway()
          .clearProviderCredential(provider)
          .then(() =>
            modelManager.updateProviderSetting(provider, {
              credentialConfigured: false,
              maskedHint: null,
            })
          )
          .catch((error) => console.error(`Failed to clear ${provider} credential:`, error));
        setServerCredentialDrafts((current) => ({ ...current, [provider]: "" }));
        setClearConfirmProvider(null);
        return;
      }
      modelManager.deleteProviderSetting(provider);
      setClearConfirmProvider(null);
    },
    [javaConfiguration, modelManager]
  );

  // Auto-reveal API key when user focuses on the input
  const handleApiKeyFocus = useCallback(
    (provider: string) => {
      if (!visibleApiKeys.has(provider)) {
        setVisibleApiKeys((prev) => new Set(prev).add(provider));
      }
    },
    [visibleApiKeys]
  );

  return (
    <>
      <div className="h-full flex flex-col">
        <GitHubOAuthConnect
          connected={providerSettings.some(
            (setting) => setting.providerId === "oauth:github" && setting.credentialConfigured
          )}
        />
        <CodexOAuthConnect
          connected={providerSettings.some(
            (setting) => setting.providerId === "oauth:codex" && setting.credentialConfigured
          )}
        />
        {/* Search Input */}
        <div className="flex-shrink-0 relative">
          <Search className="h-4 w-4 text-muted-foreground absolute left-2 top-1/2 transform -translate-y-1/2" />
          <Input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search models by ID..."
            className="w-full border-none pl-8"
          />
        </div>

        <div className="overflow-hidden flex-1 flex flex-col min-h-0">
          <div className="flex-1 overflow-y-auto">
            <Table className="border-t">
              <TableHeader>
                <TableRow className="h-9">
                  <TableHead className="w-[300px] py-2 pl-8 font-bold">Model ID</TableHead>
                  <TableHead className="w-[100px] py-2 font-bold">Free</TableHead>
                  <TableHead className="w-[160px] py-2 font-bold text-center">
                    Support Image Input
                  </TableHead>
                  <TableHead className="w-[140px] py-2 font-bold">Disabled</TableHead>
                  <TableHead className="min-w-[200px] py-2 font-bold">API Key</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {providers.map(([provider, providerModels]: [string, ModelSetting[]]) => {
                  const isExpanded = expandedProviders.has(provider);
                  const providerSetting = providerSettings.find(
                    (p: ProviderSetting) => p.provider === provider
                  );
                  const sourceInfo = providerSources[provider];
                  const hasSystemModels = !!sourceInfo?.hasSystemModels;
                  const disabledModelCount = providerModels.filter(
                    (model) => model.disabled
                  ).length;
                  const totalModelCount = providerModels.length;

                  return (
                    <React.Fragment key={provider}>
                      {/* Provider Group Header */}
                      <TableRow className="h-10 bg-muted/50 hover:bg-muted/70">
                        <TableCell colSpan={3} className="px-1 py-2">
                          <button
                            type="button"
                            onClick={() => toggleProvider(provider)}
                            className="flex items-center gap-2 w-full text-left hover:opacity-80 transition-opacity"
                          >
                            <ChevronDown
                              className={`h-4 w-4 transition-transform duration-200 ${
                                isExpanded ? "rotate-0" : "-rotate-90"
                              }`}
                            />
                            <ProviderLogo
                              provider={provider}
                              className="h-4 w-4 text-muted-foreground"
                            />
                            <span className="font-semibold text-sm">{provider}</span>
                            <span className="text-xs text-muted-foreground">
                              {hasSystemModels
                                ? `(${providerModels.length} ${
                                    providerModels.length === 1 ? "model" : "models"
                                  }, system-backed available)`
                                : `(${providerModels.length} ${
                                    providerModels.length === 1 ? "model" : "models"
                                  })`}
                            </span>
                          </button>
                        </TableCell>
                        <TableCell className="w-[140px] py-1.5 text-sm text-muted-foreground whitespace-nowrap">
                          {disabledModelCount}/{totalModelCount}
                        </TableCell>
                        <TableCell className="py-1.5 pr-4">
                          <div className="flex items-center gap-2">
                            <div className="flex items-center gap-1 flex-1 ">
                              {provider === "GitHub Copilot" ||
                              provider === "OpenAI Codex" ? (
                                <span className="text-xs text-muted-foreground">
                                  OAuth credential managed by Java
                                </span>
                              ) : (
                                <>
                                  <Input
                                    type={visibleApiKeys.has(provider) ? "text" : "password"}
                                    value={
                                      javaConfiguration
                                        ? (serverCredentialDrafts[provider] ?? "")
                                        : ""
                                    }
                                    onChange={(e) => {
                                      handleProviderApiKeyChange(provider, e.target.value);
                                      // Auto-reveal when user starts typing
                                      if (e.target.value && !visibleApiKeys.has(provider)) {
                                        setVisibleApiKeys((prev) => new Set(prev).add(provider));
                                      }
                                    }}
                                    onBlur={() => void persistServerCredential(provider)}
                                    onFocus={() => handleApiKeyFocus(provider)}
                                    placeholder={
                                      providerSetting?.credentialConfigured
                                        ? `${providerSetting.maskedHint ?? "Credential configured"} — enter a new key to rotate`
                                        : `Enter ${provider} API key (encrypted by Java backend)`
                                    }
                                    className="w-full h-8 border-0 border-b border-muted-foreground/20 rounded-none pl-0 bg-transparent focus-visible:ring-0 pr-8"
                                  />
                                  {(serverCredentialDrafts[provider] ||
                                    providerSetting?.credentialConfigured) && (
                                    <div className="right-0 flex items-center gap-1">
                                      <button
                                        type="button"
                                        onClick={() => toggleApiKeyVisibility(provider)}
                                        className="text-muted-foreground hover:text-foreground transition-colors p-1"
                                        title={
                                          visibleApiKeys.has(provider)
                                            ? "Hide API key"
                                            : "Show API key"
                                        }
                                      >
                                        {visibleApiKeys.has(provider) ? (
                                          <EyeOff className="h-4 w-4" />
                                        ) : (
                                          <Eye className="h-4 w-4" />
                                        )}
                                      </button>
                                      <StatusPopover
                                        open={clearConfirmProvider === provider}
                                        onOpenChange={(open) =>
                                          setClearConfirmProvider(open ? provider : null)
                                        }
                                        trigger={
                                          <Button
                                            type="button"
                                            variant="outline"
                                            size="sm"
                                            className="h-6 px-2 text-xs"
                                          >
                                            Clear
                                          </Button>
                                        }
                                        side="left"
                                        align="end"
                                        sideOffset={4}
                                        icon={
                                          <AlertCircle className="h-4 w-4 mt-0.5 shrink-0 text-destructive" />
                                        }
                                        title="Clear API key"
                                      >
                                        <div className="text-xs mb-3">
                                          Remove the saved API key for {provider}?
                                        </div>
                                        <div className="flex justify-end gap-2">
                                          <Button
                                            type="button"
                                            variant="outline"
                                            size="sm"
                                            className="h-8 rounded-sm text-sm"
                                            onClick={() => setClearConfirmProvider(null)}
                                          >
                                            Cancel
                                          </Button>
                                          <Button
                                            type="button"
                                            variant="destructive"
                                            size="sm"
                                            className="h-8 rounded-sm text-sm"
                                            onClick={() => handleClearProviderKey(provider)}
                                          >
                                            Clear
                                          </Button>
                                        </div>
                                      </StatusPopover>
                                    </div>
                                  )}
                                </>
                              )}
                            </div>
                          </div>
                        </TableCell>
                      </TableRow>
                      {/* Provider Models */}
                      {isExpanded &&
                        providerModels.map((model) => (
                          <TableRow key={`${model.provider}-${model.modelId}`} className="h-10">
                            <TableCell className="py-1.5 pl-8">
                              <div className="text-sm font-medium">
                                {searchQuery.trim()
                                  ? TextHighlighter.highlight(model.modelId, searchQuery)
                                  : model.modelId}
                              </div>
                            </TableCell>
                            <TableCell className="py-1.5">
                              {model.free ? (
                                <Badge
                                  variant="secondary"
                                  className="bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400 border-none hover:bg-green-100 dark:hover:bg-green-900/30"
                                  title="This is a hint, whehter it's a free model, you should always follow the provider's documentation to know more about the pricing."
                                >
                                  Free *
                                </Badge>
                              ) : (
                                <div className="text-sm text-muted-foreground">No</div>
                              )}
                            </TableCell>
                            <TableCell className="py-1.5 text-center">
                              <div className="flex items-center justify-center h-full text-muted-foreground">
                                {resolveModelSupportsImageInput(
                                  modelCatalog[`${model.provider}:${model.modelId}`] ?? model
                                ) ? (
                                  <Check
                                    className="h-4 w-4 text-green-600 dark:text-green-400"
                                    aria-label="Supports image input"
                                  />
                                ) : (
                                  <X
                                    className="h-4 w-4 text-muted-foreground"
                                    aria-label="Does not support image input"
                                  />
                                )}
                              </div>
                            </TableCell>
                            <TableCell className="py-1.5">
                              <div className="flex items-center h-full">
                                <Switch
                                  checked={!model.disabled}
                                  onCheckedChange={(checked) =>
                                    handleModelDisabled(model.provider, model.modelId, !checked)
                                  }
                                  className="h-4 w-8 data-[state=checked]:bg-primary data-[state=unchecked]:bg-input [&>span]:h-3 [&>span]:w-3 [&>span]:data-[state=checked]:translate-x-4"
                                />
                              </div>
                            </TableCell>
                            <TableCell className="py-1.5" />
                          </TableRow>
                        ))}
                    </React.Fragment>
                  );
                })}
                {providers.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center text-muted-foreground py-4">
                      {searchQuery.trim() ? "No models found" : "No models available"}
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      </div>
    </>
  );
}
