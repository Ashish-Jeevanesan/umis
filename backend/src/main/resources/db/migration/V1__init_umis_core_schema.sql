-- Flyway Migration V1: Core Multi-Tenant and Mailbox Connection Schema
-- UMIS (Universal Mailbox Integration Service)

CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    system_role VARCHAR(32) NOT NULL DEFAULT 'TENANT_USER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tenant_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL DEFAULT 'TENANT_VIEWER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE TABLE IF NOT EXISTS mailbox_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email_address VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    provider VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CONNECTED',
    sync_strategy VARCHAR(32) NOT NULL DEFAULT 'POLLING',
    polling_interval_minutes INT NOT NULL DEFAULT 5,
    last_sync_at TIMESTAMP WITH TIME ZONE,
    last_successful_sync_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(1024),
    failure_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_mailbox_email UNIQUE (tenant_id, email_address)
);

CREATE TABLE IF NOT EXISTS mailbox_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mailbox_id UUID NOT NULL UNIQUE REFERENCES mailbox_connections(id) ON DELETE CASCADE,
    auth_type VARCHAR(32) NOT NULL,
    encrypted_access_token TEXT,
    encrypted_refresh_token TEXT,
    encrypted_secret TEXT,
    token_expires_at TIMESTAMP WITH TIME ZONE,
    scope VARCHAR(1024),
    server_host VARCHAR(255),
    server_port INT,
    use_ssl BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mailbox_sync_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mailbox_id UUID NOT NULL UNIQUE REFERENCES mailbox_connections(id) ON DELETE CASCADE,
    cursor_value VARCHAR(1024),
    last_history_id VARCHAR(255),
    last_uid BIGINT,
    folder_name VARCHAR(128) NOT NULL DEFAULT 'INBOX',
    sync_lock_token VARCHAR(128),
    sync_lock_acquired_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mailbox_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mailbox_id UUID NOT NULL REFERENCES mailbox_connections(id) ON DELETE CASCADE,
    subscription_id VARCHAR(255) NOT NULL UNIQUE,
    client_state VARCHAR(255),
    notification_url VARCHAR(1024),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenant_users_tenant ON tenant_users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_users_user ON tenant_users(user_id);
CREATE INDEX IF NOT EXISTS idx_mailbox_conn_tenant ON mailbox_connections(tenant_id);
CREATE INDEX IF NOT EXISTS idx_mailbox_conn_status ON mailbox_connections(status);
CREATE INDEX IF NOT EXISTS idx_mailbox_subscriptions_expiry ON mailbox_subscriptions(expires_at);
