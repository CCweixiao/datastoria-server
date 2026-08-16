---
title: Agent Runtime Settings & Governance
description: Configuration layers, administrator overrides, and permission boundaries for DataStoria agent runtime knobs (iterations, compaction, tool-result eviction, graceful shutdown).
head:
  - - meta
    - name: keywords
      content: DataStoria agent settings, agent runtime parameters, context compaction, tool result eviction, max iterations, graceful shutdown, AgentScope configuration
---

# Agent Runtime Settings & Governance

DataStoria's AI agent (the AgentScope Harness runtime) exposes a set of server-owned runtime knobs
that bound the reasoning/tool loop per message, decide when context compaction triggers, control how
oversized tool results are handled, and define the settlement window for in-flight runs at shutdown.
These knobs are **never request-settable and never exposed to ordinary users**; they resolve through
two layers:

```text
Tenant-level admin override (Settings → AI → Agent → runtime settings, stored in the database)
        ↓ for knobs not overridden
Process defaults (DATASTORIA_AGENT_* environment variables in conf/datastoria.env)
```

Values from either layer are clamped server-side to absolute ranges; a hand-edited database entry is
re-clamped on every read.

## Configurable runtime knobs

| Knob | Environment variable (process default) | Absolute range | Effect |
|---|---|---|---|
| Max iterations | `DATASTORIA_AGENT_MAX_ITERS` (default 25) | 1–100 | Upper bound on the reasoning → tool loop per message. Multi-tool skill workflows (generate SQL → validate → execute → chart) routinely need 10+ rounds; when the bound is exhausted the model wraps up early |
| Tool-result eviction (chars) | `DATASTORIA_AGENT_TOOL_RESULT_EVICTION_CHARS` (default 32768) | ≥ 2048 | Tool results larger than this are offloaded to disk with a bounded preview left in context; `execute_sql` results additionally get their own row/cell/total trimming at the tool layer |
| Compaction trigger ratio | `DATASTORIA_AGENT_COMPACTION_TRIGGER_RATIO` (default 0.8) | 0.1–0.95 | Compaction triggers at model context window × this ratio. The window comes from the model capability `contextWindowTokens` (Settings → AI → Models) |
| Fallback context tokens | `DATASTORIA_AGENT_COMPACTION_FALLBACK_CONTEXT_TOKENS` (default 100000) | ≥ 8192 | Window assumed when a model does not advertise one; the default combination (0.8 × 100K = 80K) matches the framework's historical behavior |

Two process-level settings never take tenant overrides and are environment-only:

| Setting | Environment variable | Effect |
|---|---|---|
| Runtime data directory | `DATASTORIA_AGENT_DATA_DIR` (default `~/.datastoria.agent`) | Root for AgentScope runtime data (offloaded tool results etc.); created at startup, unusable paths fail fast |
| Graceful shutdown wait | `DATASTORIA_AGENT_SHUTDOWN_TIMEOUT_SECONDS` (default 20) | Max time to settle in-flight agent runs after SIGTERM; the stop script waits longer (30 seconds) before force-killing |

## Administrator configuration

The **Agent Runtime Settings (administrator)** block at the bottom of Settings → AI → Agent:

- Visible and editable by administrators (ADMIN role) only; hidden for ordinary users.
- An empty field means "follow the config-file default" (shown as the placeholder).
- Saving writes the tenant-level entry (`settings.ai.agent.harness`) and applies to every agent run
  in the tenant immediately — the next message uses the new values; no restart is needed.
- The API is `GET/PUT /api/admin/ai/harness-settings` with `If-Match` optimistic locking;
  non-administrators get 403.

The remaining options on the same page (context pruning, output reasoning, response language,
error auto-explain) are per-account preferences of the signed-in administrator and do not affect
other users.

## Permission boundaries

Both **Settings → SQL → Query Context** and **Settings → AI → Agent** are administrator-only:

- Ordinary users do not see these entries; their settings dialog falls back to the UI page.
- Query-context ClickHouse session settings ship with every query and are clamped by the server
  guardrails (requests may only tighten them) — platform-level configuration, hence admin-only.
- Models and Skills pages are unaffected: models remain admin-managed, skills stay read-only for
  everyone.

## Runtime behavior notes

- **Context compaction**: past the trigger threshold, the conversation prefix is summarized into a
  structured summary while recent messages are kept verbatim; if the model reports a context
  overflow anyway, the framework force-compacts and retries once. Compaction uses a redacted wrapper
  of the main model — prompts and credentials are never echoed into logs.
- **Tool-result protection**: `execute_sql` results are bounded before entering the conversation
  (≤ 200 rows, ≤ 2000 chars per value, ~192K chars total) with a `truncated` flag and rewrite
  guidance; other tool results beyond the eviction threshold are offloaded to the data directory
  with a preview. Together these make wide results *degrade gracefully* instead of failing outright.
- **Graceful shutdown**: after SIGTERM the runtime stops accepting new reasoning/acting, interrupts
  in-flight runs, and settles state; `bin/datastoria stop` sends SIGTERM, waits up to 30 seconds,
  then SIGKILLs.

## Related docs

- [Installation & Setup](../01-getting-started/installation.md): the full environment variable
  reference.
- [AI Model Configuration](./ai-model-configuration.md): how the model capability
  `contextWindowTokens` feeds the compaction threshold.
- [Agent Skills](./skills.md): the bundled skill catalog.
