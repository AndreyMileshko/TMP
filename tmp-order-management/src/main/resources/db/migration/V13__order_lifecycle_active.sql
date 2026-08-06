-- STAGE5-058 / ADR-031: uniform ACTIVE lifecycle; drop import-metadata protection.
-- Upgrade from V12. Existing aggregate data preserved where possible.

-- RevisionStatus: APPROVED → ACTIVE (language alignment with Order/Item ACTIVE).
-- Drop legacy constraint first so ACTIVE is allowed during data migration.
ALTER TABLE order_management.order_item_revisions
    DROP CONSTRAINT chk_order_item_revisions_status;

UPDATE order_management.order_item_revisions
SET revision_status = 'ACTIVE'
WHERE revision_status = 'APPROVED';

ALTER TABLE order_management.order_item_revisions
    ADD CONSTRAINT chk_order_item_revisions_status
        CHECK (revision_status IN ('DRAFT', 'ACTIVE'));

-- OrderStatus: allow ACTIVE (manual APPROVED→ACTIVE and import landing).
ALTER TABLE order_management.orders
    DROP CONSTRAINT chk_orders_status;

ALTER TABLE order_management.orders
    ADD CONSTRAINT chk_orders_status
        CHECK (status IN ('DRAFT', 'APPROVED', 'ACTIVE', 'CANCELLED'));

-- Remove checksum / import-metadata duplicate protection (ADR-031).
DROP TABLE IF EXISTS order_management.order_import_metadata;
