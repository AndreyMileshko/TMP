-- STAGE7-010: Material Transfer Template confirmation lifecycle + Production logical transfer
-- grouping / Warehouse draft operation references. No cross-capability FK.

ALTER TABLE production.material_transfer_templates
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN confirmed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE production.material_transfer_templates
    ADD CONSTRAINT chk_material_transfer_templates_status
        CHECK (status IN ('DRAFT', 'CONFIRMED'));

ALTER TABLE production.material_transfer_templates
    ADD CONSTRAINT chk_material_transfer_templates_confirmed_at
        CHECK (
            (status = 'DRAFT' AND confirmed_at IS NULL)
            OR (status = 'CONFIRMED' AND confirmed_at IS NOT NULL)
        );

CREATE TABLE production.material_transfers (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL,
    source_order_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_material_transfers_template_id UNIQUE (template_id),
    CONSTRAINT fk_material_transfers_template
        FOREIGN KEY (template_id)
        REFERENCES production.material_transfer_templates (id)
);

CREATE INDEX idx_material_transfers_source_order_id
    ON production.material_transfers (source_order_id);

CREATE TABLE production.material_transfer_operation_refs (
    id UUID PRIMARY KEY,
    material_transfer_id UUID NOT NULL,
    template_line_id UUID NOT NULL,
    warehouse_draft_operation_id UUID NOT NULL,
    material_reference_id UUID NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    source_storage_cell_id UUID NOT NULL,
    destination_storage_cell_id UUID NOT NULL,
    ref_order INT NOT NULL,
    CONSTRAINT fk_material_transfer_operation_refs_transfer
        FOREIGN KEY (material_transfer_id)
        REFERENCES production.material_transfers (id),
    CONSTRAINT chk_material_transfer_operation_refs_quantity
        CHECK (quantity > 0),
    CONSTRAINT uk_material_transfer_operation_refs_order
        UNIQUE (material_transfer_id, ref_order),
    CONSTRAINT uk_material_transfer_operation_refs_warehouse_op
        UNIQUE (warehouse_draft_operation_id)
);

CREATE INDEX idx_material_transfer_operation_refs_transfer_id
    ON production.material_transfer_operation_refs (material_transfer_id);
