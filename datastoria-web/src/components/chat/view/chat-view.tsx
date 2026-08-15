"use client";

import { useAuthSession } from "@/components/auth-session-provider";
import { useConnection } from "@/components/connection/connection-context";
import type { AppUIMessage } from "@/lib/ai/ai-types";
import { MentionContext } from "@/lib/ai/mention-context";
import { useRemoteChat, type RemoteChat } from "@/lib/ai/session/remote-chat";
import "@/lib/number-utils"; // Ensure formatTimeDiff is available

import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { v7 as uuidv7 } from "uuid";
import { ChatActionProvider, type UserActionInput } from "../chat-action-context";
import { ChatContext, getDatabaseContextFromConnection } from "../chat-context";
import { ChatFactory } from "../chat-factory";
import {
  ChatInput,
  type ChatInputHandle,
  type ChatInputImageAttachment,
} from "../input/chat-input";
import { appendChatInputHistory, resetChatInputHistory } from "../input/chat-input-history";
import { ChatMessageList } from "../message/chat-message-list";
import { SampleQuestions } from "./sample-questions";
import { type ChatComposerInput } from "./use-chat-panel";
import { useTokenUsage } from "./use-token-usage";

function useStableCallback<Args extends unknown[], Return>(
  callback: (...args: Args) => Return
): (...args: Args) => Return {
  const callbackRef = useRef(callback);

  useLayoutEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  return useCallback((...args: Args) => callbackRef.current(...args), []);
}

/**
 * Finds the newest unanswered ask_user_question in the transcript: a `data-pending-action` part
 * of kind question whose tool call has no output yet. Runs are durable, so the question survives
 * reloads; only the latest assistant turn is considered so stale questions from earlier turns do
 * not swallow new messages.
 */
function findOpenQuestion(messages: AppUIMessage[]) {
  for (let messageIndex = messages.length - 1; messageIndex >= 0; messageIndex--) {
    const message = messages[messageIndex];
    if (message.role !== "assistant") continue;
    const toolHasOutput = new Set(
      message.parts
        .filter(
          (part) =>
            (part as { type?: string }).type === "dynamic-tool" &&
            (part as { output?: unknown }).output !== undefined &&
            (part as { output?: unknown }).output !== null
        )
        .map((part) => (part as { toolCallId?: string }).toolCallId)
    );
    for (let partIndex = message.parts.length - 1; partIndex >= 0; partIndex--) {
      const part = message.parts[partIndex] as {
        type?: string;
        data?: { actionType?: string; toolCallId?: string; runId?: string; actionId?: string };
      };
      if (
        part.type === "data-pending-action" &&
        part.data?.actionType === "question" &&
        part.data.toolCallId &&
        !toolHasOutput.has(part.data.toolCallId)
      ) {
        return part.data;
      }
    }
    // Nothing open in the latest assistant turn.
    return undefined;
  }
  return undefined;
}

interface ChatViewProps {
  chat: RemoteChat;
  onClose?: () => void;
  onNewChat?: () => void;
  currentDatabase?: string;
  externalInput?: ChatComposerInput;
  onStreamingChange?: (isRunning: boolean) => void;
}

export interface ChatViewHandle {
  send: (text: string) => void;
  getInput: () => string;
  focus: () => void;
}

export const ChatView = forwardRef<ChatViewHandle, ChatViewProps>(function ChatView(
  { chat, onNewChat, currentDatabase, externalInput, onStreamingChange },
  ref
) {
  const { connection } = useConnection();
  const { user } = useAuthSession();
  const chatInputRef = useRef<ChatInputHandle | null>(null);
  const [promptInput, setPromptInput] = useState<ChatComposerInput | undefined>(externalInput);
  const promptInputNonceRef = useRef(0);
  const historySyncKeyRef = useRef("");

  // Update promptInput when externalInput changes
  useEffect(() => {
    if (externalInput !== undefined) {
      setPromptInput(externalInput);
      return;
    }
    setPromptInput(undefined);
  }, [chat.id, externalInput]);
  const { messages, error, sendMessage, status, stop } = useRemoteChat(chat);
  const [inputHistory, setInputHistory] = useState<string[]>([]);

  useEffect(() => {
    if (!user?.id) {
      historySyncKeyRef.current = "";
      setInputHistory([]);
      return;
    }
    const userMessages = (messages as AppUIMessage[]).flatMap((message) =>
      message.role === "user"
        ? [
            message.parts
              .filter((part): part is { type: "text"; text: string } => part.type === "text")
              .map((part) => part.text)
              .join("\n")
              .trim(),
          ].filter(Boolean)
        : []
    );
    const syncKey = `${user.id}:${chat.id}:${JSON.stringify(userMessages)}`;
    if (historySyncKeyRef.current === syncKey) return;
    historySyncKeyRef.current = syncKey;
    const restored = resetChatInputHistory(user.id, chat.id, userMessages);
    setInputHistory((current) =>
      current.length === restored.length && current.every((item, index) => item === restored[index])
        ? current
        : restored
    );
  }, [chat.id, messages, user?.id]);

  // Focus input when ChatView is mounted
  useEffect(() => {
    // Use a small delay to ensure ChatInput is fully mounted
    const timer = setTimeout(() => {
      chatInputRef.current?.focus();
    }, 100);
    return () => clearTimeout(timer);
  }, [chat.id]);

  // Notify parent when streaming state changes
  useEffect(() => {
    onStreamingChange?.(status === "streaming" || status === "submitted");
  }, [status, onStreamingChange]);

  const handleSubmit = useStableCallback(
    async ({ text, files = [] }: { text: string; files?: ChatInputImageAttachment[] }) => {
      if (!chat || (!text.trim() && files.length === 0)) return;

      // Typing while a question is open answers it (Gemini-CLI style) instead of racing a new
      // run past the suspended one. Attachments are not answers; they fall through to a new
      // message. If the resume request fails (e.g. the action expired), fall back to sending a
      // regular message so the user's text is never lost.
      const openQuestion =
        files.length === 0 && text.trim().length > 0
          ? findOpenQuestion(messages as AppUIMessage[])
          : undefined;
      if (openQuestion?.runId && openQuestion.actionId && status === "ready") {
        const customAnswer = text.trim();
        try {
          await ChatFactory.respondToQuestion(chat, openQuestion.runId, openQuestion.actionId, {
            optionId: "custom",
            label: customAnswer,
            input: "text",
            value: customAnswer,
          });
          if (user?.id) {
            setInputHistory(appendChatInputHistory(user.id, chat.id, customAnswer));
          }
          return;
        } catch {
          // fall through to a normal message
        }
      }

      const mentionMetadata = connection ? MentionContext.toMetadata(text, connection) : undefined;
      const createdAt = Date.now();
      const messageId = uuidv7();

      ChatContext.setBuilder(() => ({
        database: currentDatabase,
        ...getDatabaseContextFromConnection(connection),
      }));

      if (user?.id && text.trim()) {
        setInputHistory(appendChatInputHistory(user.id, chat.id, text));
      }

      sendMessage({
        id: messageId,
        role: "user",
        parts: [
          ...(text.trim().length > 0 ? [{ type: "text" as const, text }] : []),
          ...files.map((file) => ({
            type: "file" as const,
            mediaType: file.mediaType,
            url: file.url,
            filename: file.filename,
          })),
        ],
        metadata: {
          createdAt,
          ...(mentionMetadata ? { mentionMetadata } : {}),
        },
      });
    }
  );

  // Expose send and getInput to parent component via imperative handle
  useImperativeHandle(
    ref,
    () => ({
      send: async (text: string) => {
        await handleSubmit({ text });
      },
      getInput: () => {
        return chatInputRef.current?.getInput() || "";
      },
      focus: () => {
        chatInputRef.current?.focus();
      },
    }),
    [handleSubmit]
  );

  const isRunning = status === "streaming" || status === "submitted";

  const tokenUsage = useTokenUsage(isRunning ? undefined : (messages as AppUIMessage[]));

  const isEmpty = !messages || messages.length === 0;

  const createPromptInput = useCallback((text: string): ChatComposerInput => {
    return { text, mode: "replace", nonce: ++promptInputNonceRef.current };
  }, []);

  const handleQuestionClick = useStableCallback((question: { text: string; autoRun?: boolean }) => {
    if (question.autoRun) {
      // Auto-run: send the message immediately
      handleSubmit({ text: question.text });
    } else {
      // Default: set the input for user to review/edit
      setPromptInput(createPromptInput(question.text));
    }
  });

  const handleUserAction = useStableCallback((input: UserActionInput) => {
    if (input.autoRun) {
      handleSubmit({ text: input.text });
      return;
    }
    setPromptInput(createPromptInput(input.text));
  });

  const handleStop = useStableCallback(() => {
    stop();
  });

  const handleToolOutput = useStableCallback(
    async ({
      runId,
      actionId,
      output,
    }: {
      runId: string;
      actionId: string;
      toolCallId: string;
      output: unknown;
    }) => {
      await ChatFactory.respondToQuestion(chat, runId, actionId, output);
    }
  );
  const handleApproval = useStableCallback(
    async ({
      runId,
      actionId,
      approved,
    }: {
      runId: string;
      actionId: string;
      approved: boolean;
    }) => {
      await ChatFactory.resolveApproval(chat, runId, actionId, approved);
    }
  );

  return (
    <ChatActionProvider
      onAction={handleUserAction}
      onToolOutput={handleToolOutput}
      onApproval={handleApproval}
      chatId={chat.id}
    >
      <div className="flex flex-col h-full bg-background overflow-hidden relative">
        {isEmpty ? (
          <SampleQuestions onQuestionClick={handleQuestionClick} />
        ) : (
          <ChatMessageList
            messages={messages as AppUIMessage[]}
            isRunning={isRunning}
            error={error || null}
          />
        )}
        <ChatInput
          ref={chatInputRef}
          onSubmit={handleSubmit}
          onStop={handleStop}
          isRunning={isRunning}
          hasMessages={messages.length > 0}
          tokenUsage={tokenUsage}
          onNewChat={onNewChat}
          externalInput={promptInput}
          inputHistory={inputHistory}
        />
      </div>
    </ChatActionProvider>
  );
});
