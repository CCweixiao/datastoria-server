import { backendApiFetch, backendApiUrl, readBackendError } from "@/lib/backend-api";

export type ApprovalStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "APPROVED"
  | "REJECTED"
  | "QUEUED"
  | "RUNNING"
  | "RECONCILING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "EXPIRED";

export type ApprovalRequest = {
  id: string;
  requestNo: string;
  workOrderTypeKey: string;
  title: string;
  summary?: string;
  applicantUserId: string;
  applicantDisplayName?: string;
  connectionId: string;
  connectionName: string;
  status: ApprovalStatus;
  contentDigest: string;
  revision: number;
  createdAt: string;
  submittedAt?: string;
  approvedAt?: string;
  rejectedAt?: string;
  finishedAt?: string;
  updatedAt: string;
};

export type ApprovalPage = {
  items: ApprovalRequest[];
  total: number;
  page: number;
  pageSize: number;
};

export type ApprovalItem = {
  id: string;
  ordinal: number;
  operationKind: string;
  sqlText: string;
  riskLevel: string;
  warningsJson: string;
};

export type ApprovalEvent = {
  id: string;
  eventType: string;
  actorDisplayName?: string;
  safeMessage?: string;
  createdAt: string;
};

export type ApprovalDetail = {
  request: ApprovalRequest;
  items: ApprovalItem[];
  events: ApprovalEvent[];
};

export type ApprovalType = {
  typeKey: string;
  nameI18nJson: string;
  descriptionI18nJson: string;
  requiredFields: string[];
  ruleSummary: string;
  definitionRevision: number;
};

export type ApprovalTypeDefinition = {
  typeKey: string;
  nameI18nJson: string;
  descriptionI18nJson: string;
  generatorKey: string;
  generationRuleJson: string;
  status: "ENABLED" | "DISABLED";
  definitionRevision: number;
};

export type ApprovalExecution = {
  id: string;
  itemId: string;
  attemptNo: number;
  ordinal: number;
  status: string;
  queryId: string;
  durationMs?: number;
  errorCode?: string;
  safeMessage?: string;
  startedAt?: string;
  finishedAt?: string;
};

export type ApprovalNodeExecution = {
  id: string;
  executionId: string;
  nodeKey: string;
  host: string;
  port?: number;
  status: string;
  durationMs?: number;
  errorCode?: string;
  safeMessage?: string;
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await backendApiFetch(backendApiUrl(path), {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  if (!response.ok) throw new Error((await readBackendError(response)).message);
  return (await response.json()) as T;
}

export function listApprovals(options?: {
  statuses?: ApprovalStatus[];
  workOrderTypeKey?: string;
  applicant?: string;
  keyword?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  pageSize?: number;
}): Promise<ApprovalPage> {
  const parameters = new URLSearchParams({
    page: String(options?.page ?? 1),
    pageSize: String(options?.pageSize ?? 10),
  });
  options?.statuses?.forEach((status) => parameters.append("status", status));
  if (options?.workOrderTypeKey) parameters.set("workOrderTypeKey", options.workOrderTypeKey);
  if (options?.applicant) parameters.set("applicant", options.applicant);
  if (options?.keyword) parameters.set("keyword", options.keyword);
  if (options?.createdFrom) parameters.set("createdFrom", options.createdFrom);
  if (options?.createdTo) parameters.set("createdTo", options.createdTo);
  return request(`/api/approvals?${parameters}`);
}

export function getApproval(id: string): Promise<ApprovalDetail> {
  return request(`/api/approvals/${encodeURIComponent(id)}`);
}

export async function deleteApproval(id: string): Promise<void> {
  const response = await backendApiFetch(
    backendApiUrl(`/api/admin/approvals/${encodeURIComponent(id)}`),
    { method: "DELETE" }
  );
  if (!response.ok) throw new Error((await readBackendError(response)).message);
}

export function updateApprovalSqlPlan(
  id: string,
  payload: { revision: number; items: Array<{ id: string; sqlText: string }> }
): Promise<ApprovalDetail> {
  return request(`/api/admin/approvals/${encodeURIComponent(id)}/sql-plan`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function listApprovalExecutions(id: string): Promise<ApprovalExecution[]> {
  return request(`/api/admin/approvals/${encodeURIComponent(id)}/executions`);
}

export function listApprovalNodeExecutions(
  requestId: string,
  executionId: string,
  options?: { status?: string; offset?: number; limit?: number }
): Promise<ApprovalNodeExecution[]> {
  const parameters = new URLSearchParams();
  if (options?.status) parameters.set("status", options.status);
  parameters.set("offset", String(options?.offset ?? 0));
  parameters.set("limit", String(options?.limit ?? 50));
  return request(
    `/api/admin/approvals/${encodeURIComponent(requestId)}/executions/${encodeURIComponent(executionId)}/nodes?${parameters}`
  );
}

export function listApprovalTypes(connectionId: string): Promise<ApprovalType[]> {
  return request(
    `/api/approval-types/clickhouse-ddl/capabilities?connectionId=${encodeURIComponent(connectionId)}`
  );
}

export function listApprovalTypeDefinitions(): Promise<ApprovalTypeDefinition[]> {
  return request("/api/admin/approval-types/clickhouse-ddl");
}

export function updateApprovalTypeDefinition(
  typeKey: string,
  payload: {
    revision: number;
    nameEn: string;
    nameZhCn: string;
    descriptionEn: string;
    descriptionZhCn: string;
    generationRuleJson: string;
    enabled: boolean;
  }
): Promise<ApprovalTypeDefinition> {
  return request(`/api/admin/approval-types/clickhouse-ddl/${encodeURIComponent(typeKey)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function transitionApproval(
  id: string,
  action: "submit" | "interrupt" | "approve" | "reject" | "execute" | "retry" | "close",
  payload: { revision: number; contentDigest?: string; comment?: string }
): Promise<ApprovalDetail> {
  const admin = action !== "submit" && action !== "interrupt";
  return request(`${admin ? "/api/admin" : "/api"}/approvals/${encodeURIComponent(id)}/${action}`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
