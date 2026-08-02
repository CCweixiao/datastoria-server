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
3. Call `prepare_ddl_approval`. This compiles and saves a draft but does not submit or execute it.
4. Show the returned ordered statements, risk level, rule summary, draft id, revision, and content
   digest. Explain that editing any statement requires preparing the draft again.
5. Call `submit_ddl_approval` only after the user explicitly confirms the displayed digest or asks
   in the current conversation to submit a validated draft automatically. Use exactly the returned
   draft id, revision, and digest; never construct replacements.
6. Use `get_approval_status` for later status questions.

Approval, rejection, execution, retry, and reconciliation are administrator-only UI operations.
There are no Agent tools for those actions.
