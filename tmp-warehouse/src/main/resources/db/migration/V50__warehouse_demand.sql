-- Phase B3B-1: Warehouse-owned Demand aggregate foundation.
-- Operational warehouse need accepted from Production Material Requirement.
-- Demand ≠ Transfer. No Production cross-capability FK. No persisted status/received counters.

CREATE TABLE warehouse.warehouse_demands (
    id UUID PRIMARY KEY,
    source_material_requirement_id UUID NOT NULL,
    destination_warehouse_id UUID NOT NULL
        REFERENCES warehouse.warehouses (id),
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_by VARCHAR(256) NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE NULL,
    cancelled_by VARCHAR(256) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_warehouse_demands_source_material_requirement
        UNIQUE (source_material_requirement_id),
    CONSTRAINT chk_warehouse_demands_version
        CHECK (version >= 0),
    CONSTRAINT chk_warehouse_demands_cancellation_pair
        CHECK (
            (cancelled_at IS NULL AND cancelled_by IS NULL)
            OR (cancelled_at IS NOT NULL)
        )
);

CREATE TABLE warehouse.warehouse_demand_lines (
    id UUID PRIMARY KEY,
    demand_id UUID NOT NULL,
    source_material_requirement_line_id UUID NOT NULL,
    material_code VARCHAR(128) NOT NULL,
    material_name VARCHAR(512) NULL,
    color VARCHAR(128) NOT NULL DEFAULT '',
    unit_of_measure VARCHAR(64) NOT NULL,
    length_mm NUMERIC(19, 6) NULL,
    required_quantity NUMERIC(19, 6) NOT NULL,
    material_reference_id UUID NULL
        REFERENCES warehouse.material_references (id),
    waiting_reason VARCHAR(64) NULL,
    CONSTRAINT fk_warehouse_demand_lines_demand
        FOREIGN KEY (demand_id)
        REFERENCES warehouse.warehouse_demands (id),
    CONSTRAINT uk_warehouse_demand_lines_source_line
        UNIQUE (demand_id, source_material_requirement_line_id),
    CONSTRAINT chk_warehouse_demand_lines_material_code_non_blank
        CHECK (btrim(material_code) <> ''),
    CONSTRAINT chk_warehouse_demand_lines_unit_non_blank
        CHECK (btrim(unit_of_measure) <> ''),
    CONSTRAINT chk_warehouse_demand_lines_required_quantity
        CHECK (required_quantity > 0),
    CONSTRAINT chk_warehouse_demand_lines_length_mm
        CHECK (length_mm IS NULL OR length_mm > 0),
    CONSTRAINT chk_warehouse_demand_lines_waiting_reason
        CHECK (
            waiting_reason IS NULL
            OR waiting_reason IN (
                'MATERIAL_UNMATCHED',
                'MATERIAL_AMBIGUOUS',
                'NO_AVAILABLE_STOCK',
                'ROUTING_DEFERRED'
            )
        )
);

CREATE INDEX idx_warehouse_demand_lines_demand_id
    ON warehouse.warehouse_demand_lines (demand_id);

CREATE INDEX idx_warehouse_demand_lines_material_reference_id
    ON warehouse.warehouse_demand_lines (material_reference_id);

CREATE TABLE warehouse.warehouse_demand_transfer_links (
    id UUID PRIMARY KEY,
    demand_line_id UUID NOT NULL,
    transfer_document_id UUID NOT NULL,
    transfer_line_id UUID NOT NULL,
    linked_quantity NUMERIC(19, 6) NOT NULL,
    CONSTRAINT fk_warehouse_demand_transfer_links_line
        FOREIGN KEY (demand_line_id)
        REFERENCES warehouse.warehouse_demand_lines (id),
    CONSTRAINT fk_warehouse_demand_transfer_links_document
        FOREIGN KEY (transfer_document_id)
        REFERENCES warehouse.transfer_document_payload (document_id),
    CONSTRAINT fk_warehouse_demand_transfer_links_transfer_line
        FOREIGN KEY (transfer_line_id)
        REFERENCES warehouse.transfer_document_lines (id),
    CONSTRAINT uk_warehouse_demand_transfer_links_transfer_line
        UNIQUE (transfer_line_id),
    CONSTRAINT chk_warehouse_demand_transfer_links_quantity
        CHECK (linked_quantity > 0)
);

CREATE INDEX idx_warehouse_demand_transfer_links_demand_line_id
    ON warehouse.warehouse_demand_transfer_links (demand_line_id);
