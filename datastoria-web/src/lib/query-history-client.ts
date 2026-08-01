import { backendApiFetch, backendApiHeaders, backendApiUrl } from "@/lib/backend-api";

export type QueryHistoryEntryDTO = {
  id: string;
  connectionId: string;
  connectionName: string | null;
  rawSql: string;
  /** ISO-8601 instant the query was executed. */
  executedAt: string;
};

/**
 * Lists query history for the current user scoped by {@code connectionId} (the first-level filter),
 * ordered time-desc. An optional {@code keyword} narrows the raw SQL server-side; the UI currently
 * filters client-side over the full connection-scoped set and leaves this unset.
 */
export async function listQueryHistory(
  connectionId: string,
  keyword?: string
): Promise<QueryHistoryEntryDTO[]> {
  const params = new URLSearchParams({ connectionId });
  const trimmed = keyword?.trim();
  if (trimmed) {
    params.set("keyword", trimmed);
  }
  const response = await backendApiFetch(
    backendApiUrl(`/api/me/query-history?${params.toString()}`),
    {
      headers: backendApiHeaders(),
    }
  );
  if (!response.ok) {
    throw new Error(`Failed to load query history: ${response.status}`);
  }
  return (await response.json()) as QueryHistoryEntryDTO[];
}

export async function addQueryHistory(payload: {
  connectionId: string;
  rawSql: string;
  connectionName?: string;
}): Promise<QueryHistoryEntryDTO> {
  const response = await backendApiFetch(backendApiUrl("/api/me/query-history"), {
    method: "POST",
    headers: backendApiHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(`Failed to save query history: ${response.status}`);
  }
  return (await response.json()) as QueryHistoryEntryDTO;
}

export async function deleteQueryHistory(id: string): Promise<void> {
  const response = await backendApiFetch(
    backendApiUrl(`/api/me/query-history/${encodeURIComponent(id)}`),
    { method: "DELETE", headers: backendApiHeaders() }
  );
  if (!response.ok && response.status !== 404) {
    throw new Error(`Failed to delete query history: ${response.status}`);
  }
}

export async function clearQueryHistory(connectionId: string): Promise<void> {
  const response = await backendApiFetch(
    backendApiUrl(
      `/api/me/query-history?connectionId=${encodeURIComponent(connectionId)}`
    ),
    { method: "DELETE", headers: backendApiHeaders() }
  );
  if (!response.ok) {
    throw new Error(`Failed to clear query history: ${response.status}`);
  }
}
