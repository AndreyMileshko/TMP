-- STAGE7-012: Production-owned Production Release document payload (plan/fact).
-- Linked to Document Engine by document_id. No Warehouse / OM / Cutting FK.
CREATE TABLE production.production_releases (
    document_id UUID PRIMARY KEY,
    source_order_id UUID NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE NOT NULL,
    posted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_production_releases_source_order_id
    ON production.production_releases (source_order_id);

CREATE TABLE production.production_release_item_lines (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    source_order_item_id UUID NOT NULL,
    specification_id UUID NOT NULL,
    release_quantity NUMERIC(19, 0) NOT NULL,
    line_order INT NOT NULL,
    CONSTRAINT fk_production_release_item_lines_release
        FOREIGN KEY (document_id)
        REFERENCES production.production_releases (document_id),
    CONSTRAINT uk_production_release_item_lines_document_item
        UNIQUE (document_id, source_order_item_id),
    CONSTRAINT uk_production_release_item_lines_document_order
        UNIQUE (document_id, line_order),
    CONSTRAINT chk_production_release_item_lines_quantity
        CHECK (release_quantity > 0)
);

CREATE INDEX idx_production_release_item_lines_document_id
    ON production.production_release_item_lines (document_id);

CREATE TABLE production.production_release_material_lines (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    material_reference_id UUID NOT NULL,
    planned_quantity NUMERIC(19, 6) NOT NULL,
    actual_quantity NUMERIC(19, 6) NOT NULL,
    planning_source VARCHAR(32) NOT NULL,
    cutting_plan_id UUID,
    source_order_item_id UUID,
    comment_text VARCHAR(1024),
    line_order INT NOT NULL,
    CONSTRAINT fk_production_release_material_lines_release
        FOREIGN KEY (document_id)
        REFERENCES production.production_releases (document_id),
    CONSTRAINT uk_production_release_material_lines_document_order
        UNIQUE (document_id, line_order),
    CONSTRAINT chk_production_release_material_lines_planned
        CHECK (planned_quantity >= 0),
    CONSTRAINT chk_production_release_material_lines_actual
        CHECK (actual_quantity >= 0),
    CONSTRAINT chk_production_release_material_lines_planning_source
        CHECK (planning_source IN ('SPECIFICATION', 'CUTTING_PLAN'))
);

CREATE INDEX idx_production_release_material_lines_document_id
    ON production.production_release_material_lines (document_id);
