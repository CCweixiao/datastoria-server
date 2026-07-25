"use client";

import { Button } from "@/components/ui/button";
import { beginSignIn, loadAuthProviders, type AuthProvider } from "@/lib/auth-client";
import { useEffect, useState } from "react";

export default function LoginPage() {
  const [providers, setProviders] = useState<AuthProvider[]>([]);
  const [error, setError] = useState<string>();

  useEffect(() => {
    loadAuthProviders()
      .then((result) => setProviders(Object.values(result)))
      .catch((reason: unknown) =>
        setError(reason instanceof Error ? reason.message : "Authentication is unavailable")
      );
  }, []);

  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-6">
      <section className="w-full max-w-sm space-y-6 rounded-lg border bg-card p-8 shadow-sm">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-semibold">Sign in to DataStoria</h1>
          <p className="text-sm text-muted-foreground">
            Authentication is handled securely by the Java server.
          </p>
        </div>
        <div className="space-y-3">
          {providers.map((provider) => (
            <Button
              className="w-full"
              key={provider.id}
              onClick={() => beginSignIn(provider)}
            >
              Continue with {provider.name}
            </Button>
          ))}
          {!error && providers.length === 0 ? (
            <p className="text-center text-sm text-muted-foreground">Loading providers…</p>
          ) : null}
          {error ? <p className="text-center text-sm text-destructive">{error}</p> : null}
        </div>
      </section>
    </main>
  );
}
