"use client";

import { Button } from "@/components/ui/button";
import { backendApiFetch, backendApiUrl, readBackendError } from "@/lib/backend-api";
import { Check, Copy, ExternalLink, Loader2 } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

type DeviceCode = {
  device_code: string;
  user_code: string;
  verification_uri: string;
  expires_in?: number;
  interval?: number;
};

export function GitHubOAuthConnect({ connected }: { connected: boolean }) {
  const [active, setActive] = useState(false);
  const [device, setDevice] = useState<DeviceCode | null>(null);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const cancelled = useRef(false);

  const start = useCallback(async () => {
    cancelled.current = false;
    setActive(true);
    setDevice(null);
    setError("");
    try {
      const response = await backendApiFetch(backendApiUrl("/api/ai/github/auth/device/code"), {
        method: "POST",
      });
      if (!response.ok) {
        throw new Error((await readBackendError(response, "Could not start GitHub login")).message);
      }
      const nextDevice = (await response.json()) as DeviceCode;
      setDevice(nextDevice);

      let interval = Math.max(nextDevice.interval ?? 5, 1) * 1_000;
      const expiresAt = Date.now() + (nextDevice.expires_in ?? 900) * 1_000;
      while (!cancelled.current && Date.now() < expiresAt) {
        await new Promise((resolve) => window.setTimeout(resolve, interval));
        if (cancelled.current) return;
        const tokenResponse = await backendApiFetch(
          backendApiUrl("/api/ai/github/auth/device/token"),
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ device_code: nextDevice.device_code }),
          }
        );
        if (tokenResponse.ok) {
          window.location.reload();
          return;
        }
        const problem = await readBackendError(tokenResponse, "GitHub authorization failed");
        if (problem.message === "authorization_pending") continue;
        if (problem.message === "slow_down") {
          interval += 5_000;
          continue;
        }
        throw new Error(problem.message);
      }
      if (!cancelled.current) throw new Error("The GitHub device code expired. Please try again.");
    } catch (reason) {
      if (!cancelled.current) {
        setError(reason instanceof Error ? reason.message : "GitHub authorization failed");
      }
    }
  }, []);

  useEffect(
    () => () => {
      cancelled.current = true;
    },
    []
  );

  if (!active) {
    return (
      <div className="flex items-center justify-between gap-4 border-b px-3 py-2">
        <div>
          <div className="text-sm font-medium">GitHub Copilot OAuth</div>
          <div className="text-xs text-muted-foreground">
            {connected
              ? "Connected. OAuth tokens are encrypted and retained only by Java."
              : "Connect to load your Copilot model catalog. No token is returned to the browser."}
          </div>
        </div>
        <Button size="sm" variant={connected ? "outline" : "default"} onClick={() => void start()}>
          {connected ? "Reconnect" : "Connect"}
        </Button>
      </div>
    );
  }

  return (
    <div className="border-b px-3 py-3">
      {device ? (
        <div className="flex flex-wrap items-center gap-3">
          <Button
            variant="outline"
            className="font-mono tracking-wider"
            onClick={() => {
              void navigator.clipboard.writeText(device.user_code);
              setCopied(true);
            }}
          >
            {device.user_code}
            {copied ? <Check className="ml-2 h-4 w-4" /> : <Copy className="ml-2 h-4 w-4" />}
          </Button>
          <Button asChild>
            <a href={device.verification_uri} target="_blank" rel="noreferrer">
              Open GitHub <ExternalLink className="ml-2 h-4 w-4" />
            </a>
          </Button>
          <span className="text-xs text-muted-foreground">Waiting for authorization…</span>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              cancelled.current = true;
              setActive(false);
            }}
          >
            Cancel
          </Button>
        </div>
      ) : (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Starting GitHub authorization…
        </div>
      )}
      {error && (
        <div className="mt-2 flex items-center gap-2 text-sm text-destructive">
          <span>{error}</span>
          <Button size="sm" variant="outline" onClick={() => void start()}>
            Retry
          </Button>
        </div>
      )}
    </div>
  );
}
