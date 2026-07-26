"use client";

import { AgreementDialog, PRIVACY_POLICY, TERMS_OF_SERVICE } from "@/app/login/agreement-dialog";
import { AppLogo } from "@/components/app-logo";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { beginSignIn, loadAuthProviders, type AuthProvider } from "@/lib/auth-client";
import { Github } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

const GITHUB_URL = "https://github.com/FrankChen021/datastoria";
const DOCS_URL = "https://docs.datastoria.app";

function ProviderIcon({ id }: { id: string }) {
  if (id === "github") return <Github className="h-4 w-4" aria-hidden />;
  if (id === "google") {
    return (
      <svg className="h-4 w-4" aria-hidden viewBox="0 0 488 512">
        <path
          fill="currentColor"
          d="M488 261.8C488 403.3 391.1 504 248 504 110.8 504 0 393.2 0 256S110.8 8 248 8c66.8 0 123 24.5 166.3 64.9l-67.5 64.9C258.5 52.6 94.3 116.6 94.3 256c0 86.5 69.1 156.6 153.7 156.6 98.2 0 135-70.4 140.8-106.9H248v-85.3h236.1c2.3 12.7 3.9 24.9 3.9 41.4z"
        />
      </svg>
    );
  }
  if (id === "microsoft-entra-id" || id === "microsoft") {
    return (
      <svg className="h-4 w-4" aria-hidden viewBox="0 0 448 512">
        <path
          fill="currentColor"
          d="M0 32h214.6v214.6H0V32zm233.4 0H448v214.6H233.4V32zM0 265.4h214.6V480H0V265.4zm233.4 0H448V480H233.4V265.4z"
        />
      </svg>
    );
  }
  return <span className="h-4 w-4 rounded-full border" aria-hidden />;
}

function LoginPageContent() {
  const searchParams = useSearchParams();
  const [providers, setProviders] = useState<AuthProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [agreement, setAgreement] = useState<string>();
  const callbackUrl = searchParams.get("callbackUrl") ?? "/";
  const authError = searchParams.get("error");

  useEffect(() => {
    loadAuthProviders()
      .then((result) => setProviders(Object.values(result)))
      .catch((reason: unknown) =>
        setLoadError(reason instanceof Error ? reason.message : "Authentication is unavailable")
      )
      .finally(() => setLoading(false));
  }, []);

  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card className="w-full max-w-xl">
        <CardHeader className="space-y-0 pb-8 text-center">
          <div className="flex items-center justify-center gap-2">
            <AppLogo width={64} height={64} />
            <CardTitle className="text-2xl">DataStoria</CardTitle>
          </div>
          <CardDescription className="text-sm">
            The AI-native ClickHouse console for cluster diagnostics, query generation,
            evidence-based optimization, and intelligent visualization.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          {authError ? (
            <Alert variant="destructive">
              <AlertDescription>
                {authError === "OAuthCallback"
                  ? "Authentication failed. Please try again."
                  : "An error occurred during authentication."}
              </AlertDescription>
            </Alert>
          ) : null}

          {loadError ? (
            <Alert variant="destructive">
              <AlertDescription>{loadError}</AlertDescription>
            </Alert>
          ) : providers.length > 0 ? (
            <div className="grid gap-2">
              {providers.map((provider) => (
                <Button
                  variant="outline"
                  className="w-full"
                  key={provider.id}
                  onClick={() => beginSignIn(provider, callbackUrl)}
                >
                  <span className="inline-grid min-w-48 grid-cols-[1.25rem_1fr] items-center gap-2">
                    <ProviderIcon id={provider.id} />
                    <span className="text-left">Sign in with {provider.name}</span>
                  </span>
                </Button>
              ))}
            </div>
          ) : loading ? (
            <p className="text-center text-sm text-muted-foreground">Loading providers…</p>
          ) : (
            <Alert variant="warning">
              <AlertDescription>
                Authentication is not configured. Configure at least one OAuth provider on the Java
                server.
              </AlertDescription>
            </Alert>
          )}

          <div className="space-y-3 border-t pt-4 text-center text-xs text-muted-foreground">
            <p>
              <a
                href={GITHUB_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="underline underline-offset-4 transition-colors hover:text-primary"
              >
                GitHub
              </a>
              {" · "}
              <a
                href={DOCS_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="underline underline-offset-4 transition-colors hover:text-primary"
              >
                Docs
              </a>
            </p>
            <p>
              By signing in, you agree to the{" "}
              <button
                type="button"
                className="underline underline-offset-4 transition-colors hover:text-primary"
                onClick={() => setAgreement(TERMS_OF_SERVICE)}
              >
                Terms of Service
              </button>{" "}
              and{" "}
              <button
                type="button"
                className="underline underline-offset-4 transition-colors hover:text-primary"
                onClick={() => setAgreement(PRIVACY_POLICY)}
              >
                Privacy Policy
              </button>
              .
            </p>
          </div>
        </CardContent>
      </Card>
      <AgreementDialog
        isOpen={agreement != null}
        onOpenChange={(open) => {
          if (!open) setAgreement(undefined);
        }}
        content={agreement ?? ""}
      />
    </main>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center bg-background">
          <p className="text-sm text-muted-foreground">Loading sign-in options…</p>
        </main>
      }
    >
      <LoginPageContent />
    </Suspense>
  );
}
