"use client";

import { loadAuthSession, type AuthSession } from "@/lib/auth-client";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AuthSessionProvider } from "./auth-session-provider";

export function AuthGate({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [session, setSession] = useState<AuthSession | null>(pathname === "/login" ? {} : null);

  useEffect(() => {
    if (pathname === "/login") {
      setSession({});
      return;
    }
    setSession(null);
    let active = true;
    loadAuthSession()
      .then((session) => {
        if (!active) return;
        if (!session.user) {
          router.replace(`/login?callbackUrl=${encodeURIComponent(pathname)}`);
          return;
        }
        setSession(session);
      })
      .catch(() => {
        if (active) {
          setSession(null);
          router.replace(`/login?callbackUrl=${encodeURIComponent(pathname)}`);
        }
      });
    return () => {
      active = false;
    };
  }, [pathname, router]);

  return session ? <AuthSessionProvider session={session}>{children}</AuthSessionProvider> : null;
}
