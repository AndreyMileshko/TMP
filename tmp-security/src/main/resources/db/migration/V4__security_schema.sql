-- Stage 4 Security schema. Users, roles, permissions, assignments, audit.
CREATE SCHEMA IF NOT EXISTS security;

CREATE TABLE security.users (
    id UUID PRIMARY KEY,
    login VARCHAR(128) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DELETED'))
);

CREATE UNIQUE INDEX uk_users_login ON security.users (lower(login));

CREATE TABLE security.roles (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_roles_name ON security.roles (name);

CREATE TABLE security.permission_definitions (
    permission_id VARCHAR(160) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE security.role_permissions (
    role_id UUID NOT NULL,
    permission_id VARCHAR(160) NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES security.roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id)
        REFERENCES security.permission_definitions (permission_id)
);

CREATE TABLE security.user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES security.users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES security.roles (id)
);

CREATE TABLE security.user_permission_overrides (
    user_id UUID NOT NULL,
    permission_id VARCHAR(160) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_permission_overrides PRIMARY KEY (user_id, permission_id),
    CONSTRAINT chk_user_permission_overrides_decision CHECK (decision IN ('GRANT', 'REVOKE')),
    CONSTRAINT fk_user_permission_overrides_user FOREIGN KEY (user_id) REFERENCES security.users (id),
    CONSTRAINT fk_user_permission_overrides_permission FOREIGN KEY (permission_id)
        REFERENCES security.permission_definitions (permission_id)
);

CREATE TABLE security.security_audit_events (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id UUID,
    actor_login VARCHAR(128),
    operation VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(160),
    safe_description TEXT NOT NULL DEFAULT '',
    result VARCHAR(16) NOT NULL,
    CONSTRAINT chk_security_audit_events_result CHECK (result IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT fk_security_audit_events_actor FOREIGN KEY (actor_user_id) REFERENCES security.users (id)
);

CREATE INDEX idx_security_audit_events_occurred_at
    ON security.security_audit_events (occurred_at DESC);
CREATE INDEX idx_security_audit_events_target
    ON security.security_audit_events (target_type, target_id);
CREATE INDEX idx_security_audit_events_actor
    ON security.security_audit_events (actor_user_id);
