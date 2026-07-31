-- STAGE5-052A: allow NULL product_code / item_name for incomplete DRAFT order items (ADR-030).
-- Completeness for ACTIVE/approved items is enforced by domain approval gates, not status-dependent CHECK.

ALTER TABLE order_management.order_items
    ALTER COLUMN product_code DROP NOT NULL,
    ALTER COLUMN item_name DROP NOT NULL;

ALTER TABLE order_management.order_item_create_payload
    ALTER COLUMN product_code DROP NOT NULL,
    ALTER COLUMN item_name DROP NOT NULL;

ALTER TABLE order_management.order_item_update_payload
    ALTER COLUMN product_code DROP NOT NULL,
    ALTER COLUMN item_name DROP NOT NULL;
