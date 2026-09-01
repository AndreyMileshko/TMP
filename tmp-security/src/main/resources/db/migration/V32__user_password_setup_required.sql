-- Credential lifecycle: users may require self-service password setup before login.
ALTER TABLE security.users
    ADD COLUMN password_setup_required BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing accounts already have a real password hash; keep setup flag off.
UPDATE security.users SET password_setup_required = FALSE WHERE password_setup_required IS DISTINCT FROM FALSE;
