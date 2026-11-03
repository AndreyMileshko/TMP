-- ADR-038 optional resupply waiver after full Transfer receipt reject.
-- Additive only. Historical settlements default resupply_required=true; no retrospective waiver rows.

ALTER TABLE warehouse.transfer_document_settlement
    ADD COLUMN resupply_required BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN warehouse.transfer_document_settlement.resupply_required IS
    'When decision=REJECTED: true keeps Demand resupply obligation; false waives attributable linked quantities. Default true preserves pre-V57 behavior.';

CREATE TABLE warehouse.warehouse_demand_resupply_waivers (
    id UUID PRIMARY KEY,
    demand_line_id UUID NOT NULL,
    transfer_document_id UUID NOT NULL,
    waived_quantity NUMERIC(19, 6) NOT NULL,
    waived_at TIMESTAMP WITH TIME ZONE NOT NULL,
    waived_by UUID NOT NULL,
    CONSTRAINT fk_demand_resupply_waivers_line
        FOREIGN KEY (demand_line_id)
        REFERENCES warehouse.warehouse_demand_lines (id),
    CONSTRAINT fk_demand_resupply_waivers_document
        FOREIGN KEY (transfer_document_id)
        REFERENCES warehouse.transfer_document_payload (document_id),
    CONSTRAINT uk_demand_resupply_waivers_document_line
        UNIQUE (transfer_document_id, demand_line_id),
    CONSTRAINT chk_demand_resupply_waivers_quantity
        CHECK (waived_quantity > 0)
);

CREATE INDEX idx_demand_resupply_waivers_demand_line_id
    ON warehouse.warehouse_demand_resupply_waivers (demand_line_id);

CREATE INDEX idx_demand_resupply_waivers_transfer_document_id
    ON warehouse.warehouse_demand_resupply_waivers (transfer_document_id);