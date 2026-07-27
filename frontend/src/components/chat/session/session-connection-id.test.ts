import { describe, expect, it } from "vitest";
import {
  isConnectionResolutionPending,
  NO_CONNECTION_SESSION_CONNECTION_ID,
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
});
