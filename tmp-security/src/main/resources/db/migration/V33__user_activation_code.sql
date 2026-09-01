-- One-time activation credentials for password setup (hash + expiry only; plaintext never stored).
ALTER TABLE security.users
    ADD COLUMN activation_code_hash VARCHAR(255),
    ADD COLUMN activation_code_expires_at TIMESTAMP WITH TIME ZONE;
