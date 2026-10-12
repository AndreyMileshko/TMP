-- B3-MA-FIX4B: Material Requirement request quantity may be zero.
-- Zero means master intentionally requests no Warehouse supply for the line.
-- Required need remains derived from contributions (not a DB column).
-- Constraint only: quantity > 0 becomes quantity >= 0. No data rewrite.

ALTER TABLE production.material_requirement_lines
    DROP CONSTRAINT chk_material_requirement_lines_quantity;

ALTER TABLE production.material_requirement_lines
    ADD CONSTRAINT chk_material_requirement_lines_quantity
        CHECK (quantity >= 0);