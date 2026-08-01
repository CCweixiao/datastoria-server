import { describe, expect, it } from "vitest";
import {
  isConnectionResolutionPending,
  NO_CONNECTION_SESSION_CONNECTION_ID,
  resolveSessionConnection,
} from "./session-connection-id";

describe("session connection resolution", () => {
  it("waits for connection-manager hydration", () => {
    expect(
      isConnectionResolutionPending({
        isInitialized: false,
        isConnectionAvailable: false,
        hasPendingConfig: false,
      })
    ).toBe(true);
  });

  it("waits while a saved ClickHouse connection is still initializing", () => {
    expect(
      isConnectionResolutionPending({
        isInitialized: true,
        isConnectionAvailable: false,
        hasPendingConfig: true,
      })
    ).toBe(true);
  });

  it("allows a deliberately connectionless chat", () => {
    expect(
      isConnectionResolutionPending({
        isInitialized: true,
        isConnectionAvailable: false,
        hasPendingConfig: false,
      })
    ).toBe(false);
    expect(NO_CONNECTION_SESSION_CONNECTION_ID).toBe("__datastoria_no_connection__");
  });

  it("allows chat initialization after the ClickHouse connection is ready", () => {
    expect(
      isConnectionResolutionPending({
        isInitialized: true,
        isConnectionAvailable: true,
        hasPendingConfig: true,
      })
    ).toBe(false);
  });

  it("keeps the active connection when no override is provided", () => {
    const connection = { id: "connection-1" };

    expect(resolveSessionConnection(connection)).toBe(connection);
    expect(resolveSessionConnection(connection, undefined)).toBe(connection);
  });

  it("allows an explicit connectionless session", () => {
    const connection = { id: "connection-1" };

    expect(resolveSessionConnection(connection, null)).toBeNull();
  });

  it("uses an explicit connection override", () => {
    const connection = { id: "connection-1" };
    const override = { id: "connection-2" };

    expect(resolveSessionConnection(connection, override)).toBe(override);
  });
});
