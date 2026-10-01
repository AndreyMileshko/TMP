-- Stage 7: Production-owned per-order quantity mode setting (STANDARD / FLEXIBLE).
-- Not a Document Engine document, not a Production Order, not a Production state.
-- A missing row means STANDARD. source_order_id is an opaque Order Management reference (no FK).
CREATE TABLE production.order_quantity_modes (
    source_order_id UUID PRIMARY KEY,
    quantity_mode VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_order_quantity_modes_quantity_mode
        CHECK (quantity_mode IN ('STANDARD', 'FLEXIBLE')),
    CONSTRAINT chk_order_quantity_modes_version
        CHECK (version >= 1)
);
