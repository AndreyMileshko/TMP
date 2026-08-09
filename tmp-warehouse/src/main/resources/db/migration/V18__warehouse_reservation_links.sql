-- Informational material reservation links (Warehouse Specification §8).
-- Does not alter stock_positions or warehouse_movements. No RESERVED stock state.
CREATE TABLE warehouse.material_reservation_links (
    id UUID PRIMARY KEY,
    material_reference VARCHAR(128) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_reference VARCHAR(128) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_material_reservation_links_material_non_blank
        CHECK (btrim(material_reference) <> ''),
    CONSTRAINT chk_material_reservation_links_target_reference_non_blank
        CHECK (btrim(target_reference) <> ''),
    CONSTRAINT chk_material_reservation_links_target_type
        CHECK (target_type IN ('ORDER', 'PRODUCTION_DEMAND')),
    CONSTRAINT chk_material_reservation_links_quantity_positive
        CHECK (quantity > 0)
);

CREATE INDEX idx_material_reservation_links_material
    ON warehouse.material_reservation_links (material_reference);

CREATE INDEX idx_material_reservation_links_target
    ON warehouse.material_reservation_links (target_type, target_reference);
