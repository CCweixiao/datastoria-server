-- V16__agentscope_sessions.sql (MySQL dialect)
--
-- AgentScope MysqlAgentStateStore session-state table. Owned by agentscope-extensions-mysql
-- (io.agentscope.extensions.mysql.state.MysqlAgentStateStore), which issues fully-qualified
-- queries against `<database>.ds_agentscope_sessions` and requires exactly these columns and
-- primary key. Built by Flyway because DataStoria keeps Flyway as the sole runtime schema owner;
-- the store is instantiated with createIfNotExist=false so it only verifies the table exists.
--
-- Development and production both use this table through MysqlAgentStateStore.

CREATE TABLE ds_agentscope_sessions (
    session_id   varchar(255) NOT NULL,
    state_key    varchar(255) NOT NULL,
    item_index   int          NOT NULL DEFAULT 0,
    state_data   longtext     NOT NULL,
    created_at   datetime     DEFAULT CURRENT_TIMESTAMP,
    updated_at   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, state_key, item_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
