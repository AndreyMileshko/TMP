-- Stage 3.5.6: Transfer Document physical send execution links (document→line→cell→TRANSFER_SEND).
-- Same-document line integrity via composite FK; no cross-schema FK to documents.documents.

ALTER TABLE warehouse.transfer_document_lines
    ADD CONSTRAINT uk_transfer_document_lines_document_id_id
        UNIQUE (document_id, id);

CREATE TABLE warehouse.transfer_document_send_allocation (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    line_id UUID NOT NULL,
    source_storage_cell_id UUID NOT NULL
        REFERENCES warehouse.storage_cells (id),
    quantity NUMERIC(19, 6) NOT NULL,
    send_operation_id UUID NULL
        REFERENCES warehouse.warehouse_operations (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_send_allocation_payload
        FOREIGN KEY (document_id)
        REFERENCES warehouse.transfer_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_send_allocation_line_same_document
        FOREIGN KEY (document_id, line_id)
        REFERENCES warehouse.transfer_document_lines (document_id, id),
    CONSTRAINT chk_send_allocation_quantity
        CHECK (quantity > 0),
    CONSTRAINT uq_send_allocation_send_operation_id
        UNIQUE (send_operation_id)
);

CREATE INDEX idx_send_allocation_document_id
    ON warehouse.transfer_document_send_allocation (document_id);
