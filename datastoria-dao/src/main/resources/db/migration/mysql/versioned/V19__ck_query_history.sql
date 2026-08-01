-- V19__ck_query_history.sql (MySQL dialect)
--
-- Per-user ClickHouse SQL query history. One row per saved executed query, owned by a single
-- (tenant_id, user_id) and bound to a cluster connection (ds_clickhouse_connection.id).
--
-- Dedup-on-rerun (same raw_sql for the same user+connection moves the row to the top) and the
-- 100-entry cap per (user_id, connection_id) are enforced in CkQueryHistoryService; this table is
-- hard-deleted on user request (no deleted_at) like ds_chat_session.

CREATE TABLE ds_ck_query_history (
    id              varchar(26)   NOT NULL PRIMARY KEY,
    tenant_id       varchar(64)   NOT NULL,
    user_id         varchar(255)  NOT NULL,
    connection_id   varchar(255)  NOT NULL,
    connection_name varchar(255)  NULL,
    raw_sql         text          NOT NULL,
    executed_at     datetime(6)   NOT NULL,
    created_at      datetime(6)   NOT NULL,
    UNIQUE KEY uk_ck_query_history_tenant_id (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- First-level filter (connection_id + user_id) then default time-desc ordering.
CREATE INDEX idx_ck_query_history_user_conn_time
    ON ds_ck_query_history (tenant_id, user_id, connection_id, executed_at);

-- Connection-agnostic per-user listing, time-desc.
CREATE INDEX idx_ck_query_history_user_time
    ON ds_ck_query_history (tenant_id, user_id, executed_at);
