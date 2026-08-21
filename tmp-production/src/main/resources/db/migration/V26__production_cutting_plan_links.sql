-- STAGE7-008: Production-owned Cutting Plan links (0..N by material reference).
-- Opaque external references only; no FK to warehouse or cutting schemas.
CREATE TABLE production.production_item_cutting_plan_links (
    id UUID PRIMARY KEY,
    production_item_id UUID NOT NULL,
    material_reference_id UUID NOT NULL,
    cutting_plan_id UUID NOT NULL,
    CONSTRAINT fk_production_item_cutting_plan_links_item
        FOREIGN KEY (production_item_id)
        REFERENCES production.production_item_states (id),
    CONSTRAINT uk_production_item_cutting_plan_links_item_material
        UNIQUE (production_item_id, material_reference_id)
);

CREATE INDEX idx_production_item_cutting_plan_links_cutting_plan_id
    ON production.production_item_cutting_plan_links (cutting_plan_id);
