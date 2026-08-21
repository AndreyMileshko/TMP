-- STAGE7-009: Production-owned editable Material Transfer Template.
-- Not a Document Engine document. No Warehouse / OM / Cutting FK.
CREATE TABLE production.material_transfer_templates (
    id UUID PRIMARY KEY,
    source_order_id UUID NOT NULL,
    source_warehouse_id UUID NOT NULL,
    destination_warehouse_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_material_transfer_templates_warehouses_distinct
        CHECK (source_warehouse_id <> destination_warehouse_id),
    CONSTRAINT chk_material_transfer_templates_version
        CHECK (version >= 0)
);

CREATE INDEX idx_material_transfer_templates_source_order_id
    ON production.material_transfer_templates (source_order_id);

CREATE TABLE production.material_transfer_template_lines (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL,
    material_reference_id UUID NOT NULL,
    material_code VARCHAR(128) NOT NULL,
    material_name VARCHAR(512),
    color VARCHAR(128) NOT NULL DEFAULT '',
    unit_of_measure VARCHAR(64) NOT NULL,
    recommended_quantity NUMERIC(19, 6) NOT NULL,
    requested_quantity NUMERIC(19, 6) NOT NULL,
    included BOOLEAN NOT NULL,
    planning_source VARCHAR(32) NOT NULL,
    cutting_plan_id UUID,
    cutting_link_status VARCHAR(32) NOT NULL,
    required_quantity NUMERIC(19, 6) NOT NULL,
    main_warehouse_available NUMERIC(19, 6) NOT NULL,
    production_warehouse_available NUMERIC(19, 6) NOT NULL,
    uncovered_deficit NUMERIC(19, 6) NOT NULL,
    line_order INT NOT NULL,
    CONSTRAINT fk_material_transfer_template_lines_template
        FOREIGN KEY (template_id)
        REFERENCES production.material_transfer_templates (id),
    CONSTRAINT chk_material_transfer_template_lines_recommended_quantity
        CHECK (recommended_quantity > 0),
    CONSTRAINT chk_material_transfer_template_lines_requested_quantity
        CHECK (requested_quantity >= 0),
    CONSTRAINT chk_material_transfer_template_lines_included_requested
        CHECK ((included = FALSE) OR (requested_quantity > 0)),
    CONSTRAINT chk_material_transfer_template_lines_planning_source
        CHECK (planning_source IN ('SPECIFICATION', 'CUTTING_PLAN')),
    CONSTRAINT chk_material_transfer_template_lines_cutting_link_status
        CHECK (cutting_link_status IN ('NONE', 'SINGLE', 'MULTIPLE_REFERENCES')),
    CONSTRAINT uk_material_transfer_template_lines_template_order
        UNIQUE (template_id, line_order)
);

CREATE INDEX idx_material_transfer_template_lines_template_id
    ON production.material_transfer_template_lines (template_id);

CREATE TABLE production.material_transfer_template_line_source_items (
    line_id UUID NOT NULL,
    source_order_item_id UUID NOT NULL,
    CONSTRAINT pk_material_transfer_template_line_source_items
        PRIMARY KEY (line_id, source_order_item_id),
    CONSTRAINT fk_material_transfer_template_line_source_items_line
        FOREIGN KEY (line_id)
        REFERENCES production.material_transfer_template_lines (id)
);

CREATE TABLE production.material_transfer_template_line_cutting_refs (
    line_id UUID NOT NULL,
    cutting_plan_id UUID NOT NULL,
    CONSTRAINT pk_material_transfer_template_line_cutting_refs
        PRIMARY KEY (line_id, cutting_plan_id),
    CONSTRAINT fk_material_transfer_template_line_cutting_refs_line
        FOREIGN KEY (line_id)
        REFERENCES production.material_transfer_template_lines (id)
);
