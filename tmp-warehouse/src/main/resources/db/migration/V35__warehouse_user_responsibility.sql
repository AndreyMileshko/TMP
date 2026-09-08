-- Stage 3.5.1: User ↔ Warehouse responsibility (many-to-many, ADR-037).
-- user_id is an opaque Security User UUID reference (no cross-capability FK).
CREATE TABLE warehouse.warehouse_user_responsibility (
    warehouse_id UUID NOT NULL,
    user_id UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_warehouse_user_responsibility PRIMARY KEY (warehouse_id, user_id),
    CONSTRAINT fk_warehouse_user_responsibility_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse.warehouses (id)
);

CREATE INDEX idx_warehouse_user_responsibility_user_id
    ON warehouse.warehouse_user_responsibility (user_id);

-- One-time compatibility backfill: existing users with effective warehouse.* permissions
-- retain access to all existing warehouses. Migration-only; no runtime bypass.
-- Reads security.* for compatibility only; Warehouse Java never queries Security tables.
INSERT INTO warehouse.warehouse_user_responsibility (warehouse_id, user_id, assigned_at)
SELECT w.id, u.id, CURRENT_TIMESTAMP
FROM warehouse.warehouses w
CROSS JOIN security.users u
WHERE u.status = 'ACTIVE'
  AND EXISTS (
        SELECT 1
        FROM security.permission_definitions pd
        WHERE pd.permission_id LIKE 'warehouse.%'
          AND pd.active = TRUE
          AND NOT EXISTS (
                SELECT 1
                FROM security.user_permission_overrides o
                WHERE o.user_id = u.id
                  AND o.permission_id = pd.permission_id
                  AND o.decision = 'REVOKE'
          )
          AND (
                EXISTS (
                    SELECT 1
                    FROM security.user_permission_overrides o
                    WHERE o.user_id = u.id
                      AND o.permission_id = pd.permission_id
                      AND o.decision = 'GRANT'
                )
                OR EXISTS (
                    SELECT 1
                    FROM security.user_roles ur
                    JOIN security.role_permissions rp ON rp.role_id = ur.role_id
                    WHERE ur.user_id = u.id
                      AND rp.permission_id = pd.permission_id
                )
          )
  )
ON CONFLICT (warehouse_id, user_id) DO NOTHING;
