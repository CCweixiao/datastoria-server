"use client";

import { DdlWorkOrderDialog } from "@/components/approval/ddl-work-order-dialog";
import { useAuthSession } from "@/components/auth-session-provider";
import { useChatPanel } from "@/components/chat/view/use-chat-panel";
import { useConnection } from "@/components/connection/connection-context";
import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import {
  getApproval,
  listApprovalExecutions,
  listApprovalNodeExecutions,
  listApprovals,
  listApprovalTypeDefinitions,
  listApprovalTypes,
  transitionApproval,
  updateApprovalTypeDefinition,
  type ApprovalDetail,
  type ApprovalExecution,
  type ApprovalNodeExecution,
  type ApprovalRequest,
  type ApprovalStatus,
  type ApprovalType,
  type ApprovalTypeDefinition,
} from "@/lib/approval-client";
import {
  AlertCircle,
  CheckCircle2,
  ClipboardCheck,
  Clock3,
  FilePlus2,
  Play,
  RefreshCw,
  Send,
  Sparkles,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

const STATUS_FILTERS: Array<ApprovalStatus | "ALL"> = [
  "ALL",
  "DRAFT",
  "SUBMITTED",
  "APPROVED",
  "RUNNING",
  "SUCCEEDED",
  "FAILED",
  "REJECTED",
];

function localizedJson(value: string): string {
  try {
    const messages = JSON.parse(value) as Record<string, string>;
    const language = document.documentElement.lang.toLowerCase().startsWith("zh") ? "zh-CN" : "en";
    return messages[language] ?? messages.en ?? Object.values(messages)[0] ?? "";
  } catch {
    return value;
  }
}

function i18nValue(value: string, language: "en" | "zh-CN"): string {
  try {
    return (JSON.parse(value) as Record<string, string>)[language] ?? "";
  } catch {
    return "";
  }
}

function StatusIcon({ status }: { status: ApprovalStatus }) {
  if (status === "SUCCEEDED") return <CheckCircle2 className="h-4 w-4 text-emerald-500" />;
  if (status === "FAILED" || status === "REJECTED")
    return <XCircle className="h-4 w-4 text-destructive" />;
  if (status === "RUNNING") return <RefreshCw className="h-4 w-4 animate-spin text-primary" />;
  return <Clock3 className="h-4 w-4 text-muted-foreground" />;
}

export function ApprovalCenterTab() {
  const { t } = useUiPreferences();
  const { user } = useAuthSession();
  const { connection } = useConnection();
  const { setDisplayMode } = useChatPanel();
  const [requests, setRequests] = useState<ApprovalRequest[]>([]);
  const [types, setTypes] = useState<ApprovalType[]>([]);
  const [definitions, setDefinitions] = useState<ApprovalTypeDefinition[]>([]);
  const [selected, setSelected] = useState<ApprovalDetail | null>(null);
  const [status, setStatus] = useState<ApprovalStatus | "ALL">("ALL");
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const isAdmin = user?.role === "ADMIN";

  const reload = useCallback(async () => {
    if (!connection) return;
    setLoading(true);
    setError(null);
    try {
      const [nextRequests, nextTypes, nextDefinitions] = await Promise.all([
        listApprovals(status === "ALL" ? undefined : status),
        listApprovalTypes(connection.connectionId),
        isAdmin ? listApprovalTypeDefinitions() : Promise.resolve([]),
      ]);
      setRequests(nextRequests);
      setTypes(nextTypes);
      setDefinitions(nextDefinitions);
      if (selected) setSelected(await getApproval(selected.request.id));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t("approval.error.load"));
    } finally {
      setLoading(false);
    }
  }, [connection, isAdmin, selected?.request.id, status, t]);

  useEffect(() => {
    void reload();
  }, [connection?.connectionId, status]);

  const typeNames = useMemo(
    () => new Map(types.map((type) => [type.typeKey, localizedJson(type.nameI18nJson)])),
    [types]
  );

  const openDetail = useCallback(
    async (id: string) => {
      setError(null);
      try {
        setSelected(await getApproval(id));
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : t("approval.error.load"));
      }
    },
    [t]
  );

  const act = useCallback(
    async (action: "submit" | "approve" | "reject" | "execute" | "close", comment?: string) => {
      if (!selected) return;
      setActing(true);
      setError(null);
      try {
        const next = await transitionApproval(selected.request.id, action, {
          revision: selected.request.revision,
          contentDigest: selected.request.contentDigest,
          comment,
        });
        setSelected(next);
        const nextRequests = await listApprovals(status === "ALL" ? undefined : status);
        setRequests(nextRequests);
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : t("approval.error.action"));
      } finally {
        setActing(false);
      }
    },
    [selected, status, t]
  );

  if (!connection) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
        {t("approval.connectionRequired")}
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-muted/10">
      <div className="flex items-center justify-between border-b bg-background px-5 py-3">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <ClipboardCheck className="h-5 w-5 text-primary" />
            {t("approval.title")}
          </h2>
          <p className="text-xs text-muted-foreground">{t("approval.subtitle")}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => void reload()} disabled={loading}>
            <RefreshCw className="mr-2 h-4 w-4" />
            {t("approval.refresh")}
          </Button>
          <Button variant="outline" size="sm" onClick={() => setCreateOpen(true)}>
            <FilePlus2 className="mr-2 h-4 w-4" />
            {t("approval.create.new")}
          </Button>
          <Button size="sm" onClick={() => setDisplayMode("tabWidth")}>
            <Sparkles className="mr-2 h-4 w-4" />
            {t("approval.createWithAi")}
          </Button>
        </div>
      </div>

      {error && (
        <div className="mx-5 mt-3 flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
          <AlertCircle className="h-4 w-4" />
          {error}
        </div>
      )}

      <Tabs defaultValue="orders" className="flex min-h-0 flex-1 flex-col px-5 pt-3">
        <TabsList className="w-fit">
          <TabsTrigger value="orders">{t("approval.orders")}</TabsTrigger>
          {isAdmin && <TabsTrigger value="types">{t("approval.types")}</TabsTrigger>}
        </TabsList>
        <TabsContent value="orders" className="mt-3 min-h-0 flex-1">
          <div className="grid h-full min-h-0 grid-cols-[minmax(320px,0.9fr)_minmax(440px,1.4fr)] gap-3">
            <Card className="min-h-0 overflow-hidden">
              <CardHeader className="space-y-3 pb-3">
                <CardTitle className="text-sm">{t("approval.list")}</CardTitle>
                <div className="flex flex-wrap gap-1">
                  {STATUS_FILTERS.map((value) => (
                    <Button
                      key={value}
                      size="sm"
                      variant={status === value ? "secondary" : "ghost"}
                      className="h-7 px-2 text-xs"
                      onClick={() => setStatus(value)}
                    >
                      {t(`approval.status.${value}`)}
                    </Button>
                  ))}
                </div>
              </CardHeader>
              <CardContent className="h-[calc(100%-96px)] p-0">
                <ScrollArea className="h-full">
                  {loading ? (
                    <p className="p-5 text-sm text-muted-foreground">{t("approval.loading")}</p>
                  ) : requests.length === 0 ? (
                    <p className="p-5 text-sm text-muted-foreground">{t("approval.empty")}</p>
                  ) : (
                    requests.map((request) => (
                      <button
                        key={request.id}
                        type="button"
                        onClick={() => void openDetail(request.id)}
                        className={`w-full border-b px-4 py-3 text-left transition-colors hover:bg-muted/60 ${selected?.request.id === request.id ? "bg-primary/5" : ""}`}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <span className="truncate text-sm font-medium">{request.title}</span>
                          <Badge variant="outline" className="shrink-0 gap-1">
                            <StatusIcon status={request.status} />
                            {t(`approval.status.${request.status}`)}
                          </Badge>
                        </div>
                        <div className="mt-1 flex items-center justify-between text-xs text-muted-foreground">
                          <span>
                            {typeNames.get(request.workOrderTypeKey) ?? request.workOrderTypeKey}
                          </span>
                          <span>{request.requestNo}</span>
                        </div>
                      </button>
                    ))
                  )}
                </ScrollArea>
              </CardContent>
            </Card>
            <ApprovalDetailPanel
              detail={selected}
              isAdmin={isAdmin}
              acting={acting}
              typeName={selected ? typeNames.get(selected.request.workOrderTypeKey) : undefined}
              onAction={act}
            />
          </div>
        </TabsContent>
        {isAdmin && (
          <TabsContent value="types" className="mt-3 min-h-0 flex-1">
            <ScrollArea className="h-full">
              <div className="grid gap-3 pb-5 md:grid-cols-2">
                {definitions.map((definition) => (
                  <TypeDefinitionEditor
                    key={definition.typeKey}
                    definition={definition}
                    onSaved={(updated) =>
                      setDefinitions((current) =>
                        current.map((item) => (item.typeKey === updated.typeKey ? updated : item))
                      )
                    }
                  />
                ))}
              </div>
            </ScrollArea>
          </TabsContent>
        )}
      </Tabs>
      <DdlWorkOrderDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        connectionId={connection.connectionId}
        cluster={connection.cluster ?? undefined}
        types={types}
        onCreated={(requestId) => {
          void reload();
          void openDetail(requestId);
        }}
      />
    </div>
  );
}

function TypeDefinitionEditor({
  definition,
  onSaved,
}: {
  definition: ApprovalTypeDefinition;
  onSaved: (definition: ApprovalTypeDefinition) => void;
}) {
  const { t } = useUiPreferences();
  const [nameEn, setNameEn] = useState(i18nValue(definition.nameI18nJson, "en"));
  const [nameZh, setNameZh] = useState(i18nValue(definition.nameI18nJson, "zh-CN"));
  const [descriptionEn, setDescriptionEn] = useState(
    i18nValue(definition.descriptionI18nJson, "en")
  );
  const [descriptionZh, setDescriptionZh] = useState(
    i18nValue(definition.descriptionI18nJson, "zh-CN")
  );
  const [rules, setRules] = useState(definition.generationRuleJson);
  const [enabled, setEnabled] = useState(definition.status === "ENABLED");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      const updated = await updateApprovalTypeDefinition(definition.typeKey, {
        revision: definition.definitionRevision,
        nameEn,
        nameZhCn: nameZh,
        descriptionEn,
        descriptionZhCn: descriptionZh,
        generationRuleJson: rules,
        enabled,
      });
      onSaved(updated);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t("approval.error.action"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="text-sm">{localizedJson(definition.nameI18nJson)}</CardTitle>
            <p className="mt-1 font-mono text-xs text-muted-foreground">
              {definition.typeKey} · {definition.generatorKey}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Label htmlFor={`${definition.typeKey}-enabled`} className="text-xs">
              {t("approval.type.enabled")}
            </Label>
            <Switch
              id={`${definition.typeKey}-enabled`}
              checked={enabled}
              onCheckedChange={setEnabled}
            />
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-2 gap-2">
          <div>
            <Label className="text-xs">{t("approval.type.nameEn")}</Label>
            <Input value={nameEn} onChange={(event) => setNameEn(event.target.value)} />
          </div>
          <div>
            <Label className="text-xs">{t("approval.type.nameZh")}</Label>
            <Input value={nameZh} onChange={(event) => setNameZh(event.target.value)} />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <Label className="text-xs">{t("approval.type.descriptionEn")}</Label>
            <Textarea
              value={descriptionEn}
              onChange={(event) => setDescriptionEn(event.target.value)}
            />
          </div>
          <div>
            <Label className="text-xs">{t("approval.type.descriptionZh")}</Label>
            <Textarea
              value={descriptionZh}
              onChange={(event) => setDescriptionZh(event.target.value)}
            />
          </div>
        </div>
        <div>
          <Label className="text-xs">{t("approval.type.rules")}</Label>
          <Textarea
            className="min-h-28 font-mono text-xs"
            value={rules}
            onChange={(event) => setRules(event.target.value)}
          />
        </div>
        {error && <p className="text-xs text-destructive">{error}</p>}
        <div className="flex items-center justify-between">
          <span className="text-xs text-muted-foreground">
            {t("approval.revision")}: {definition.definitionRevision}
          </span>
          <Button size="sm" disabled={saving} onClick={() => void save()}>
            {saving ? t("approval.type.saving") : t("approval.type.save")}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function ApprovalDetailPanel({
  detail,
  isAdmin,
  acting,
  typeName,
  onAction,
}: {
  detail: ApprovalDetail | null;
  isAdmin: boolean;
  acting: boolean;
  typeName?: string;
  onAction: (
    action: "submit" | "approve" | "reject" | "execute" | "close",
    comment?: string
  ) => void;
}) {
  const { t } = useUiPreferences();
  const [comment, setComment] = useState("");
  if (!detail)
    return (
      <Card className="flex items-center justify-center text-sm text-muted-foreground">
        {t("approval.select")}
      </Card>
    );
  const request = detail.request;
  return (
    <Card className="min-h-0 overflow-hidden">
      <ScrollArea className="h-full">
        <CardHeader className="border-b">
          <div className="flex items-start justify-between gap-3">
            <div>
              <CardTitle>{request.title}</CardTitle>
              <p className="mt-1 text-xs text-muted-foreground">
                {request.requestNo} · {typeName ?? request.workOrderTypeKey} ·{" "}
                {request.connectionName}
              </p>
            </div>
            <Badge className="gap-1" variant="outline">
              <StatusIcon status={request.status} />
              {t(`approval.status.${request.status}`)}
            </Badge>
          </div>
          {request.summary && <p className="text-sm text-muted-foreground">{request.summary}</p>}
          {isAdmin && (request.status === "SUBMITTED" || request.status === "FAILED") && (
            <div className="space-y-1 pt-2">
              <Label htmlFor={`approval-comment-${request.id}`} className="text-xs">
                {t("approval.reviewComment")}
              </Label>
              <Textarea
                id={`approval-comment-${request.id}`}
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                placeholder={t("approval.reviewComment.placeholder")}
                className="min-h-20"
              />
            </div>
          )}
          <div className="flex flex-wrap gap-2 pt-2">
            {request.status === "DRAFT" && (
              <Button size="sm" disabled={acting} onClick={() => onAction("submit")}>
                <Send className="mr-2 h-4 w-4" />
                {t("approval.submit")}
              </Button>
            )}
            {isAdmin && request.status === "SUBMITTED" && (
              <>
                <Button size="sm" disabled={acting} onClick={() => onAction("approve", comment)}>
                  <CheckCircle2 className="mr-2 h-4 w-4" />
                  {t("approval.approve")}
                </Button>
                <Button
                  size="sm"
                  variant="destructive"
                  disabled={acting || !comment.trim()}
                  onClick={() => onAction("reject", comment)}
                >
                  <XCircle className="mr-2 h-4 w-4" />
                  {t("approval.reject")}
                </Button>
              </>
            )}
            {isAdmin && request.status === "APPROVED" && (
              <Button size="sm" disabled={acting} onClick={() => onAction("execute")}>
                <Play className="mr-2 h-4 w-4" />
                {t("approval.execute")}
              </Button>
            )}
            {isAdmin && request.status === "FAILED" && (
              <Button
                size="sm"
                variant="outline"
                disabled={acting || !comment.trim()}
                onClick={() => onAction("close", comment)}
              >
                <XCircle className="mr-2 h-4 w-4" />
                {t("approval.closeFailed")}
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent className="space-y-5 p-5">
          <section>
            <h3 className="mb-2 text-sm font-semibold">{t("approval.sqlPlan")}</h3>
            <div className="space-y-3">
              {detail.items.map((item) => (
                <div key={item.id} className="overflow-hidden rounded-md border">
                  <div className="flex justify-between bg-muted/50 px-3 py-2 text-xs">
                    <span>
                      #{item.ordinal} · {item.operationKind}
                    </span>
                    <Badge variant="secondary">{item.riskLevel}</Badge>
                  </div>
                  <pre className="overflow-x-auto whitespace-pre-wrap p-3 font-mono text-xs leading-5">
                    {item.sqlText}
                  </pre>
                </div>
              ))}
            </div>
          </section>
          {isAdmin && <ExecutionHistory requestId={request.id} />}
          <section>
            <h3 className="mb-2 text-sm font-semibold">{t("approval.timeline")}</h3>
            <div className="space-y-3 border-l pl-4">
              {detail.events.map((event) => (
                <div key={event.id}>
                  <div className="text-sm font-medium">{event.eventType}</div>
                  <div className="text-xs text-muted-foreground">
                    {event.actorDisplayName} · {new Date(event.createdAt).toLocaleString()}
                  </div>
                  {event.safeMessage && (
                    <div className="text-xs text-muted-foreground">{event.safeMessage}</div>
                  )}
                </div>
              ))}
            </div>
          </section>
        </CardContent>
      </ScrollArea>
    </Card>
  );
}

function ExecutionHistory({ requestId }: { requestId: string }) {
  const { t } = useUiPreferences();
  const [executions, setExecutions] = useState<ApprovalExecution[]>([]);
  const [nodes, setNodes] = useState<Record<string, ApprovalNodeExecution[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    void listApprovalExecutions(requestId)
      .then(async (nextExecutions) => {
        const nodeEntries = await Promise.all(
          nextExecutions.map(
            async (execution) =>
              [execution.id, await listApprovalNodeExecutions(requestId, execution.id)] as const
          )
        );
        if (!cancelled) {
          setExecutions(nextExecutions);
          setNodes(Object.fromEntries(nodeEntries));
        }
      })
      .catch((caught) => {
        if (!cancelled)
          setError(caught instanceof Error ? caught.message : t("approval.execution.loadError"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [requestId, t]);

  return (
    <section>
      <h3 className="mb-2 text-sm font-semibold">{t("approval.execution.title")}</h3>
      {loading ? (
        <p className="text-xs text-muted-foreground">{t("approval.loading")}</p>
      ) : error ? (
        <p className="text-xs text-destructive">{error}</p>
      ) : executions.length === 0 ? (
        <p className="text-xs text-muted-foreground">{t("approval.execution.empty")}</p>
      ) : (
        <div className="space-y-2">
          {executions.map((execution) => (
            <div key={execution.id} className="rounded-md border p-3 text-xs">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium">
                  {t("approval.execution.attempt")} {execution.attemptNo} · #{execution.ordinal}
                </span>
                <Badge variant={execution.status === "FAILED" ? "destructive" : "outline"}>
                  {execution.status}
                </Badge>
              </div>
              <div className="mt-1 break-all font-mono text-muted-foreground">
                query_id: {execution.queryId}
              </div>
              {execution.durationMs !== undefined && (
                <div className="mt-1 text-muted-foreground">
                  {t("approval.execution.duration")}: {execution.durationMs} ms
                </div>
              )}
              {(nodes[execution.id] ?? []).map((node) => (
                <div
                  key={node.id}
                  className="mt-2 flex flex-wrap items-center justify-between gap-2 rounded bg-muted/50 px-2 py-1.5"
                >
                  <span>
                    {node.host}
                    {node.port ? `:${node.port}` : ""}
                  </span>
                  <span
                    className={
                      node.status === "FAILED" ? "text-destructive" : "text-muted-foreground"
                    }
                  >
                    {node.status}
                    {node.durationMs !== undefined ? ` · ${node.durationMs} ms` : ""}
                  </span>
                </div>
              ))}
              {(execution.safeMessage || execution.errorCode) && (
                <p className="mt-2 text-destructive">
                  {[execution.errorCode, execution.safeMessage].filter(Boolean).join(" · ")}
                </p>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
