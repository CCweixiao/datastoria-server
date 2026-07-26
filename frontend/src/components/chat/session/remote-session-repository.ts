import type { Chat, Message } from "@/lib/ai/ai-types";
import { getSessionApiBase, sessionIdentityHeaders } from "@/lib/ai/session/session-api-base";
import { SESSION_SHARE_CODE_HEADER } from "@/lib/ai/session/session-share-constants";
import { backendApiFetch } from "@/lib/backend-api";
import type {
  CreateSessionFromMessagesInput,
  SessionAccessOptions,
  SessionPage,
  SessionPageInput,
  SessionRepository,
} from "./session-repository";

type ChatSessionDTO = {
  chatId: string;
  databaseId: string;
  title: string | null;
  createdAt: string;
  updatedAt: string;
};

type ChatMessageDTO = {
  id: string;
  role: Message["role"];
  parts: Message["parts"];
  metadata: Message["metadata"] | null;
  sequence: number;
  createdAt: string;
  updatedAt: string;
};

function toChat(dto: ChatSessionDTO): Chat {
  return {
    chatId: dto.chatId,
    databaseId: dto.databaseId,
    title: dto.title ?? undefined,
    createdAt: new Date(dto.createdAt),
    updatedAt: new Date(dto.updatedAt),
  };
}

function toMessage(dto: ChatMessageDTO): Message {
  return {
    id: dto.id,
    role: dto.role,
    parts: dto.parts,
    metadata: dto.metadata ?? undefined,
    sequence: dto.sequence,
    createdAt: new Date(dto.createdAt),
    updatedAt: new Date(dto.updatedAt),
  };
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
  return (await response.json()) as T;
}

function buildShareCodeHeaders(options?: SessionAccessOptions): HeadersInit | undefined {
  return options?.shareCode ? { [SESSION_SHARE_CODE_HEADER]: options.shareCode } : undefined;
}

/**
 * Merges the caller-supplied headers with the gateway's dev identity headers. Returns a plain
 * object so existing tests that assert against literal header objects continue to pass.
 */
function mergeHeaders(
  base: HeadersInit | undefined,
  identity: Record<string, string>
): HeadersInit | undefined {
  if (!base && Object.keys(identity).length === 0) {
    return undefined;
  }
  const merged: Record<string, string> = {};
  if (base) {
    if (base instanceof Headers) {
      base.forEach((v, k) => {
        merged[k] = v;
      });
    } else if (Array.isArray(base)) {
      for (const [k, v] of base) {
        merged[k] = v;
      }
    } else {
      for (const [k, v] of Object.entries(base)) {
        merged[k] = String(v);
      }
    }
  }
  for (const [k, v] of Object.entries(identity)) {
    merged[k] = v;
  }
  return merged;
}

/** Adds the development identity header to direct Spring session API calls. */
async function sessionFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const identity = sessionIdentityHeaders() as Record<string, string>;
  const headers = mergeHeaders(init.headers as HeadersInit | undefined, identity);
  const { headers: _unused, ...rest } = init;
  return backendApiFetch(url, {
    ...rest,
    ...(headers ? { headers } : {}),
    credentials: init.credentials ?? "same-origin",
  });
}

export class RemoteSessionRepository implements SessionRepository {
  async getSession(sessionId: string, options?: SessionAccessOptions): Promise<Chat | null> {
    const response = await sessionFetch(
      `${getSessionApiBase()}/api/ai/chat/sessions/${encodeURIComponent(sessionId)}`,
      {
        headers: buildShareCodeHeaders(options),
        cache: "no-store",
      }
    );

    if (response.status === 404) {
      return null;
    }

    const dto = await parseJson<ChatSessionDTO>(response);
    return toChat(dto);
  }

  async getSessions(input: SessionPageInput): Promise<SessionPage> {
    const searchParams = new URLSearchParams({ limit: String(input.limit) });
    if (input.connectionId) {
      searchParams.set("connectionId", input.connectionId);
    }
    if (input.cursor) {
      searchParams.set("cursor", input.cursor);
    }

    const response = await sessionFetch(
      `${getSessionApiBase()}/api/ai/chat/sessions?${searchParams.toString()}`,
      {
        cache: "no-store",
      }
    );
    const page = await parseJson<{ sessions: ChatSessionDTO[]; nextCursor: string | null }>(
      response
    );
    return {
      sessions: page.sessions.map(toChat),
      nextCursor: page.nextCursor,
    };
  }

  async getMessages(sessionId: string, options?: SessionAccessOptions): Promise<Message[]> {
    const response = await sessionFetch(
      `${getSessionApiBase()}/api/ai/chat/sessions/${encodeURIComponent(sessionId)}/messages`,
      {
        headers: buildShareCodeHeaders(options),
        cache: "no-store",
      }
    );

    if (response.status === 404) {
      return [];
    }

    const messages = await parseJson<ChatMessageDTO[]>(response);
    return messages.map(toMessage);
  }

  async createSessionFromMessages(input: CreateSessionFromMessagesInput): Promise<Chat> {
    const response = await sessionFetch(`${getSessionApiBase()}/api/ai/chat/sessions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        connectionId: input.connectionId,
        sessionId: input.sessionId,
        title: input.title,
        messages: input.messages,
      }),
    });

    const data = await parseJson<{ session: ChatSessionDTO }>(response);
    return toChat(data.session);
  }

  async saveSession(session: Chat): Promise<void> {
    const response = await sessionFetch(`${getSessionApiBase()}/api/ai/chat/sessions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        connectionId: session.databaseId,
        sessionId: session.chatId,
        title: session.title,
        messages: [],
      }),
    });
    const persisted = await parseJson<{ session: ChatSessionDTO }>(response);

    // Session creation is idempotent on sessionId. For an existing session the create endpoint
    // intentionally preserves its current title, so apply a later local title change explicitly.
    if (
      session.title &&
      session.title.trim().length > 0 &&
      persisted.session.title !== session.title
    ) {
      await this.renameSession(session.chatId, session.title);
    }
  }

  async saveMessages(_chatId: string, _messages: Message[]): Promise<void> {}

  async saveMessage(_sessionId: string, _message: Message): Promise<void> {}

  async renameSession(
    sessionId: string,
    title: string,
    options?: SessionAccessOptions
  ): Promise<void> {
    const response = await sessionFetch(
      `${getSessionApiBase()}/api/ai/chat/sessions/${encodeURIComponent(sessionId)}`,
      {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          ...buildShareCodeHeaders(options),
        },
        body: JSON.stringify({ title }),
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to rename session: ${response.status}`);
    }
  }

  async deleteSession(sessionId: string, options?: SessionAccessOptions): Promise<void> {
    const response = await sessionFetch(
      `${getSessionApiBase()}/api/ai/chat/sessions/${encodeURIComponent(sessionId)}`,
      {
        method: "DELETE",
        headers: buildShareCodeHeaders(options),
      }
    );

    if (!response.ok && response.status !== 404) {
      throw new Error(`Failed to delete session: ${response.status}`);
    }
  }
}
