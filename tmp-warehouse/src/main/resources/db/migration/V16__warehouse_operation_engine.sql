-- STAGE6-006: Warehouse Operation Engine — lifecycle DRAFT + execution payload columns.
-- Aligns warehouse_operations with Operation Engine (create / execute / result).

ALTER TABLE warehouse.warehouse_operations
    DROP CONSTRAINT chk_warehouse_operations_status;

UPDATE warehouse.warehouse_operations
SET status = 'DRAFT'
WHERE status = 'CREATED';

ALTER TABLE warehouse.warehouse_operations
    ADD COLUMN warehouse_id UUID,
    ADD COLUMN storage_cell_id UUID,
    ADD COLUMN material_reference VARCHAR(128),
    ADD COLUMN quantity NUMERIC(19, 6),
    ADD COLUMN stock_state VARCHAR(32);

ALTER TABLE warehouse.warehouse_operations
    ADD CONSTRAINT chk_warehouse_operations_status
        CHECK (status IN ('DRAFT', 'COMPLETED', 'FAILED'));

ALTER TABLE warehouse.warehouse_operations
    ADD CONSTRAINT chk_warehouse_operations_material_non_blank
        CHECK (material_reference IS NULL OR btrim(material_reference) <> '');

ALTER TABLE warehouse.warehouse_operations
    ADD CONSTRAINT chk_warehouse_operations_quantity_non_negative
        CHECK (quantity IS NULL OR quantity >= 0);

ALTER TABLE warehouse.warehouse_operations
    ADD CONSTRAINT chk_warehouse_operations_stock_state
        CHECK (stock_state IS NULL OR stock_state IN ('AVAILABLE', 'IN_TRANSIT', 'BLOCKED'));

ALTER TABLE warehouse.warehouse_operations
    ADD CONSTRAINT fk_warehouse_operations_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse.warehouses (id);

ALTER TABLE warehouse.warehouse_operations
    ADD CONSTRAINT fk_warehouse_operations_storage_cell_warehouse
        FOREIGN KEY (storage_cell_id, warehouse_id)
        REFERENCES warehouse.storage_cells (id, warehouse_id);

CREATE INDEX idx_warehouse_operations_warehouse_id
    ON warehouse.warehouse_operations (warehouse_id);
CREATE INDEX idx_warehouse_operations_status
    ON warehouse.warehouse_operations (status);
