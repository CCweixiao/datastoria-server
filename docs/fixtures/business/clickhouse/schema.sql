-- Sanitized ClickHouse test schema for Golden Tests of get_tables,
-- explore_schema and validate_sql tools. All host/user/credential values are
-- synthetic. Loaded into a throwaway ClickHouse instance (docker) during
-- contract tests; never points at production.

CREATE DATABASE IF NOT EXISTS ds_test;

CREATE TABLE ds_test.events
(
    event_id    UInt64,
    event_type  LowCardinality(String),
    user_id     String,
    occurred_at DateTime64(3, 'UTC'),
    payload     String
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (event_type, user_id, occurred_at)
SETTINGS index_granularity = 8192;

CREATE TABLE ds_test.users
(
    user_id   String,
    email     String,
    created_at DateTime64(3, 'UTC')
)
ENGINE = MergeTree
ORDER BY user_id;

CREATE TABLE ds_test.query_log_sample
(
    query_id        String,
    query_duration_ms UInt64,
    read_rows       UInt64,
    event_time      DateTime64(3, 'UTC')
)
ENGINE = MergeTree
ORDER BY event_time;

INSERT INTO ds_test.events (event_id, event_type, user_id, occurred_at, payload) VALUES
    (1, 'click',   'u_1', '2026-07-24 10:00:00.000', '{}'),
    (2, 'view',    'u_1', '2026-07-24 10:01:00.000', '{}'),
    (3, 'click',   'u_2', '2026-07-24 10:02:00.000', '{}');

INSERT INTO ds_test.users (user_id, email, created_at) VALUES
    ('u_1', 'alice@example.test', '2026-07-01 00:00:00.000'),
    ('u_2', 'bob@example.test',   '2026-07-02 00:00:00.000');

INSERT INTO ds_test.query_log_sample (query_id, query_duration_ms, read_rows, event_time) VALUES
    ('q_42', 12500, 1000000000, '2026-07-24 10:00:05.000'),
    ('q_43',  3200,    5000000, '2026-07-24 10:00:10.000');
