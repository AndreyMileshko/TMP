-- STAGE6-019 FIX: constrain warehouse unit_of_measure to fixed reference codes.
-- Canonical codes: шт. | м. | кв.м. | компл. | л. | гр. | кг.
-- Empty string remains allowed only for legacy MaterialReference rows migrated from materialCode.

-- Drop natural-key uniqueness temporarily so alias normalization can collapse duplicates.
ALTER TABLE warehouse.material_references
    DROP CONSTRAINT IF EXISTS uk_material_references_natural;

-- Normalize common free-text aliases to canonical codes.
UPDATE warehouse.material_references
SET unit_of_measure = CASE
    WHEN lower(trim(both FROM unit_of_measure)) IN ('шт', 'штука', 'штуки', 'штук') THEN 'шт.'
    WHEN lower(trim(both FROM unit_of_measure)) IN ('м', 'метр', 'метры', 'метров') THEN 'м.'
    WHEN lower(trim(both FROM unit_of_measure)) IN ('кв.м', 'квм', 'м2', 'м²') THEN 'кв.м.'
    WHEN lower(trim(both FROM unit_of_measure)) IN ('компл', 'комплект') THEN 'компл.'
    WHEN lower(trim(both FROM unit_of_measure)) IN ('л', 'литр', 'литры', 'литров') THEN 'л.'
    WHEN lower(trim(both FROM unit_of_measure)) IN ('гр', 'г', 'грамм', 'граммы') THEN 'гр.'
    WHEN lower(trim(both FROM unit_of_measure)) IN ('кг', 'килограмм', 'килограммы') THEN 'кг.'
    WHEN trim(both FROM unit_of_measure) IN ('шт.', 'м.', 'кв.м.', 'компл.', 'л.', 'гр.', 'кг.')
        THEN trim(both FROM unit_of_measure)
    WHEN trim(both FROM unit_of_measure) = '' THEN ''
    ELSE trim(both FROM unit_of_measure)
END,
    updated_at = NOW();

-- Merge MaterialReference rows that share the same natural key after normalization.
CREATE TEMP TABLE tmp_material_merge AS
SELECT id, survivor_id
FROM (
    SELECT
        id,
        FIRST_VALUE(id) OVER (
            PARTITION BY article, color, size, unit_of_measure
            ORDER BY created_at, id
        ) AS survivor_id
    FROM warehouse.material_references
) d
WHERE id <> survivor_id;

-- Stock positions that would collide after FK retarget: add quantities, then delete duplicates.
CREATE TEMP TABLE tmp_stock_conflicts AS
SELECT
    sp.id AS duplicate_position_id,
    survivor_sp.id AS survivor_position_id,
    sp.quantity AS duplicate_quantity
FROM warehouse.stock_positions sp
JOIN tmp_material_merge tm ON sp.material_reference_id = tm.id
JOIN warehouse.stock_positions survivor_sp
  ON survivor_sp.warehouse_id = sp.warehouse_id
 AND survivor_sp.storage_cell_id = sp.storage_cell_id
 AND survivor_sp.material_reference_id = tm.survivor_id
 AND survivor_sp.stock_state = sp.stock_state;

UPDATE warehouse.stock_positions sp
SET quantity = sp.quantity + c.duplicate_quantity,
    updated_at = NOW()
FROM tmp_stock_conflicts c
WHERE sp.id = c.survivor_position_id;

-- Movements must point at survivor stock positions before duplicates are removed.
UPDATE warehouse.warehouse_movements wm
SET stock_position_id = c.survivor_position_id
FROM tmp_stock_conflicts c
WHERE wm.stock_position_id = c.duplicate_position_id;

DELETE FROM warehouse.stock_positions sp
WHERE sp.id IN (SELECT duplicate_position_id FROM tmp_stock_conflicts);

UPDATE warehouse.stock_positions sp
SET material_reference_id = tm.survivor_id
FROM tmp_material_merge tm
WHERE sp.material_reference_id = tm.id;

UPDATE warehouse.warehouse_operations wo
SET material_reference_id = tm.survivor_id
FROM tmp_material_merge tm
WHERE wo.material_reference_id = tm.id;

UPDATE warehouse.material_reservation_links mrl
SET material_reference_id = tm.survivor_id
FROM tmp_material_merge tm
WHERE mrl.material_reference_id = tm.id;

DELETE FROM warehouse.material_references mr
WHERE mr.id IN (SELECT id FROM tmp_material_merge);

DROP TABLE tmp_stock_conflicts;
DROP TABLE tmp_material_merge;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM warehouse.material_references
        WHERE unit_of_measure NOT IN ('', 'шт.', 'м.', 'кв.м.', 'компл.', 'л.', 'гр.', 'кг.')
    ) THEN
        RAISE EXCEPTION
            'Unsupported unit_of_measure values remain after V20 normalization';
    END IF;
END $$;

ALTER TABLE warehouse.material_references
    ADD CONSTRAINT uk_material_references_natural
    UNIQUE (article, color, size, unit_of_measure);

ALTER TABLE warehouse.material_references
    ADD CONSTRAINT chk_material_references_unit_of_measure
    CHECK (unit_of_measure IN ('', 'шт.', 'м.', 'кв.м.', 'компл.', 'л.', 'гр.', 'кг.'));
