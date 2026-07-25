import { getAuthenticatedUserEmail } from "@/auth";
import { getChatJavaApiBase } from "./chat-backend";

/**
 * P4.7 gateway: proxies a Node {@code POST /api/ai/agent} request to the Java A01 endpoint and
 * streams the AI SDK UI Message Stream back verbatim.
 *
 * Non-goals: no body reshaping beyond stripping client secrets, no SSE parsing/re-encoding, no
 * provider calls, no run resume. The Java endpoint resolves model config + credentials
 * server-side; this proxy only forwards identity + a stable idempotency key.
 *
 * <b>Security:</b> client credentials (apiKey / password / token / authorization) are stripped
 * from the forwarded body and never sent to Java. Only the authenticated user email
 * ({@code x-datastoria-user-email}) is forwarded as identity.
 *
 * <b>Streaming:</b> the upstream SSE body is piped through untouched; an {@link AbortController}
 * aborts the upstream fetch when the downstream client cancels (disconnect), so the Java backend
 * cancels the run and stops provider token emission. Java non-2xx responses are passed through
 * with their status and body (the Java error contract is already frontend-compatible; no internal
 * exception text is synthesized here).
 */

const FORWARDED_RESPONSE_HEADERS = [
  "content-type",
  "cache-control",
  "connection",
  "x-vercel-ai-ui-message-stream",
  "x-accel-buffering",
];

export async function proxyChatToJava(req: Request): Promise<Response> {
  let base: string;
  try {
    base = getChatJavaApiBase();
  } catch {
    return new Response("Java chat backend URL not configured", {
      status: 502,
      headers: { "content-type": "text/plain" },
    });
  }

  const raw = await req.text();
  let body: unknown;
  try {
    body = JSON.parse(raw);
  } catch {
    return new Response("Invalid JSON in request body", {
      status: 400,
      headers: { "content-type": "text/plain" },
    });
  }
  stripClientSecrets(body);

  const idempotencyKey =
    req.headers.get("idempotency-key") || resolveIdempotencyKey(body) || randomKey();

  const headers: Record<string, string> = {
    "content-type": "application/json",
    "idempotency-key": idempotencyKey,
  };
  const email = getAuthenticatedUserEmail(req);
  if (email) {
    headers["x-datastoria-user-email"] = email;
  }

  const abortController = new AbortController();
  let upstream: Response;
  try {
    upstream = await fetch(`${base}/api/ai/agent`, {
      method: "POST",
      headers,
      body: JSON.stringify(body),
      signal: abortController.signal,
    });
  } catch {
    return new Response("Java chat backend unreachable", {
      status: 502,
      headers: { "content-type": "text/plain" },
    });
  }

  const responseHeaders = new Headers();
  for (const name of FORWARDED_RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) {
      responseHeaders.set(name, value);
    }
  }
  if (!responseHeaders.has("content-type")) {
    responseHeaders.set(
      "content-type",
      upstream.headers.get("content-type") ?? "application/octet-stream"
    );
  }

  // Pipe the upstream SSE stream verbatim. When the browser cancels the downstream response
  // (disconnect), the ReadableStream cancel hook aborts the upstream fetch so the Java backend
  // cancels the run and stops provider token emission.
  const upstreamBody = upstream.body;
  const responseBody = new ReadableStream<Uint8Array>({
    async start(streamController) {
      if (!upstreamBody) {
        streamController.close();
        return;
      }
      const reader = upstreamBody.getReader();
      try {
        let result = await reader.read();
        while (!result.done) {
          streamController.enqueue(result.value);
          result = await reader.read();
        }
        streamController.close();
      } catch (error) {
        streamController.error(error);
      }
    },
    cancel() {
      abortController.abort();
    },
  });
  return new Response(responseBody, { status: upstream.status, headers: responseHeaders });
}

/** Recursively deletes any client-credential-looking key before forwarding. */
export function stripClientSecrets(node: unknown): void {
  if (Array.isArray(node)) {
    for (const child of node) {
      stripClientSecrets(child);
    }
    return;
  }
  if (node && typeof node === "object") {
    const obj = node as Record<string, unknown>;
    for (const key of Object.keys(obj)) {
      if (isSensitiveKey(key)) {
        delete obj[key];
      } else {
        stripClientSecrets(obj[key]);
      }
    }
  }
}

function isSensitiveKey(key: string): boolean {
  const normalized = key.toLowerCase().replace(/[-_]/g, "");
  return (
    normalized === "apikey" ||
    normalized === "password" ||
    normalized === "token" ||
    normalized === "accesstoken" ||
    normalized === "refreshtoken" ||
    normalized === "authorization" ||
    normalized === "secret" ||
    normalized === "clientsecret"
  );
}

/**
 * Derives a stable idempotency key from the request so a retried request reuses the same key. The
 * client-generated {@code message.id} is stable across retries, so {@code sessionId + message.id}
 * identifies the logical request; without it, fall back to {@code sessionId + user text}.
 */
export function resolveIdempotencyKey(body: unknown): string | null {
  if (!body || typeof body !== "object") {
    return null;
  }
  const obj = body as Record<string, unknown>;
  const sessionId = typeof obj.sessionId === "string" ? obj.sessionId : "";
  const message = obj.message as { id?: unknown; parts?: unknown } | undefined;
  const messageId = message && typeof message.id === "string" ? message.id : "";
  if (messageId) {
    return `ds-${stableHash(sessionId + ":" + messageId)}`;
  }
  const text = extractUserText(message);
  if (sessionId || text) {
    return `ds-${stableHash(sessionId + "|" + text)}`;
  }
  return null;
}

function extractUserText(message: { parts?: unknown } | undefined): string {
  if (!message || !Array.isArray(message.parts)) {
    return "";
  }
  return message.parts
    .map((part) =>
      part && typeof part === "object" && (part as { type?: string }).type === "text"
        ? String((part as { text?: unknown }).text ?? "")
        : ""
    )
    .join("")
    .trim();
}

/** Deterministic non-crypto hash (djb2) — stable for the same input, sufficient for a key. */
function stableHash(input: string): string {
  let hash = 5381;
  for (let i = 0; i < input.length; i++) {
    hash = ((hash << 5) + hash + input.charCodeAt(i)) | 0;
  }
  return (hash >>> 0).toString(16);
}

function randomKey(): string {
  return `ds-rand-${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`;
}
