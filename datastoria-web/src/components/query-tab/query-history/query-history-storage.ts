"use client";

/**
 * A saved ClickHouse SQL query. {@code timestamp} is the execution time as epoch millis (derived
 * from the backend {@code executedAt} instant) for compatibility with the existing UI formatters.
 */
export interface QueryHistoryEntry {
  id: string;
  rawSQL: string;
  timestamp: number;
  connectionId: string;
  connectionName: string;
}
