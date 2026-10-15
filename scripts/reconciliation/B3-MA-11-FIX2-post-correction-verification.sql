-- =============================================================================
-- B3-MA-11-FIX2: Read-only post-correction verification (run AFTER approved execution)
-- =============================================================================
BEGIN READ ONLY;

-- 1) Integrity diagnostic should show zero orphans
--    (re-run scripts/warehouse-in-transit-integrity-diagnostic.sql)

-- 2) Target balances
SELECT 'A' AS target, sp.id, sp.stock_state, sp.quantity
FROM warehouse.stock_positions sp
WHERE sp.id IN (
  '51593362-c2ab-4405-a885-b9245d700bbd'::uuid,
  '9d0142b7-f557-460e-b0aa-7890c933cf0d'::uuid
)
UNION ALL
SELECT 'B', sp.id, sp.stock_state, sp.quantity
FROM warehouse.stock_positions sp
WHERE sp.id IN (
  'baa6da3d-4c0c-42d4-9229-aea1668a06f5'::uuid,
  'becd521c-c680-420d-a0df-4ea24d0f66ae'::uuid
)
ORDER BY 1, 3;

-- Expected:
-- A IN_TRANSIT 0, A AVAILABLE 3
-- B IN_TRANSIT 0, B AVAILABLE 0.5

-- 3) Corrective audit ops
SELECT id, operation_type, status, quantity, stock_state, actor_login, comment_text, created_at
FROM warehouse.warehouse_operations
WHERE id IN (
  'b311a111-f1f2-4a01-8000-51593362c2ab'::uuid,
  'b311a111-f1f2-4b01-8000-baa6da3d4c0c'::uuid
);

-- 4) Corrective movements
SELECT id, stock_position_id, operation_type, quantity_delta, created_at
FROM warehouse.warehouse_movements
WHERE id IN (
  'b311a111-f1f2-4a01-8000-9d0142b7f557'::uuid,
  'b311a111-f1f2-4a01-8001-51593362c2ab'::uuid,
  'b311a111-f1f2-4b01-8000-becd521cc680'::uuid,
  'b311a111-f1f2-4b01-8001-baa6da3d4c0c'::uuid
)
ORDER BY id;

-- 5) Historical legacy sends intact
SELECT id, operation_type, status, quantity, created_at
FROM warehouse.warehouse_operations
WHERE id IN (
  '5f458632-8761-4490-94de-80c32ebcd796'::uuid,
  'ddd59b3d-d5e5-4784-a0a1-54d24239e2c6'::uuid
);

-- 6) No unexplained positive IN_TRANSIT; document outstanding still 0
SELECT COUNT(*) AS positive_in_transit_rows
FROM warehouse.stock_positions
WHERE stock_state = 'IN_TRANSIT' AND quantity > 0;

SELECT COALESCE(SUM(
         sa.quantity
         - COALESCE((SELECT SUM(r.quantity) FROM warehouse.transfer_receipt_settlement_item r
                     WHERE r.send_allocation_id = sa.id), 0)
         - COALESCE((SELECT SUM(rt.quantity) FROM warehouse.transfer_return_settlement_item rt
                     WHERE rt.send_allocation_id = sa.id), 0)
       ), 0) AS document_outstanding
FROM warehouse.transfer_document_send_allocation sa;

-- 7) Unrelated protection baseline (compare to pre-correction snapshot)
-- Pre-correction (2026-10-15 prepare): stock_position_count=78, sum_available=883.8, sum_in_transit=3.5
-- Expected after: count=78, sum_available=887.3, sum_in_transit=0
SELECT COUNT(*) AS stock_position_count,
       SUM(CASE WHEN stock_state='AVAILABLE' THEN quantity ELSE 0 END) AS sum_available,
       SUM(CASE WHEN stock_state='IN_TRANSIT' THEN quantity ELSE 0 END) AS sum_in_transit
FROM warehouse.stock_positions;

COMMIT;