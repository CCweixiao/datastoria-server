# ADR-0001: Session share permissions

- **Status**: Proposed
- **Date**: 2026-07-25
- **Phase**: P3
- **Inventory scope**: A05, A06, A07, A08, A09, A09b
- **Supersedes**: none
- **Related**: `docs/api/p3-openapi-extensions.yaml`, `docs/api/http-api.md`
  (A06/A07 share-writer behaviour row)

## Context

The Node.js implementation of chat-session sharing, in
`src/lib/ai/session/session-share-code.ts` and
`src/lib/ai/session/session-access.ts`, currently:

1. Signs a single HS256 JWT with `scope = chat_session:full` and a far-future
   expiry of `2100-01-01T00:00:00.000Z`. There is no shorter-lived option and
   no per-share metadata persisted anywhere.
2. Resolves access symmetrically for read and write routes: a valid share code
   in the `X-Session-Share-Code` header returns `access.kind = "share"` with
   `access.ownerId = claims.iss`. The PATCH (`A06`) and DELETE (`A07`) handlers
   in `src/app/api/ai/chat/sessions/[sessionId]/route.ts` then call
   `renameSession(access.ownerId, ...)` and `deleteSession(access.ownerId, ...)`
   — meaning a share-code visitor is treated as the owner for every mutation.
3. Has no revocation API. Once a share JWT is issued, it remains valid until
   its hard-coded 2100 expiry or until `SESSION_SHARE_SECRET` /
   `NEXTAUTH_SECRET` is rotated (which invalidates every outstanding share).

The migration disposition matrix flags this as a security item that must be
resolved before P3 ships: "A06/A07 分享者可写行为先冻结，安全评审后用 ADR 决定是否
收紧".

The P3 PRD additionally requires the Java backend to:

- Implement share issuance **and** verification (`A09` and the share-code side
  of `A05`/`A08`).
- Make share **revocation** possible.
- Default the share permission to **read-only**.

## Frontend usage analysis

A grep of `/Users/jielongping/OpenProjects/datastoria/src` shows:

- The share URL landing page `src/app/session/[sessionId]/page.tsx` renders a
  read-only viewer. The share code is consumed by `RemoteSessionRepository`
  for `getSession` and `getMessages` only.
- `SessionManager.renameSession` (`session-manager.ts:372`) and
  `SessionManager.deleteSessions` (`session-manager.ts:380`) are reachable
  only from `chat-session-list.tsx`, which is part of the owner-authenticated
  main app shell — not the read-only share viewer.
- Although `RemoteSessionRepository.renameSession` / `deleteSession` accept a
  `shareCode` option and will forward it as the `X-Session-Share-Code` header,
  no actual UI flow triggers that path for a share visitor. The cached
  `shareCode` on a `ManagedSession` is `undefined` for any session the user
  created or opened via the owner shell.

In short: there is no user-facing flow that requires share-code visitors to
rename or delete a session. The Node behaviour is an unintended side effect of
the symmetric access resolver, not a deliberate product feature.

## Decision

The Java backend will implement the following session-share contract for P3:

### 1. Share issuance (A09 — `POST /api/ai/sessions/{sessionId}/share`)

- Owner-only. The authenticated user must own the session; otherwise HTTP 404
  `Not found` (404, not 403, to avoid leaking the existence of other users'
  sessions — matches Node).
- Response body shape is preserved verbatim:
  `{ url, code, expiresAt }`.
- `code` is an HS256 JWT, signed with `datastoria.session-share.secret`
  falling back to `datastoria.master-key`. The claims mirror Node:
  - `alg=HS256`, `typ=JWT`
  - `iss=<owner_user_id>` (the owner's resolved user id)
  - `sub=<sessionId>`
  - `aud=https://datastoria.app/session/share`
  - `iat=nbf=<now>` (seconds)
  - `exp=<now + ttl>` where `ttl` defaults to
    `datastoria.session-share.default-ttl-seconds` (default
    `4102444800`, i.e. year 2100 — preserves Node compatibility).
  - `scope=chat_session:full` (kept for compatibility; see §3 below for the
    effective scope).
- A row is inserted into `ds_session_share` keyed by
  `id = ULID()`, `session_id`, `owner_user_id`, `token_hash` (SHA-256 of the
  JWT string), `expires_at`, `revoked_at = NULL`. The natural key is
  `(session_id, active_key)` via the same `active_key` generated-column
  pattern used in P2 — i.e. **at most one active share per session**.

### 2. Share verification (`X-Session-Share-Code` header)

Verification is a strict AND of three conditions:

1. The JWT signature is valid (`HS256`, audience matches), `sub` matches the
   requested `sessionId`, and `exp` has not passed. Failure here returns HTTP
   403 `SHARE_TOKEN_INVALID`.
2. `SHA-256(jwt)` matches a row in `ds_session_share` with
   `revoked_at IS NULL` and `expires_at > now`. A revoked or expired row
   yields HTTP 403 `SHARE_TOKEN_INVALID`.
3. The referenced session row exists and is not soft-deleted. Failure yields
   HTTP 404 `Not found` (matches Node).

The verification path does **not** consult the `scope` claim. Effective
permissions are hard-coded per route as specified in §3.

### 3. Effective permissions

| Route | Owner | Share visitor |
|---|---|---|
| `GET /api/ai/chat/sessions/{id}` (A05) | ✓ | **read** |
| `GET /api/ai/chat/sessions/{id}/messages` (A08) | ✓ | **read** |
| `POST /api/ai/agent` and `POST /api/ai/chat` (A01/A02, P4+) | ✓ | denied by default (out of scope for P3) |
| `PATCH /api/ai/chat/sessions/{id}` (A06) | ✓ | **denied** |
| `DELETE /api/ai/chat/sessions/{id}` (A07) | ✓ | **denied** |
| `POST /api/ai/sessions/{id}/share` (A09 re-issue) | ✓ | denied (no share header accepted) |
| `POST /api/ai/sessions/{id}/share:revoke` (A09b) | ✓ | denied |
| `POST /api/ai/chat/feedback/auto-explain` (A10) | ✓ | n/a (uses authenticated user_id) |

Share visitors attempting PATCH/DELETE on a session receive HTTP 403 with
`code = SHARE_PERMISSION_DENIED` and a Problem Detail body. A warn-level log
line is emitted including the request id, session id, and the share row id
(not the token).

### 4. Migration flag (compat window)

A server-side flag `datastoria.session-share.allow-write` (default `false`)
gates whether PATCH/DELETE via a valid share code are honoured. When set to
`true`, the Java target behaves like Node (full write for share visitors).

The flag exists **only** as a rollback safety during the P3 cutover. The flag
MUST be removed in P11. Setting it to `true` in production requires a
recorded exception in this ADR's revision log.

### 5. Revocation

`POST /api/ai/sessions/{sessionId}/share:revoke` (owner-only) sets
`revoked_at = now` on the active row. Subsequent verifications fail with
`SHARE_TOKEN_INVALID`. The endpoint returns:

- `204` on success (no body).
- `404` with `code = SHARE_NOT_FOUND` when no active share exists for the
  session.

Issuing a new share after revocation creates a new row with a new JWT; the
old JWT remains invalid because its hash is still on a revoked row.

### 6. Tenant and audit

- The `ds_session_share` row carries `tenant_id` for cross-tenant isolation
  tests; the owner's tenant is resolved from the authenticated identity.
- Every issuance and revocation writes a `ds_audit_log` row with
  `action = session_share.issue | session_share.revoke`, `actor =
  <owner_user_id>`, `resource = session:<sessionId>`. Share visitors reads
  are not audited (volume) but write attempts that result in 403 are.

## Consequences

- **Frontend impact**: none in any exercised path. The share viewer already
  only calls `getSession` and `getMessages`. If a future feature wants share
  visitors to edit, it must (a) request a new scope, (b) update this ADR, and
  (c) introduce a UI affordance.
- **Compatibility with existing shares**: existing Node-issued JWTs continue
  to work for read routes after the migration **only if** `datastoria.session-share.secret`
  is initialised from the same value Node used (`SESSION_SHARE_SECRET` or
  `NEXTAUTH_SECRET`). Because Java also requires a matching row in
  `ds_session_share`, operators must run the JSONL import (P3 sub-phase 4)
  to seed share rows from the Node database before flipping traffic to Java.
  Any share whose hash was not imported will fail verification with
  `SHARE_TOKEN_INVALID` and the user must re-issue.
- **Database**: a new `ds_session_share` table is introduced; see the P3
  Flyway migration. The unique `(tenant_id, session_id, active_key)` index
  enforces "at most one active share per session".
- **Security posture**: bearer share URLs no longer grant destructive
  capability. Revocation is now possible, closing the long-lived-bearer
  exposure.
- **Observability**: every write attempt by a share visitor emits a warn log
  line with request id and share row id (never the token itself).

## Alternatives considered

1. **Preserve Node behaviour exactly (share = full read/write/delete).**
   Rejected because no frontend flow exercises the write path, and bearer
   URLs that can delete production sessions on behalf of an unknown visitor
   are an unacceptable regression when the Java backend takes ownership of
   product data. The disposition matrix explicitly defers this to an ADR.

2. **Make `scope` claim drive permissions (e.g. `chat_session:read` vs
   `chat_session:full`).** Rejected for P3 because:
   - Existing Node-issued tokens all carry `chat_session:full`; introducing
     scope-based decisions now would not actually tighten behaviour unless
     the issuer also changes.
   - The token payload is not the right place to encode permission policy;
     the server-side row + route check is.
   Scope evolution is left as a future option via a separate ADR.

3. **Drop the JWT entirely and use opaque server-issued tokens.** Rejected
   for P3 because the frontend already stores share URLs in JWT form and
   passes them verbatim. Java continues to emit a JWT for wire compatibility
   but the JWT alone is no longer sufficient — the matching `ds_session_share`
   row is the real authority.

## Open items

- **Expiry default**: this ADR keeps `2100-01-01` as the default to preserve
  exact compatibility. Operators SHOULD shorten this via
  `datastoria.session-share.default-ttl-seconds` for production deployments
  with stricter security requirements. A follow-up ADR may shorten the
  default once frontend support for short-lived shares with refresh is in
  place.
- **Per-share scoping** (e.g. time-boxed or field-redacted shares) is out of
  scope for P3 and will be revisited if a product need emerges.
