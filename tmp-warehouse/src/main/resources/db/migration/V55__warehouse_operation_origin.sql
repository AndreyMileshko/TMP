-- P1-4: durable opaque origin metadata on Warehouse operations (Production Release consumption).
-- Informational only — does not affect stock accounting. No cross-module FKs.
-- Historical rows remain NULL (UI shows «—»).

ALTER TABLE warehouse.warehouse_operations
    ADD COLUMN origin_document_type VARCHAR(64) NULL,
    ADD COLUMN origin_document_id UUID NULL,
    ADD COLUMN origin_document_number VARCHAR(128) NULL,
    ADD COLUMN origin_order_numbers TEXT NULL;

ALTER TABLE warehouse.warehouse_operations
    ADD CONSTRAINT chk_warehouse_operations_origin_complete
        CHECK (
            (
                origin_document_type IS NULL
                AND origin_document_id IS NULL
                AND origin_document_number IS NULL
                AND origin_order_numbers IS NULL
            )
            OR (
                origin_document_type IS NOT NULL
                AND btrim(origin_document_type) <> ''
                AND origin_document_id IS NOT NULL
                AND origin_document_number IS NOT NULL
                AND btrim(origin_document_number) <> ''
                AND origin_order_numbers IS NOT NULL
                AND btrim(origin_order_numbers) <> ''
            )
        );

CREATE INDEX idx_warehouse_operations_origin_document_id
    ON warehouse.warehouse_operations (origin_document_id)
    WHERE origin_document_id IS NOT NULL;
