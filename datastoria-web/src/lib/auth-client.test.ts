import { beforeEach, describe, expect, it, vi } from "vitest";
import { login } from "./auth-client";
import { clearAuthToken, getAuthToken } from "./auth-token-store";

describe("username and password authentication", () => {
  beforeEach(() => {
    clearAuthToken();
    vi.restoreAllMocks();
  });

  it("stores the JWT after a successful login", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          token: "signed-jwt",
          user: {
            userId: "user-1",
            username: "alice",
            role: "USER",
            tenantId: "default",
            status: 1,
          },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );

    await expect(login("alice", "password123")).resolves.toEqual({
      user: { id: "user-1", name: "alice", email: undefined, role: "USER" },
    });
    expect(getAuthToken()).toBe("signed-jwt");
  });

  it("does not store a token when credentials are rejected", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ detail: "Authentication failed" }), {
        status: 401,
        headers: { "Content-Type": "application/problem+json" },
      })
    );

    await expect(login("alice", "wrong-password")).rejects.toThrow("Authentication failed");
    expect(getAuthToken()).toBeNull();
  });
});
