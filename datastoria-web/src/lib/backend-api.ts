export function backendApiUrl(path: string): string {
  const base = (process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "/backend").replace(
    /\/+$/,
    ""
  );
  if (base.startsWith("/") && typeof window === "undefined") {
    const internalBase = (
      process.env.DATASTORIA_JAVA_INTERNAL_URL ?? "http://127.0.0.1:8080"
    ).replace(/\/+$/, "");
    return `${internalBase}${path}`;
  }
  return `${base}${path}`;
}

export function backendApiHeaders(extra?: HeadersInit): HeadersInit {
  const email = process.env.NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL;
  return {
    ...(email ? { "x-datastoria-user-email": email } : {}),
    ...(extra ?? {}),
  };
}

/** Sends a browser request to Java with the server-owned login session cookie. */
export function backendApiFetch(
  input: string | URL | Request,
  init?: RequestInit
): Promise<Response> {
  return fetch(input, {
    ...init,
    credentials: "include",
    headers: backendApiHeaders(init?.headers),
  });
}

type BackendProblemDetail = {
  detail?: unknown;
  title?: unknown;
  message?: unknown;
};

/** Extracts a user-facing message from Spring ProblemDetail or a plain-text response. */
export async function readBackendError(
  response: Response,
  fallback = `Request failed: ${response.status}`
): Promise<{ message: string; body: string }> {
  const body = await response.text();
  if (!body.trim()) {
    return { message: fallback, body };
  }

  try {
    const problem = JSON.parse(body) as BackendProblemDetail;
    for (const candidate of [problem.detail, problem.message, problem.title]) {
      if (typeof candidate === "string" && candidate.trim()) {
        return { message: candidate.trim(), body };
      }
    }
  } catch {
    // Non-JSON responses are already suitable for display.
  }

  return { message: body.trim(), body };
}
