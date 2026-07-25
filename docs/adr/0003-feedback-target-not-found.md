# ADR-0003: Feedback target not found returns 404

- **Status**: Proposed
- **Date**: 2026-07-25
- **Phase**: P3
- **Inventory scope**: A10 (`POST /api/ai/chat/feedback/auto-explain`)
- **Related**: `docs/api/p3-openapi-extensions.yaml`

## Context

The Node.js handler at
`src/app/api/ai/chat/feedback/auto-explain/route.ts:48-53` catches every
thrown exception from `upsertFeedbackEvent(...)` and returns HTTP 500 with
body `Failed to record feedback`. The repository call at
`src/lib/ai/session/impl/server-session-repository-sql-shared.ts:361-459`
looks up the referenced chat message by
`(user_id, session_id, message_id)` and throws `"Chat message does not
exist"` when the row is missing. As a result, a feedback submission that
references a non-existent message returns HTTP 500 on Node.

The likely root cause is that the Node handler never distinguished "the
target message is gone" from a real infrastructure failure. The HTTP 500
response gives clients no actionable signal.

## Decision

The Java backend classifies "the referenced `(tenantId, userId, sessionId,
messageId)` does not resolve to a persisted chat message" as HTTP 404 with
`code = FEEDBACK_TARGET_NOT_FOUND` and a ProblemDetail body. All other
repository failures continue to surface as HTTP 500 `INTERNAL_ERROR`.

This applies only when the caller is authenticated AND the persistence
backend is configured (i.e. the path that would have returned
`recorded:true`). The anonymous / unconfigured paths continue to return
HTTP 202 `{recorded:false}` without performing the lookup.

## Consequences

- **Observable divergence from Node**: a `messageId` that does not exist now
  returns 404 instead of 500. The frontend (`query-error-ai-explanation.tsx`)
  does not special-case the 500 response and surfaces the failure to the
  user as a generic toast; the new 404 will behave the same in the UI.
- **New error code `FEEDBACK_TARGET_NOT_FOUND`** is added to the
  `ProblemDetail.code` enum; this ADR is the canonical reference.
- **Telemetry**: 404 responses are NOT counted as server errors in SLO
  dashboards, which is the correct classification (this is a client error).

## Alternatives considered

1. **Preserve Node's 500 response.** Rejected: it misclassifies a client
   error as a server error, pollutes error-rate SLOs, and gives the client
   no way to distinguish "transient backend failure" from "the message no
   longer exists".

2. **Skip the lookup entirely and accept orphan feedback rows.** Rejected
   because feedback rows that reference missing messages are not actionable
   in the report (A11, P10) and complicate retention cleanup.
