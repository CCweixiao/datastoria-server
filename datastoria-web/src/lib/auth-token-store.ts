const STORAGE_KEY = "datastoria.auth.v1";

let cachedToken: string | null | undefined;

export function getAuthToken(): string | null {
  if (cachedToken !== undefined) return cachedToken;
  if (typeof window === "undefined") return null;
  cachedToken = window.sessionStorage.getItem(STORAGE_KEY);
  return cachedToken;
}

export function setAuthToken(token: string): void {
  cachedToken = token;
  if (typeof window !== "undefined") {
    window.sessionStorage.setItem(STORAGE_KEY, token);
  }
}

export function clearAuthToken(): void {
  cachedToken = null;
  if (typeof window !== "undefined") {
    window.sessionStorage.removeItem(STORAGE_KEY);
  }
}
