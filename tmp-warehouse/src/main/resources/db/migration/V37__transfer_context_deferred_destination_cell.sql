-- Stage 3.5.3: destination storage cell may be absent until receive (ADR-037).
-- Additive relaxation only: existing rows keep their cell; composite FK unchanged for non-null values.

ALTER TABLE warehouse.transfer_operation_context
    ALTER COLUMN destination_storage_cell_id DROP NOT NULL;
