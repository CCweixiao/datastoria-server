import { getSessionApiBase, sessionIdentityHeaders } from "@/lib/ai/session/session-api-base";
import { afterEach, describe, expect, it, vi } from "vitest";

const ENV_BACKUP = { ...process.env };

function withEnv(env: Record<string, string | undefined>) {
  process.env = { ...ENV_BACKUP, ...env };
}

describe("session-api-base", () => {
  afterEach(() => {
    process.env = { ...ENV_BACKUP };
    vi.resetModules();
  });

  it("always targets Spring Boot and defaults to the local backend", () => {
    withEnv({ NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL: undefined });
    expect(getSessionApiBase()).toBe("http://127.0.0.1:8080");
  });

  it("strips trailing slashes from the configured backend URL", () => {
    withEnv({ NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL: "http://localhost:8080////" });
    expect(getSessionApiBase()).toBe("http://localhost:8080");
  });

  it("adds development identity and merges caller headers", () => {
    withEnv({ NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL: "dev@example.com" });
    expect(sessionIdentityHeaders({ "Content-Type": "application/json" })).toEqual({
      "Accept-Language": "en",
      "x-datastoria-user-email": "dev@example.com",
      "Content-Type": "application/json",
    });
  });

  it("returns caller headers when development identity is unset", () => {
    withEnv({ NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL: undefined });
    expect(sessionIdentityHeaders({ Accept: "application/json" })).toEqual({
      "Accept-Language": "en",
      Accept: "application/json",
    });
  });
});
