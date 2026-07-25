import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { proxyChatToJava, resolveIdempotencyKey, stripClientSecrets } from "./java-chat-proxy";

// Mirror the real auth reader so the proxy resolves identity from the request header (the
// middleware sets it server-side from the next-auth session).
vi.mock("@/auth", () => ({
  getAuthenticatedUserEmail: (req: Request) =>
    req.headers.get("x-datastoria-user-email") || undefined,
}));

const JAVA_BASE = "http://java-backend.test";
const SSE_BODY =
  'data: {"type":"start"}\n\ndata: {"type":"text-delta","delta":"Hi"}\n\ndata: [DONE]\n\n';

function sseResponse(body: string, init?: ResponseInit): Response {
  const encoder = new TextEncoder();
  const chunks = body.split("\n\n").filter((c) => c.length > 0);
  const stream = new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk + "\n\n"));
      }
      controller.close();
    },
  });
  return new Response(stream, {
    status: 200,
    headers: {
      "content-type": "text/event-stream",
      "cache-control": "no-cache",
      "x-vercel-ai-ui-message-stream": "v1",
      "x-accel-buffering": "no",
      ...(init?.headers ?? {}),
    },
  });
}

function chatRequest(payload: unknown, headers: Record<string, string> = {}): Request {
  return new Request("http://localhost/api/ai/agent", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-datastoria-user-email": "dev@example.com",
      ...headers,
    },
    body: JSON.stringify(payload),
  });
}

async function readText(response: Response): Promise<string> {
  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let out = "";
  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    out += decoder.decode(value);
  }
  return out;
}

describe("java-chat-proxy", () => {
  const backup = { ...process.env };
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    process.env = { ...backup, NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL: JAVA_BASE };
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    process.env = { ...backup };
  });

  it("forwards to the Java /api/ai/agent endpoint with identity + idempotency headers", async () => {
    fetchMock.mockResolvedValue(sseResponse(SSE_BODY));
    const payload = {
      sessionId: "sess-1",
      connectionId: "ch-1",
      message: { id: "msg-1", role: "user", parts: [{ type: "text", text: "hi" }] },
      modelConfigId: "mdl-1",
    };

    await proxyChatToJava(chatRequest(payload));

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${JAVA_BASE}/api/ai/agent`);
    expect((init as RequestInit).method).toBe("POST");
    const headers = new Headers((init as RequestInit).headers);
    expect(headers.get("x-datastoria-user-email")).toBe("dev@example.com");
    expect(headers.get("idempotency-key")).toMatch(/^ds-/);
  });

  it("strips client credentials before forwarding (never sends apiKey/password/token)", async () => {
    fetchMock.mockResolvedValue(sseResponse(SSE_BODY));
    const payload = {
      sessionId: "sess-1",
      connectionId: "ch-1",
      message: { id: "msg-1", role: "user", parts: [{ type: "text", text: "hi" }] },
      model: { provider: "openai", modelId: "gpt", apiKey: "sk-LEAK-123" },
      connection: { user: "u", password: "pw-LEAK" },
      accessToken: "tok-LEAK",
    };

    await proxyChatToJava(chatRequest(payload));

    const body = JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string);
    expect(JSON.stringify(body)).not.toContain("sk-LEAK-123");
    expect(JSON.stringify(body)).not.toContain("pw-LEAK");
    expect(JSON.stringify(body)).not.toContain("tok-LEAK");
    expect(body.model.apiKey).toBeUndefined();
    expect(body.connection.password).toBeUndefined();
    expect(body.accessToken).toBeUndefined();
  });

  it("derives a stable idempotency key: same request -> same key, different -> different", () => {
    const a = {
      sessionId: "sess-1",
      message: { id: "msg-1", role: "user", parts: [] },
    };
    const aRetry = { ...a };
    const b = { sessionId: "sess-1", message: { id: "msg-2", role: "user", parts: [] } };
    expect(resolveIdempotencyKey(a)).toBe(resolveIdempotencyKey(aRetry));
    expect(resolveIdempotencyKey(a)).not.toBe(resolveIdempotencyKey(b));
  });

  it("forwards a client-provided Idempotency-Key when present", async () => {
    fetchMock.mockResolvedValue(sseResponse(SSE_BODY));
    await proxyChatToJava(
      chatRequest(
        {
          sessionId: "sess-1",
          message: { id: "msg-1", role: "user", parts: [] },
        },
        { "idempotency-key": "client-supplied-key" }
      )
    );
    const headers = new Headers((fetchMock.mock.calls[0][1] as RequestInit).headers);
    expect(headers.get("idempotency-key")).toBe("client-supplied-key");
  });

  it("streams the SSE body verbatim and preserves AI SDK headers + 200 status", async () => {
    fetchMock.mockResolvedValue(sseResponse(SSE_BODY));
    const response = await proxyChatToJava(
      chatRequest({
        sessionId: "sess-1",
        message: { id: "msg-1", role: "user", parts: [{ type: "text", text: "hi" }] },
      })
    );
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("text/event-stream");
    expect(response.headers.get("x-vercel-ai-ui-message-stream")).toBe("v1");
    expect(response.headers.get("x-accel-buffering")).toBe("no");
    expect(await readText(response)).toBe(SSE_BODY);
  });

  it("passes through Java non-2xx status + body without synthesizing internals", async () => {
    fetchMock.mockResolvedValue(
      new Response("AgentRun run_xyz is still referenced", {
        status: 409,
        headers: { "content-type": "text/plain" },
      })
    );
    const response = await proxyChatToJava(
      chatRequest({
        sessionId: "sess-1",
        message: { id: "msg-1", role: "user", parts: [] },
      })
    );
    expect(response.status).toBe(409);
    expect(await response.text()).toContain("run_xyz");
  });

  it("aborts the upstream fetch when the client disconnects (cancel propagation)", async () => {
    let capturedSignal: AbortSignal | undefined;
    fetchMock.mockImplementation((_url, init) => {
      capturedSignal = (init as RequestInit).signal as AbortSignal;
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode("data: partial\n\n"));
        },
        cancel() {
          /* upstream would abort provider token emission */
        },
      });
      return Promise.resolve(
        new Response(stream, { status: 200, headers: { "content-type": "text/event-stream" } })
      );
    });

    const response = await proxyChatToJava(
      chatRequest({
        sessionId: "sess-1",
        message: { id: "msg-1", role: "user", parts: [] },
      })
    );
    const reader = response.body!.getReader();
    await reader.read();
    await reader.cancel(); // simulate client disconnect
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(capturedSignal).toBeDefined();
    expect(capturedSignal!.aborted).toBe(true);
  });

  it("returns 502 when the Java backend URL is not configured", async () => {
    delete process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL;
    const response = await proxyChatToJava(
      chatRequest({
        sessionId: "sess-1",
        message: { id: "msg-1", role: "user", parts: [] },
      })
    );
    expect(response.status).toBe(502);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("stripClientSecrets removes nested credential keys but leaves safe fields", () => {
    const body: Record<string, unknown> = {
      model: { provider: "openai", apiKey: "sk-x", modelId: "gpt" },
      safe: "keep",
      nested: { token: "t", ok: 1 },
    };
    stripClientSecrets(body);
    expect(body.model).toEqual({ provider: "openai", modelId: "gpt" });
    expect(body.safe).toBe("keep");
    expect(body.nested).toEqual({ ok: 1 });
  });
});
