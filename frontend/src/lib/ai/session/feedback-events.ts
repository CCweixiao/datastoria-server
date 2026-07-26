/** Negative-feedback reason codes rendered by the browser. Validation belongs to Spring. */
export const AUTO_EXPLAIN_NEGATIVE_REASON_CODES = [
  "wrong_diagnosis",
  "too_vague",
  "unsafe_fix",
  "missing_context",
  "other",
] as const;

export type AutoExplainNegativeReasonCode = (typeof AUTO_EXPLAIN_NEGATIVE_REASON_CODES)[number];
