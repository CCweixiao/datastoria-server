"use client";

import { loadAuthSession } from "@/lib/auth-client";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export function AuthGate({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [ready, setReady] = useState(pathname === "/login");

  useEffect(() => {
    if (pathname === "/login") {
      setReady(true);
      return;
    }
    let active = true;
    loadAuthSession()
      .then((session) => {
        if (!active) return;
        if (!session.user) {
          router.replace(`/login?callbackUrl=${encodeURIComponent(pathname)}`);
          return;
        }
        setReady(true);
      })
      .catch(() => {
        if (active) {
          router.replace(`/login?callbackUrl=${encodeURIComponent(pathname)}`);
        }
      });
    return () => {
      active = false;
    };
  }, [pathname, router]);

  return ready ? children : null;
}
