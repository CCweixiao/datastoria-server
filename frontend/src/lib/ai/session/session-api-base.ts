/** Returns the Spring Boot origin used by all session APIs. */
export function getSessionApiBase(): string {
  return (process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "http://127.0.0.1:8080").replace(
    /\/+$/,
    ""
  );
}

export function isJavaSessionBackend(): boolean {
  return true;
}

export function sessionIdentityHeaders(extra?: HeadersInit): HeadersInit {
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
