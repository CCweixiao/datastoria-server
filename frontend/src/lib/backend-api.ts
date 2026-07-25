export function backendApiUrl(path: string): string {
  const base = (
    process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "http://127.0.0.1:8080"
  ).replace(/\/+$/, "");
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
