-- Stage 3.5.7: immutable shortfall continuation lineage on Transfer Document payload.
-- Warehouse-internal self-FK only (no cross-schema Document Engine FK).
-- Existing rows remain valid with NULL lineage (ordinary transfers).

ALTER TABLE warehouse.transfer_document_payload
    ADD COLUMN continuation_of_document_id UUID NULL,
    ADD COLUMN continuation_reason VARCHAR(32) NULL;

ALTER TABLE warehouse.transfer_document_payload
    ADD CONSTRAINT fk_transfer_document_continuation_of
        FOREIGN KEY (continuation_of_document_id)
        REFERENCES warehouse.transfer_document_payload (document_id);

ALTER TABLE warehouse.transfer_document_payload
    ADD CONSTRAINT chk_transfer_document_continuation_lineage_pair
        CHECK (
            (continuation_of_document_id IS NULL AND continuation_reason IS NULL)
            OR (continuation_of_document_id IS NOT NULL AND continuation_reason IS NOT NULL)
        );

CREATE INDEX idx_transfer_document_payload_continuation_of
    ON warehouse.transfer_document_payload (continuation_of_document_id)
    WHERE continuation_of_document_id IS NOT NULL;
