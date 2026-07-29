"use client";

import { deleteUserState, listUserState, putUserState } from "@/lib/user-state-client";
import type { QueryHistoryEntry, QueryHistoryStorage } from "./query-history-storage";

const QUERY_HISTORY_STORAGE_KEY = "history";

export class QueryHistoryLocalStorage implements QueryHistoryStorage {
  private entries: QueryHistoryEntry[] = [];

  load(): QueryHistoryEntry[] {
    return this.entries;
  }

  async hydrate(): Promise<QueryHistoryEntry[]> {
    const stored = await listUserState<QueryHistoryEntry[]>("query-history");
    this.entries = stored.find((entry) => entry.key === QUERY_HISTORY_STORAGE_KEY)?.value ?? [];
    return this.entries;
  }

  save(entries: QueryHistoryEntry[]): void {
    this.entries = entries;
    void putUserState("query-history", QUERY_HISTORY_STORAGE_KEY, entries).catch((error) =>
      console.error("Failed to save query history:", error)
    );
  }

  clear(): void {
    this.entries = [];
    void deleteUserState("query-history", QUERY_HISTORY_STORAGE_KEY).catch((error) =>
      console.error("Failed to clear query history:", error)
    );
  }
}
