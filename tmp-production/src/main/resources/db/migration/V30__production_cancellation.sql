-- STAGE7-014: Production-owned whole-order Cancellation document payload.
-- Linked to Document Engine by document_id. No Warehouse / OM / Cutting FK.
CREATE TABLE production.production_cancellations (
    document_id UUID PRIMARY KEY,
    source_order_id UUID NOT NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason_text VARCHAR(1024),
    posted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_production_cancellations_source_order_id UNIQUE (source_order_id)
);

CREATE TABLE production.production_cancellation_item_lines (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    source_order_item_id UUID NOT NULL,
    specification_id UUID NOT NULL,
    previous_status VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    active_quantity_cancelled NUMERIC(19, 0) NOT NULL,
    released_quantity_preserved NUMERIC(19, 0) NOT NULL,
    line_order INT NOT NULL,
    CONSTRAINT fk_production_cancellation_item_lines_cancellation
        FOREIGN KEY (document_id)
        REFERENCES production.production_cancellations (document_id),
    CONSTRAINT uk_production_cancellation_item_lines_document_item
        UNIQUE (document_id, source_order_item_id),
    CONSTRAINT uk_production_cancellation_item_lines_document_order
        UNIQUE (document_id, line_order),
    CONSTRAINT chk_production_cancellation_item_lines_previous_status
        CHECK (previous_status IN (
            'IN_PRODUCTION', 'PARTIALLY_RELEASED', 'RELEASED', 'CANCELLED')),
    CONSTRAINT chk_production_cancellation_item_lines_action
        CHECK (action IN ('CANCELLED_UNFINISHED', 'PRESERVED_RELEASED')),
    CONSTRAINT chk_production_cancellation_item_lines_active
        CHECK (active_quantity_cancelled >= 0),
    CONSTRAINT chk_production_cancellation_item_lines_released
        CHECK (released_quantity_preserved >= 0)
);

CREATE INDEX idx_production_cancellation_item_lines_document_id
    ON production.production_cancellation_item_lines (document_id);
