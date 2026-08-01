"use client";

export const NO_CONNECTION_SESSION_CONNECTION_ID = "__datastoria_no_connection__";

export function toSessionRepositoryConnectionId(connectionId: string): string {
  return connectionId;
}

export function getSessionRepositoryConnectionId(
  connection?: { connectionId?: string } | null
): string {
  return connection?.connectionId ?? NO_CONNECTION_SESSION_CONNECTION_ID;
}

export function isNoConnectionSessionConnectionId(connectionId?: string | null): boolean {
  return connectionId === NO_CONNECTION_SESSION_CONNECTION_ID;
}

export function resolveSessionConnection<T>(
  currentConnection: T | null,
  connectionOverride?: T | null
): T | null {
  return connectionOverride === undefined ? currentConnection : connectionOverride;
}

export function isConnectionResolutionPending({
  isInitialized,
  isConnectionAvailable,
  hasPendingConfig,
}: {
  isInitialized: boolean;
  isConnectionAvailable: boolean;
  hasPendingConfig: boolean;
}): boolean {
  return !isInitialized || (hasPendingConfig && !isConnectionAvailable);
}
