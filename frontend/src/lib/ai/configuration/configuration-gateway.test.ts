import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe("configuration gateway", () => {
  it("keeps the existing Node route as the default rollback path", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ systemModels: [], githubModels: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getAiConfigurationGateway } = await import("./configuration-gateway");
    await getAiConfigurationGateway().listAvailableModels({ githubToken: "legacy-token" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/ai/models/available",
      expect.objectContaining({
        body: JSON.stringify({ github: { token: "legacy-token" } }),
      })
    );
  });

  it("uses Java APIs without sending a browser apiKey", async () => {
    vi.stubEnv("NEXT_PUBLIC_DATASTORIA_CONFIG_BACKEND", "java");
    vi.stubEnv("NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL", "http://localhost:8080");
    vi.stubEnv("NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL", "dev@example.com");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ systemModels: [], githubModels: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getAiConfigurationGateway } = await import("./configuration-gateway");
    await getAiConfigurationGateway().listAvailableModels({ githubToken: "must-not-leak" });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/ai/models/available",
      expect.objectContaining({
        body: "{}",
        headers: expect.objectContaining({
          "x-datastoria-user-email": "dev@example.com",
        }),
      })
    );
    expect(JSON.stringify(fetchMock.mock.calls)).not.toContain("must-not-leak");
  });

  it("persists selected model by backend config id", async () => {
    vi.stubEnv("NEXT_PUBLIC_DATASTORIA_CONFIG_BACKEND", "java");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("{}", {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getAiConfigurationGateway } = await import("./configuration-gateway");
    await getAiConfigurationGateway().setModelPreference({
      provider: "OpenAI",
      modelId: "gpt-test",
      configId: "model-config-id",
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/me/ai/model-preference",
      expect.objectContaining({
        body: JSON.stringify({ modelConfigId: "model-config-id" }),
      })
    );
  });
});
