import { describe, expect, it } from "vitest";
import { buildSignInUrl, type AuthProvider } from "./auth-client";

const provider: AuthProvider = {
  id: "github",
  name: "GitHub",
  signinUrl: "/api/auth/signin/github",
};

describe("buildSignInUrl", () => {
  it("preserves a local callback for the Java OAuth round-trip", () => {
    expect(new URL(buildSignInUrl(provider, "/session/abc")).searchParams.get("callbackUrl")).toBe(
      "/session/abc"
    );
  });

  it("rejects an absolute callback URL", () => {
    expect(
      new URL(buildSignInUrl(provider, "https://evil.example")).searchParams.get("callbackUrl")
    ).toBe("/");
  });
});
