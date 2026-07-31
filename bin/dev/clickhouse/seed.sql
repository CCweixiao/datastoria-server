CREATE DATABASE IF NOT EXISTS datastoria_test;

CREATE TABLE IF NOT EXISTS datastoria_test.query_events
(
    event_date Date,
    tenant_id LowCardinality(String),
    service LowCardinality(String),
    query_id UUID,
    duration_ms UInt32,
    rows_read UInt64,
    bytes_read UInt64,
    status Enum8('success' = 1, 'error' = 2),
    query String
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(event_date)
ORDER BY (tenant_id, service, event_date, query_id);

TRUNCATE TABLE datastoria_test.query_events;

INSERT INTO datastoria_test.query_events VALUES
    ('2026-07-24', 'tenant-local', 'analytics-api', generateUUIDv4(), 42, 1200, 64000, 'success', 'SELECT count() FROM events'),
    ('2026-07-24', 'tenant-local', 'analytics-api', generateUUIDv4(), 910, 900000, 48000000, 'success', 'SELECT user_id, count() FROM events GROUP BY user_id'),
    ('2026-07-25', 'tenant-local', 'reporting', generateUUIDv4(), 1500, 3000000, 160000000, 'error', 'SELECT * FROM missing_table');
