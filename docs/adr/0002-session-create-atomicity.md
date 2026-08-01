# ADR-0002: Atomic session creation

- **Status**: Proposed
- **Date**: 2026-07-25
- **Phase**: P3
- **Inventory scope**: A04 (`POST /api/ai/chat/sessions`)
- **Related**: `docs/api/p3-openapi-extensions.yaml`, `docs/api/http-api.md`

## Context

The Node.js handler at
`src/app/api/ai/chat/sessions/route.ts:91-118` creates a session row via
`createSession(...)` and then loops over `payload.messages[]`, calling
`upsertMessage(...)` sequentially. Each repository call uses its own
Knex transaction. There is **no** surrounding transaction spanning the
session insert and the message upserts.

Failure modes that the Node implementation exposes:

1. If `createSession` succeeds but a mid-loop `upsertMessage` throws
   (database error, timeout, schema violation, etc.), the session row remains
   in the database with only a subset of the requested messages persisted.
2. A subsequent retry with the same `sessionId` returns the half-written
   session because of the idempotency check at `route.ts:97-110`.
3. The handler returns HTTP 500 `Session was not created` only when the
   post-loop `getSession` lookup returns null; a half-written session is
   returned as a 200 success.

The API contract requires session and initial messages to be created atomically.

## Decision

The Java backend wraps the **entire** A04 operation — session row INSERT
(when not idempotent reuse) and all `messages[]` upserts — in a single JDBC
transaction (`TransactionTemplate.execute(...)`). Any exception rolls back
the transaction and propagates as HTTP 500 with the standard ProblemDetail
body. No partial state is observable.

This applies only when the call results in a CREATE. The idempotent reuse
path (existing session with matching `connectionId`) upserts the supplied
messages in a single transaction as well, so partial-message state is
impossible there too.

## Consequences

- **Observable divergence from Node**: a client that today retries a failed
  POST and observes a pre-existing session row will see different behaviour
  on Java — the failed POST leaves nothing behind. This is the desired
  behaviour and the reason for the divergence.
- **Connection pool sizing**: the transaction holds a single JDBC connection
  for the duration of the loop. With MySQL 5.7 this is
  the same constraint P2 already lives with. With MySQL (prod) the bounded
  Hikari pool is unaffected because message counts per POST are small
  (typically 1–4).
- **No new error code is introduced**. A rollback surfaces as HTTP 500
  `INTERNAL_ERROR` with a safe `detail` (no SQL or stack trace).

## Alternatives considered

1. **Preserve Node's non-atomic behaviour bit-for-bit.** Rejected because
   the design doc explicitly requires atomicity, and P3 takes ownership of
   product data; leaving the bug in place would require an ADR justifying
   the regression, which is harder to justify than fixing it.

2. **Introduce a `PartialSessionCreated` error code so the client can retry
   intelligently.** Rejected: there is no client-side recovery flow that
   benefits from this distinction, and atomic transactions make the state
   impossible to reach on Java anyway.
