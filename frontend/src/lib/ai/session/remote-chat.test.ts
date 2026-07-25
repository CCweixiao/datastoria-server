import type { AppUIMessage } from "@/lib/ai/ai-types";
import { describe, expect, it, vi } from "vitest";
import { RemoteChat } from "./remote-chat";

function streamResponse(chunks: Record<string, unknown>[]): Response {
  return new Response(
    chunks.map((chunk) => `data: ${JSON.stringify(chunk)}\n\n`).join("") + "data: [DONE]\n\n",
    {
      status: 200,
      headers: { "Content-Type": "text/event-stream" },
    }
  );
}

function userMessage(): AppUIMessage {
  return { id: "user-1", role: "user", parts: [{ type: "text", text: "hello" }] };
}

describe("RemoteChat", () => {
  it("parses Java text, reasoning and metadata chunks without the AI SDK", async () => {
    const onFinish = vi.fn();
    const chat = new RemoteChat({
      id: "chat-1",
      messages: [],
      sendRequest: async () =>
        streamResponse([
          { type: "start", messageId: "assistant-1" },
          { type: "reasoning-start", id: "reasoning-1" },
          { type: "reasoning-delta", id: "reasoning-1", delta: "checking" },
          { type: "text-start", id: "text-1" },
          { type: "text-delta", id: "text-1", delta: "Hello" },
          { type: "text-delta", id: "text-1", delta: " world" },
          {
            type: "finish",
            messageMetadata: { usage: { inputTokens: 2, outputTokens: 2, totalTokens: 4 } },
          },
        ]),
      resumeRequest: async () => streamResponse([]),
      onFinish,
    });

    await chat.sendMessage(userMessage());

    expect(chat.getSnapshot()).toMatchObject({ status: "ready" });
    expect(chat.getSnapshot().messages[1]).toMatchObject({
      id: "assistant-1",
      role: "assistant",
      parts: [
        { type: "reasoning", text: "checking" },
        { type: "text", text: "Hello world" },
      ],
      metadata: { usage: { totalTokens: 4 } },
    });
    expect(onFinish).toHaveBeenCalledOnce();
  });

  it("maps tool lifecycle and pending actions to display-only message parts", async () => {
    const chat = new RemoteChat({
      id: "chat-2",
      messages: [],
      sendRequest: async () =>
        streamResponse([
          { type: "start", messageId: "assistant-2" },
          {
            type: "tool-input-available",
            toolCallId: "call-1",
            toolName: "execute_sql",
            input: { sql: "SELECT 1" },
          },
          { type: "tool-output-available", toolCallId: "call-1", output: { rows: 1 } },
          {
            type: "data-pending-action",
            id: "action-1",
            data: {
              runId: "run-1",
              actionId: "action-1",
              actionType: "approval",
              toolCallId: "call-1",
              toolName: "execute_sql",
              request: { sql: "SELECT 1" },
            },
          },
          { type: "finish", messageMetadata: {} },
        ]),
      resumeRequest: async () => streamResponse([]),
    });

    await chat.sendMessage(userMessage());

    expect(chat.getSnapshot().messages[1]?.parts).toEqual([
      expect.objectContaining({
        type: "dynamic-tool",
        toolName: "execute_sql",
        state: "output-available",
        input: { sql: "SELECT 1" },
        output: { rows: 1 },
      }),
      expect.objectContaining({ type: "data-pending-action", id: "action-1" }),
    ]);
  });
});
