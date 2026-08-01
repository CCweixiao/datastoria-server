# Repository Development Rules

## Internationalization

- Every new or changed user-facing frontend message must be defined in the centralized English and Simplified Chinese catalogs under `datastoria-web/src/lib/i18n/messages`; do not introduce inline UI copy.
- Every new or changed backend caller-visible error must use a stable `ApiErrorCode` and provide both English and Simplified Chinese title/message text.
- Preserve technical identifiers, SQL, error codes, and protocol field names when translating.
- Use the effective `settings.ui.language` preference: user scope overrides tenant scope, tenant overrides system, and `system` follows the client locale.
- Add or update bilingual tests whenever a user-facing message or API error is added.
