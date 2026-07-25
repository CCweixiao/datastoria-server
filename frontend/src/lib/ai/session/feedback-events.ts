import { validateSessionId } from "./remote-chat-request";

export const AI_FEEDBACK_SOURCES = ["auto_explain_error"] as const;
export type AIFeedbackSource = (typeof AI_FEEDBACK_SOURCES)[number];

export const AUTO_EXPLAIN_NEGATIVE_REASON_CODES = [
  "wrong_diagnosis",
  "too_vague",
  "unsafe_fix",
  "missing_context",
  "other",
] as const;

export type AutoExplainNegativeReasonCode = (typeof AUTO_EXPLAIN_NEGATIVE_REASON_CODES)[number];

export type AutoExplainFeedbackPayload = {
  queryId: string;
  errorCode?: string | null;
  sql?: string | null;
};

export type AIFeedbackEventPayload = AutoExplainFeedbackPayload;

export type UpsertFeedbackEventRequest = {
  source: AIFeedbackSource;
  sessionId: string;
  messageId: string;
  solved: boolean;
  reasonCode: AutoExplainNegativeReasonCode | null;
  payload: AIFeedbackEventPayload;
  freeText: string | null;
  recoveryActionTaken: boolean;
};

export function normalizeFeedbackText(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

export function validateUpsertFeedbackEventRequest(
  payload: unknown
): UpsertFeedbackEventRequest | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }
  const value = payload as Record<string, unknown>;
  const detail =
    value.payload && typeof value.payload === "object"
      ? (value.payload as Record<string, unknown>)
      : null;
  const source = value.source;
  const sessionId = value.sessionId;
  const messageId = typeof value.messageId === "string" ? value.messageId.trim() : "";
  const solved = value.solved;
  const reasonCode = value.reasonCode;
  const queryId = typeof detail?.queryId === "string" ? detail.queryId.trim() : "";
  const errorCode =
    detail?.errorCode == null
      ? null
      : typeof detail.errorCode === "string"
        ? detail.errorCode.trim()
        : undefined;
  const sql =
    detail?.sql == null ? null : typeof detail.sql === "string" ? detail.sql.trim() : undefined;
  const freeText =
    value.freeText == null
      ? null
      : typeof value.freeText === "string"
        ? value.freeText.trim()
        : undefined;
  const validReason =
    reasonCode == null ||
    AUTO_EXPLAIN_NEGATIVE_REASON_CODES.includes(reasonCode as AutoExplainNegativeReasonCode);

  if (
    source !== "auto_explain_error" ||
    typeof sessionId !== "string" ||
    !validateSessionId(sessionId) ||
    !messageId ||
    messageId.length > 255 ||
    typeof solved !== "boolean" ||
    !detail ||
    !queryId ||
    queryId.length > 255 ||
    errorCode === undefined ||
    (errorCode?.length ?? 0) > 64 ||
    sql === undefined ||
    (sql?.length ?? 0) > 100_000 ||
    freeText === undefined ||
    (freeText?.length ?? 0) > 2_000 ||
    !validReason ||
    (!solved && reasonCode == null) ||
    (value.recoveryActionTaken != null && typeof value.recoveryActionTaken !== "boolean")
  ) {
    return null;
  }

  return {
    source,
    sessionId,
    messageId,
    solved,
    reasonCode: solved ? null : (reasonCode as AutoExplainNegativeReasonCode),
    payload: {
      queryId,
      errorCode,
      sql: normalizeFeedbackText(sql),
    },
    freeText: solved ? null : normalizeFeedbackText(freeText),
    recoveryActionTaken: (value.recoveryActionTaken as boolean | undefined) ?? false,
  };
}

export function normalizeFeedbackEventForStorage(input: UpsertFeedbackEventRequest) {
  if (input.solved) {
    return {
      ...input,
      reasonCode: null,
      freeText: null,
      payload: {
        ...input.payload,
      },
    };
  }

  return {
    ...input,
    freeText: normalizeFeedbackText(input.freeText),
    payload: {
      ...input.payload,
    },
  };
}

export type FeedbackReportFilters = {
  source?: AIFeedbackSource;
  days?: number;
};

export function validateFeedbackReportFilters(url: URL): FeedbackReportFilters {
  const source = url.searchParams.get("source");
  const daysRaw = url.searchParams.get("days");
  const days = daysRaw ? Number.parseInt(daysRaw, 10) : undefined;

  return {
    source:
      source && AI_FEEDBACK_SOURCES.includes(source as AIFeedbackSource)
        ? (source as AIFeedbackSource)
        : undefined,
    days: Number.isFinite(days) && days && days > 0 ? days : undefined,
  };
}
