-- Explicit per-source-item Material Requirement product request kind (COUNTED / REPEATED /
-- LEGACY_EXCLUDED). Preserves counts_toward_product_coverage for existing coverage SQL.
-- No row is backfilled as REPEATED. No cross-module FKs.

ALTER TABLE production.material_requirement_source_items
    ADD COLUMN product_request_kind VARCHAR(32) NULL;

UPDATE production.material_requirement_source_items
SET product_request_kind = CASE
    WHEN counts_toward_product_coverage = TRUE THEN 'COUNTED'
    ELSE 'LEGACY_EXCLUDED'
END
WHERE product_request_kind IS NULL;

ALTER TABLE production.material_requirement_source_items
    ALTER COLUMN product_request_kind SET NOT NULL;

ALTER TABLE production.material_requirement_source_items
    ALTER COLUMN product_request_kind SET DEFAULT 'COUNTED';

ALTER TABLE production.material_requirement_source_items
    ADD CONSTRAINT chk_material_requirement_source_items_request_kind
        CHECK (product_request_kind IN ('COUNTED', 'REPEATED', 'LEGACY_EXCLUDED'));

ALTER TABLE production.material_requirement_source_items
    ADD CONSTRAINT chk_material_requirement_source_items_kind_coverage
        CHECK (
            (product_request_kind = 'COUNTED' AND counts_toward_product_coverage = TRUE)
            OR (product_request_kind = 'REPEATED' AND counts_toward_product_coverage = FALSE)
            OR (product_request_kind = 'LEGACY_EXCLUDED' AND counts_toward_product_coverage = FALSE)
        );