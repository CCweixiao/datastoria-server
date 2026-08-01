import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { AuthSessionProvider, PermissionGuard } from "./auth-session-provider";

describe("PermissionGuard", () => {
  it("renders administrator-only content for administrators", () => {
    const html = renderToStaticMarkup(
      <AuthSessionProvider session={{ user: { id: "1", name: "admin", role: "ADMIN" } }}>
        <PermissionGuard roles={["ADMIN"]}>User management</PermissionGuard>
      </AuthSessionProvider>
    );

    expect(html).toContain("User management");
  });

  it("hides administrator-only content from ordinary users", () => {
    const html = renderToStaticMarkup(
      <AuthSessionProvider session={{ user: { id: "2", name: "user", role: "USER" } }}>
        <PermissionGuard roles={["ADMIN"]} fallback="Forbidden">
          User management
        </PermissionGuard>
      </AuthSessionProvider>
    );

    expect(html).toBe("Forbidden");
  });
});
