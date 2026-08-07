-- Stage 6 Warehouse persistence schema (Warehouse Specification §5-10, §19).
-- Owns warehouse stock state only. No Material Master, Batch, Reservation or Production tables.
CREATE SCHEMA IF NOT EXISTS warehouse;

CREATE TABLE warehouse.warehouses (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_warehouses_code UNIQUE (code),
    CONSTRAINT chk_warehouses_code_non_blank CHECK (btrim(code) <> ''),
    CONSTRAINT chk_warehouses_name_non_blank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_warehouses_version CHECK (version >= 0)
);

CREATE TABLE warehouse.storage_cells (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_storage_cells_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse.warehouses (id),
    CONSTRAINT uk_storage_cells_warehouse_code UNIQUE (warehouse_id, code),
    CONSTRAINT uk_storage_cells_id_warehouse UNIQUE (id, warehouse_id),
    CONSTRAINT chk_storage_cells_code_non_blank CHECK (btrim(code) <> ''),
    CONSTRAINT chk_storage_cells_version CHECK (version >= 0)
);

CREATE INDEX idx_storage_cells_warehouse_id
    ON warehouse.storage_cells (warehouse_id);

CREATE TABLE warehouse.stock_positions (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL,
    storage_cell_id UUID NOT NULL,
    material_reference VARCHAR(128) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    stock_state VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_stock_positions_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse.warehouses (id),
    CONSTRAINT fk_stock_positions_storage_cell_warehouse
        FOREIGN KEY (storage_cell_id, warehouse_id)
        REFERENCES warehouse.storage_cells (id, warehouse_id),
    CONSTRAINT uk_stock_positions_identity
        UNIQUE (warehouse_id, storage_cell_id, material_reference, stock_state),
    CONSTRAINT chk_stock_positions_material_non_blank CHECK (btrim(material_reference) <> ''),
    CONSTRAINT chk_stock_positions_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT chk_stock_positions_stock_state
        CHECK (stock_state IN ('AVAILABLE', 'IN_TRANSIT', 'BLOCKED')),
    CONSTRAINT chk_stock_positions_version CHECK (version >= 0)
);

CREATE INDEX idx_stock_positions_warehouse_id
    ON warehouse.stock_positions (warehouse_id);
CREATE INDEX idx_stock_positions_storage_cell_id
    ON warehouse.stock_positions (storage_cell_id);
CREATE INDEX idx_stock_positions_material_reference
    ON warehouse.stock_positions (material_reference);

CREATE TABLE warehouse.warehouse_operations (
    id UUID PRIMARY KEY,
    operation_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_warehouse_operations_type
        CHECK (operation_type IN (
            'RECEIPT',
            'MOVE',
            'TRANSFER',
            'RESERVATION',
            'CONSUMPTION',
            'ADJUSTMENT',
            'INVENTORY'
        )),
    CONSTRAINT chk_warehouse_operations_status
        CHECK (status IN ('CREATED', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_warehouse_operations_version CHECK (version >= 0)
);

CREATE TABLE warehouse.warehouse_movements (
    id UUID PRIMARY KEY,
    stock_position_id UUID NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    quantity_delta NUMERIC(19, 6) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_warehouse_movements_stock_position
        FOREIGN KEY (stock_position_id) REFERENCES warehouse.stock_positions (id),
    CONSTRAINT chk_warehouse_movements_operation_type
        CHECK (operation_type IN (
            'RECEIPT',
            'MOVE',
            'TRANSFER',
            'RESERVATION',
            'CONSUMPTION',
            'ADJUSTMENT',
            'INVENTORY'
        ))
);

CREATE INDEX idx_warehouse_movements_stock_position_id
    ON warehouse.warehouse_movements (stock_position_id);
CREATE INDEX idx_warehouse_movements_created_at
    ON warehouse.warehouse_movements (created_at);
