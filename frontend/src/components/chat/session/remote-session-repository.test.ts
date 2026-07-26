import { SESSION_SHARE_CODE_HEADER } from "@/lib/ai/session/session-share-constants";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RemoteSessionRepository } from "./remote-session-repository";

const ENV_BACKUP = { ...process.env };

describe("RemoteSessionRepository", () => {
  beforeEach(() => {
    process.env = { ...ENV_BACKUP };
    delete process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL;
    delete process.env.NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL;
  });

  afterEach(() => {
    process.env = { ...ENV_BACKUP };
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it("uses Spring by default and adds the session share code header", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          chatId: "session-1",
          databaseId: "conn-1",
          title: "Session",
          createdAt: "2026-01-01T00:00:00.000Z",
          updatedAt: "2026-01-01T00:00:00.000Z",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );
    vi.stubGlobal("fetch", fetchMock);

    const repository = new RemoteSessionRepository();
    await repository.getSession("session-1", { shareCode: "share-token" });

    expect(fetchMock).toHaveBeenCalledWith("http://127.0.0.1:8080/api/ai/chat/sessions/session-1", {
      headers: { [SESSION_SHARE_CODE_HEADER]: "share-token" },
      credentials: "include",
      cache: "no-store",
    });
  });

  it("targets the Java backend and attaches the identity header", async () => {
    process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL = "http://localhost:8080";
    process.env.NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL = "dev@example.com";
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          chatId: "session-1",
          databaseId: "conn-1",
          title: "Session",
          createdAt: "2026-01-01T00:00:00.000Z",
          updatedAt: "2026-01-01T00:00:00.000Z",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );
    vi.stubGlobal("fetch", fetchMock);

    const { RemoteSessionRepository } = await import("./remote-session-repository");
    const repository = new RemoteSessionRepository();
    await repository.getSession("session-1", { shareCode: "share-token" });

    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8080/api/ai/chat/sessions/session-1", {
      headers: {
        [SESSION_SHARE_CODE_HEADER]: "share-token",
        "x-datastoria-user-email": "dev@example.com",
      },
      credentials: "include",
      cache: "no-store",
    });
  });

  it("persists a new session before the first agent request", async () => {
    process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL = "http://localhost:8080";
    process.env.NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL = "dev@example.com";
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          session: {
            chatId: "session-1",
            databaseId: "conn-1",
            title: "First prompt",
            createdAt: "2026-01-01T00:00:00.000Z",
            updatedAt: "2026-01-01T00:00:00.000Z",
          },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );
    vi.stubGlobal("fetch", fetchMock);

    const { RemoteSessionRepository } = await import("./remote-session-repository");
    const repository = new RemoteSessionRepository();
    await repository.saveSession({
      chatId: "session-1",
      databaseId: "conn-1",
      title: "First prompt",
      createdAt: new Date("2026-01-01T00:00:00.000Z"),
      updatedAt: new Date("2026-01-01T00:00:00.000Z"),
    });

    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8080/api/ai/chat/sessions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-datastoria-user-email": "dev@example.com",
      },
      credentials: "include",
      body: JSON.stringify({
        connectionId: "conn-1",
        sessionId: "session-1",
        title: "First prompt",
        messages: [],
      }),
    });
  });
});
