import { backendApiFetch, backendApiUrl, readBackendError } from "@/lib/backend-api";

export type ApprovalStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "APPROVED"
  | "REJECTED"
  | "QUEUED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED";

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

export type PreparedApproval = {
  requestId: string;
  requestNo: string;
  revision: number;
  contentDigest: string;
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await backendApiFetch(backendApiUrl(path), {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  if (!response.ok) throw new Error((await readBackendError(response)).message);
  return (await response.json()) as T;
}

export function listApprovals(status?: ApprovalStatus): Promise<ApprovalRequest[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return request(`/api/approvals${query}`);
}

export function getApproval(id: string): Promise<ApprovalDetail> {
  return request(`/api/approvals/${encodeURIComponent(id)}`);
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

export function prepareDdlApproval(payload: {
  connectionId: string;
  workOrderTypeKey: string;
  title: string;
  summary?: string;
  intent: Record<string, unknown>;
}): Promise<PreparedApproval> {
  return request("/api/approval-types/clickhouse-ddl/prepare", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function transitionApproval(
  id: string,
  action: "submit" | "approve" | "reject" | "execute" | "close",
  payload: { revision: number; contentDigest?: string; comment?: string }
): Promise<ApprovalDetail> {
  const admin = action !== "submit";
  return request(`${admin ? "/api/admin" : "/api"}/approvals/${encodeURIComponent(id)}/${action}`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
