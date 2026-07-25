import { afterEach, describe, expect, it } from "vitest";
import { getChatBackend, getChatJavaApiBase, isJavaChatBackend } from "./chat-backend";

describe("chat-backend selector", () => {
  const backup = { ...process.env };

  afterEach(() => {
    process.env = { ...backup };
  });

  it("defaults to node when the env var is unset", () => {
    delete process.env.NEXT_PUBLIC_DATASTORIA_CHAT_BACKEND;
    expect(getChatBackend()).toBe("node");
    expect(isJavaChatBackend()).toBe(false);
  });

  it("selects java only when the env var is exactly 'java'", () => {
    process.env.NEXT_PUBLIC_DATASTORIA_CHAT_BACKEND = "java";
    expect(getChatBackend()).toBe("java");
    expect(isJavaChatBackend()).toBe(true);

    process.env.NEXT_PUBLIC_DATASTORIA_CHAT_BACKEND = "node";
    expect(getChatBackend()).toBe("node");
    expect(isJavaChatBackend()).toBe(false);
  });

  it("returns the Java base URL with trailing slashes stripped", () => {
    process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL = "http://localhost:8080///";
    expect(getChatJavaApiBase()).toBe("http://localhost:8080");
  });

  it("throws when the Java base URL is unset (fails loud, not silent empty origin)", () => {
    delete process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL;
    expect(() => getChatJavaApiBase()).toThrow(
      /NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL is required/
    );
  });
});
