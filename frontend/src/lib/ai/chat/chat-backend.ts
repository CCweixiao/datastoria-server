/**
 * P4.7 chat backend selector — the chat-stream counterpart of the P3 session backend selector
 * (`lib/ai/session/session-api-base.ts`).
 *
 * Set {@code NEXT_PUBLIC_DATASTORIA_CHAT_BACKEND=java} at build time to make the Node
 * `POST /api/ai/agent` route proxy the request to the Java A01 endpoint at
 * {@code NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL}. Default {@code "node"} keeps the existing
 * Next.js route handler byte-for-byte unchanged.
 *
 * The Java endpoint speaks the same AI SDK UI Message Stream wire contract, so the gateway only
 * forwards the request and streams the SSE response back as-is (no re-encoding).
 */
export type ChatBackend = "node" | "java";

/** Returns the configured chat backend; defaults to {@code "node"}. */
export function getChatBackend(): ChatBackend {
  return process.env.NEXT_PUBLIC_DATASTORIA_CHAT_BACKEND === "java" ? "java" : "node";
}

/** True iff chat requests should be proxied to the Java backend. */
export function isJavaChatBackend(): boolean {
  return getChatBackend() === "java";
}

/**
 * Returns the Java backend origin (no trailing slash) for chat proxying. Throws if the chat
 * backend is {@code java} but the base URL is unset, so misconfiguration fails loudly at request
 * time rather than silently hitting an empty origin.
 */
export function getChatJavaApiBase(): string {
  const base = (process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "").replace(/\/+$/, "");
  if (!base) {
    throw new Error(
      "NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL is required when the chat backend is java"
    );
  }
  return base;
}
