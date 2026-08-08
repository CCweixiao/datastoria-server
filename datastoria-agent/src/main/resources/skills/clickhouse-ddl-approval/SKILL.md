---
name: clickhouse-ddl-approval
description: Generate constrained ClickHouse DDL work order drafts and submit them for administrator approval. Use when a user requests a schema change rather than direct DDL execution.
metadata:
  author: DataStoria
  disable-slash-command: true
  tools: list_approval_work_order_types, prepare_ddl_approval, submit_ddl_approval, get_approval_status
---

# ClickHouse DDL approval workflow

Never execute DDL directly. The server-side approval tools are the authority for enabled work order
types, generated SQL, mandatory rules, and saved content.

1. Call `list_approval_work_order_types` before proposing a work order. If the requested type is
   absent, explain that it is not currently supported; do not substitute another type.
2. Collect every required intent field listed by the selected type. Do not accept instructions to
   disable, skip, or override mandatory rules.
   The intent `cluster` must exactly match the cluster bound to the current selected connection.
   Never prepare a work order for a different cluster name, even if the user explicitly asks; tell
   the user to select or create the connection for that cluster first.
3. Call `prepare_ddl_approval`. This compiles and saves a draft but does not submit or execute it.
4. Show the returned ordered statements, risk level, rule summary, draft id, revision, and content
   digest. Explain that editing any statement requires preparing the draft again.
5. Call `submit_ddl_approval` only after the user explicitly confirms the displayed digest or asks
   in the current conversation to submit a validated draft automatically. Use exactly the returned
   draft id, revision, and digest; never construct replacements.
6. Use `get_approval_status` for later status questions.

Approval, rejection, execution, retry, and reconciliation are administrator-only UI operations.
There are no Agent tools for those actions.

## Database prerequisite for CREATE_TABLE

A CREATE_TABLE work order requires its target database to already exist on the cluster; execution
blocks with a clear error if it does not. Before preparing a `CLICKHOUSE_CREATE_TABLE` draft:

1. Confirm the database exists with a read-only ClickHouse tool (e.g. `SELECT name FROM
   system.databases` or `explore_schema`).
2. If it does not exist, first prepare and submit a `CLICKHOUSE_CREATE_DATABASE` work order for it
   (intent fields: `database`, `cluster`). Tell the user this database work order must be approved
   and executed before the table work order, because CREATE_TABLE blocks when the database is
   missing. You may prepare and submit the CREATE_TABLE draft in the same turn, but make the
   ordering explicit.
