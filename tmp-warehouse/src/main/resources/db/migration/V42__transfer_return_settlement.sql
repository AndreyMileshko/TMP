-- Stage 3.5.8.3: TRANSFER_RETURN operation type + transfer return settlement items.
-- Additive only. Preserves all previously allowed operation/movement types.

ALTER TABLE warehouse.warehouse_operations
    DROP CONSTRAINT chk_warehouse_operations_type;

ALTER TABLE warehouse.warehouse_operations
    ADD CONSTRAINT chk_warehouse_operations_type
        CHECK (operation_type IN (
            'RECEIPT',
            'MOVE',
            'TRANSFER_SEND',
            'TRANSFER_RECEIVE',
            'TRANSFER_RETURN',
            'RESERVATION',
            'CONSUMPTION',
            'ADJUSTMENT',
            'INVENTORY'
        ));

ALTER TABLE warehouse.warehouse_movements
    DROP CONSTRAINT chk_warehouse_movements_operation_type;

ALTER TABLE warehouse.warehouse_movements
    ADD CONSTRAINT chk_warehouse_movements_operation_type
        CHECK (operation_type IN (
            'RECEIPT',
            'MOVE',
            'TRANSFER_SEND',
            'TRANSFER_RECEIVE',
            'TRANSFER_RETURN',
            'RESERVATION',
            'CONSUMPTION',
            'ADJUSTMENT',
            'INVENTORY'
        ));

CREATE TABLE warehouse.transfer_return_settlement_item (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    send_allocation_id UUID NOT NULL,
    return_storage_cell_id UUID NOT NULL
        REFERENCES warehouse.storage_cells (id),
    quantity NUMERIC(19, 6) NOT NULL,
    return_operation_id UUID NOT NULL
        REFERENCES warehouse.warehouse_operations (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_return_item_payload
        FOREIGN KEY (document_id)
        REFERENCES warehouse.transfer_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_return_item_send_allocation_same_document
        FOREIGN KEY (document_id, send_allocation_id)
        REFERENCES warehouse.transfer_document_send_allocation (document_id, id),
    CONSTRAINT chk_return_item_quantity
        CHECK (quantity > 0),
    CONSTRAINT uq_return_item_return_operation_id
        UNIQUE (return_operation_id)
);

CREATE INDEX idx_return_settlement_item_document_id
    ON warehouse.transfer_return_settlement_item (document_id);

CREATE INDEX idx_return_settlement_item_send_allocation_id
    ON warehouse.transfer_return_settlement_item (send_allocation_id);
