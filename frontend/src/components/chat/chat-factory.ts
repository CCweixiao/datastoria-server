import {
  AgentConfigurationManager,
  normalizeAIResponseLanguage,
} from "@/components/settings/agent/agent-manager";
import { ModelManager } from "@/components/settings/models/model-manager";
import type { PlanToolOutput } from "@/lib/ai/agent/plan/planning-types";
import type { AgentContext, AppUIMessage, Message } from "@/lib/ai/ai-types";
import { SESSION_SHARE_CODE_HEADER } from "@/lib/ai/session/session-share-constants";
import { useToolProgressStore } from "@/lib/ai/tools/clickhouse/tool-progress-store";
import { SERVER_TOOL_NAMES } from "@/lib/ai/tools/server/server-tool-names";
import { backendApiFetch } from "@/lib/backend-api";
import { Connection } from "@/lib/connection/connection";
import { Chat } from "@ai-sdk/react";
import { DefaultChatTransport } from "ai";
import { v7 as uuidv7 } from "uuid";
import { ChatContext, type DatabaseContext } from "./chat-context";
import { ChatUIContext } from "./chat-ui-context";
import {
  getSessionRepositoryConnectionId,
  toSessionRepositoryConnectionId,
} from "./session/session-connection-id";
import { SessionManager } from "./session/session-manager";

const PROVISIONAL_SESSION_TITLE_WORDS = 8;

type ChatFactoryCreateOptions = {
  sessionId?: string;
  connectionId?: string;
  connection?: Connection | null;
  apiEndpoint?: string;
  context?: DatabaseContext;
  agentContext?: Partial<AgentContext>;
  ephemeral?: boolean;
  initialMessages: AppUIMessage[];
  model?: {
    configId?: string;
  };
  shareCode?: string;
};
type PrepareSendMessagesRequestArgs = {
  sessionId: string;
  connection: Connection | null;
  connectionId: string;
  historicalMessages: AppUIMessage[];
  messages: AppUIMessage[];
};
type FinishMessageArgs = {
  sessionId: string;
  connection: Connection | null;
  connectionId: string;
  message: AppUIMessage;
};
type CreateInternalOptions = ChatFactoryCreateOptions & {
  initialMessages: AppUIMessage[];
  generateTitle: boolean;
  onPrepareSendMessagesRequest?: (args: PrepareSendMessagesRequestArgs) => Promise<void> | void;
  onFinish?: (args: FinishMessageArgs) => Promise<void> | void;
};
type SendMessagesRequestPayloadArgs = {
  sessionId: string;
  connectionId: string;
  messages: AppUIMessage[];
  trigger: unknown;
  messageId: string | undefined;
  body: unknown;
  requestContext?: DatabaseContext;
  currentModel?: {
    configId?: string;
  };
  generateTitle: boolean;
  ephemeral?: boolean;
  pruneValidateSql: boolean;
  outputReasoning?: boolean;
  reasoningLevel?: AgentContext["reasoningLevel"];
  agentContext?: Partial<AgentContext>;
  chatPersistenceMode: "local" | "remote";
};

export function buildAgentContextWithResponseLanguage(
  agentContext: Partial<AgentContext> | undefined,
  configuredLanguage: string | undefined
): Partial<AgentContext> | undefined {
  const responseLanguage = normalizeAIResponseLanguage(configuredLanguage);
  const configuredAgentContext =
    responseLanguage === "en" ? undefined : ({ responseLanguage } satisfies Partial<AgentContext>);

  return configuredAgentContext || agentContext
    ? {
        ...configuredAgentContext,
        ...(agentContext ?? {}),
      }
    : undefined;
}

function extractTextFromMessage(
  message: Pick<Message, "parts"> | Pick<AppUIMessage, "parts">
): string {
  return message.parts
    .filter(
      (
        part
      ): part is {
        type: "text";
        text: string;
      } => part.type === "text" && typeof part.text === "string"
    )
    .map((part) => part.text.trim())
    .filter((text) => text.length > 0)
    .join(" ")
    .trim();
}

function buildProvisionalSessionTitle(text: string): string | undefined {
  const words = text.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return undefined;
  }

  const truncatedWords = words.slice(0, PROVISIONAL_SESSION_TITLE_WORDS);
  const title = truncatedWords.join(" ").trim();
  return title || undefined;
}

function newUniqueSessionId(): string {
  return uuidv7().replace(/-/g, "");
}

export function buildSendMessagesRequestPayload({
  sessionId,
  connectionId,
  messages,
  trigger,
  messageId,
  body,
  requestContext,
  currentModel,
  generateTitle,
  ephemeral,
  pruneValidateSql,
  outputReasoning = true,
  reasoningLevel,
  agentContext,
  chatPersistenceMode,
}: SendMessagesRequestPayloadArgs): Record<string, unknown> {
  if (chatPersistenceMode === "remote") {
    const lastMessage = messages[messages.length - 1];

    return {
      sessionId,
      connectionId: toSessionRepositoryConnectionId(connectionId),
      message: lastMessage,
      generateTitle,
      ...(ephemeral ? { ephemeral: true } : {}),
      agentContext: {
        ...(agentContext ?? {}),
        pruneValidateSql,
        outputReasoning,
        reasoningLevel,
      },
      ...(requestContext ? { context: requestContext } : {}),
      ...(currentModel?.configId ? { modelConfigId: currentModel.configId } : {}),
    };
  }

  return {
    ...(typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {}),
    messages,
    trigger,
    messageId,
    agentContext: {
      ...(agentContext ?? {}),
      pruneValidateSql,
      outputReasoning,
      reasoningLevel,
    },
    generateTitle,
    ...(requestContext ? { context: requestContext } : {}),
    ...(currentModel ? { model: currentModel } : {}),
  };
}

function buildChatRequestHeaders(
  headers: HeadersInit | undefined,
  shareCode: string | undefined
): HeadersInit | undefined {
  const normalizedHeaders =
    headers instanceof Headers
      ? Object.fromEntries(headers.entries())
      : Array.isArray(headers)
        ? Object.fromEntries(headers)
        : (headers ?? {});

  const identity = process.env.NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL;
  return {
    ...normalizedHeaders,
    ...(identity ? { "x-datastoria-user-email": identity } : {}),
    ...(shareCode ? { [SESSION_SHARE_CODE_HEADER]: shareCode } : {}),
  };
}

export class ChatFactory {
  private static readonly resumeTargets = new WeakMap<
    Chat<AppUIMessage>,
    (runId: string) => void
  >();

  static async respondToQuestion(
    chat: Chat<AppUIMessage>,
    runId: string,
    actionId: string,
    response: unknown
  ): Promise<void> {
    const javaApiBase = (
      process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "http://127.0.0.1:8080"
    ).replace(/\/+$/, "");
    const headers = buildChatRequestHeaders(
      {
        "Content-Type": "application/json",
        "Idempotency-Key": uuidv7(),
      },
      undefined
    );
    const resolved = await backendApiFetch(
      `${javaApiBase}/api/ai/runs/${encodeURIComponent(runId)}/actions/${encodeURIComponent(actionId)}:respond`,
      {
        method: "POST",
        headers,
        body: JSON.stringify({ response }),
      }
    );
    if (!resolved.ok) {
      throw new Error((await resolved.text()) || "Failed to submit answer.");
    }
    const selectTarget = ChatFactory.resumeTargets.get(chat);
    if (!selectTarget) {
      throw new Error("Chat resume transport is unavailable.");
    }
    selectTarget(runId);
    await chat.resumeStream({
      headers: new Headers(buildChatRequestHeaders({ "Idempotency-Key": uuidv7() }, undefined)),
    });
  }

  static async resolveApproval(
    chat: Chat<AppUIMessage>,
    runId: string,
    actionId: string,
    approved: boolean
  ): Promise<void> {
    const javaApiBase = (
      process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "http://127.0.0.1:8080"
    ).replace(/\/+$/, "");
    const resolved = await backendApiFetch(
      `${javaApiBase}/api/ai/runs/${encodeURIComponent(runId)}/actions/${encodeURIComponent(actionId)}:${approved ? "approve" : "deny"}`,
      {
        method: "POST",
        headers: buildChatRequestHeaders(
          {
            "Content-Type": "application/json",
            "Idempotency-Key": uuidv7(),
          },
          undefined
        ),
        body: "{}",
      }
    );
    if (!resolved.ok) {
      throw new Error((await resolved.text()) || "Failed to resolve approval.");
    }
    const snapshot = await backendApiFetch(
      `${javaApiBase}/api/ai/runs/${encodeURIComponent(runId)}`,
      {
        headers: buildChatRequestHeaders(undefined, undefined),
      }
    );
    if (!snapshot.ok) {
      throw new Error((await snapshot.text()) || "Failed to inspect pending approvals.");
    }
    const state = (await snapshot.json()) as {
      pendingActions?: { status?: string }[];
    };
    if (state.pendingActions?.some((action) => action.status === "PENDING")) {
      return;
    }
    const selectTarget = ChatFactory.resumeTargets.get(chat);
    if (!selectTarget) {
      throw new Error("Chat resume transport is unavailable.");
    }
    selectTarget(runId);
    await chat.resumeStream({
      headers: new Headers(buildChatRequestHeaders({ "Idempotency-Key": uuidv7() }, undefined)),
    });
  }

  /**
   * Get the current model configuration based on user settings
   */
  private static getCurrentModelConfig(): { configId?: string } | undefined {
    const modelManager = ModelManager.getInstance();
    const selectedModel = modelManager.getSelectedModel();

    if (
      !selectedModel ||
      (selectedModel.provider === "System" && selectedModel.modelId === "Auto")
    ) {
      return undefined;
    }

    return { configId: (selectedModel as { configId?: string }).configId };
  }

  /**
   * Create or retrieve a persisted chat instance
   */
  static async create(options: ChatFactoryCreateOptions): Promise<Chat<AppUIMessage>> {
    const sessionId = options.sessionId || newUniqueSessionId();
    const historicalMessages = options.initialMessages;

    // A full chat session should start with a clean tool-progress timeline.
    useToolProgressStore.getState().clearAllProgress();

    return ChatFactory.createInternal({
      ...options,
      sessionId,
      initialMessages: historicalMessages,
      generateTitle: true,
      onPrepareSendMessagesRequest: async ({
        messages,
        connectionId,
        sessionId,
        historicalMessages,
      }) => {
        let provisionalTitle: string | undefined;
        if (
          historicalMessages.length === 0 &&
          messages.length === 1 &&
          messages[0]?.role === "user"
        ) {
          provisionalTitle = buildProvisionalSessionTitle(extractTextFromMessage(messages[0]));
          if (provisionalTitle) {
            ChatUIContext.updateTitle(provisionalTitle);
          }
        }

        await SessionManager.touchSessionById(sessionId, connectionId, provisionalTitle, {
          shareCode: options.shareCode,
        });
      },
      onFinish: async ({ message, connectionId, sessionId }) => {
        let title: string | undefined;
        if (message.metadata?.title && typeof message.metadata.title.text === "string") {
          title = message.metadata.title.text;
          ChatUIContext.updateTitle(title);
        } else if (
          message.role === "assistant" &&
          message.parts.length > 1 &&
          message.parts[0].type === "dynamic-tool" &&
          message.parts[0].toolName === SERVER_TOOL_NAMES.PLAN
        ) {
          const output = message.parts[0].output as PlanToolOutput;
          if (output.title) {
            title = output.title;
            ChatUIContext.updateTitle(title);
          }
        }

        await SessionManager.touchSessionById(sessionId, connectionId, title, {
          shareCode: options.shareCode,
        });
      },
    });
  }

  /**
   * Create an ephemeral chat instance for one-off UI surfaces.
   * Does not load history, persist messages, or request a generated title.
   */
  static async createEphemeral(options: ChatFactoryCreateOptions): Promise<Chat<AppUIMessage>> {
    return ChatFactory.createInternal({
      ...options,
      ephemeral: true,
      initialMessages: options.initialMessages,
      generateTitle: false,
    });
  }

  private static async createInternal(options: CreateInternalOptions): Promise<Chat<AppUIMessage>> {
    const sessionId = options.sessionId || newUniqueSessionId();
    const modelConfig = options.model;
    const connection = options.connection ?? null;
    const connectionId = options.connectionId ?? getSessionRepositoryConnectionId(connection);

    let resumeRunId: string | undefined;
    const javaApiBase = (
      process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "http://127.0.0.1:8080"
    ).replace(/\/+$/, "");
    // Create Chat instance
    const chat = new Chat<AppUIMessage>({
      id: sessionId,
      generateId: newUniqueSessionId,

      transport: new DefaultChatTransport({
        fetch: async (input, init) => {
          if (resumeRunId && String(input).includes(`${encodeURIComponent(resumeRunId)}:resume`)) {
            return backendApiFetch(input, { ...init, method: "POST" });
          }
          const endpoint = `${javaApiBase}/api/ai/agent`;
          return backendApiFetch(endpoint, init);
        },
        prepareReconnectToStreamRequest: ({ headers, credentials }) => {
          if (!resumeRunId) {
            throw new Error("No suspended run is selected.");
          }
          return {
            api: `${javaApiBase}/api/ai/runs/${encodeURIComponent(resumeRunId)}:resume`,
            headers,
            credentials,
          };
        },

        prepareSendMessagesRequest: async ({
          messages,
          trigger,
          messageId,
          body,
          headers,
          credentials,
        }) => {
          // Get current model config dynamically if not provided in options
          const currentModel = modelConfig || ChatFactory.getCurrentModelConfig();

          await options.onPrepareSendMessagesRequest?.({
            sessionId,
            connection,
            connectionId,
            historicalMessages: options.initialMessages,
            messages: messages as AppUIMessage[],
          });

          const requestContext = options.context ?? ChatContext.build();
          const agentConfiguration = AgentConfigurationManager.getConfiguration();
          const agentContext = buildAgentContextWithResponseLanguage(
            options.agentContext,
            agentConfiguration.aiResponseLanguage
          );
          return {
            body: buildSendMessagesRequestPayload({
              sessionId,
              connectionId,
              messages: messages as AppUIMessage[],
              trigger,
              messageId,
              body,
              requestContext,
              currentModel,
              generateTitle: options.generateTitle,
              ephemeral: options.ephemeral,
              pruneValidateSql: agentConfiguration.pruneValidateSql ?? true,
              outputReasoning: agentConfiguration.outputReasoning ?? true,
              reasoningLevel: agentConfiguration.reasoningLevel,
              agentContext,
              chatPersistenceMode: "remote",
            }),
            headers: buildChatRequestHeaders(headers, options.shareCode),
            credentials,
          };
        },
      }),

      messages: options.initialMessages,

      onFinish: options.onFinish
        ? async ({ message }) => {
            await options.onFinish?.({
              sessionId,
              connection,
              connectionId,
              message: message as AppUIMessage,
            });
          }
        : undefined,
    });

    ChatFactory.resumeTargets.set(chat, (runId) => {
      resumeRunId = runId;
    });
    return chat;
  }
}
