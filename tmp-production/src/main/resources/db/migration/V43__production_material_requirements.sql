-- Stage 3.5.9: Production-owned editable Material Requirement (DRAFT).
-- Not a Document Engine document. No Warehouse / OM / Cutting FK.
-- No source warehouse / recommendation / availability columns.
CREATE TABLE production.material_requirements (
    id UUID PRIMARY KEY,
    source_order_id UUID NOT NULL,
    destination_warehouse_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT chk_material_requirements_version
        CHECK (version >= 0),
    CONSTRAINT chk_material_requirements_status
        CHECK (status = 'DRAFT')
);

CREATE INDEX idx_material_requirements_source_order_id
    ON production.material_requirements (source_order_id);

CREATE TABLE production.material_requirement_lines (
    id UUID PRIMARY KEY,
    requirement_id UUID NOT NULL,
    material_reference_id UUID NOT NULL,
    material_code VARCHAR(128) NOT NULL,
    material_name VARCHAR(512),
    color VARCHAR(128) NOT NULL DEFAULT '',
    unit_of_measure VARCHAR(64) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    line_order INT NOT NULL,
    CONSTRAINT fk_material_requirement_lines_requirement
        FOREIGN KEY (requirement_id)
        REFERENCES production.material_requirements (id),
    CONSTRAINT chk_material_requirement_lines_quantity
        CHECK (quantity > 0),
    CONSTRAINT uk_material_requirement_lines_requirement_order
        UNIQUE (requirement_id, line_order)
);

CREATE INDEX idx_material_requirement_lines_requirement_id
    ON production.material_requirement_lines (requirement_id);

CREATE TABLE production.material_requirement_line_source_items (
    line_id UUID NOT NULL,
    source_order_item_id UUID NOT NULL,
    CONSTRAINT pk_material_requirement_line_source_items
        PRIMARY KEY (line_id, source_order_item_id),
    CONSTRAINT fk_material_requirement_line_source_items_line
        FOREIGN KEY (line_id)
        REFERENCES production.material_requirement_lines (id)
);
