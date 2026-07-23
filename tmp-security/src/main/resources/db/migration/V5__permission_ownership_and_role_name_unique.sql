-- Stage 4 corrective: permission ownership + case-insensitive unique role names.
-- Does not modify V4.

ALTER TABLE security.permission_definitions
    ADD COLUMN IF NOT EXISTS owner_capability_id VARCHAR(160);

UPDATE security.permission_definitions
SET owner_capability_id = 'legacy.unassigned'
WHERE owner_capability_id IS NULL;

ALTER TABLE security.permission_definitions
    ALTER COLUMN owner_capability_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_permission_definitions_owner
    ON security.permission_definitions (owner_capability_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_roles_name
    ON security.roles (lower(name));
