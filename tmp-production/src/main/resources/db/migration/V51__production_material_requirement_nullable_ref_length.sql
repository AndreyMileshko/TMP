-- B3B-2: Material Requirement lines may exist without Warehouse MaterialReference.
-- PREPARE no longer hard-requires resolution; Warehouse resolves at Demand accept.
-- length_mm is an immutable snapshot only (not part of material identity matching).

ALTER TABLE production.material_requirement_lines
    ALTER COLUMN material_reference_id DROP NOT NULL;

ALTER TABLE production.material_requirement_lines
    ADD COLUMN length_mm NUMERIC(19, 6) NULL;

ALTER TABLE production.material_requirement_lines
    ADD CONSTRAINT chk_material_requirement_lines_length_mm
        CHECK (length_mm IS NULL OR length_mm > 0);
