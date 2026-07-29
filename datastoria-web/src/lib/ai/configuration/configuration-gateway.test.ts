import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe("configuration gateway", () => {
  it("defaults to the Spring Boot API and ignores browser OAuth tokens", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ systemModels: [], githubModels: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getAiConfigurationGateway } = await import("./configuration-gateway");
    await getAiConfigurationGateway().listAvailableModels();

    expect(fetchMock).toHaveBeenCalledWith(
      "http://127.0.0.1:8080/api/ai/models/available",
      expect.objectContaining({
        body: "{}",
      })
    );
    expect(JSON.stringify(fetchMock.mock.calls)).not.toContain("legacy-token");
  });

  it("uses Java APIs without sending a browser apiKey", async () => {
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
    await getAiConfigurationGateway().listAvailableModels();

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
    vi.stubEnv("NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL", "http://localhost:8080");
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
      "http://localhost:8080/api/me/ai/model-preference",
      expect.objectContaining({
        body: JSON.stringify({ modelConfigId: "model-config-id" }),
      })
    );
  });

  it("creates a provider before sending its credential to the dedicated secret endpoint", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            id: "provider-zhipu",
            providerKey: "zhipu",
            displayName: "智谱 GLM",
            authType: "api_key",
            enabled: true,
            revision: 0,
            credentialConfigured: false,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      )
      .mockResolvedValueOnce(new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const { getAiConfigurationGateway } = await import("./configuration-gateway");
    await getAiConfigurationGateway().createProvider(
      {
        providerKey: "zhipu",
        displayName: "智谱 GLM",
        baseUrl: "https://open.bigmodel.cn/api/paas/v4",
      },
      "server-only-secret"
    );

    const createBody = String(fetchMock.mock.calls[0][1]?.body);
    expect(createBody).not.toContain("server-only-secret");
    expect(fetchMock.mock.calls[1][0]).toBe(
      "http://127.0.0.1:8080/api/admin/ai/providers/provider-zhipu/credential"
    );
    expect(String(fetchMock.mock.calls[1][1]?.body)).toContain("server-only-secret");
  });
});
