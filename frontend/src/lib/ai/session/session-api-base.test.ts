import { afterEach, describe, expect, it, vi } from "vitest";

import {
  getSessionApiBase,
  isJavaSessionBackend,
  sessionIdentityHeaders,
} from "@/lib/ai/session/session-api-base";

const NODE_ENV_BACKUP = { ...process.env };

function withEnv(env: Record<string, string | undefined>) {
  process.env = { ...NODE_ENV_BACKUP, ...env };
}

describe("session-api-base", () => {
  afterEach(() => {
    process.env = { ...NODE_ENV_BACKUP };
    vi.resetModules();
  });

  describe("node mode (default)", () => {
    it("returns the Next.js base path (empty string when unset)", () => {
      withEnv({
        NEXT_PUBLIC_BASE_PATH: undefined,
        NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND: undefined,
      });
      expect(getSessionApiBase()).toBe("");
      expect(isJavaSessionBackend()).toBe(false);
    });

    it("returns the configured Next.js base path when set", () => {
      withEnv({
        NEXT_PUBLIC_BASE_PATH: "/app",
        NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND: "node",
      });
      expect(getSessionApiBase()).toBe("/app");
    });

    it("emits no identity headers", () => {
      withEnv({
        NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND: "node",
        NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL: "dev@example.com",
      });
      expect(sessionIdentityHeaders()).toEqual({});
    });
  });

  describe("java mode", () => {
    it("returns the configured Java base URL with trailing slashes stripped", () => {
      withEnv({
        NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND: "java",
        NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL: "http://localhost:8080////",
      });
      expect(getSessionApiBase()).toBe("http://localhost:8080");
      expect(isJavaSessionBackend()).toBe(true);
    });

    it("falls back to empty string when NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL is unset", () => {
      withEnv({
        NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND: "java",
        NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL: undefined,
      });
      expect(getSessionApiBase()).toBe("");
    });

    it("emits the x-datastoria-user-email header when dev email is set", () => {
      withEnv({
        NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND: "java",
        NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL: "dev@example.com",
      });
      expect(sessionIdentityHeaders()).toEqual({
        "x-datastoria-user-email": "dev@example.com",
      });
    });

    it("returns an empty object when dev email is unset", () => {
      withEnv({
        NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND: "java",
        NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL: undefined,
      });
      expect(sessionIdentityHeaders()).toEqual({});
    });

    it("merges caller-supplied headers with identity headers", () => {
      withEnv({
        NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND: "java",
        NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL: "dev@example.com",
      });
      const merged = sessionIdentityHeaders({ "Content-Type": "application/json" });
      expect(merged).toEqual({
        "x-datastoria-user-email": "dev@example.com",
        "Content-Type": "application/json",
      });
    });
  });
});
