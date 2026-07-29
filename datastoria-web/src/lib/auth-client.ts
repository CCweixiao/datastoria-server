import { backendApiFetch, backendApiUrl } from "@/lib/backend-api";

const SIGNIN_PATH_PREFIX = "/api/auth/signin/";

export type AuthProvider = {
  id: string;
  name: string;
  signinUrl: string;
};

export type AuthSession = {
  user?: {
    id: string;
    name?: string;
    email?: string;
    image?: string;
  };
  expires?: string;
};

export async function loadAuthProviders(): Promise<Record<string, AuthProvider>> {
  const response = await backendApiFetch(backendApiUrl("/api/auth/providers"));
  if (!response.ok) {
    throw new Error(`Failed to load authentication providers: ${response.status}`);
  }
  return (await response.json()) as Record<string, AuthProvider>;
}

export async function loadAuthSession(): Promise<AuthSession> {
  const response = await backendApiFetch(backendApiUrl("/api/auth/session"));
  if (response.status === 401) {
    return {};
  }
  if (!response.ok) {
    throw new Error(`Failed to load authentication session: ${response.status}`);
  }
  return (await response.json()) as AuthSession;
}

export function beginSignIn(provider: AuthProvider, callbackUrl = "/"): void {
  window.location.assign(buildSignInUrl(provider, callbackUrl));
}

export function buildSignInUrl(provider: AuthProvider, callbackUrl = "/"): string {
  if (!provider.signinUrl.startsWith(SIGNIN_PATH_PREFIX)) {
    throw new Error("Authentication provider returned an invalid sign-in URL");
  }
  const url = new URL(backendApiUrl(provider.signinUrl));
  const safeCallback =
    callbackUrl.startsWith("/") && !callbackUrl.startsWith("//") ? callbackUrl : "/";
  url.searchParams.set("callbackUrl", safeCallback);
  return url.toString();
}

export async function signOut(callbackUrl = "/login"): Promise<void> {
  const response = await backendApiFetch(backendApiUrl("/api/auth/signout"), {
    method: "POST",
  });
  if (!response.ok) {
    throw new Error(`Failed to sign out: ${response.status}`);
  }
  window.location.assign(callbackUrl);
}
