-- STAGE7-000C: one completed TRANSFER_SEND has 0..1 TRANSFER_RECEIVE.

ALTER TABLE warehouse.transfer_operation_context
    ADD COLUMN receive_operation_id UUID NULL
        REFERENCES warehouse.warehouse_operations (id);

CREATE UNIQUE INDEX uq_transfer_operation_context_receive_operation_id
    ON warehouse.transfer_operation_context (receive_operation_id)
    WHERE receive_operation_id IS NOT NULL;
