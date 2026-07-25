"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { backendApiFetch, backendApiUrl, readBackendError } from "@/lib/backend-api";
import { ExternalLink, Loader2 } from "lucide-react";
import { useCallback, useRef, useState } from "react";

const CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
const REDIRECT_URI = "http://localhost:1455/auth/callback";

function base64Url(bytes: ArrayBuffer): string {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function randomValue(size: number): string {
  const bytes = new Uint8Array(size);
  crypto.getRandomValues(bytes);
  return base64Url(bytes.buffer);
}

export function CodexOAuthConnect({ connected }: { connected: boolean }) {
  const [active, setActive] = useState(false);
  const [redirect, setRedirect] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const verifier = useRef("");
  const state = useRef("");

  const start = useCallback(async () => {
    setError("");
    setRedirect("");
    verifier.current = randomValue(32);
    state.current = randomValue(24);
    const digest = await crypto.subtle.digest(
      "SHA-256",
      new TextEncoder().encode(verifier.current)
    );
    const url = new URL("https://auth.openai.com/oauth/authorize");
    url.searchParams.set("response_type", "code");
    url.searchParams.set("client_id", CLIENT_ID);
    url.searchParams.set("redirect_uri", REDIRECT_URI);
    url.searchParams.set("scope", "openid profile email offline_access");
    url.searchParams.set("code_challenge", base64Url(digest));
    url.searchParams.set("code_challenge_method", "S256");
    url.searchParams.set("state", state.current);
    url.searchParams.set("id_token_add_organizations", "true");
    url.searchParams.set("codex_cli_simplified_flow", "true");
    url.searchParams.set("originator", "pi");
    setActive(true);
    window.open(url, "datastoria-codex-oauth", "popup=yes,width=520,height=760");
  }, []);

  const complete = useCallback(async () => {
    let code = redirect.trim();
    try {
      const parsed = new URL(code);
      const returnedState = parsed.searchParams.get("state");
      if (returnedState && returnedState !== state.current) {
        throw new Error("Codex returned an invalid OAuth state.");
      }
      code = parsed.searchParams.get("code") ?? "";
    } catch (reason) {
      if (reason instanceof Error && reason.message.includes("OAuth state")) {
        setError(reason.message);
        return;
      }
      // Plain authorization codes are also accepted.
    }
    if (!code || !verifier.current) {
      setError("Paste the redirect URL or authorization code.");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      const response = await backendApiFetch(backendApiUrl("/api/ai/codex/auth/token"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          code,
          code_verifier: verifier.current,
          redirect_uri: REDIRECT_URI,
        }),
      });
      if (!response.ok) {
        throw new Error((await readBackendError(response, "Codex authorization failed")).message);
      }
      window.location.reload();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Codex authorization failed");
    } finally {
      setSubmitting(false);
    }
  }, [redirect]);

  return (
    <div className="border-b px-3 py-2">
      <div className="flex items-center justify-between gap-4">
        <div>
          <div className="text-sm font-medium">OpenAI Codex OAuth</div>
          <div className="text-xs text-muted-foreground">
            {connected
              ? "Connected. OAuth tokens are encrypted and retained only by Java."
              : "Connect a ChatGPT/Codex subscription. Tokens are exchanged and stored by Java."}
          </div>
        </div>
        <Button size="sm" variant={connected ? "outline" : "default"} onClick={() => void start()}>
          {connected ? "Reconnect" : "Connect"} <ExternalLink className="ml-2 h-4 w-4" />
        </Button>
      </div>
      {active && (
        <div className="mt-3 flex items-center gap-2">
          <Input
            value={redirect}
            onChange={(event) => setRedirect(event.target.value)}
            placeholder="Paste the localhost redirect URL or authorization code"
          />
          <Button onClick={() => void complete()} disabled={submitting}>
            {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Complete
          </Button>
          <Button variant="ghost" onClick={() => setActive(false)}>
            Cancel
          </Button>
        </div>
      )}
      {error && <div className="mt-2 text-sm text-destructive">{error}</div>}
    </div>
  );
}
