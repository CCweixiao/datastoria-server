"use client";

import {
  addQueryHistory,
  clearQueryHistory,
  deleteQueryHistory,
  listQueryHistory,
  type QueryHistoryEntryDTO,
} from "@/lib/query-history-client";
import type { QueryHistoryEntry } from "./query-history-storage";

/**
 * Per-(user, connection) history cap. Enforced server-side by CkQueryHistoryService; kept here as a
 * display constant for the history sheet help text.
 */
export const MAX_QUERY_HISTORY_SIZE = 100;
export const QUERY_HISTORY_UPDATED_EVENT = "query-history-updated";

function notifyQueryHistoryUpdated() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent(QUERY_HISTORY_UPDATED_EVENT));
  }
}

function toEntry(dto: QueryHistoryEntryDTO): QueryHistoryEntry {
  return {
    id: dto.id,
    rawSQL: dto.rawSql,
    timestamp: Date.parse(dto.executedAt),
    connectionId: dto.connectionId,
    connectionName: dto.connectionName ?? "",
  };
}

export type AddQueryHistoryInput = {
  rawSQL: string;
  connectionId: string;
  connectionName: string;
};

/**
 * Backend-driven query history. Dedup-on-rerun, the per-(user, connection) cap, and time-desc
 * ordering all live in the {@code ds_ck_query_history} table; this manager is an in-memory cache of
 * the currently active connection's entries plus an event bus for the UI to re-render on change.
 */
export class QueryHistoryManager {
  private entries: QueryHistoryEntry[] = [];
  private activeConnectionId: string | null = null;
  private loadGeneration = 0;

  list(): QueryHistoryEntry[] {
    return [...this.entries];
  }

  /** Fetches the connection-scoped history (optionally keyword-filtered) and updates the cache. */
  async load(connectionId: string, keyword?: string): Promise<QueryHistoryEntry[]> {
    const generation = ++this.loadGeneration;
    const connectionChanged = this.activeConnectionId !== connectionId;
    this.activeConnectionId = connectionId;
    if (connectionChanged) {
      // Never render the previous cluster's history while the new request is in flight.
      this.entries = [];
      notifyQueryHistoryUpdated();
    }
    try {
      const dtos = await listQueryHistory(connectionId, keyword);
      if (generation !== this.loadGeneration || this.activeConnectionId !== connectionId) {
        return this.list();
      }
      this.entries = dtos.map(toEntry);
    } catch (error) {
      console.error("Failed to load query history:", error);
      if (generation !== this.loadGeneration || this.activeConnectionId !== connectionId) {
        return this.list();
      }
      this.entries = [];
    }
    notifyQueryHistoryUpdated();
    return this.list();
  }

  /** Saves a query, then refreshes the cache when it is the active connection. */
  async add(entry: AddQueryHistoryInput): Promise<QueryHistoryEntry[]> {
    try {
      await addQueryHistory({
        connectionId: entry.connectionId,
        rawSql: entry.rawSQL,
        connectionName: entry.connectionName,
      });
    } catch (error) {
      console.error("Failed to save query history:", error);
      return this.list();
    }
    if (this.activeConnectionId === entry.connectionId) {
      return this.load(entry.connectionId);
    }
    return this.list();
  }

  /** Deletes one entry by id; the ULID is globally unique so no connectionId is needed. */
  async remove(id: string): Promise<QueryHistoryEntry[]> {
    try {
      await deleteQueryHistory(id);
    } catch (error) {
      console.error("Failed to delete query history:", error);
      return this.list();
    }
    this.entries = this.entries.filter((item) => item.id !== id);
    notifyQueryHistoryUpdated();
    return this.list();
  }

  /** Clears every entry for the connection; only updates the cache when it is the active one. */
  async clear(connectionId: string): Promise<QueryHistoryEntry[]> {
    try {
      await clearQueryHistory(connectionId);
    } catch (error) {
      console.error("Failed to clear query history:", error);
      return this.list();
    }
    if (this.activeConnectionId === connectionId) {
      this.entries = [];
      notifyQueryHistoryUpdated();
    }
    return [];
  }

  addListener(listener: EventListener): void {
    if (typeof window === "undefined") {
      return;
    }
    window.addEventListener(QUERY_HISTORY_UPDATED_EVENT, listener);
  }

  removeListener(listener: EventListener): void {
    if (typeof window === "undefined") {
      return;
    }
    window.removeEventListener(QUERY_HISTORY_UPDATED_EVENT, listener);
  }
}

export const queryHistoryManager = new QueryHistoryManager();
