"use client";

import { AppShell } from "@/components/app-shell";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";

/** Reads the deep-link target (?sessionId=…&code=…) from the URL on the client. */
function SessionPageContent() {
  const searchParams = useSearchParams();
  const sessionId = searchParams.get("sessionId") ?? undefined;
  const shareCodeParam = searchParams.get("code");
  const initialSessionShareCode = shareCodeParam ?? undefined;

  return (
    <AppShell initialSessionId={sessionId} initialSessionShareCode={initialSessionShareCode} />
  );
}

export default function SessionPage() {
  return (
    <Suspense fallback={null}>
      <SessionPageContent />
    </Suspense>
  );
}
