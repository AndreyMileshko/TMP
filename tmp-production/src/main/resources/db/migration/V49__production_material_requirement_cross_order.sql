-- Stage 7 Phase 2: cross-order Material Requirement + product quantity coverage.
-- Preserves historical V43/V44 rows. Active source of truth for covered products moves to
-- material_requirement_source_items. Header source_order_id becomes nullable historical/legacy.
--
-- CRITICAL: pre-V49 MRs never stored requested product quantity. Legacy backfill therefore
-- reconstructs provenance for reopen/history but MUST NOT invent authoritative product coverage
-- (counts_toward_product_coverage = FALSE). Coverage sums ignore those rows.

ALTER TABLE production.material_requirements
    ALTER COLUMN source_order_id DROP NOT NULL;

CREATE TABLE production.material_requirement_source_items (
    requirement_id UUID NOT NULL,
    source_order_id UUID NOT NULL,
    source_order_item_id UUID NOT NULL,
    requested_product_quantity BIGINT NOT NULL,
    counts_toward_product_coverage BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_material_requirement_source_items
        PRIMARY KEY (requirement_id, source_order_id, source_order_item_id),
    CONSTRAINT fk_material_requirement_source_items_requirement
        FOREIGN KEY (requirement_id)
        REFERENCES production.material_requirements (id),
    CONSTRAINT chk_material_requirement_source_items_qty
        CHECK (requested_product_quantity > 0)
);

CREATE INDEX idx_material_requirement_source_items_order_item
    ON production.material_requirement_source_items (source_order_id, source_order_item_id);

CREATE INDEX idx_material_requirement_source_items_item
    ON production.material_requirement_source_items (source_order_item_id);

ALTER TABLE production.material_requirement_line_source_items
    ADD COLUMN source_order_id UUID NULL,
    ADD COLUMN contributed_material_quantity NUMERIC(19, 6) NULL;

ALTER TABLE production.material_requirement_line_source_items
    ADD CONSTRAINT chk_material_requirement_line_source_items_contrib_qty
        CHECK (
            contributed_material_quantity IS NULL
            OR contributed_material_quantity > 0
        );

-- Backfill header source_order_id onto historical line provenance rows.
UPDATE production.material_requirement_line_source_items lsi
SET source_order_id = mr.source_order_id
FROM production.material_requirement_lines mrl
JOIN production.material_requirements mr ON mr.id = mrl.requirement_id
WHERE lsi.line_id = mrl.id
  AND lsi.source_order_id IS NULL
  AND mr.source_order_id IS NOT NULL;

-- Backfill requirement-level source items for historical MRs (provenance only).
-- requested_product_quantity is a non-authoritative placeholder (launched_quantity or 1) so the
-- NOT NULL / CHECK constraints hold; counts_toward_product_coverage = FALSE ensures these rows
-- never enter cumulativeSubmittedProductQuantity.
INSERT INTO production.material_requirement_source_items (
    requirement_id,
    source_order_id,
    source_order_item_id,
    requested_product_quantity,
    counts_toward_product_coverage)
SELECT DISTINCT
    mr.id,
    mr.source_order_id,
    lsi.source_order_item_id,
    COALESCE(pis.launched_quantity, 1),
    FALSE
FROM production.material_requirements mr
JOIN production.material_requirement_lines mrl ON mrl.requirement_id = mr.id
JOIN production.material_requirement_line_source_items lsi ON lsi.line_id = mrl.id
LEFT JOIN production.production_item_states pis
    ON pis.source_order_id = mr.source_order_id
   AND pis.source_order_item_id = lsi.source_order_item_id
WHERE mr.source_order_id IS NOT NULL
  AND NOT EXISTS (
        SELECT 1
        FROM production.material_requirement_source_items existing
        WHERE existing.requirement_id = mr.id
          AND existing.source_order_id = mr.source_order_id
          AND existing.source_order_item_id = lsi.source_order_item_id
  );
