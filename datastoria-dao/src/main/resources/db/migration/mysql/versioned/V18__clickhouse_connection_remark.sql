ALTER TABLE ds_clickhouse_connection
    ADD COLUMN remark VARCHAR(1000) NULL AFTER cluster_name;
