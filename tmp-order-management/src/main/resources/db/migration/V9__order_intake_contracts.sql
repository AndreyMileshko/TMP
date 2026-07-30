-- STAGE5-051: Order Intake contracts — externalPositionNumber, specification line model,
-- incomplete commercial DRAFT (ADR-030). Compatible upgrade from V8; existing rows preserved.

-- Customer order: allow incomplete commercial DRAFT (ADR-030).
ALTER TABLE order_management.orders
    ALTER COLUMN customer_name DROP NOT NULL;

ALTER TABLE order_management.orders
    ALTER COLUMN direction DROP NOT NULL;

ALTER TABLE order_management.orders
    ALTER COLUMN currency DROP NOT NULL;

-- Order item: external position number from calculation export.
ALTER TABLE order_management.order_items
    ADD COLUMN external_position_number VARCHAR(128);

-- Specification lines: lineQuantity, color, lengthMm; remove consumptionNorm.
ALTER TABLE order_management.item_specification_lines
    RENAME COLUMN quantity TO line_quantity;

ALTER TABLE order_management.item_specification_lines
    ADD COLUMN color VARCHAR(64);

ALTER TABLE order_management.item_specification_lines
    ADD COLUMN length_mm NUMERIC(19, 6);

ALTER TABLE order_management.item_specification_lines
    DROP COLUMN consumption_norm;

ALTER TABLE order_management.item_specification_lines
    ADD CONSTRAINT chk_item_specification_lines_length_mm
        CHECK (length_mm IS NULL OR length_mm > 0);

-- Typed payload: incomplete commercial DRAFT on order create/update.
ALTER TABLE order_management.order_create_payload
    ALTER COLUMN customer_name DROP NOT NULL;

ALTER TABLE order_management.order_create_payload
    ALTER COLUMN direction DROP NOT NULL;

ALTER TABLE order_management.order_create_payload
    ALTER COLUMN currency_code DROP NOT NULL;

ALTER TABLE order_management.order_update_payload
    ALTER COLUMN customer_name DROP NOT NULL;

ALTER TABLE order_management.order_update_payload
    ALTER COLUMN direction DROP NOT NULL;

ALTER TABLE order_management.order_update_payload
    ALTER COLUMN currency_code DROP NOT NULL;

-- Order item payloads: external position number.
ALTER TABLE order_management.order_item_create_payload
    ADD COLUMN external_position_number VARCHAR(128);

ALTER TABLE order_management.order_item_update_payload
    ADD COLUMN external_position_number VARCHAR(128);

-- Revision payload lines: same specification line contract as aggregates.
ALTER TABLE order_management.order_item_revision_payload_line
    RENAME COLUMN quantity TO line_quantity;

ALTER TABLE order_management.order_item_revision_payload_line
    ADD COLUMN color VARCHAR(64);

ALTER TABLE order_management.order_item_revision_payload_line
    ADD COLUMN length_mm NUMERIC(18, 4);

ALTER TABLE order_management.order_item_revision_payload_line
    DROP COLUMN consumption_norm;

ALTER TABLE order_management.order_item_revision_payload_line
    ADD CONSTRAINT chk_order_item_revision_payload_line_length_mm
        CHECK (length_mm IS NULL OR length_mm > 0);
