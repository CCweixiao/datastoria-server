import { clearAuthToken, setAuthToken } from "@/lib/auth-token-store";
import { backendApiFetch, backendApiUrl, readBackendError } from "@/lib/backend-api";

type BackendUser = {
  userId: string;
  username: string;
  email?: string;
  role: "USER" | "ADMIN";
  tenantId: string;
  status: number;
};

type LoginResponse = {
  token: string;
  user: BackendUser;
};

export type AuthUser = {
  id: string;
  name: string;
  email?: string;
  role: "USER" | "ADMIN";
};

export type AuthSession = {
  user?: AuthUser;
};

function toAuthUser(user: BackendUser): AuthUser {
  return {
    id: user.userId,
    name: user.username,
    email: user.email || undefined,
    role: user.role,
  };
}

export async function login(username: string, password: string): Promise<AuthSession> {
  const response = await backendApiFetch(backendApiUrl("/api/auth/login"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) {
    throw new Error((await readBackendError(response)).message);
  }
  const result = (await response.json()) as LoginResponse;
  setAuthToken(result.token);
  return { user: toAuthUser(result.user) };
}

export async function loadAuthSession(): Promise<AuthSession> {
  const response = await backendApiFetch(backendApiUrl("/api/auth/me"));
  if (response.status === 401) {
    clearAuthToken();
    return {};
  }
  if (!response.ok) {
    throw new Error((await readBackendError(response)).message);
  }
  return { user: toAuthUser((await response.json()) as BackendUser) };
}

export function signOut(callbackUrl = "/login"): void {
  clearAuthToken();
  window.location.assign(callbackUrl);
}
