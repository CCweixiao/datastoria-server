---
title: AI Model Configuration
description: Configure server-managed AI providers, credentials, and model catalog entries.
---

# AI Model Configuration

DataStoria's model catalog and credentials are managed by Spring Boot.

Open **Settings → Models** to:

- view enabled models returned by `/api/ai/models/available`;
- enable or disable a database-backed model;
- submit or rotate a provider credential; and
- select the model stored in your backend user preference.

On first use, Spring materializes the built-in provider/model catalog in
`ds_model_provider` and `ds_model`. Administrators can edit or replace those rows through
`/api/admin/ai/providers` and `/api/admin/ai/models`.

## Credential security

The browser holds a newly typed credential only until the save request completes. Spring encrypts
it into `ds_secret`; API responses contain only a configured flag and masked hint. Credentials are
never accepted by chat, skill-review, or model-discovery requests.

## Adding a provider or model

Use the backend admin APIs to create a provider, save its credential, discover supported models,
and create or update catalog entries. The frontend deliberately contains no provider SDKs or
hard-coded executable model catalog.
