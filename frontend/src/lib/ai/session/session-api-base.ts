import { BasePath } from "@/lib/base-path";

/**
 * P3 session/message/feedback/share backend selector.
 *
 * The Java backend exposes the same wire contract as the Node route handlers (same paths,
 * same JSON shapes, same status codes), so callers only need to swap the URL origin. This
 * mirrors the P2 configuration gateway (`configuration-gateway.ts`) but is intentionally a
 * single URL helper rather than a per-method interface — the wire compatibility means there
 * is no request/response transformation work to do.
 *
 * Set {@code NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=java} at build time to redirect all P3
 * calls to {@code NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL}. Default {@code "node"} keeps
 * the existing Next.js route handlers.
 *
 * Dev identity: when running with the Java backend, callers should also pass the dev user
 * email via {@code NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL}; see {@link identityHeaders}.
 */

export type SessionBackend = "node" | "java";

function backend(): SessionBackend {
  return process.env.NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND === "java" ? "java" : "node";
}

/**
 * Returns the URL prefix for P3 API calls. For Node this is the configured Next.js base path
 * (so subsequent {@code fetch(`${getSessionApiBase()}/api/ai/chat/sessions`)} resolves to the
 * internal Next.js route handler). For Java this is the configured base URL with any trailing
 * slash stripped.
 *
 * Path arguments passed to {@code fetch} MUST still begin with a leading {@code /}; the helper
 * does not insert one.
 */
export function getSessionApiBase(): string {
  if (backend() === "java") {
    const base = (process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "").replace(/\/+$/, "");
    if (!base) {
      throw new Error(
        "NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL is required when the session backend is java"
      );
    }
    return base;
  }
  // Node mode: BasePath.getBasePath() returns "" when there's no base path configured, so the
  // caller's leading "/" produces the correct relative URL. BasePath.getURL("") would normalize
  // to "/" and produce a double-slash.
  return BasePath.getBasePath();
}

/** True iff P3 calls should be directed to the Java backend. */
export function isJavaSessionBackend(): boolean {
  return backend() === "java";
}

/**
 * Returns the dev identity header to attach when calling the Java backend in development.
 * Returns an empty object in Node mode (Node resolves identity via its own session middleware).
 */
export function sessionIdentityHeaders(extra?: HeadersInit): HeadersInit {
  if (backend() !== "java") {
    return extra ?? {};
  }
  const email = process.env.NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL;
  const merged: Record<string, string> = {};
  if (email) {
    merged["x-datastoria-user-email"] = email;
  }
  if (extra) {
    for (const [k, v] of Object.entries(extra)) {
      merged[k] = String(v);
    }
  }
  return merged;
}
