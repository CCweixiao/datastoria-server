/**
 * @vitest-environment jsdom
 */
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it, vi } from "vitest";
import { ModelConfigBootstrap } from "./model-config-bootstrap";

const { fetchAvailableModels, setSystemModels, setProviderSettings, setServerSelectedModel } =
  vi.hoisted(() => ({
    fetchAvailableModels: vi.fn().mockResolvedValue({
      systemModels: [{ provider: "OpenAI", modelId: "gpt-5", source: "system" }],
      githubModels: [{ provider: "GitHub Copilot", modelId: "copilot-model", source: "user" }],
    }),
    setSystemModels: vi.fn(),
    setProviderSettings: vi.fn(),
    setServerSelectedModel: vi.fn(),
  }));

vi.mock("@/lib/ai/llm/available-models-client", () => ({ fetchAvailableModels }));
vi.mock("@/components/settings/agent/agent-manager", () => ({
  AgentConfigurationManager: { hydrate: vi.fn().mockResolvedValue(undefined) },
}));
vi.mock("@/lib/ai/configuration/configuration-gateway", () => ({
  getAiConfigurationGateway: () => ({
    listProviders: vi.fn().mockResolvedValue([
      {
        id: "provider-1",
        providerKey: "OpenAI",
        displayName: "OpenAI",
        credentialConfigured: true,
        maskedHint: "sk-…test",
      },
    ]),
    getModelPreference: vi.fn().mockResolvedValue("model-config-1"),
  }),
}));
vi.mock("@/components/settings/models/model-manager", () => ({
  ModelManager: {
    getInstance: () => ({
      setSystemModels,
      setProviderSettings,
      setServerSelectedModel,
    }),
  },
}));

it("loads only the Spring model catalog and server-side preference", async () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  await act(async () => {
    root.render(
      <ModelConfigBootstrap>
        <div>ready</div>
      </ModelConfigBootstrap>
    );
  });

  expect(fetchAvailableModels).toHaveBeenCalledWith();
  expect(setSystemModels).toHaveBeenCalledWith(
    [
      { provider: "OpenAI", modelId: "gpt-5", source: "system" },
      { provider: "GitHub Copilot", modelId: "copilot-model", source: "user" },
    ],
    false
  );
  expect(setProviderSettings).toHaveBeenCalledWith([
    {
      provider: "OpenAI",
      providerId: "provider-1",
      credentialConfigured: true,
      maskedHint: "sk-…test",
    },
    {
      provider: "GitHub Copilot",
      providerId: "oauth:github",
      credentialConfigured: true,
      maskedHint: "OAuth connected",
    },
  ]);
  expect(setServerSelectedModel).toHaveBeenCalledWith("model-config-1");
  expect(container.textContent).toBe("ready");

  act(() => root.unmount());
});
