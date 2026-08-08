"use client";

import { useAuthSession } from "@/components/auth-session-provider";
import { useChatPanel } from "@/components/chat/view/use-chat-panel";
import { useConnection } from "@/components/connection/connection-context";
import { ThemedSyntaxHighlighter } from "@/components/shared/themed-syntax-highlighter";
import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import {
  deleteApproval,
  getApproval,
  listApprovalExecutions,
  listApprovalNodeExecutions,
  listApprovals,
  listApprovalTypeDefinitions,
  listApprovalTypes,
  transitionApproval,
  updateApprovalSqlPlan,
  updateApprovalTypeDefinition,
  type ApprovalDetail,
  type ApprovalExecution,
  type ApprovalNodeExecution,
  type ApprovalRequest,
  type ApprovalStatus,
  type ApprovalType,
  type ApprovalTypeDefinition,
} from "@/lib/approval-client";
import type { MessageKey } from "@/lib/i18n/messages/en";
import { SqlUtils } from "@/lib/sql-utils";
import {
  AlertCircle,
  ArrowLeft,
  CalendarDays,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleStop,
  ClipboardCheck,
  Clock3,
  Code2,
  Eye,
  FileClock,
  Pencil,
  Play,
  RefreshCw,
  RotateCcw,
  Save,
  Search,
  Send,
  Sparkles,
  Trash2,
  X,
  XCircle,
} from "lucide-react";
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import type { DateRange } from "react-day-picker";

const STATUS_FILTERS: ApprovalStatus[] = [
  "DRAFT",
  "SUBMITTED",
  "APPROVED",
  "QUEUED",
  "RUNNING",
  "RECONCILING",
  "SUCCEEDED",
  "FAILED",
  "REJECTED",
  "CANCELLED",
  "EXPIRED",
];

const PAGE_SIZE = 10;
const APPROVAL_TABLE_HEAD_CLASS =
  "sticky top-0 z-20 h-11 whitespace-nowrap border-b bg-muted px-4 text-xs font-semibold text-foreground shadow-[inset_0_-1px_0_hsl(var(--border))]";
const STICKY_ACTION_HEAD_CLASS = `${APPROVAL_TABLE_HEAD_CLASS} right-0 z-30 border-l-2`;
const STICKY_ACTION_CELL_CLASS =
  "sticky right-0 z-10 border-l-2 bg-card text-right group-hover:bg-muted";

const AUDIT_EVENT_KEYS = {
  DRAFT_CREATED: "approval.timeline.event.DRAFT_CREATED",
  DRAFT_UPDATED: "approval.timeline.event.DRAFT_UPDATED",
  SUBMITTED: "approval.timeline.event.SUBMITTED",
  APPROVED: "approval.timeline.event.APPROVED",
  REJECTED: "approval.timeline.event.REJECTED",
  INTERRUPTED_BY_APPLICANT: "approval.timeline.event.INTERRUPTED_BY_APPLICANT",
  SQL_PLAN_EDITED: "approval.timeline.event.SQL_PLAN_EDITED",
  EXECUTION_STARTED: "approval.timeline.event.EXECUTION_STARTED",
  EXECUTION_SUCCEEDED: "approval.timeline.event.EXECUTION_SUCCEEDED",
  EXECUTION_FAILED: "approval.timeline.event.EXECUTION_FAILED",
  EXECUTION_STUCK_RECONCILING: "approval.timeline.event.EXECUTION_STUCK_RECONCILING",
  FAILED_EXECUTION_CLOSED: "approval.timeline.event.FAILED_EXECUTION_CLOSED",
  APPROVAL_EXPIRED: "approval.timeline.event.APPROVAL_EXPIRED",
} as const;

const AUDIT_SYSTEM_MESSAGE_KEYS = {
  "DDL approval draft created": "approval.timeline.message.draftCreated",
  "DDL approval draft updated": "approval.timeline.message.draftUpdated",
  "DDL approval submitted": "approval.timeline.message.submitted",
  "SQL plan edited by administrator": "approval.timeline.message.sqlEdited",
  "Manual DDL execution started": "approval.timeline.message.manualExecutionStarted",
  "Auto DDL execution started": "approval.timeline.message.autoExecutionStarted",
  "DDL execution succeeded": "approval.timeline.message.executionSucceeded",
  "DDL execution failed": "approval.timeline.message.executionFailed",
  "Failed DDL execution closed by administrator": "approval.timeline.message.failedClosed",
} as const;

function auditEventLabel(eventType: string, t: (key: MessageKey) => string): string {
  const key = AUDIT_EVENT_KEYS[eventType as keyof typeof AUDIT_EVENT_KEYS];
  return key ? t(key) : eventType.replaceAll("_", " ");
}

function auditMessage(message: string, t: (key: MessageKey) => string): string {
  const key = AUDIT_SYSTEM_MESSAGE_KEYS[message as keyof typeof AUDIT_SYSTEM_MESSAGE_KEYS];
  return key ? t(key) : message;
}

type FilterOption = { value: string; label: string };

function ClearFilterButton({
  label,
  onClear,
  className = "right-1",
}: {
  label: string;
  onClear: () => void;
  className?: string;
}) {
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      aria-label={label}
      className={`absolute top-1/2 z-10 h-7 w-7 -translate-y-1/2 rounded-full text-muted-foreground hover:text-foreground ${className}`}
      onClick={(event) => {
        event.stopPropagation();
        onClear();
      }}
    >
      <X className="h-3.5 w-3.5" />
    </Button>
  );
}

function FilterSelect({
  label,
  value,
  options,
  onValueChange,
  clearLabel,
}: {
  label: string;
  value: string;
  options: FilterOption[];
  onValueChange: (value: string) => void;
  clearLabel: string;
}) {
  const selectedLabel = options.find((option) => option.value === value)?.label ?? value;
  return (
    <div className="relative min-w-0">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="outline"
            className={`relative h-9 w-full justify-start bg-background pl-3 font-normal text-foreground hover:bg-accent hover:text-accent-foreground ${value !== "ALL" ? "pr-16" : "pr-10"}`}
            aria-label={label}
          >
            <span className="truncate">{selectedLabel}</span>
            <ChevronDown className="pointer-events-none absolute right-3 h-4 w-4 text-muted-foreground" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          align="start"
          className="z-[100] w-[var(--radix-dropdown-menu-trigger-width)] border-border bg-popover text-popover-foreground shadow-lg"
        >
          <DropdownMenuRadioGroup value={value} onValueChange={onValueChange}>
            {options.map((option) => (
              <DropdownMenuRadioItem
                key={option.value}
                value={option.value}
                className="cursor-pointer focus:bg-accent focus:text-accent-foreground"
              >
                {option.label}
              </DropdownMenuRadioItem>
            ))}
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
      {value !== "ALL" ? (
        <ClearFilterButton
          label={clearLabel}
          onClear={() => onValueChange("ALL")}
          className="right-8"
        />
      ) : null}
    </div>
  );
}

function StatusMultiSelect({
  values,
  onValuesChange,
  label,
  allLabel,
  clearLabel,
  t,
}: {
  values: ApprovalStatus[];
  onValuesChange: (values: ApprovalStatus[]) => void;
  label: string;
  allLabel: string;
  clearLabel: string;
  t: (key: `approval.status.${ApprovalStatus}`) => string;
}) {
  const summary =
    values.length === 0
      ? allLabel
      : values.length === 1
        ? t(`approval.status.${values[0]}`)
        : `${t(`approval.status.${values[0]}`)} +${values.length - 1}`;

  return (
    <div className="relative min-w-0">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            type="button"
            variant="outline"
            aria-label={label}
            className={`relative h-9 w-full justify-start bg-background pl-3 font-normal ${values.length > 0 ? "pr-16" : "pr-10"}`}
          >
            <span className="truncate">{summary}</span>
            <ChevronDown className="pointer-events-none absolute right-3 h-4 w-4 text-muted-foreground" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-[var(--radix-dropdown-menu-trigger-width)]">
          <DropdownMenuCheckboxItem
            checked={values.length === 0}
            onCheckedChange={() => onValuesChange([])}
            onSelect={(event) => event.preventDefault()}
          >
            {allLabel}
          </DropdownMenuCheckboxItem>
          {STATUS_FILTERS.map((status) => (
            <DropdownMenuCheckboxItem
              key={status}
              checked={values.includes(status)}
              onCheckedChange={(checked) =>
                onValuesChange(
                  checked ? [...values, status] : values.filter((current) => current !== status)
                )
              }
              onSelect={(event) => event.preventDefault()}
            >
              {t(`approval.status.${status}`)}
            </DropdownMenuCheckboxItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
      {values.length > 0 ? (
        <ClearFilterButton
          label={clearLabel}
          onClear={() => onValuesChange([])}
          className="right-8"
        />
      ) : null}
    </div>
  );
}

function DateRangeFilter({
  value,
  onChange,
  label,
  placeholder,
  locale,
  clearLabel,
}: {
  value: DateRange | undefined;
  onChange: (value: DateRange | undefined) => void;
  label: string;
  placeholder: string;
  locale: string;
  clearLabel: string;
}) {
  const [open, setOpen] = useState(false);
  const dateFormatter = useMemo(
    () => new Intl.DateTimeFormat(locale, { year: "numeric", month: "2-digit", day: "2-digit" }),
    [locale]
  );
  const summary = value?.from
    ? value.to
      ? `${dateFormatter.format(value.from)} — ${dateFormatter.format(value.to)}`
      : dateFormatter.format(value.from)
    : placeholder;

  return (
    <div className="relative min-w-0">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            type="button"
            variant="outline"
            aria-label={label}
            className="h-9 w-full justify-start bg-background px-3 pr-10 font-normal"
          >
            <CalendarDays className="mr-2 h-4 w-4 shrink-0 text-muted-foreground" />
            <span className={value?.from ? "truncate" : "truncate text-muted-foreground"}>
              {summary}
            </span>
          </Button>
        </PopoverTrigger>
        <PopoverContent align="start" className="w-auto p-0">
          <Calendar
            initialFocus
            mode="range"
            min={0}
            defaultMonth={value?.from}
            selected={value}
            disabled={value?.from && !value.to ? { before: value.from } : undefined}
            onSelect={(nextValue) => {
              onChange(nextValue);
              if (nextValue?.from && nextValue.to) setOpen(false);
            }}
            numberOfMonths={2}
          />
        </PopoverContent>
      </Popover>
      {value?.from ? (
        <ClearFilterButton label={clearLabel} onClear={() => onChange(undefined)} />
      ) : null}
    </div>
  );
}

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
  if (
    status === "FAILED" ||
    status === "REJECTED" ||
    status === "CANCELLED" ||
    status === "EXPIRED"
  )
    return <XCircle className="h-4 w-4 text-destructive" />;
  if (status === "RUNNING") return <RefreshCw className="h-4 w-4 animate-spin text-primary" />;
  if (status === "RECONCILING")
    return <AlertCircle className="h-4 w-4 animate-pulse text-amber-500" />;
  return <Clock3 className="h-4 w-4 text-muted-foreground" />;
}

export function ApprovalCenterTab() {
  const { locale, t } = useUiPreferences();
  const { user } = useAuthSession();
  const { connection } = useConnection();
  const { setDisplayMode } = useChatPanel();
  const [requests, setRequests] = useState<ApprovalRequest[]>([]);
  const [requestTotal, setRequestTotal] = useState(0);
  const [types, setTypes] = useState<ApprovalType[]>([]);
  const [definitions, setDefinitions] = useState<ApprovalTypeDefinition[]>([]);
  const [selected, setSelected] = useState<ApprovalDetail | null>(null);
  const [statuses, setStatuses] = useState<ApprovalStatus[]>([]);
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [keyword, setKeyword] = useState("");
  const [dateRange, setDateRange] = useState<DateRange>();
  const [page, setPage] = useState(1);
  const deferredKeyword = useDeferredValue(keyword.trim());
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<ApprovalRequest | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isAdmin = user?.role === "ADMIN";
  const selectedRequestId = selected?.request.id;
  const createdFrom = dateRange?.from
    ? new Date(
        dateRange.from.getFullYear(),
        dateRange.from.getMonth(),
        dateRange.from.getDate()
      ).toISOString()
    : undefined;
  const createdTo = dateRange?.to
    ? new Date(
        dateRange.to.getFullYear(),
        dateRange.to.getMonth(),
        dateRange.to.getDate(),
        23,
        59,
        59,
        999
      ).toISOString()
    : undefined;

  const reload = useCallback(async () => {
    if (!connection) return;
    setLoading(true);
    setError(null);
    try {
      const nextPage = await listApprovals({
        statuses: statuses.length === 0 ? undefined : statuses,
        workOrderTypeKey: typeFilter === "ALL" ? undefined : typeFilter,
        keyword: deferredKeyword || undefined,
        createdFrom,
        createdTo,
        page,
        pageSize: PAGE_SIZE,
      });
      setRequests(nextPage.items);
      setRequestTotal(nextPage.total);
      if (selectedRequestId) setSelected(await getApproval(selectedRequestId));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t("approval.error.load"));
    } finally {
      setLoading(false);
    }
  }, [
    connection,
    createdFrom,
    createdTo,
    deferredKeyword,
    page,
    selectedRequestId,
    statuses,
    t,
    typeFilter,
  ]);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 250);
    return () => window.clearTimeout(timer);
  }, [reload]);

  useEffect(() => {
    if (!connection) return;
    let cancelled = false;
    void Promise.all([
      listApprovalTypes(connection.connectionId),
      isAdmin ? listApprovalTypeDefinitions() : Promise.resolve([]),
    ])
      .then(([nextTypes, nextDefinitions]) => {
        if (!cancelled) {
          setTypes(nextTypes);
          setDefinitions(nextDefinitions);
        }
      })
      .catch((caught) => {
        if (!cancelled)
          setError(caught instanceof Error ? caught.message : t("approval.error.load"));
      });
    return () => {
      cancelled = true;
    };
  }, [connection, isAdmin, t]);

  const typeNames = useMemo(
    () => new Map(types.map((type) => [type.typeKey, localizedJson(type.nameI18nJson)])),
    [types]
  );
  const typeFilterOptions = useMemo<FilterOption[]>(
    () => [
      { value: "ALL", label: t("approval.filter.allTypes") },
      ...types.map((type) => ({
        value: type.typeKey,
        label: typeNames.get(type.typeKey) ?? type.typeKey,
      })),
    ],
    [t, typeNames, types]
  );

  const pageCount = Math.max(1, Math.ceil(requestTotal / PAGE_SIZE));
  const pageRequests = requests;

  useEffect(() => setPage(1), [statuses, typeFilter, keyword, createdFrom, createdTo]);
  useEffect(() => setPage((current) => Math.min(current, pageCount)), [pageCount]);

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

  const confirmDelete = useCallback(async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setError(null);
    try {
      await deleteApproval(deleteTarget.id);
      setDeleteTarget(null);
      await reload();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t("approval.error.delete"));
    } finally {
      setDeleting(false);
    }
  }, [deleteTarget, reload, t]);

  const act = useCallback(
    async (
      action: "submit" | "interrupt" | "approve" | "reject" | "execute" | "retry" | "close",
      comment?: string
    ) => {
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
        await reload();
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : t("approval.error.action"));
      } finally {
        setActing(false);
      }
    },
    [reload, selected, t]
  );

  const saveSqlPlan = useCallback(
    async (items: Array<{ id: string; sqlText: string }>) => {
      if (!selected) return false;
      setActing(true);
      setError(null);
      try {
        const next = await updateApprovalSqlPlan(selected.request.id, {
          revision: selected.request.revision,
          items,
        });
        setSelected(next);
        await reload();
        return true;
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : t("approval.error.action"));
        return false;
      } finally {
        setActing(false);
      }
    },
    [reload, selected, t]
  );

  if (!connection) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
        {t("approval.connectionRequired")}
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 min-w-0 flex-col bg-muted/10">
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

      <Tabs defaultValue="orders" className="flex min-h-0 min-w-0 flex-1 flex-col px-5 pt-3">
        <TabsList className="w-fit">
          <TabsTrigger value="orders">{t("approval.orders")}</TabsTrigger>
          {isAdmin && <TabsTrigger value="types">{t("approval.types")}</TabsTrigger>}
        </TabsList>
        <TabsContent value="orders" className="mt-3 min-h-0 min-w-0 flex-1 overflow-hidden">
          {selected ? (
            <ApprovalDetailPanel
              detail={selected}
              isAdmin={isAdmin}
              currentUserId={user?.id}
              acting={acting}
              typeName={typeNames.get(selected.request.workOrderTypeKey)}
              onBack={() => setSelected(null)}
              onAction={act}
              onSaveSql={saveSqlPlan}
            />
          ) : (
            <Card className="flex h-full min-h-0 flex-col overflow-hidden">
              <CardHeader className="space-y-4 border-b pb-4">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-base">{t("approval.list")}</CardTitle>
                  <span className="text-xs text-muted-foreground">
                    {t("approval.list.total").replace("{count}", String(requestTotal))}
                  </span>
                </div>
                <div className="grid gap-2 lg:grid-cols-[1.7fr_1fr_1fr_1.5fr_auto]">
                  <div className="relative">
                    <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                    <Input
                      className="pl-8 pr-9"
                      value={keyword}
                      onChange={(e) => setKeyword(e.target.value)}
                      placeholder={t("approval.filter.keyword")}
                    />
                    {keyword ? (
                      <ClearFilterButton
                        label={t("approval.filter.clearKeyword")}
                        onClear={() => setKeyword("")}
                      />
                    ) : null}
                  </div>
                  <FilterSelect
                    label={t("approval.filter.type")}
                    value={typeFilter}
                    options={typeFilterOptions}
                    onValueChange={setTypeFilter}
                    clearLabel={t("approval.filter.clearType")}
                  />
                  <StatusMultiSelect
                    values={statuses}
                    onValuesChange={setStatuses}
                    label={t("approval.filter.status")}
                    allLabel={t("approval.filter.allStatuses")}
                    clearLabel={t("approval.filter.clearStatus")}
                    t={t}
                  />
                  <DateRangeFilter
                    value={dateRange}
                    onChange={setDateRange}
                    label={t("approval.filter.dateRange")}
                    placeholder={t("approval.filter.dateRange.placeholder")}
                    locale={locale}
                    clearLabel={t("approval.filter.clearDateRange")}
                  />
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setKeyword("");
                      setTypeFilter("ALL");
                      setDateRange(undefined);
                      setStatuses([]);
                    }}
                  >
                    <RotateCcw className="mr-2 h-4 w-4" />
                    {t("approval.filter.reset")}
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="min-h-0 flex-1 overflow-hidden px-6 py-0">
                <Table
                  containerClassName="h-full overscroll-x-contain [scrollbar-gutter:stable]"
                  className="min-w-[1680px] table-fixed"
                >
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[300px]`}>
                        {t("approval.column.id")}
                      </TableHead>
                      <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[340px]`}>
                        {t("approval.column.title")}
                      </TableHead>
                      <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[160px]`}>
                        {t("approval.column.type")}
                      </TableHead>
                      <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[220px]`}>
                        {t("approval.column.applicant")}
                      </TableHead>
                      <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[140px]`}>
                        {t("approval.column.status")}
                      </TableHead>
                      <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[200px]`}>
                        {t("approval.column.createdAt")}
                      </TableHead>
                      <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[200px]`}>
                        {t("approval.column.updatedAt")}
                      </TableHead>
                      <TableHead
                        className={`${STICKY_ACTION_HEAD_CLASS} ${isAdmin ? "w-[200px]" : "w-[120px]"} text-right`}
                      >
                        {t("approval.column.actions")}
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {loading ? (
                      <TableRow>
                        <TableCell colSpan={8} className="h-40 text-center text-muted-foreground">
                          {t("approval.loading")}
                        </TableCell>
                      </TableRow>
                    ) : pageRequests.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={8} className="h-40 text-center text-muted-foreground">
                          {t("approval.empty")}
                        </TableCell>
                      </TableRow>
                    ) : (
                      pageRequests.map((request) => (
                        <TableRow key={request.id} className="group transition-colors">
                          <TableCell>
                            <button
                              className="break-all text-left font-mono text-xs font-medium text-primary hover:underline"
                              onClick={() => void openDetail(request.id)}
                            >
                              {request.requestNo}
                            </button>
                          </TableCell>
                          <TableCell className="whitespace-nowrap">
                            <div className="max-w-72">
                              <div className="truncate font-medium">{request.title}</div>
                              {request.summary ? (
                                <div className="truncate text-xs text-muted-foreground">
                                  {request.summary}
                                </div>
                              ) : null}
                            </div>
                          </TableCell>
                          <TableCell className="max-w-0 overflow-hidden">
                            <span
                              className="block max-w-full truncate"
                              title={
                                typeNames.get(request.workOrderTypeKey) ?? request.workOrderTypeKey
                              }
                            >
                              {typeNames.get(request.workOrderTypeKey) ?? request.workOrderTypeKey}
                            </span>
                          </TableCell>
                          <TableCell className="max-w-0 overflow-hidden">
                            <span
                              className="block max-w-full truncate"
                              title={request.applicantDisplayName ?? request.applicantUserId}
                            >
                              {request.applicantDisplayName ?? request.applicantUserId}
                            </span>
                          </TableCell>
                          <TableCell className="whitespace-nowrap">
                            <Badge variant="outline" className="gap-1">
                              <StatusIcon status={request.status} />
                              {t(`approval.status.${request.status}`)}
                            </Badge>
                          </TableCell>
                          <TableCell className="whitespace-nowrap text-xs text-muted-foreground">
                            {new Date(request.createdAt).toLocaleString(locale)}
                          </TableCell>
                          <TableCell className="whitespace-nowrap text-xs text-muted-foreground">
                            {new Date(request.updatedAt).toLocaleString(locale)}
                          </TableCell>
                          <TableCell className={STICKY_ACTION_CELL_CLASS}>
                            <div className="flex items-center justify-end gap-1">
                              <Button
                                variant="ghost"
                                size="sm"
                                className="h-8 gap-1.5 rounded-md px-2.5 text-primary hover:bg-primary/10 hover:text-primary"
                                onClick={() => void openDetail(request.id)}
                              >
                                <Eye className="h-3.5 w-3.5" />
                                {t("approval.viewDetails")}
                              </Button>
                              {isAdmin ? (
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  className="h-8 gap-1.5 rounded-md px-2.5 text-destructive hover:bg-destructive/10 hover:text-destructive"
                                  disabled={request.status === "RUNNING"}
                                  title={
                                    request.status === "RUNNING"
                                      ? t("approval.delete.runningDisabled")
                                      : t("approval.delete")
                                  }
                                  onClick={() => setDeleteTarget(request)}
                                >
                                  <Trash2 className="h-3.5 w-3.5" />
                                  {t("approval.delete")}
                                </Button>
                              ) : null}
                            </div>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </CardContent>
              <div className="flex items-center justify-between border-t px-6 py-3 text-xs text-muted-foreground">
                <span>
                  {t("approval.pagination.range")
                    .replace("{from}", String(requestTotal ? (page - 1) * PAGE_SIZE + 1 : 0))
                    .replace("{to}", String(Math.min(page * PAGE_SIZE, requestTotal)))
                    .replace("{total}", String(requestTotal))}
                </span>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="icon"
                    className="h-8 w-8"
                    disabled={page <= 1}
                    onClick={() => setPage((v) => v - 1)}
                    aria-label={t("approval.pagination.previous")}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span>
                    {page} / {pageCount}
                  </span>
                  <Button
                    variant="outline"
                    size="icon"
                    className="h-8 w-8"
                    disabled={page >= pageCount}
                    onClick={() => setPage((v) => v + 1)}
                    aria-label={t("approval.pagination.next")}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </Card>
          )}
        </TabsContent>
        {isAdmin && (
          <TabsContent value="types" className="mt-3 min-h-0 min-w-0 flex-1 overflow-hidden">
            <TypeDefinitionsManager
              definitions={definitions}
              onSaved={(updated) =>
                setDefinitions((current) =>
                  current.map((item) => (item.typeKey === updated.typeKey ? updated : item))
                )
              }
            />
          </TabsContent>
        )}
      </Tabs>
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t("approval.delete.confirmTitle")}</DialogTitle>
            <DialogDescription>
              {t("approval.delete.confirmDescription")
                .replace("{requestNo}", deleteTarget?.requestNo ?? "")
                .replace("{title}", deleteTarget?.title ?? "")}
            </DialogDescription>
          </DialogHeader>
          <div className="rounded-lg border border-destructive/20 bg-destructive/5 px-3 py-2 text-xs leading-5 text-muted-foreground">
            {t("approval.delete.cascadeNotice")}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={deleting} onClick={() => setDeleteTarget(null)}>
              {t("approval.delete.cancel")}
            </Button>
            <Button variant="destructive" disabled={deleting} onClick={() => void confirmDelete()}>
              <Trash2 className="mr-2 h-4 w-4" />
              {deleting ? t("approval.delete.deleting") : t("approval.delete.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

const TYPE_PAGE_SIZE = 8;

function TypeDefinitionsManager({
  definitions,
  onSaved,
}: {
  definitions: ApprovalTypeDefinition[];
  onSaved: (definition: ApprovalTypeDefinition) => void;
}) {
  const { locale, t } = useUiPreferences();
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("ALL");
  const [page, setPage] = useState(1);
  const [editing, setEditing] = useState<ApprovalTypeDefinition | null>(null);
  const deferredKeyword = useDeferredValue(keyword.trim().toLocaleLowerCase());
  const statusOptions = useMemo<FilterOption[]>(
    () => [
      { value: "ALL", label: t("approval.type.filter.allStatuses") },
      { value: "ENABLED", label: t("approval.type.enabled") },
      { value: "DISABLED", label: t("approval.type.disabled") },
    ],
    [t]
  );
  const filtered = useMemo(
    () =>
      definitions.filter((definition) => {
        if (status !== "ALL" && definition.status !== status) return false;
        if (!deferredKeyword) return true;
        const searchable = [
          definition.typeKey,
          definition.generatorKey,
          localizedJson(definition.nameI18nJson),
          i18nValue(definition.nameI18nJson, "en"),
          i18nValue(definition.nameI18nJson, "zh-CN"),
        ]
          .join(" ")
          .toLocaleLowerCase();
        return searchable.includes(deferredKeyword);
      }),
    [definitions, deferredKeyword, status]
  );
  const pageCount = Math.max(1, Math.ceil(filtered.length / TYPE_PAGE_SIZE));
  const pageItems = filtered.slice((page - 1) * TYPE_PAGE_SIZE, page * TYPE_PAGE_SIZE);

  useEffect(() => setPage(1), [deferredKeyword, status]);
  useEffect(() => setPage((current) => Math.min(current, pageCount)), [pageCount]);

  return (
    <Card className="flex h-full min-h-0 flex-col overflow-hidden">
      <CardHeader className="space-y-3 border-b pb-4">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">{t("approval.type.list")}</CardTitle>
          <span className="text-xs text-muted-foreground">
            {t("approval.type.total").replace("{count}", String(filtered.length))}
          </span>
        </div>
        <div className="grid gap-2 sm:grid-cols-[minmax(260px,1fr)_220px]">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              className="pl-8 pr-9"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder={t("approval.type.filter.keyword")}
            />
            {keyword ? (
              <ClearFilterButton
                label={t("approval.type.filter.clearKeyword")}
                onClear={() => setKeyword("")}
              />
            ) : null}
          </div>
          <FilterSelect
            label={t("approval.type.filter.status")}
            value={status}
            options={statusOptions}
            onValueChange={setStatus}
            clearLabel={t("approval.type.filter.clearStatus")}
          />
        </div>
      </CardHeader>
      <CardContent className="min-h-0 flex-1 overflow-hidden px-6 py-0">
        <Table
          containerClassName="h-full overscroll-x-contain [scrollbar-gutter:stable]"
          className="min-w-[980px] table-fixed"
        >
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[190px]`}>
                {t("approval.type.column.name")}
              </TableHead>
              <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[220px]`}>
                {t("approval.type.column.key")}
              </TableHead>
              <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[200px]`}>
                {t("approval.type.column.generator")}
              </TableHead>
              <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[100px]`}>
                {t("approval.type.column.status")}
              </TableHead>
              <TableHead className={`${APPROVAL_TABLE_HEAD_CLASS} w-[70px]`}>
                {t("approval.revision")}
              </TableHead>
              <TableHead className={`${STICKY_ACTION_HEAD_CLASS} w-[120px] text-right`}>
                {t("approval.type.column.actions")}
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {pageItems.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-40 text-center text-muted-foreground">
                  {t("approval.type.empty")}
                </TableCell>
              </TableRow>
            ) : (
              pageItems.map((definition) => (
                <TableRow key={definition.typeKey} className="group transition-colors">
                  <TableCell className="max-w-0 overflow-hidden">
                    <div
                      className="truncate font-medium"
                      title={localizedJson(definition.nameI18nJson)}
                    >
                      {localizedJson(definition.nameI18nJson)}
                    </div>
                    <div className="truncate text-xs text-muted-foreground">
                      {i18nValue(definition.descriptionI18nJson, locale)}
                    </div>
                  </TableCell>
                  <TableCell className="max-w-0 overflow-hidden font-mono text-xs">
                    <span className="block truncate" title={definition.typeKey}>
                      {definition.typeKey}
                    </span>
                  </TableCell>
                  <TableCell className="max-w-0 overflow-hidden font-mono text-xs">
                    <span className="block truncate" title={definition.generatorKey}>
                      {definition.generatorKey}
                    </span>
                  </TableCell>
                  <TableCell>
                    <Badge variant={definition.status === "ENABLED" ? "outline" : "secondary"}>
                      {definition.status === "ENABLED"
                        ? t("approval.type.enabled")
                        : t("approval.type.disabled")}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-center text-muted-foreground">
                    {definition.definitionRevision}
                  </TableCell>
                  <TableCell className={STICKY_ACTION_CELL_CLASS}>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-8 gap-1.5 rounded-md px-2.5 text-primary hover:bg-primary/10 hover:text-primary"
                      onClick={() => setEditing(definition)}
                    >
                      <Pencil className="h-3.5 w-3.5" />
                      {t("approval.type.edit")}
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </CardContent>
      <div className="flex items-center justify-between border-t px-6 py-3 text-xs text-muted-foreground">
        <span>
          {t("approval.pagination.range")
            .replace("{from}", String(filtered.length ? (page - 1) * TYPE_PAGE_SIZE + 1 : 0))
            .replace("{to}", String(Math.min(page * TYPE_PAGE_SIZE, filtered.length)))
            .replace("{total}", String(filtered.length))}
        </span>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={page <= 1}
            onClick={() => setPage((current) => current - 1)}
            aria-label={t("approval.pagination.previous")}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span>
            {page} / {pageCount}
          </span>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={page >= pageCount}
            onClick={() => setPage((current) => current + 1)}
            aria-label={t("approval.pagination.next")}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>
      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        {editing ? (
          <DialogContent className="max-h-[88vh] max-w-4xl overflow-y-auto">
            <DialogHeader>
              <DialogTitle>{t("approval.type.editTitle")}</DialogTitle>
              <DialogDescription>
                {localizedJson(editing.nameI18nJson)} · {editing.typeKey}
              </DialogDescription>
            </DialogHeader>
            <TypeDefinitionEditor
              key={`${editing.typeKey}-${editing.definitionRevision}`}
              definition={editing}
              onSaved={(updated) => {
                onSaved(updated);
                setEditing(null);
              }}
              onCancel={() => setEditing(null)}
            />
          </DialogContent>
        ) : null}
      </Dialog>
    </Card>
  );
}

function TypeDefinitionEditor({
  definition,
  onSaved,
  onCancel,
}: {
  definition: ApprovalTypeDefinition;
  onSaved: (definition: ApprovalTypeDefinition) => void;
  onCancel: () => void;
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

  const formatRules = () => {
    try {
      setRules(JSON.stringify(JSON.parse(rules), null, 2));
      setError(null);
    } catch {
      setError(t("approval.type.rules.invalid"));
    }
  };

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
    <div className="space-y-4">
      <div className="flex items-center justify-between rounded-md border bg-muted/30 px-3 py-2">
        <div className="font-mono text-xs text-muted-foreground">
          {definition.generatorKey} · {t("approval.revision")} {definition.definitionRevision}
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
      <div className="space-y-3">
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
          <div className="mb-1 flex items-center justify-between gap-2">
            <div>
              <Label className="text-xs">{t("approval.type.rules")}</Label>
              <p className="text-xs text-muted-foreground">{t("approval.type.rules.help")}</p>
            </div>
            <Button type="button" variant="outline" size="sm" onClick={formatRules}>
              {t("approval.type.rules.format")}
            </Button>
          </div>
          <Textarea
            className="min-h-40 font-mono text-xs leading-5"
            value={rules}
            onChange={(event) => setRules(event.target.value)}
          />
        </div>
        {error && <p className="text-xs text-destructive">{error}</p>}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancel}>
            {t("approval.sql.cancel")}
          </Button>
          <Button size="sm" disabled={saving} onClick={() => void save()}>
            {saving ? t("approval.type.saving") : t("approval.type.save")}
          </Button>
        </DialogFooter>
      </div>
    </div>
  );
}

function ApprovalDetailPanel({
  detail,
  isAdmin,
  currentUserId,
  acting,
  typeName,
  onBack,
  onAction,
  onSaveSql,
}: {
  detail: ApprovalDetail;
  isAdmin: boolean;
  currentUserId?: string;
  acting: boolean;
  typeName?: string;
  onBack: () => void;
  onAction: (
    action: "submit" | "interrupt" | "approve" | "reject" | "execute" | "retry" | "close",
    comment?: string
  ) => void;
  onSaveSql: (items: Array<{ id: string; sqlText: string }>) => Promise<boolean>;
}) {
  const { locale, t } = useUiPreferences();
  const [comment, setComment] = useState("");
  const [editingSql, setEditingSql] = useState(false);
  const [editedSql, setEditedSql] = useState<Record<string, string>>({});
  const request = detail.request;
  const canEditSql = isAdmin && (request.status === "DRAFT" || request.status === "SUBMITTED");
  const hasAvailableActions =
    request.status === "DRAFT" ||
    (request.applicantUserId === currentUserId && request.status === "SUBMITTED") ||
    (isAdmin &&
      (request.status === "SUBMITTED" ||
        request.status === "APPROVED" ||
        request.status === "FAILED" ||
        request.status === "RECONCILING"));
  const formattedSqlById = useMemo(
    () => new Map(detail.items.map((item) => [item.id, SqlUtils.prettyFormatQuery(item.sqlText)])),
    [detail.items]
  );
  const beginEditing = () => {
    setEditedSql(Object.fromEntries(detail.items.map((item) => [item.id, item.sqlText])));
    setEditingSql(true);
  };
  const saveSql = async () => {
    const saved = await onSaveSql(
      detail.items.map((item) => ({ id: item.id, sqlText: editedSql[item.id] ?? item.sqlText }))
    );
    if (saved) setEditingSql(false);
  };
  return (
    <Card className="h-full min-h-0 min-w-0 max-w-full overflow-hidden border-border/70 shadow-sm">
      <div className="h-full min-w-0 overflow-y-auto overflow-x-hidden">
        <CardHeader className="space-y-4 border-b bg-gradient-to-br from-primary/[0.09] via-background to-muted/30 p-5">
          <Button variant="ghost" size="sm" className="mb-1 w-fit -ml-2" onClick={onBack}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            {t("approval.backToList")}
          </Button>
          <div className="flex items-start justify-between gap-3">
            <div>
              <CardTitle className="text-xl tracking-tight">{request.title}</CardTitle>
              <p className="mt-1 font-mono text-xs text-muted-foreground">{request.requestNo}</p>
            </div>
            <Badge className="gap-1" variant="outline">
              <StatusIcon status={request.status} />
              {t(`approval.status.${request.status}`)}
            </Badge>
          </div>
          {request.summary && <p className="text-sm text-muted-foreground">{request.summary}</p>}
          <div className="grid min-w-0 gap-2 sm:grid-cols-2 lg:grid-cols-4">
            <DetailMeta
              label={t("approval.column.type")}
              value={typeName ?? request.workOrderTypeKey}
            />
            <DetailMeta
              label={t("approval.column.applicant")}
              value={request.applicantDisplayName ?? request.applicantUserId}
            />
            <DetailMeta label={t("approval.detail.connection")} value={request.connectionName} />
            <DetailMeta
              label={t("approval.column.createdAt")}
              value={new Date(request.createdAt).toLocaleString(locale)}
            />
          </div>
          {isAdmin &&
            (request.status === "SUBMITTED" ||
              request.status === "FAILED" ||
              request.status === "RECONCILING") && (
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
          {hasAvailableActions ? (
            <div className="flex flex-wrap items-center gap-2 rounded-lg border bg-background/70 p-2.5 shadow-sm backdrop-blur">
              {request.status === "DRAFT" && (
                <Button size="sm" disabled={acting} onClick={() => onAction("submit")}>
                  <Send className="mr-2 h-4 w-4" />
                  {t("approval.submit")}
                </Button>
              )}
              {request.applicantUserId === currentUserId &&
                (request.status === "DRAFT" || request.status === "SUBMITTED") && (
                  <Button
                    size="sm"
                    variant="destructive"
                    disabled={acting}
                    onClick={() => onAction("interrupt")}
                  >
                    <CircleStop className="mr-2 h-4 w-4" />
                    {t("approval.interrupt")}
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
              {isAdmin && (request.status === "FAILED" || request.status === "RECONCILING") && (
                <Button size="sm" disabled={acting} onClick={() => onAction("retry")}>
                  <RotateCcw className="mr-2 h-4 w-4" />
                  {t("approval.retry")}
                </Button>
              )}
              {isAdmin && (request.status === "FAILED" || request.status === "RECONCILING") && (
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
          ) : null}
        </CardHeader>
        <CardContent className="min-w-0 space-y-5 bg-muted/10 p-5">
          <section className="rounded-xl border bg-background p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between gap-3">
              <div>
                <h3 className="flex items-center gap-2 text-sm font-semibold">
                  <Code2 className="h-4 w-4 text-primary" />
                  {t("approval.sqlPlan")}
                </h3>
                <p className="mt-1 text-xs text-muted-foreground">
                  {t("approval.sqlPlan.description")}
                </p>
              </div>
              {canEditSql ? (
                editingSql ? (
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={() => setEditingSql(false)}>
                      {t("approval.sql.cancel")}
                    </Button>
                    <Button size="sm" disabled={acting} onClick={() => void saveSql()}>
                      <Save className="mr-2 h-4 w-4" />
                      {t("approval.sql.save")}
                    </Button>
                  </div>
                ) : (
                  <Button variant="outline" size="sm" onClick={beginEditing}>
                    <Pencil className="mr-2 h-4 w-4" />
                    {t("approval.sql.edit")}
                  </Button>
                )
              ) : null}
            </div>
            {editingSql ? (
              <div className="mb-3 rounded-md border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
                {t("approval.sql.editNotice")}
              </div>
            ) : null}
            <div className="min-w-0 space-y-3">
              {detail.items.map((item) => (
                <div
                  key={item.id}
                  className="min-w-0 max-w-full overflow-hidden rounded-lg border bg-card shadow-sm"
                >
                  <div className="flex items-center justify-between border-b bg-muted/40 px-4 py-2.5 text-xs">
                    <span className="font-medium">
                      #{item.ordinal} · {item.operationKind}
                    </span>
                    <div className="flex items-center gap-2">
                      {editingSql ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-7"
                          onClick={() =>
                            setEditedSql((current) => ({
                              ...current,
                              [item.id]: SqlUtils.prettyFormatQuery(
                                current[item.id] ?? item.sqlText
                              ),
                            }))
                          }
                        >
                          {t("approval.sql.format")}
                        </Button>
                      ) : null}
                      <Badge variant="secondary">{item.riskLevel}</Badge>
                    </div>
                  </div>
                  {editingSql ? (
                    <Textarea
                      className="min-h-52 resize-y rounded-none border-0 font-mono text-xs leading-6 focus-visible:ring-0"
                      value={editedSql[item.id] ?? item.sqlText}
                      onChange={(event) =>
                        setEditedSql((current) => ({ ...current, [item.id]: event.target.value }))
                      }
                    />
                  ) : (
                    <div className="max-w-full overflow-x-auto overscroll-x-contain">
                      <ThemedSyntaxHighlighter
                        language="sql"
                        showLineNumbers
                        wrapLongLines={false}
                        customStyle={{
                          margin: 0,
                          padding: "0.875rem",
                          fontSize: "12px",
                          lineHeight: "1.55",
                          width: "max-content",
                          minWidth: "100%",
                        }}
                      >
                        {formattedSqlById.get(item.id) ?? item.sqlText}
                      </ThemedSyntaxHighlighter>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </section>
          {isAdmin && (
            <ExecutionHistory requestId={request.id} requestRevision={request.revision} />
          )}
          <section className="rounded-xl border bg-background p-4 shadow-sm">
            <div className="mb-5 flex items-center justify-between gap-3">
              <h3 className="flex items-center gap-2 text-sm font-semibold">
                <FileClock className="h-4 w-4 text-primary" />
                {t("approval.timeline")}
              </h3>
              <Badge variant="secondary" className="rounded-full px-2.5 font-normal">
                {t("approval.timeline.count").replace("{count}", String(detail.events.length))}
              </Badge>
            </div>
            <div className="relative ml-2 space-y-0 border-l-2 border-primary/15 pl-7">
              {[...detail.events].reverse().map((event, index) => (
                <div key={event.id} className="relative pb-6 last:pb-0">
                  <span
                    className={`absolute -left-[34px] top-4 h-3 w-3 rounded-full border-2 border-background ring-4 ring-background ${index === 0 ? "bg-primary shadow-[0_0_0_3px_hsl(var(--primary)/0.14)]" : "bg-muted-foreground/40"}`}
                  />
                  <div
                    className={`rounded-xl border p-4 transition-colors ${index === 0 ? "border-primary/25 bg-primary/[0.035] shadow-sm" : "bg-card hover:bg-muted/30"}`}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="text-sm font-semibold">
                        {auditEventLabel(event.eventType, t)}
                      </div>
                      <time className="rounded-full bg-muted px-2.5 py-1 text-[11px] text-muted-foreground">
                        {new Date(event.createdAt).toLocaleString(locale)}
                      </time>
                    </div>
                    <div className="mt-1.5 flex items-center gap-1.5 text-xs text-muted-foreground">
                      <span className="h-1.5 w-1.5 rounded-full bg-muted-foreground/50" />
                      {event.actorDisplayName || t("approval.timeline.system")}
                    </div>
                    {event.safeMessage ? (
                      <div className="mt-3 rounded-md border-l-2 border-primary/25 bg-muted/40 px-3 py-2 text-xs leading-5 text-muted-foreground">
                        {auditMessage(event.safeMessage, t)}
                      </div>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          </section>
        </CardContent>
      </div>
    </Card>
  );
}

function DetailMeta({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border bg-background/70 px-3 py-2">
      <div className="text-[11px] text-muted-foreground">{label}</div>
      <div className="mt-0.5 truncate text-sm font-medium" title={value}>
        {value}
      </div>
    </div>
  );
}

function ExecutionHistory({
  requestId,
  requestRevision,
}: {
  requestId: string;
  requestRevision: number;
}) {
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
  }, [requestId, requestRevision, t]);

  return (
    <section className="rounded-xl border bg-background p-4 shadow-sm">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold">
        <Play className="h-4 w-4 text-primary" />
        {t("approval.execution.title")}
      </h3>
      {loading ? (
        <p className="text-xs text-muted-foreground">{t("approval.loading")}</p>
      ) : error ? (
        <p className="text-xs text-destructive">{error}</p>
      ) : executions.length === 0 ? (
        <p className="text-xs text-muted-foreground">{t("approval.execution.empty")}</p>
      ) : (
        <div className="space-y-3">
          {executions.map((execution) => (
            <div key={execution.id} className="rounded-lg border bg-muted/15 p-3.5 text-xs">
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
