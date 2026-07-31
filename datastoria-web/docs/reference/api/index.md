# HTTP API reference

DataStoria's browser application communicates with the Spring Boot backend through JSON HTTP APIs
and Server-Sent Events. The OpenAPI contract is validated whenever this documentation site is
built and is also checked against the Java controllers and frontend call inventory by backend tests.

## OpenAPI specification

- [Download the current OpenAPI YAML](/api/openapi.yaml)
- [AI interaction guide](/manual/02-ai-features/ask-ai-for-help)

The OpenAPI file is generated from the canonical contract in `docs/api/openapi-baseline.yaml`. Do
not edit the published copy under the documentation output directory.

## Compatibility checks

The documentation pipeline rejects changes when:

- the OpenAPI document is invalid or violates the configured Redocly rules;
- a stream fixture does not conform to its JSON Schema;
- a documentation link or page cannot be built by VitePress.

Java tests separately verify that controller routes, frontend calls, stream event ordering, and
golden fixtures remain aligned with the published contract.
