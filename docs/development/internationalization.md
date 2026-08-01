# Internationalization

DataStoria supports English (`en`) and Simplified Chinese (`zh-CN`) across the web UI and backend error responses.

## Language resolution

The effective `settings.ui` configuration is resolved with the existing precedence:

1. system default;
2. tenant override;
3. user override.

`language: "system"` follows the browser language. The frontend sends the resolved locale in `Accept-Language`, so backend ProblemDetail and compatible plain-text errors use the same language.

## Frontend messages

All caller-visible copy belongs in:

- `datastoria-web/src/lib/i18n/messages/en.ts`
- `datastoria-web/src/lib/i18n/messages/zh-CN.ts`

Components call `useUiPreferences().t(key)`. Both catalogs are type-checked against the English key set.

## Backend errors

Caller-visible errors use `ApiErrorCode`. Each entry owns a stable code, HTTP status, English title/message, and Simplified Chinese title/message. `ProblemDetailFactory` adds `code`, `message`, `locale`, and `requestId` to every standardized ProblemDetail response.

Protocol compatibility endpoints that require `text/plain` keep their content type. They expose `X-Error-Code` and `Content-Language`, and known fixed errors are localized without changing the default English wire behavior.

## Development rule

New frontend messages and backend response messages are incomplete until both English and Simplified Chinese variants and tests are supplied. SQL, identifiers, setting names, and stable error codes must not be translated.
