# Frontend Migration Guide — P2

This guide walks the frontend team through migrating from browser-localStorage-based
configuration to the new server-managed APIs introduced in P2.

## Runtime Switch

The frontend adapter is selected at build/runtime configuration:

```dotenv
# Rollback-safe default
NEXT_PUBLIC_DATASTORIA_CONFIG_BACKEND=node

# P2 Java configuration backend
NEXT_PUBLIC_DATASTORIA_CONFIG_BACKEND=java
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=admin@datastoria.local
```

Switching back to `node` restores the legacy configuration path. In `java` mode the frontend
does not send provider credentials to A12 and removes legacy provider/model secrets from browser
storage during bootstrap.

## What Changed

Before P2 the browser was the source of truth for:
- Model provider list + API keys (stored in localStorage)
- Available models list (hard-coded or fetched on demand)
- User preferences (theme, default model, language)
- Agent definitions (if any existed)

After P2 the server owns all of this. The browser holds only:
- The dev identity header `x-datastoria-user-email` (dev only — P10 replaces with OAuth)
- An ETag revision for optimistic-locking writes

## Step 1 — Model Providers

### Before (localStorage)
```ts
const providers = JSON.parse(localStorage.getItem('ai.providers') || '[]');
providers.push({key: 'openai', apiKey: 'sk-...'});
localStorage.setItem('ai.providers', JSON.stringify(providers));
```

### After (server API)
```ts
// List providers
const providers = await fetch('/api/admin/ai/providers', {
  headers: {'x-datastoria-user-email': userEmail}
}).then(r => r.json());

// Create provider
const resp = await fetch('/api/admin/ai/providers', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'x-datastoria-user-email': userEmail,
  },
  body: JSON.stringify({
    providerKey: 'openai',
    displayName: 'OpenAI',
    authType: 'api_key',
    enabled: true,
    configJson: '{}',
  }),
});
const provider = await resp.json();
const etag = resp.headers.get('ETag'); // store for updates
```

### Credential rotation (API key never returned in plaintext)
```ts
await fetch(`/api/admin/ai/providers/${providerId}/credential`, {
  method: 'PUT',
  headers: {'Content-Type': 'application/json', 'x-datastoria-user-email': userEmail},
  body: JSON.stringify({secretKind: 'api_key', value: 'sk-...', expiresAt: null}),
});
// Response: {configured: true, maskedHint: 'sk-…def', updatedAt: '...'}
// maskedHint is the only thing the frontend ever sees.
```

## Step 2 — Available Models (A12)

### Before
```ts
// Hard-coded model list or fetched with apiKey in body
const resp = await fetch('/api/ai/models/available', {
  method: 'POST',
  body: JSON.stringify({apiKey: 'sk-...'}), // REJECTED in P2
});
```

### After
```ts
const resp = await fetch('/api/ai/models/available', {
  method: 'POST',
  headers: {'Content-Type': 'application/json', 'x-datastoria-user-email': userEmail},
  body: JSON.stringify({}), // no apiKey — server owns credentials now
});
const {systemModels, githubModels} = await resp.json();
// githubModels is always [] until P10 OAuth.
```

**Important**: if the body contains `apiKey`, the server returns `400 CLIENT_SECRET_NOT_ALLOWED`.
The legacy `github.token` field is accepted but ignored (logged as a security warning).

## Step 3 — User Preferences

### Before
```ts
localStorage.setItem('user.theme', 'dark');
localStorage.setItem('user.defaultModel', 'gpt-4');
```

### After
```ts
// Write a user preference
await fetch('/api/me/ai/preferences', {
  method: 'PUT',
  headers: {
    'Content-Type': 'application/json',
    'x-datastoria-user-email': userEmail,
  },
  body: JSON.stringify({configKey: 'theme', valueJson: '"dark"'}),
});

// Read the merged effective config (system < tenant < user)
const {entries, revision} = await fetch('/api/me/ai/preferences', {
  headers: {'x-datastoria-user-email': userEmail},
}).then(r => r.json());
// entries = {theme: '"dark"', language: '"en"', ...}
// Each value is a JSON-encoded string, so parse it: JSON.parse(entries.theme)
```

### Model selection
```ts
await fetch('/api/me/ai/model-preference', {
  method: 'PUT',
  headers: {'Content-Type': 'application/json', 'x-datastoria-user-email': userEmail},
  body: JSON.stringify({modelConfigId: '01H...'}),
});

const {selectedModelId} = await fetch('/api/me/ai/model-preference', {
  headers: {'x-datastoria-user-email': userEmail},
}).then(r => r.json());
```

## Step 4 — Agent Definitions

### Create and publish
```ts
// 1. Create a draft agent
const agent = await fetch('/api/admin/ai/agents', {
  method: 'POST',
  headers: {'Content-Type': 'application/json', 'x-datastoria-user-email': userEmail},
  body: JSON.stringify({agentKey: 'main', name: 'Main Agent'}),
}).then(r => r.json());

// 2. Create an immutable revision
const rev = await fetch(`/api/admin/ai/agents/${agent.id}/revisions`, {
  method: 'POST',
  headers: {'Content-Type': 'application/json', 'x-datastoria-user-email': userEmail},
  body: JSON.stringify({systemPrompt: 'You are helpful.', modelId: null}),
}).then(r => r.json());

// 3. Publish (atomic)
await fetch(`/api/admin/ai/agents/${agent.id}/revisions/${rev.id}:publish`, {
  method: 'POST',
  headers: {'x-datastoria-user-email': userEmail, 'If-Match': String(agent.revision)},
});
```

## Step 5 — Optimistic Locking Pattern

Responses for revisioned resources return an `ETag` header. For updates, pass the current ETag
value unchanged as the `If-Match` header. If another writer changed the resource first, you get `409
REVISION_CONFLICT`.

```ts
async function updateWithRetry(url, body, etag) {
  const resp = await fetch(url, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'If-Match': etag,
    },
    body: JSON.stringify(body),
  });
  if (resp.status === 409) {
    // Re-fetch, merge user intent with latest, retry
    const latest = await fetch(url).then(r => r.json());
    return updateWithRetry(url, {...body, ...mergeStrategy(latest)}, String(latest.revision));
  }
  return resp;
}
```

## Error Handling

All errors use RFC 9457 Problem Details (`application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "Revision conflict",
  "status": 409,
  "detail": "Expected revision 3 but current is 4",
  "code": "REVISION_CONFLICT",
  "requestId": "abc-123"
}
```

Common codes:
| Code | HTTP | Cause |
|---|---|---|
| `NOT_FOUND` | 404 | Resource doesn't exist or belongs to another tenant |
| `REVISION_CONFLICT` | 409 | If-Match didn't match current revision |
| `CLIENT_SECRET_NOT_ALLOWED` | 400 | Body contained `apiKey` |
| `INVALID_REQUEST` | 400 | Bean Validation constraint violated |
| `RESOURCE_IN_USE` | 409 | Provider is still referenced by an active model |

## Cleanup Checklist

After migration, remove these from localStorage:
- [x] Provider settings — purged by the Java-mode bootstrap and replaced by server APIs
- [x] Selected model — server-backed by `/api/me/ai/model-preference` in Java mode
- [x] Provider API keys — kept only in transient form state and sent directly to the Java API
- [ ] `user.theme`, `user.defaultModel`, etc. — replaced by `PUT /api/me/ai/preferences`
- [ ] `github.token` — ignored server-side, remove once P10 OAuth ships
