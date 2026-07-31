# P3 API Wire-Format Fixtures

This directory freezes the wire-level HTTP contract for inventory items
A03–A10 (chat sessions, messages, feedback, share). Each `.json` file is one
self-contained scenario with a canonical request and expected response.

These fixtures are the **source of truth** for the Java HTTP implementation. A
Java `WebTestClient` test for each scenario must produce the documented status,
headers, and response body after applying the scenario's explicitly documented
opaque-field rules. The OpenAPI contract and frontend call inventory provide
the corresponding route- and schema-level checks.

Drift between the Java implementation, OpenAPI document, frontend inventory,
or fixture is a release blocker.

## Layout

```
docs/fixtures/api/p3/
├── MANIFEST.md                # this file
├── index.json                 # machine-readable list of scenarios
└── <scenario-id>.json         # one file per scenario
```

## Scenario file shape

```jsonc
{
  "id": "A04-create-with-messages",
  "xDatastoriaId": "A04",
  "operationId": "createSession",
  "description": "Human-readable summary of what this scenario proves.",
  "nodeBaselineNotes": "Optional notes on Node behaviour reproduced or diverged.",
  "request": {
    "method": "POST",
    "path": "/api/ai/chat/sessions",
    "headers": { "x-datastoria-user-email": "dev@example.com" },
    "body": { /* request JSON, or null for no body */ }
  },
  "response": {
    "status": 200,
    "headers": { /* only the headers we assert */ },
    "body": { /* response JSON, or null/omitted for no body */ }
  },
  "semanticDiffIgnores": [
    "$.response.body.session.createdAt",
    "$.response.body.session.updatedAt"
  ]
}
```

The Java fixture assertions ignore volatile `createdAt`, `updatedAt`, and
`occurredAt` values, and treat generated identifiers as presence-only where
the scenario requires it. The `semanticDiffIgnores` array documents additional
scenario-specific opaque paths, such as a signed share code or cursor.

## Coverage

The fixtures cover the contract surfaces called out in the P3 task brief:

| Surface | Scenarios |
|---|---|
| session create | `A04-create-minimal`, `A04-create-with-messages`, `A04-create-idempotent-reuse`, `A04-create-connection-mismatch`, `A04-create-invalid-connection-id` |
| session list | `A03-list-basic`, `A03-list-with-cursor`, `A03-list-invalid-limit`, `A03-list-unauthenticated` |
| session detail | `A05-get-owner`, `A05-get-via-share`, `A05-get-not-found`, `A05-get-invalid-share-code` |
| session update | `A06-rename-happy`, `A06-rename-missing-title`, `A06-rename-share-denied` |
| session delete | `A07-delete-happy`, `A07-delete-not-found`, `A07-delete-share-denied` |
| messages list | `A08-messages-happy`, `A08-messages-empty`, `A08-messages-uimessage-roundtrip`, `A08-messages-unknown-part-preserved` |
| share | `A09-share-happy`, `A09-share-unauthenticated`, `A09-share-not-found`, `A09b-revoke-happy`, `A09b-revoke-not-found` |
| feedback | `A10-feedback-recorded`, `A10-feedback-accepted-not-stored`, `A10-feedback-invalid-format`, `A10-feedback-target-not-found` |

## Cross-references

- `docs/api/p3-openapi-extensions.yaml` — OpenAPI 3.0 definition of the same
  shapes.
- `docs/adr/0001-session-share-permissions.md` — share permission decisions.
- `docs/adr/0002-session-create-atomicity.md` — atomic session creation.
- `docs/adr/0003-feedback-target-not-found.md` — feedback 404 vs 500.
- `docs/fixtures/business/` — dialect-agnostic logical records used by the
  import/checksum flow (P3 sub-phase 4).

## Update protocol

- A fixture change MUST be accompanied by a matching OpenAPI change in the
  same commit.
- A fixture change that diverges from Node's actual behaviour MUST cite the
  enabling ADR in `nodeBaselineNotes`.
- Fixtures MUST NOT contain real credentials, share codes, or JWT signatures.
  Synthetic values (e.g. `synthetic-share-code`, fake ULIDs starting with
  `019523a0`) are used throughout.
