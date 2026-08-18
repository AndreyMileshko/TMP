-- STAGE7-000B: Transfer draft destination context (Warehouse-owned; no stock mutation on draft create).

CREATE TABLE warehouse.transfer_operation_context (
    operation_id UUID PRIMARY KEY
        REFERENCES warehouse.warehouse_operations (id),
    destination_warehouse_id UUID NOT NULL
        REFERENCES warehouse.warehouses (id),
    destination_storage_cell_id UUID NOT NULL,
    CONSTRAINT fk_transfer_context_destination_cell
        FOREIGN KEY (destination_storage_cell_id, destination_warehouse_id)
        REFERENCES warehouse.storage_cells (id, warehouse_id)
);

CREATE INDEX idx_transfer_operation_context_destination
    ON warehouse.transfer_operation_context (destination_warehouse_id);
