-- V17__user_account.sql (MySQL dialect)
--
-- Local user accounts for username+password authentication. user_id is an application-assigned
-- ULID (opaque owner key reused as owner_user_id across the rest of the schema); tenant_id is a
-- single default value until multi-organization support is introduced. password_hash is nullable
-- so that future SSO-only accounts can share this table. Uses MySQL 5.7 types: varchar, tinyint,
-- datetime(6).

CREATE TABLE ds_user_account (
    user_id         varchar(64)  NOT NULL PRIMARY KEY,
    tenant_id       varchar(64)  NOT NULL DEFAULT 'default',
    username        varchar(64)  NOT NULL,
    email           varchar(255) NULL,
    password_hash   varchar(255) NULL,
    role            varchar(32)  NOT NULL DEFAULT 'USER',
    status          tinyint      NOT NULL DEFAULT 1,
    token_version   int          NOT NULL DEFAULT 1,
    created_at      datetime(6)  NOT NULL,
    updated_at      datetime(6)  NOT NULL,
    CONSTRAINT chk_user_role CHECK (role IN ('USER','ADMIN')),
    CONSTRAINT chk_user_status CHECK (status IN (0,1)),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_user_account_tenant
    ON ds_user_account (tenant_id, status);
