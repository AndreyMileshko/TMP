-- B3-MA-11-FIX1: Read-only IN_TRANSIT integrity diagnostic
-- Compares positive stock IN_TRANSIT to document-linked outstanding + unreceived legacy contexts.
-- Does not mutate data. Run with BEGIN READ ONLY / COMMIT (or SET TRANSACTION READ ONLY).
--
-- Expected invariant:
--   recorded_in_transit = document_outstanding + legacy_context_outstanding
-- Orphans = recorded positions that are not explained by either category.
--
-- Report each position separately; do not treat cross-UoM quantity sums as a physical total.

BEGIN READ ONLY;

-- 1) Positive IN_TRANSIT stock positions
SELECT
    w.code AS warehouse_code,
    sc.code AS cell_code,
    mr.article,
    mr.name AS material_name,
    mr.unit_of_measure,
    sp.quantity AS recorded_in_transit,
    sp.id AS stock_position_id,
    sp.warehouse_id,
    sp.storage_cell_id,
    sp.material_reference_id
FROM warehouse.stock_positions sp
JOIN warehouse.warehouses w ON w.id = sp.warehouse_id
JOIN warehouse.storage_cells sc ON sc.id = sp.storage_cell_id
JOIN warehouse.material_references mr ON mr.id = sp.material_reference_id
WHERE sp.stock_state = 'IN_TRANSIT'
  AND sp.quantity > 0
ORDER BY w.code, sc.code, mr.article;

-- 2) Document-linked outstanding (send " received " returned) by source cell/material
SELECT
    sa.source_storage_cell_id,
    tdl.material_reference_id,
    SUM(sa.quantity
        - COALESCE((SELECT SUM(r.quantity)
                    FROM warehouse.transfer_receipt_settlement_item r
                    WHERE r.send_allocation_id = sa.id), 0)
        - COALESCE((SELECT SUM(rt.quantity)
                    FROM warehouse.transfer_return_settlement_item rt
                    WHERE rt.send_allocation_id = sa.id), 0)
    ) AS document_outstanding
FROM warehouse.transfer_document_send_allocation sa
JOIN warehouse.transfer_document_lines tdl ON tdl.id = sa.line_id
GROUP BY sa.source_storage_cell_id, tdl.material_reference_id
HAVING SUM(sa.quantity
        - COALESCE((SELECT SUM(r.quantity)
                    FROM warehouse.transfer_receipt_settlement_item r
                    WHERE r.send_allocation_id = sa.id), 0)
        - COALESCE((SELECT SUM(rt.quantity)
                    FROM warehouse.transfer_return_settlement_item rt
                    WHERE rt.send_allocation_id = sa.id), 0)
    ) > 0
ORDER BY 1, 2;

-- 3) Unreceived legacy operation-context outstanding (no document allocation)
SELECT
    wo.storage_cell_id,
    wo.material_reference_id,
    SUM(wo.quantity) AS legacy_context_outstanding
FROM warehouse.warehouse_operations wo
JOIN warehouse.transfer_operation_context toc ON toc.operation_id = wo.id
WHERE wo.operation_type = 'TRANSFER_SEND'
  AND wo.status = 'COMPLETED'
  AND toc.receive_operation_id IS NULL
  AND NOT EXISTS (
        SELECT 1
        FROM warehouse.transfer_document_send_allocation sa
        WHERE sa.send_operation_id = wo.id)
GROUP BY wo.storage_cell_id, wo.material_reference_id
HAVING SUM(wo.quantity) > 0
ORDER BY 1, 2;

-- 4) Orphan positions: recorded IN_TRANSIT not fully explained by (2)+(3)
WITH recorded AS (
    SELECT
        sp.warehouse_id,
        sp.storage_cell_id,
        sp.material_reference_id,
        sp.quantity AS recorded,
        sp.id AS stock_position_id
    FROM warehouse.stock_positions sp
    WHERE sp.stock_state = 'IN_TRANSIT'
      AND sp.quantity > 0
),
doc_out AS (
    SELECT
        sa.source_storage_cell_id AS storage_cell_id,
        tdl.material_reference_id,
        SUM(sa.quantity
            - COALESCE((SELECT SUM(r.quantity)
                        FROM warehouse.transfer_receipt_settlement_item r
                        WHERE r.send_allocation_id = sa.id), 0)
            - COALESCE((SELECT SUM(rt.quantity)
                        FROM warehouse.transfer_return_settlement_item rt
                        WHERE rt.send_allocation_id = sa.id), 0)
        ) AS qty
    FROM warehouse.transfer_document_send_allocation sa
    JOIN warehouse.transfer_document_lines tdl ON tdl.id = sa.line_id
    GROUP BY sa.source_storage_cell_id, tdl.material_reference_id
),
legacy_out AS (
    SELECT
        wo.storage_cell_id,
        wo.material_reference_id,
        SUM(wo.quantity) AS qty
    FROM warehouse.warehouse_operations wo
    JOIN warehouse.transfer_operation_context toc ON toc.operation_id = wo.id
    WHERE wo.operation_type = 'TRANSFER_SEND'
      AND wo.status = 'COMPLETED'
      AND toc.receive_operation_id IS NULL
      AND NOT EXISTS (
            SELECT 1
            FROM warehouse.transfer_document_send_allocation sa
            WHERE sa.send_operation_id = wo.id)
    GROUP BY wo.storage_cell_id, wo.material_reference_id
)
SELECT
    w.code AS warehouse_code,
    sc.code AS cell_code,
    mr.article,
    mr.unit_of_measure,
    r.recorded AS recorded_in_transit,
    COALESCE(d.qty, 0) AS document_outstanding,
    COALESCE(l.qty, 0) AS legacy_context_outstanding,
    r.recorded - COALESCE(d.qty, 0) - COALESCE(l.qty, 0) AS orphan_quantity,
    r.stock_position_id
FROM recorded r
JOIN warehouse.warehouses w ON w.id = r.warehouse_id
JOIN warehouse.storage_cells sc ON sc.id = r.storage_cell_id
JOIN warehouse.material_references mr ON mr.id = r.material_reference_id
LEFT JOIN doc_out d
       ON d.storage_cell_id = r.storage_cell_id
      AND d.material_reference_id = r.material_reference_id
LEFT JOIN legacy_out l
       ON l.storage_cell_id = r.storage_cell_id
      AND l.material_reference_id = r.material_reference_id
WHERE r.recorded - COALESCE(d.qty, 0) - COALESCE(l.qty, 0) <> 0
ORDER BY w.code, sc.code, mr.article;

COMMIT;
