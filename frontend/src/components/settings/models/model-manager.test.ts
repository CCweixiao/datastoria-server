import { beforeEach, describe, expect, it, vi } from "vitest";
import { ModelManager } from "./model-manager";

describe("ModelManager.getAvailableModels", () => {
  beforeEach(() => {
    vi.stubGlobal("window", {
      dispatchEvent: vi.fn(),
    });

    (ModelManager as unknown as { instance?: ModelManager }).instance = undefined;
  });

  it("does not offer a server model until its provider credential is configured", () => {
    const manager = ModelManager.getInstance();
    manager.setSystemModels(
      [
        {
          provider: "TestProvider",
          modelId: "test-model",
          source: "system",
        },
      ],
      false
    );

    const models = manager.getAvailableModels();

    expect(models).toEqual([]);
  });

  it("never changes server model provenance based on a browser credential field", () => {
    const manager = ModelManager.getInstance();
    manager.setSystemModels(
      [
        {
          provider: "TestProvider",
          modelId: "test-model",
          source: "system",
        },
      ],
      false
    );
    manager.updateProviderSetting("TestProvider", { credentialConfigured: true });

    const models = manager.getAvailableModels();

    expect(models).toContainEqual(
      expect.objectContaining({
        provider: "TestProvider",
        modelId: "test-model",
        source: "system",
      })
    );
  });
});
