import {
  AgentConfigurationManager,
  normalizeAIResponseLanguage,
} from "@/components/settings/agent/agent-manager";
import { ModelManager } from "@/components/settings/models/model-manager";
import type { PlanToolOutput } from "@/lib/ai/agent/plan/planning-types";
import type { AgentContext, AppUIMessage, Message } from "@/lib/ai/ai-types";
import { RemoteChat } from "@/lib/ai/session/remote-chat";
import { SESSION_SHARE_CODE_HEADER } from "@/lib/ai/session/session-share-constants";
import { useToolProgressStore } from "@/lib/ai/tools/clickhouse/tool-progress-store";
import { SERVER_TOOL_NAMES } from "@/lib/ai/tools/server/server-tool-names";
import { backendApiFetch, backendApiHeaders, backendApiUrl } from "@/lib/backend-api";
import { Connection } from "@/lib/connection/connection";
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

  return backendApiHeaders({
    ...normalizedHeaders,
    ...(shareCode ? { [SESSION_SHARE_CODE_HEADER]: shareCode } : {}),
  });
}

export class ChatFactory {
  private static readonly resumeTargets = new WeakMap<RemoteChat, (runId: string) => void>();

  static async respondToQuestion(
    chat: RemoteChat,
    runId: string,
    actionId: string,
    response: unknown
  ): Promise<void> {
    const selectTarget = ChatFactory.resumeTargets.get(chat);
    if (!selectTarget) {
      throw new Error("Chat resume transport is unavailable.");
    }
    selectTarget(runId);
    const idempotencyKey = uuidv7();
    await chat.resumeStream({
      request: (signal) =>
        backendApiFetch(
          backendApiUrl(
            `/api/ai/runs/${encodeURIComponent(runId)}/actions/${encodeURIComponent(actionId)}:respond-and-resume`
          ),
          {
            method: "POST",
            headers: buildChatRequestHeaders(
              {
                "Content-Type": "application/json",
                "Idempotency-Key": idempotencyKey,
              },
              undefined
            ),
            body: JSON.stringify({ response }),
            signal,
          }
        ),
    });
  }

  static async resolveApproval(
    chat: RemoteChat,
    runId: string,
    actionId: string,
    approved: boolean
  ): Promise<void> {
    const resolved = await backendApiFetch(
      backendApiUrl(
        `/api/ai/runs/${encodeURIComponent(runId)}/actions/${encodeURIComponent(actionId)}:${approved ? "approve" : "deny"}`
      ),
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
      backendApiUrl(`/api/ai/runs/${encodeURIComponent(runId)}`),
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

    return { configId: selectedModel.configId };
  }

  /**
   * Create or retrieve a persisted chat instance
   */
  static async create(options: ChatFactoryCreateOptions): Promise<RemoteChat> {
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

        // A01 expects the incoming user message to already exist in the session repository. Create
        // or idempotently update the session with the complete UI message list before opening the
        // agent stream so a reload restores both user and assistant turns.
        await SessionManager.createSessionFromMessages(
          connectionId,
          messages,
          provisionalTitle,
          sessionId
        );
      },
      onFinish: async ({ message, connectionId, sessionId }) => {
        let title: string | undefined;
        if (typeof message.metadata?.title === "string") {
          title = message.metadata.title;
          ChatUIContext.updateTitle(title);
        } else if (message.metadata?.title && typeof message.metadata.title.text === "string") {
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
  static async createEphemeral(options: ChatFactoryCreateOptions): Promise<RemoteChat> {
    return ChatFactory.createInternal({
      ...options,
      ephemeral: true,
      initialMessages: options.initialMessages,
      generateTitle: false,
    });
  }

  private static async createInternal(options: CreateInternalOptions): Promise<RemoteChat> {
    const sessionId = options.sessionId || newUniqueSessionId();
    const modelConfig = options.model;
    const connection = options.connection ?? null;
    const connectionId = options.connectionId ?? getSessionRepositoryConnectionId(connection);

    let resumeRunId: string | undefined;
    const prepareRequest = async (
      messages: AppUIMessage[],
      signal: AbortSignal
    ): Promise<Response> => {
      const clientRequestId = uuidv7();
      const currentModel = modelConfig || ChatFactory.getCurrentModelConfig();
      await options.onPrepareSendMessagesRequest?.({
        sessionId,
        connection,
        connectionId,
        historicalMessages: options.initialMessages,
        messages,
      });
      const requestContext = options.context ?? ChatContext.build();
      const agentConfiguration = AgentConfigurationManager.getConfiguration();
      const agentContext = buildAgentContextWithResponseLanguage(
        options.agentContext,
        agentConfiguration.aiResponseLanguage
      );
      const body = buildSendMessagesRequestPayload({
        sessionId,
        connectionId,
        messages,
        trigger: "submit-message",
        messageId: messages.at(-1)?.id,
        body: {},
        requestContext,
        currentModel,
        generateTitle: options.generateTitle,
        ephemeral: options.ephemeral,
        pruneValidateSql: agentConfiguration.pruneValidateSql ?? true,
        outputReasoning: agentConfiguration.outputReasoning ?? true,
        reasoningLevel: agentConfiguration.reasoningLevel,
        agentContext,
        chatPersistenceMode: "remote",
      });
      return backendApiFetch(backendApiUrl("/api/ai/agent"), {
        method: "POST",
        headers: buildChatRequestHeaders(
          {
            "Content-Type": "application/json",
            "Idempotency-Key": clientRequestId,
          },
          options.shareCode
        ),
        body: JSON.stringify(body),
        signal,
      });
    };

    const chat = new RemoteChat({
      id: sessionId,
      messages: options.initialMessages,
      sendRequest: prepareRequest,
      resumeRequest: async (headers, signal) => {
        if (!resumeRunId) {
          throw new Error("No suspended run is selected.");
        }
        return backendApiFetch(
          backendApiUrl(`/api/ai/runs/${encodeURIComponent(resumeRunId)}:resume`),
          {
            method: "POST",
            headers: buildChatRequestHeaders(headers, options.shareCode),
            signal,
          }
        );
      },
      onFinish: options.onFinish
        ? async (message) => {
            await options.onFinish?.({
              sessionId,
              connection,
              connectionId,
              message,
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
