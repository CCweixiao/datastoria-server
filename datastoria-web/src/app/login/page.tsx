"use client";

import {
  AgreementDialog,
  PRIVACY_POLICY,
  PRIVACY_POLICY_ZH_CN,
  TERMS_OF_SERVICE,
  TERMS_OF_SERVICE_ZH_CN,
} from "@/app/login/agreement-dialog";
import { AppLogo } from "@/components/app-logo";
import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { login } from "@/lib/auth-client";
import { Loader2 } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";

const GITHUB_URL = "https://github.com/FrankChen021/datastoria";
const DOCS_URL = "https://docs.datastoria.app";

function LoginPageContent() {
  const { locale, t } = useUiPreferences();
  const searchParams = useSearchParams();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [loginError, setLoginError] = useState<string>();
  const [agreement, setAgreement] = useState<string>();
  const requestedCallback = searchParams.get("callbackUrl") ?? "/";
  const callbackUrl =
    requestedCallback.startsWith("/") && !requestedCallback.startsWith("//")
      ? requestedCallback
      : "/";

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setLoginError(undefined);
    try {
      await login(username.trim(), password);
      window.location.assign(callbackUrl);
    } catch (reason) {
      setLoginError(
        reason instanceof Error ? reason.message : t("login.authenticationUnavailable")
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card className="w-full max-w-xl">
        <CardHeader className="space-y-0 pb-8 text-center">
          <div className="flex items-center justify-center gap-2">
            <AppLogo width={64} height={64} />
            <CardTitle className="text-2xl">DataStoria</CardTitle>
          </div>
          <CardDescription className="text-sm">{t("login.tagline")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          {loginError ? (
            <Alert variant="destructive">
              <AlertDescription>{loginError}</AlertDescription>
            </Alert>
          ) : null}

          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-2">
              <Label htmlFor="username">{t("login.username")}</Label>
              <Input
                id="username"
                name="username"
                autoComplete="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                disabled={submitting}
                required
                maxLength={64}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">{t("login.password")}</Label>
              <Input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                disabled={submitting}
                required
                minLength={8}
                maxLength={256}
              />
            </div>
            <Button className="w-full" type="submit" disabled={submitting}>
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
              {submitting ? t("login.signingIn") : t("login.signIn")}
            </Button>
          </form>

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
                {t("login.docs")}
              </a>
            </p>
            <p>
              {t("login.consentPrefix")} {" "}
              <button
                type="button"
                className="underline underline-offset-4 transition-colors hover:text-primary"
                onClick={() =>
                  setAgreement(locale === "zh-CN" ? TERMS_OF_SERVICE_ZH_CN : TERMS_OF_SERVICE)
                }
              >
                {t("login.terms")}
              </button>{" "}
              {t("login.and")} {" "}
              <button
                type="button"
                className="underline underline-offset-4 transition-colors hover:text-primary"
                onClick={() =>
                  setAgreement(locale === "zh-CN" ? PRIVACY_POLICY_ZH_CN : PRIVACY_POLICY)
                }
              >
                {t("login.privacy")}
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
          <LoginFallback />
        </main>
      }
    >
      <LoginPageContent />
    </Suspense>
  );
}

function LoginFallback() {
  const { t } = useUiPreferences();
  return <p className="text-sm text-muted-foreground">{t("login.loadingOptions")}</p>;
}
