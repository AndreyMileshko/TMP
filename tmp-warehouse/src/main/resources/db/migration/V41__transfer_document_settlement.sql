-- Stage 3.5.8.1: Transfer Document post-send operational settlement + receipt items.
-- Additive only. Backfills existing POSTED warehouse.transfer payloads to AWAITING_RECEIPT.
-- No permanent cross-schema FK to documents.documents; one-time backfill join only.

-- Same-document integrity key for receipt items → send allocations.
ALTER TABLE warehouse.transfer_document_send_allocation
    ADD CONSTRAINT uk_send_allocation_document_id_id
        UNIQUE (document_id, id);

CREATE TABLE warehouse.transfer_document_settlement (
    document_id UUID PRIMARY KEY,
    settlement_state VARCHAR(32) NOT NULL,
    operational_revision BIGINT NOT NULL DEFAULT 0,
    decision VARCHAR(16) NULL,
    rejection_reason VARCHAR(500) NULL,
    rejected_at TIMESTAMP WITH TIME ZONE NULL,
    rejected_by UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transfer_document_settlement_payload
        FOREIGN KEY (document_id)
        REFERENCES warehouse.transfer_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_transfer_document_settlement_state
        CHECK (settlement_state IN ('AWAITING_RECEIPT', 'RETURN_PENDING', 'SETTLED')),
    CONSTRAINT chk_transfer_document_settlement_revision
        CHECK (operational_revision >= 0),
    CONSTRAINT chk_transfer_document_settlement_decision
        CHECK (decision IS NULL OR decision IN ('ACCEPTED', 'REJECTED')),
    CONSTRAINT chk_transfer_document_settlement_rejection_metadata
        CHECK (
            (
                decision IS DISTINCT FROM 'REJECTED'
                AND rejection_reason IS NULL
                AND rejected_at IS NULL
                AND rejected_by IS NULL
            )
            OR (
                decision = 'REJECTED'
                AND rejection_reason IS NOT NULL
                AND btrim(rejection_reason) <> ''
                AND rejected_at IS NOT NULL
                AND rejected_by IS NOT NULL
            )
        )
);

CREATE TABLE warehouse.transfer_receipt_settlement_item (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    send_allocation_id UUID NOT NULL,
    destination_storage_cell_id UUID NOT NULL
        REFERENCES warehouse.storage_cells (id),
    quantity NUMERIC(19, 6) NOT NULL,
    receive_operation_id UUID NOT NULL
        REFERENCES warehouse.warehouse_operations (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_receipt_item_payload
        FOREIGN KEY (document_id)
        REFERENCES warehouse.transfer_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_receipt_item_send_allocation_same_document
        FOREIGN KEY (document_id, send_allocation_id)
        REFERENCES warehouse.transfer_document_send_allocation (document_id, id),
    CONSTRAINT chk_receipt_item_quantity
        CHECK (quantity > 0),
    CONSTRAINT uq_receipt_item_receive_operation_id
        UNIQUE (receive_operation_id)
);

CREATE INDEX idx_receipt_settlement_item_document_id
    ON warehouse.transfer_receipt_settlement_item (document_id);

CREATE INDEX idx_receipt_settlement_item_send_allocation_id
    ON warehouse.transfer_receipt_settlement_item (send_allocation_id);

-- One-time compatibility backfill: POSTED Document Engine docs with warehouse.transfer payload.
INSERT INTO warehouse.transfer_document_settlement (
    document_id,
    settlement_state,
    operational_revision,
    decision,
    rejection_reason,
    rejected_at,
    rejected_by,
    created_at,
    updated_at
)
SELECT
    p.document_id,
    'AWAITING_RECEIPT',
    0,
    NULL,
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM warehouse.transfer_document_payload p
INNER JOIN documents.documents d ON d.id = p.document_id
WHERE d.status = 'POSTED'
  AND d.document_type_id = 'warehouse.transfer'
  AND NOT EXISTS (
        SELECT 1
          FROM warehouse.transfer_document_settlement s
         WHERE s.document_id = p.document_id
  );
