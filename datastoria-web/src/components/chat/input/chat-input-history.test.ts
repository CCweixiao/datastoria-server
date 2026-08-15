// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  appendChatInputHistory,
  CHAT_INPUT_HISTORY_TTL_MS,
  navigateChatInputHistory,
  resetChatInputHistory,
} from "./chat-input-history";

describe("chat input history", () => {
  beforeEach(() => localStorage.clear());

  it("isolates histories by user and conversation and resets from remote messages", () => {
    resetChatInputHistory("alice", "chat-1", ["first"], 100);
    appendChatInputHistory("alice", "chat-1", "second", 101);
    resetChatInputHistory("bob", "chat-1", ["bob message"], 102);

    expect(resetChatInputHistory("alice", "chat-1", ["remote"], 103)).toEqual(["remote"]);
    expect(appendChatInputHistory("bob", "chat-1", "new", 104)).toEqual(["bob message", "new"]);
    expect(resetChatInputHistory("alice", "chat-2", [], 105)).toEqual([]);
  });

  it("drops conversations that exceeded the inactivity TTL", () => {
    resetChatInputHistory("alice", "stale-chat", ["stale"], 1);

    expect(
      appendChatInputHistory("alice", "active-chat", "active", CHAT_INPUT_HISTORY_TTL_MS + 2)
    ).toEqual(["active"]);

    const stored = JSON.parse(
      localStorage.getItem("datastoria:chat-input-history:v1:alice") ?? "{}"
    );
    expect(stored.conversations).not.toHaveProperty("stale-chat");
  });

  it("moves backward and forward and restores the draft", () => {
    const first = navigateChatInputHistory(
      ["one", "two"],
      { index: null, draft: "" },
      "draft",
      "previous"
    );
    expect(first).toEqual({ cursor: { index: 1, draft: "draft" }, value: "two" });

    const second = navigateChatInputHistory(
      ["one", "two"],
      first!.cursor,
      first!.value,
      "previous"
    );
    expect(second?.value).toBe("one");

    const newest = navigateChatInputHistory(["one", "two"], first!.cursor, first!.value, "next");
    expect(newest).toEqual({ cursor: { index: null, draft: "draft" }, value: "draft" });
  });

  it("does not throw when localStorage is unavailable", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementationOnce(() => {
      throw new DOMException("quota");
    });
    expect(() => resetChatInputHistory("alice", "chat", ["message"])).not.toThrow();
  });
});
