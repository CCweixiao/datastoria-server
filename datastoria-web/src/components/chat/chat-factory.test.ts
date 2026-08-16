import type { AppUIMessage } from "@/lib/ai/ai-types";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  buildAgentContextWithResponseLanguage,
  buildSendMessagesRequestPayload,
  ChatFactory,
} from "./chat-factory";
import { NO_CONNECTION_SESSION_CONNECTION_ID } from "./session/session-connection-id";

function createMessage(overrides: Partial<AppUIMessage>): AppUIMessage {
  return {
    id: "message-1",
    role: "user",
    parts: [{ type: "text", text: "diagnose this" }],
    ...overrides,
  } as AppUIMessage;
}

describe("buildSendMessagesRequestPayload", () => {
  const diagnosisContext = {
    clusterName: "prod-eu",
    serverVersion: "24.8.1.1",
    clickHouseUser: "default",
  };

  it("includes diagnosis context in remote chat payloads", () => {
    const payload = buildSendMessagesRequestPayload({
      sessionId: "session-1",
      connectionId: "default@https://example.com",
      messages: [createMessage({})],
      trigger: "submit-message",
      messageId: "message-1",
      body: {},
      requestContext: diagnosisContext,
      currentModel: { configId: "model-config-1" },
      generateTitle: false,
      ephemeral: true,
      pruneValidateSql: true,
      chatPersistenceMode: "remote",
    });

    expect(payload).toMatchObject({
      sessionId: "session-1",
      context: diagnosisContext,
      ephemeral: true,
    });
  });

  it("never includes server credentials and uses backend model id", () => {
    const payload = buildSendMessagesRequestPayload({
      sessionId: "session-1",
      connectionId: "default@https://example.com",
      messages: [createMessage({})],
      trigger: "submit-message",
      messageId: "message-1",
      body: {},
      requestContext: diagnosisContext,
      currentModel: { configId: "model-config-1" },
      generateTitle: false,
      ephemeral: true,
      pruneValidateSql: true,
      chatPersistenceMode: "remote",
    });

    expect(payload).toMatchObject({ modelConfigId: "model-config-1" });
    expect(payload).not.toHaveProperty("connection");
    expect(payload).not.toHaveProperty("model");
  });

  it("includes diagnosis context in local chat payloads", () => {
    const message = createMessage({});
    const payload = buildSendMessagesRequestPayload({
      sessionId: "session-1",
      connectionId: "default@https://example.com",
      messages: [message],
      trigger: "submit-message",
      messageId: "message-1",
      body: { existing: true },
      requestContext: diagnosisContext,
      currentModel: undefined,
      generateTitle: true,
      pruneValidateSql: true,
      chatPersistenceMode: "local",
      ephemeral: false,
    });

    expect(payload).toMatchObject({
      existing: true,
      messages: [message],
      context: diagnosisContext,
      generateTitle: true,
    });
  });

  it("does not create legacy client continuation requests for completed tool outputs", () => {
    const payload = buildSendMessagesRequestPayload({
      sessionId: "session-1",
      connectionId: "default@https://example.com",
      messages: [
        createMessage({
          id: "assistant-1",
          role: "assistant",
          parts: [{ type: "dynamic-tool", state: "output-available" }] as AppUIMessage["parts"],
        }),
      ],
      trigger: "submit-message",
      messageId: "assistant-1",
      body: {},
      requestContext: diagnosisContext,
      currentModel: undefined,
      generateTitle: true,
      ephemeral: false,
      pruneValidateSql: true,
      chatPersistenceMode: "remote",
    });

    expect(payload).not.toHaveProperty("continuation");
  });

  it("keeps pruneValidateSql authoritative over agentContext overrides", () => {
    const payload = buildSendMessagesRequestPayload({
      sessionId: "session-1",
      connectionId: "default@https://example.com",
      messages: [createMessage({})],
      trigger: "submit-message",
      messageId: "message-1",
      body: {},
      requestContext: diagnosisContext,
      currentModel: undefined,
      generateTitle: false,
      ephemeral: true,
      pruneValidateSql: true,
      agentContext: {
        pruneValidateSql: false,
        responseLanguage: "zh-CN",
      },
      chatPersistenceMode: "remote",
    });

    expect(payload).toMatchObject({
      agentContext: {
        pruneValidateSql: true,
        responseLanguage: "zh-CN",
      },
    });
  });

  it("adds the selected reasoning level to agent context", () => {
    const payload = buildSendMessagesRequestPayload({
      sessionId: "session-1",
      connectionId: "default@https://example.com",
      messages: [createMessage({})],
      trigger: "submit-message",
      messageId: "message-1",
      body: {},
      requestContext: diagnosisContext,
      currentModel: { configId: "model-config-2" },
      generateTitle: false,
      ephemeral: true,
      pruneValidateSql: true,
      outputReasoning: true,
      reasoningLevel: "high",
      chatPersistenceMode: "remote",
    });

    expect(payload).toMatchObject({
      agentContext: {
        outputReasoning: true,
        reasoningLevel: "high",
      },
    });
  });

  it("preserves mention metadata on remote user messages", () => {
    const message = createMessage({
      metadata: {
        mentionMetadata: {
          version: 1,
          mentions: [{ kind: "setting", name: "max_threads", type: "UInt64" }],
        },
      },
    });

    const payload = buildSendMessagesRequestPayload({
      sessionId: "session-1",
      connectionId: "default@https://example.com",
      messages: [message],
      trigger: "submit-message",
      messageId: "message-1",
      body: {},
      requestContext: diagnosisContext,
      currentModel: undefined,
      generateTitle: false,
      ephemeral: true,
      pruneValidateSql: true,
      chatPersistenceMode: "remote",
    });

    expect(payload).toMatchObject({
      message: {
        metadata: {
          mentionMetadata: {
            version: 1,
            mentions: [{ kind: "setting", name: "max_threads", type: "UInt64" }],
          },
        },
      },
    });
  });

  it("supports remote chat payloads without a ClickHouse connection", () => {
    const payload = buildSendMessagesRequestPayload({
      sessionId: "session-1",
      connectionId: NO_CONNECTION_SESSION_CONNECTION_ID,
      messages: [createMessage({})],
      trigger: "submit-message",
      messageId: "message-1",
      body: {},
      requestContext: undefined,
      currentModel: undefined,
      generateTitle: false,
      ephemeral: false,
      pruneValidateSql: true,
      chatPersistenceMode: "remote",
    });

    expect(payload).toMatchObject({
      sessionId: "session-1",
      connectionId: NO_CONNECTION_SESSION_CONNECTION_ID,
    });
    expect(payload).not.toHaveProperty("context");
  });
});

describe("buildAgentContextWithResponseLanguage", () => {
  it("adds configured non-English response language to agent context", () => {
    expect(buildAgentContextWithResponseLanguage(undefined, "zh-CN")).toEqual({
      responseLanguage: "zh-CN",
    });
  });

  it("lets explicit agent context override configured response language", () => {
    expect(buildAgentContextWithResponseLanguage({ responseLanguage: "ja" }, "zh-CN")).toEqual({
      responseLanguage: "ja",
    });
  });

  it("does not force English as an explicit response language", () => {
    expect(buildAgentContextWithResponseLanguage(undefined, "en")).toBeUndefined();
  });
});

describe("ChatFactory durable actions", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function resumableChat() {
    const resumeStream = vi
      .fn()
      .mockImplementation(
        async (options?: { request?: (signal: AbortSignal) => Promise<Response> }) => {
          await options?.request?.(new AbortController().signal);
        }
      );
    const chat = { resumeStream } as never;
    (
      ChatFactory as unknown as {
        resumeTargets: WeakMap<object, (runId: string) => void>;
      }
    ).resumeTargets.set(chat, vi.fn());
    return { chat, resumeStream };
  }

  it("responds to a question then resumes the same run stream", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const { chat, resumeStream } = resumableChat();

    await ChatFactory.respondToQuestion(chat, "run/1", "action/1", { value: "Production" });

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(String(fetchMock.mock.calls[0][0])).toContain(
      "/api/ai/runs/run%2F1/actions/action%2F1:respond-and-resume"
    );
    expect(JSON.parse(fetchMock.mock.calls[0][1].body as string)).toEqual({
      response: { value: "Production" },
    });
    expect(resumeStream).toHaveBeenCalledOnce();
  });

  it("waits for every approval before resuming a batched checkpoint", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response("{}", { status: 200 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ pendingActions: [{ status: "PENDING" }] }), {
          status: 200,
        })
      );
    vi.stubGlobal("fetch", fetchMock);
    const { chat, resumeStream } = resumableChat();

    await ChatFactory.resolveApproval(chat, "run-1", "action-1", true);

    expect(String(fetchMock.mock.calls[0][0])).toContain(":approve");
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(resumeStream).not.toHaveBeenCalled();
  });
});
