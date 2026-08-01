/**
 * @vitest-environment jsdom
 */
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it, vi } from "vitest";
import { ModelConfigBootstrap } from "./model-config-bootstrap";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT =
  true;

const { fetchAvailableModels, setSystemModels, setProviderSettings, setServerSelectedModel } =
  vi.hoisted(() => ({
    fetchAvailableModels: vi.fn().mockResolvedValue({
      systemModels: [{ provider: "OpenAI", modelId: "gpt-5", source: "system" }],
      githubModels: [],
      codexModels: [],
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
    [{ provider: "OpenAI", modelId: "gpt-5", source: "system" }],
    false
  );
  expect(setProviderSettings).toHaveBeenCalledWith([
    {
      provider: "OpenAI",
      providerId: "",
      credentialConfigured: true,
    },
  ]);
  expect(setServerSelectedModel).toHaveBeenCalledWith("model-config-1");
  expect(container.textContent).toBe("ready");

  act(() => root.unmount());
});
