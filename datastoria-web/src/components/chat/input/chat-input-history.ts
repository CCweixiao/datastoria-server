const STORAGE_PREFIX = "datastoria:chat-input-history:v1";
export const CHAT_INPUT_HISTORY_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const MAX_HISTORY_ITEMS = 100;

type StoredConversationHistory = {
  items: string[];
  touchedAt: number;
};

type StoredUserHistory = {
  version: 1;
  conversations: Record<string, StoredConversationHistory>;
};

export type ChatInputHistoryCursor = {
  index: number | null;
  draft: string;
};

function storageKey(userId: string) {
  return `${STORAGE_PREFIX}:${encodeURIComponent(userId)}`;
}

function normalizeItems(items: string[]) {
  return items
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(-MAX_HISTORY_ITEMS);
}

function readUserHistory(userId: string, now: number): StoredUserHistory {
  const empty: StoredUserHistory = { version: 1, conversations: {} };
  if (typeof window === "undefined") return empty;

  try {
    const parsed = JSON.parse(
      window.localStorage.getItem(storageKey(userId)) ?? "null"
    ) as StoredUserHistory | null;
    if (!parsed || parsed.version !== 1 || typeof parsed.conversations !== "object") return empty;

    const conversations = Object.fromEntries(
      Object.entries(parsed.conversations).filter(
        ([, value]) =>
          value &&
          Array.isArray(value.items) &&
          Number.isFinite(value.touchedAt) &&
          now - value.touchedAt <= CHAT_INPUT_HISTORY_TTL_MS
      )
    );
    return { version: 1, conversations };
  } catch {
    return empty;
  }
}

function writeUserHistory(userId: string, history: StoredUserHistory) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(storageKey(userId), JSON.stringify(history));
  } catch {
    // Input history is an optional convenience; storage restrictions must not block chat.
  }
}

export function resetChatInputHistory(
  userId: string,
  conversationId: string,
  items: string[],
  now = Date.now()
) {
  const history = readUserHistory(userId, now);
  const normalized = normalizeItems(items);
  history.conversations[conversationId] = { items: normalized, touchedAt: now };
  writeUserHistory(userId, history);
  return normalized;
}

export function appendChatInputHistory(
  userId: string,
  conversationId: string,
  item: string,
  now = Date.now()
) {
  const history = readUserHistory(userId, now);
  const current = history.conversations[conversationId]?.items ?? [];
  const items = normalizeItems([...current, item]);
  history.conversations[conversationId] = { items, touchedAt: now };
  writeUserHistory(userId, history);
  return items;
}

export function navigateChatInputHistory(
  items: string[],
  cursor: ChatInputHistoryCursor,
  currentInput: string,
  direction: "previous" | "next"
): { cursor: ChatInputHistoryCursor; value: string } | null {
  if (items.length === 0) return null;

  if (direction === "previous") {
    const draft = cursor.index === null ? currentInput : cursor.draft;
    const index = cursor.index === null ? items.length - 1 : Math.max(0, cursor.index - 1);
    return { cursor: { index, draft }, value: items[index] };
  }

  if (cursor.index === null) return null;
  const index = cursor.index + 1;
  if (index >= items.length) {
    return { cursor: { index: null, draft: cursor.draft }, value: cursor.draft };
  }
  return { cursor: { ...cursor, index }, value: items[index] };
}
