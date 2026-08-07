-- STAGE6-009: Inter-warehouse Transfer stages as distinct operation types.
-- Replaces single TRANSFER with TRANSFER_SEND (AVAILABLE→IN_TRANSIT) and
-- TRANSFER_RECEIVE (IN_TRANSIT→AVAILABLE).

ALTER TABLE warehouse.warehouse_operations
    DROP CONSTRAINT chk_warehouse_operations_type;

ALTER TABLE warehouse.warehouse_operations
    ADD CONSTRAINT chk_warehouse_operations_type
        CHECK (operation_type IN (
            'RECEIPT',
            'MOVE',
            'TRANSFER_SEND',
            'TRANSFER_RECEIVE',
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
            'RESERVATION',
            'CONSUMPTION',
            'ADJUSTMENT',
            'INVENTORY'
        ));
