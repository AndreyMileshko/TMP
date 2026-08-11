-- STAGE6-019: Warehouse-owned MaterialReference with extended identity fields.

CREATE TABLE warehouse.material_references (
    id UUID PRIMARY KEY,
    article VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    color VARCHAR(128) NOT NULL DEFAULT '',
    size VARCHAR(128) NOT NULL DEFAULT '',
    unit_of_measure VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_material_references_natural
        UNIQUE (article, color, size, unit_of_measure),
    CONSTRAINT chk_material_references_article_non_blank CHECK (btrim(article) <> ''),
    CONSTRAINT chk_material_references_name_non_blank CHECK (btrim(name) <> '')
);

INSERT INTO warehouse.material_references (
    id, article, name, color, size, unit_of_measure, created_at, updated_at)
SELECT
    gen_random_uuid(),
    code,
    code,
    '',
    '',
    '',
    NOW(),
    NOW()
FROM (
    SELECT DISTINCT btrim(material_reference) AS code
    FROM (
        SELECT material_reference FROM warehouse.stock_positions
        UNION ALL
        SELECT material_reference FROM warehouse.warehouse_operations
        WHERE material_reference IS NOT NULL
        UNION ALL
        SELECT material_reference FROM warehouse.material_reservation_links
    ) existing_codes
    WHERE btrim(material_reference) <> ''
) distinct_codes;

ALTER TABLE warehouse.stock_positions
    ADD COLUMN material_reference_id UUID;

UPDATE warehouse.stock_positions sp
SET material_reference_id = mr.id
FROM warehouse.material_references mr
WHERE btrim(sp.material_reference) = mr.article
  AND mr.color = ''
  AND mr.size = ''
  AND mr.unit_of_measure = '';

ALTER TABLE warehouse.stock_positions
    DROP CONSTRAINT uk_stock_positions_identity;
ALTER TABLE warehouse.stock_positions
    DROP CONSTRAINT chk_stock_positions_material_non_blank;
DROP INDEX warehouse.idx_stock_positions_material_reference;

ALTER TABLE warehouse.stock_positions
    DROP COLUMN material_reference;

ALTER TABLE warehouse.stock_positions
    ALTER COLUMN material_reference_id SET NOT NULL;

ALTER TABLE warehouse.stock_positions
    ADD CONSTRAINT fk_stock_positions_material_reference
        FOREIGN KEY (material_reference_id) REFERENCES warehouse.material_references (id);

ALTER TABLE warehouse.stock_positions
    ADD CONSTRAINT uk_stock_positions_identity
        UNIQUE (warehouse_id, storage_cell_id, material_reference_id, stock_state);

CREATE INDEX idx_stock_positions_material_reference_id
    ON warehouse.stock_positions (material_reference_id);

ALTER TABLE warehouse.warehouse_operations
    ADD COLUMN material_reference_id UUID;

UPDATE warehouse.warehouse_operations wo
SET material_reference_id = mr.id
FROM warehouse.material_references mr
WHERE wo.material_reference IS NOT NULL
  AND btrim(wo.material_reference) = mr.article
  AND mr.color = ''
  AND mr.size = ''
  AND mr.unit_of_measure = '';

ALTER TABLE warehouse.warehouse_operations
    DROP CONSTRAINT chk_warehouse_operations_material_non_blank;

ALTER TABLE warehouse.warehouse_operations
    DROP COLUMN material_reference;

ALTER TABLE warehouse.warehouse_operations
    ADD CONSTRAINT fk_warehouse_operations_material_reference
        FOREIGN KEY (material_reference_id) REFERENCES warehouse.material_references (id);

CREATE INDEX idx_warehouse_operations_material_reference_id
    ON warehouse.warehouse_operations (material_reference_id);

ALTER TABLE warehouse.material_reservation_links
    ADD COLUMN material_reference_id UUID;

UPDATE warehouse.material_reservation_links mrl
SET material_reference_id = mr.id
FROM warehouse.material_references mr
WHERE btrim(mrl.material_reference) = mr.article
  AND mr.color = ''
  AND mr.size = ''
  AND mr.unit_of_measure = '';

ALTER TABLE warehouse.material_reservation_links
    DROP CONSTRAINT chk_material_reservation_links_material_non_blank;
DROP INDEX warehouse.idx_material_reservation_links_material;

ALTER TABLE warehouse.material_reservation_links
    DROP COLUMN material_reference;

ALTER TABLE warehouse.material_reservation_links
    ALTER COLUMN material_reference_id SET NOT NULL;

ALTER TABLE warehouse.material_reservation_links
    ADD CONSTRAINT fk_material_reservation_links_material_reference
        FOREIGN KEY (material_reference_id) REFERENCES warehouse.material_references (id);

CREATE INDEX idx_material_reservation_links_material_reference_id
    ON warehouse.material_reservation_links (material_reference_id);
