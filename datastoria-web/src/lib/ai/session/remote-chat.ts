"use client";

import type { AppUIMessage, MessageMetadata, UIMessagePart } from "@/lib/ai/ai-types";
import { readBackendError } from "@/lib/backend-api";
import { useSyncExternalStore } from "react";

export type RemoteChatStatus = "ready" | "submitted" | "streaming" | "error";

type RemoteChatState = {
  messages: AppUIMessage[];
  status: RemoteChatStatus;
  error?: Error;
};

type RemoteChatOptions = {
  id: string;
  messages: AppUIMessage[];
  sendRequest: (messages: AppUIMessage[], signal: AbortSignal) => Promise<Response>;
  resumeRequest: (headers: Headers | undefined, signal: AbortSignal) => Promise<Response>;
  onFinish?: (message: AppUIMessage) => Promise<void> | void;
};

type StreamChunk = Record<string, unknown> & { type?: string };

function replacePart(
  message: AppUIMessage,
  predicate: (part: UIMessagePart) => boolean,
  update: (part: UIMessagePart | undefined) => UIMessagePart
): void {
  const index = message.parts.findIndex(predicate);
  if (index < 0) {
    message.parts.push(update(undefined));
  } else {
    message.parts[index] = update(message.parts[index]);
  }
}

function toolCallId(part: UIMessagePart): string | undefined {
  return "toolCallId" in part && typeof part.toolCallId === "string" ? part.toolCallId : undefined;
}

function applyChunk(message: AppUIMessage, chunk: StreamChunk): boolean {
  const type = chunk.type;
  if (type === "text-start") {
    message.parts.push({ type: "text", text: "" });
  } else if (type === "text-delta") {
    const delta = typeof chunk.delta === "string" ? chunk.delta : "";
    for (let index = message.parts.length - 1; index >= 0; index -= 1) {
      const part = message.parts[index];
      if (part?.type === "text") {
        message.parts[index] = { ...part, text: part.text + delta };
        return false;
      }
    }
    message.parts.push({ type: "text", text: delta });
  } else if (type === "reasoning-start") {
    message.parts.push({ type: "reasoning", text: "" });
  } else if (type === "reasoning-delta") {
    const delta = typeof chunk.delta === "string" ? chunk.delta : "";
    for (let index = message.parts.length - 1; index >= 0; index -= 1) {
      const part = message.parts[index];
      if (part?.type === "reasoning") {
        message.parts[index] = { ...part, text: part.text + delta };
        return false;
      }
    }
    message.parts.push({ type: "reasoning", text: delta });
  } else if (type === "tool-input-start" || type === "tool-input-available") {
    const id = String(chunk.toolCallId ?? "");
    const name = String(chunk.toolName ?? "");
    replacePart(
      message,
      (part) => toolCallId(part) === id,
      (part) => ({
        ...(part && typeof part === "object" ? part : {}),
        type: "dynamic-tool",
        toolCallId: id,
        toolName: name,
        state: type === "tool-input-start" ? "input-streaming" : "input-available",
        ...(type === "tool-input-available" ? { input: chunk.input } : {}),
      })
    );
  } else if (
    type === "tool-output-available" ||
    type === "tool-output-error" ||
    type === "tool-output-denied"
  ) {
    const id = String(chunk.toolCallId ?? "");
    replacePart(
      message,
      (part) => toolCallId(part) === id,
      (part) => ({
        ...(part && typeof part === "object" ? part : {}),
        type: "dynamic-tool",
        toolCallId: id,
        state:
          type === "tool-output-available"
            ? "output-available"
            : type === "tool-output-denied"
              ? "output-denied"
              : "output-error",
        ...(type === "tool-output-available" ? { output: chunk.output } : {}),
        ...(type === "tool-output-error"
          ? {
              errorText:
                typeof chunk.errorText === "string" ? chunk.errorText : "Tool execution failed",
            }
          : {}),
      })
    );
  } else if (type === "tool-approval-request") {
    const id = String(chunk.toolCallId ?? "");
    replacePart(
      message,
      (part) => toolCallId(part) === id,
      (part) => ({
        ...(part && typeof part === "object" ? part : {}),
        type: "dynamic-tool",
        toolCallId: id,
        state: "approval-requested",
        approval: { id: String(chunk.approvalId ?? "") },
      })
    );
  } else if (type === "data-pending-action") {
    message.parts.push({
      type: "data-pending-action",
      id: typeof chunk.id === "string" ? chunk.id : undefined,
      data: chunk.data as never,
    });
  } else if (type === "message-metadata" || type === "finish") {
    const metadata = chunk.messageMetadata;
    if (metadata && typeof metadata === "object") {
      message.metadata = { ...(message.metadata ?? {}), ...(metadata as MessageMetadata) };
    }
    return type === "finish";
  } else if (type === "error") {
    throw new Error(
      typeof chunk.errorText === "string" ? chunk.errorText : "Agent stream returned an error"
    );
  } else if (type === "abort") {
    throw new DOMException(
      typeof chunk.reason === "string" ? chunk.reason : "Request was cancelled",
      "AbortError"
    );
  }
  return false;
}

export class RemoteChat {
  readonly id: string;
  private readonly options: RemoteChatOptions;
  private state: RemoteChatState;
  private listeners = new Set<() => void>();
  private abortController?: AbortController;

  constructor(options: RemoteChatOptions) {
    this.id = options.id;
    this.options = options;
    this.state = { messages: [...options.messages], status: "ready" };
  }

  getSnapshot = (): RemoteChatState => this.state;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  sendMessage = async (message: AppUIMessage): Promise<void> => {
    const messages = [...this.state.messages, message];
    this.setState({ messages, status: "submitted" });
    await this.consume((signal) => this.options.sendRequest(messages, signal));
  };

  resumeStream = async (options?: {
    headers?: Headers;
    request?: (signal: AbortSignal) => Promise<Response>;
  }): Promise<void> => {
    this.setState({ ...this.state, status: "submitted", error: undefined });
    await this.consume(
      options?.request ?? ((signal) => this.options.resumeRequest(options?.headers, signal)),
      true
    );
  };

  stop = (): void => {
    this.abortController?.abort();
  };

  private async consume(
    request: (signal: AbortSignal) => Promise<Response>,
    propagateError = false
  ): Promise<void> {
    this.abortController?.abort();
    this.abortController = new AbortController();
    try {
      const response = await request(this.abortController.signal);
      if (!response.ok) {
        throw new Error((await readBackendError(response, "Agent request failed")).message);
      }
      if (!response.body) throw new Error("Agent response did not include a stream");

      const assistant: AppUIMessage = {
        id: crypto.randomUUID(),
        role: "assistant",
        parts: [],
        createdAt: new Date(),
      };
      let assistantAdded = false;
      let finished = false;
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      this.setState({ ...this.state, status: "streaming", error: undefined });

      while (true) {
        const read = await reader.read();
        if (read.done) break;
        if (this.abortController.signal.aborted) {
          await reader.cancel();
          throw new DOMException("Request was cancelled", "AbortError");
        }
        buffer += decoder.decode(read.value, { stream: true });
        const frames = buffer.split(/\r?\n\r?\n/);
        buffer = frames.pop() ?? "";
        for (const frame of frames) {
          for (const line of frame.split(/\r?\n/)) {
            if (!line.startsWith("data:")) continue;
            const data = line.slice(5).trim();
            if (!data || data === "[DONE]") continue;
            const chunk = JSON.parse(data) as StreamChunk;
            if (chunk.type === "start" && typeof chunk.messageId === "string") {
              assistant.id = chunk.messageId;
            }
            finished = applyChunk(assistant, chunk) || finished;
            if (!assistantAdded) {
              assistantAdded = true;
              this.setState({
                messages: [...this.state.messages, assistant],
                status: "streaming",
              });
            } else {
              this.setState({
                messages: this.state.messages.map((candidate) =>
                  candidate.id === assistant.id && candidate.role === "assistant"
                    ? { ...assistant, parts: [...assistant.parts] }
                    : candidate
                ),
                status: "streaming",
              });
            }
          }
        }
      }

      this.setState({ ...this.state, status: "ready", error: undefined });
      if (assistantAdded && finished) await this.options.onFinish?.(assistant);
    } catch (reason) {
      if (reason instanceof Error && reason.name === "AbortError") {
        this.setState({ ...this.state, status: "ready", error: undefined });
        return;
      }
      const error = reason instanceof Error ? reason : new Error("Agent request failed");
      this.setState({ ...this.state, status: "error", error });
      if (propagateError) throw error;
    }
  }

  private setState(state: RemoteChatState): void {
    this.state = state;
    for (const listener of this.listeners) listener();
  }
}

export function useRemoteChat(chat: RemoteChat) {
  const state = useSyncExternalStore(chat.subscribe, chat.getSnapshot, chat.getSnapshot);
  return {
    ...state,
    sendMessage: chat.sendMessage,
    stop: chat.stop,
  };
}
