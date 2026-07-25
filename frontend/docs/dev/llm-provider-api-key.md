# Configuring an LLM provider credential

Provider credentials are backend resources. They must never be placed in a frontend environment
variable, browser storage, chat request, or model-discovery request.

1. Open **Settings → Models**.
2. Enter the credential for a provider returned by the Spring model catalog.
3. Leave the field or press Enter to submit it directly to Spring Boot.

Spring encrypts the credential before writing `ds_secret`. Subsequent responses expose only
`credentialConfigured` and a masked hint. Model/provider catalog administration is also available
under `/api/admin/ai/providers` and `/api/admin/ai/models`.
