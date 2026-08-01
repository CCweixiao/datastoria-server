"use client";

import type { AuthSession, AuthUser } from "@/lib/auth-client";
import { createContext, useContext, type ReactNode } from "react";

const AuthSessionContext = createContext<AuthSession | null>(null);

export function AuthSessionProvider({
  session,
  children,
}: {
  session: AuthSession;
  children: ReactNode;
}) {
  return <AuthSessionContext.Provider value={session}>{children}</AuthSessionContext.Provider>;
}

export function useAuthSession(): AuthSession {
  const session = useContext(AuthSessionContext);
  if (session === null) {
    throw new Error("useAuthSession must be used within AuthSessionProvider");
  }
  return session;
}

export function PermissionGuard({
  roles,
  fallback = null,
  children,
}: {
  roles: AuthUser["role"][];
  fallback?: ReactNode;
  children: ReactNode;
}) {
  const { user } = useAuthSession();
  return user && roles.includes(user.role) ? children : fallback;
}
