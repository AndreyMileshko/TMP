-- =============================================================================
-- B3-MA-11-FIX2: One-time historical orphan IN_TRANSIT reconciliation
-- =============================================================================
-- MODE: MANUAL EXECUTION ONLY — after explicit user approval of exact targets.
-- DO NOT run from Flyway, application startup, or automated tests.
-- DO NOT execute until approved.
--
-- Target DB: tmp_gui_stage5 (tmp-stage5-pg :55432) — test data only.
--
-- Intent:
--   Restore two orphaned IN_TRANSIT balances to AVAILABLE at the original
--   source cells, conserving AVAILABLE + IN_TRANSIT per material/cell pair,
--   while preserving historical TRANSFER_SEND movements and recording audited
--   ADJUSTMENT operations (confirmed discrepancy — not a business return).
--
-- Why not application API:
--   WarehouseAdjustmentService mutates AVAILABLE only.
--   TRANSFER_RETURN would falsely record a normal business return.
--   No permanent reconciliation service is introduced.
--
-- Idempotency:
--   Fixed correction operation UUIDs. Re-run refuses if those ops already exist.
--
-- All-or-nothing: single transaction; any mismatch RAISE EXCEPTION -> ROLLBACK.
-- =============================================================================

BEGIN;

DO $$
DECLARE
  -- Fixed correction operation IDs (idempotency keys)
  op_a constant uuid := 'b311a111-f1f2-4a01-8000-51593362c2ab';
  op_b constant uuid := 'b311a111-f1f2-4b01-8000-baa6da3d4c0c';

  -- Fixed movement IDs (AVAILABLE UUID sorts before IN_TRANSIT for History phys lookup)
  mov_a_av constant uuid := 'b311a111-f1f2-4a01-8000-9d0142b7f557';
  mov_a_it constant uuid := 'b311a111-f1f2-4a01-8001-51593362c2ab';
  mov_b_av constant uuid := 'b311a111-f1f2-4b01-8000-becd521cc680';
  mov_b_it constant uuid := 'b311a111-f1f2-4b01-8001-baa6da3d4c0c';

  -- Exact targets (resolved 2026-10-15; do not retarget by article/name)
  wh_main constant uuid := '3a986a21-9970-4716-bee7-6680a7770444';
  cell_a01 constant uuid := 'fbc4712d-1601-4ec8-a996-174d4e97e17a';
  cell_d01 constant uuid := '163a026e-1300-4ac9-b102-cda5282d6fe3';
  mat_111 constant uuid := '0a634c9c-fabc-4732-81d6-9facd2a82bf6';
  mat_spd constant uuid := 'b76dfb36-b9f2-4260-a564-c12c102f9336';

  pos_a_it constant uuid := '51593362-c2ab-4405-a885-b9245d700bbd';
  pos_a_av constant uuid := '9d0142b7-f557-460e-b0aa-7890c933cf0d';
  pos_b_it constant uuid := 'baa6da3d-4c0c-42d4-9229-aea1668a06f5';
  pos_b_av constant uuid := 'becd521c-c680-420d-a0df-4ea24d0f66ae';

  legacy_send_a constant uuid := '5f458632-8761-4490-94de-80c32ebcd796';
  legacy_send_b constant uuid := 'ddd59b3d-d5e5-4784-a0a1-54d24239e2c6';

  qty_a constant numeric(19,6) := 3.000000;
  qty_b constant numeric(19,6) := 0.500000;

  comment_a constant text :=
    'B3-MA-11-FIX2: one-time orphan IN_TRANSIT→AVAILABLE reconciliation at source cell; '
    || 'legacy TRANSFER_SEND=' || legacy_send_a::text
    || '; in_transit_position=' || pos_a_it::text
    || '; qty=3; not a business transfer return';
  comment_b constant text :=
    'B3-MA-11-FIX2: one-time orphan IN_TRANSIT→AVAILABLE reconciliation at source cell; '
    || 'legacy TRANSFER_SEND=' || legacy_send_b::text
    || '; in_transit_position=' || pos_b_it::text
    || '; qty=0.5; not a business transfer return';

  actor_login_const constant text := 'B3-MA-11-FIX2';

  now_ts timestamptz := clock_timestamp();

  -- Locked / measured state
  a_it_qty numeric(19,6);
  a_av_qty numeric(19,6);
  a_it_ver bigint;
  a_av_ver bigint;
  a_it_wh uuid;
  a_it_cell uuid;
  a_it_mat uuid;
  a_av_wh uuid;
  a_av_cell uuid;
  a_av_mat uuid;

  b_it_qty numeric(19,6);
  b_av_qty numeric(19,6);
  b_it_ver bigint;
  b_av_ver bigint;
  b_it_wh uuid;
  b_it_cell uuid;
  b_it_mat uuid;
  b_av_wh uuid;
  b_av_cell uuid;
  b_av_mat uuid;

  a_total_before numeric(19,6);
  b_total_before numeric(19,6);
  a_total_after numeric(19,6);
  b_total_after numeric(19,6);

  global_it_before numeric(19,6);
  global_av_before numeric(19,6);
  global_it_after numeric(19,6);
  global_av_after numeric(19,6);
  other_it_count integer;
  doc_outstanding numeric(19,6);

  updated integer;
BEGIN
  -- -------------------------------------------------------------------------
  -- Idempotency / safe refusal
  -- -------------------------------------------------------------------------
  IF EXISTS (
      SELECT 1 FROM warehouse.warehouse_operations
      WHERE id IN (op_a, op_b)
  ) THEN
    RAISE EXCEPTION
      'B3-MA-11-FIX2 refusal: correction operation(s) already exist (idempotent refuse). ops=% / %',
      op_a, op_b;
  END IF;

  IF EXISTS (
      SELECT 1 FROM warehouse.warehouse_movements
      WHERE id IN (mov_a_av, mov_a_it, mov_b_av, mov_b_it)
  ) THEN
    RAISE EXCEPTION
      'B3-MA-11-FIX2 refusal: correction movement(s) already exist (idempotent refuse).';
  END IF;

  -- -------------------------------------------------------------------------
  -- Lock exact stock positions (and only these)
  -- -------------------------------------------------------------------------
  SELECT quantity, version, warehouse_id, storage_cell_id, material_reference_id
    INTO a_it_qty, a_it_ver, a_it_wh, a_it_cell, a_it_mat
  FROM warehouse.stock_positions
  WHERE id = pos_a_it AND stock_state = 'IN_TRANSIT'
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: target A IN_TRANSIT position missing: %', pos_a_it;
  END IF;

  SELECT quantity, version, warehouse_id, storage_cell_id, material_reference_id
    INTO a_av_qty, a_av_ver, a_av_wh, a_av_cell, a_av_mat
  FROM warehouse.stock_positions
  WHERE id = pos_a_av AND stock_state = 'AVAILABLE'
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: target A AVAILABLE position missing: %', pos_a_av;
  END IF;

  SELECT quantity, version, warehouse_id, storage_cell_id, material_reference_id
    INTO b_it_qty, b_it_ver, b_it_wh, b_it_cell, b_it_mat
  FROM warehouse.stock_positions
  WHERE id = pos_b_it AND stock_state = 'IN_TRANSIT'
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: target B IN_TRANSIT position missing: %', pos_b_it;
  END IF;

  SELECT quantity, version, warehouse_id, storage_cell_id, material_reference_id
    INTO b_av_qty, b_av_ver, b_av_wh, b_av_cell, b_av_mat
  FROM warehouse.stock_positions
  WHERE id = pos_b_av AND stock_state = 'AVAILABLE'
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: target B AVAILABLE position missing: %', pos_b_av;
  END IF;

  -- -------------------------------------------------------------------------
  -- Identity / pairing preconditions
  -- -------------------------------------------------------------------------
  IF a_it_wh <> wh_main OR a_av_wh <> wh_main OR b_it_wh <> wh_main OR b_av_wh <> wh_main THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: warehouse identity mismatch (expected MAIN %).', wh_main;
  END IF;
  IF a_it_cell <> cell_a01 OR a_av_cell <> cell_a01 THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: target A cell mismatch (expected A-01 %).', cell_a01;
  END IF;
  IF b_it_cell <> cell_d01 OR b_av_cell <> cell_d01 THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: target B cell mismatch (expected D-01 %).', cell_d01;
  END IF;
  IF a_it_mat <> mat_111 OR a_av_mat <> mat_111 THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: target A material mismatch (expected %).', mat_111;
  END IF;
  IF b_it_mat <> mat_spd OR b_av_mat <> mat_spd THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: target B material mismatch (expected %).', mat_spd;
  END IF;

  -- -------------------------------------------------------------------------
  -- Current-balance verification (exact expected orphan amounts)
  -- -------------------------------------------------------------------------
  IF a_it_qty <> qty_a THEN
    RAISE EXCEPTION
      'B3-MA-11-FIX2: target A IN_TRANSIT qty mismatch: found % expected %', a_it_qty, qty_a;
  END IF;
  IF b_it_qty <> qty_b THEN
    RAISE EXCEPTION
      'B3-MA-11-FIX2: target B IN_TRANSIT qty mismatch: found % expected %', b_it_qty, qty_b;
  END IF;
  IF a_it_qty <= 0 OR b_it_qty <= 0 THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: correction quantity must be positive';
  END IF;

  a_total_before := a_av_qty + a_it_qty;
  b_total_before := b_av_qty + b_it_qty;

  -- -------------------------------------------------------------------------
  -- No other unexplained positive IN_TRANSIT; document outstanding unchanged (=0)
  -- -------------------------------------------------------------------------
  SELECT COUNT(*) INTO other_it_count
  FROM warehouse.stock_positions
  WHERE stock_state = 'IN_TRANSIT'
    AND quantity > 0
    AND id NOT IN (pos_a_it, pos_b_it);

  IF other_it_count <> 0 THEN
    RAISE EXCEPTION
      'B3-MA-11-FIX2: refuse — other positive IN_TRANSIT positions exist (count=%). Re-run diagnostic.',
      other_it_count;
  END IF;

  SELECT COALESCE(SUM(
           sa.quantity
           - COALESCE((SELECT SUM(r.quantity) FROM warehouse.transfer_receipt_settlement_item r
                       WHERE r.send_allocation_id = sa.id), 0)
           - COALESCE((SELECT SUM(rt.quantity) FROM warehouse.transfer_return_settlement_item rt
                       WHERE rt.send_allocation_id = sa.id), 0)
         ), 0)
    INTO doc_outstanding
  FROM warehouse.transfer_document_send_allocation sa;

  IF doc_outstanding <> 0 THEN
    RAISE EXCEPTION
      'B3-MA-11-FIX2: refuse — document allocation outstanding is % (expected 0).',
      doc_outstanding;
  END IF;

  -- Legacy sends still present and unmatched to receive/allocation (historical evidence)
  IF NOT EXISTS (
      SELECT 1 FROM warehouse.warehouse_operations wo
      WHERE wo.id = legacy_send_a
        AND wo.operation_type = 'TRANSFER_SEND'
        AND wo.status = 'COMPLETED'
        AND wo.quantity = qty_a
        AND wo.storage_cell_id = cell_a01
        AND wo.material_reference_id = mat_111
        AND NOT EXISTS (
              SELECT 1 FROM warehouse.transfer_document_send_allocation sa
              WHERE sa.send_operation_id = wo.id)
  ) THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: legacy send A missing or unexpectedly linked: %', legacy_send_a;
  END IF;

  IF NOT EXISTS (
      SELECT 1 FROM warehouse.warehouse_operations wo
      WHERE wo.id = legacy_send_b
        AND wo.operation_type = 'TRANSFER_SEND'
        AND wo.status = 'COMPLETED'
        AND wo.quantity = qty_b
        AND wo.storage_cell_id = cell_d01
        AND wo.material_reference_id = mat_spd
        AND NOT EXISTS (
              SELECT 1 FROM warehouse.transfer_document_send_allocation sa
              WHERE sa.send_operation_id = wo.id)
  ) THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: legacy send B missing or unexpectedly linked: %', legacy_send_b;
  END IF;

  SELECT COALESCE(SUM(CASE WHEN stock_state = 'IN_TRANSIT' THEN quantity ELSE 0 END), 0),
         COALESCE(SUM(CASE WHEN stock_state = 'AVAILABLE' THEN quantity ELSE 0 END), 0)
    INTO global_it_before, global_av_before
  FROM warehouse.stock_positions;

  IF global_it_before <> (qty_a + qty_b) THEN
    RAISE EXCEPTION
      'B3-MA-11-FIX2: global IN_TRANSIT sum % differs from orphan total %',
      global_it_before, (qty_a + qty_b);
  END IF;

  -- -------------------------------------------------------------------------
  -- Apply correction A
  -- -------------------------------------------------------------------------
  INSERT INTO warehouse.warehouse_operations (
      id, operation_type, status, warehouse_id, storage_cell_id, material_reference_id,
      quantity, stock_state, version, created_at, updated_at,
      actor_user_id, actor_login, comment_text
  ) VALUES (
      op_a, 'ADJUSTMENT', 'COMPLETED', wh_main, cell_a01, mat_111,
      a_av_qty + qty_a, 'AVAILABLE', 1, now_ts, now_ts,
      NULL, actor_login_const, comment_a
  );

  UPDATE warehouse.stock_positions
  SET quantity = a_it_qty - qty_a,
      version = a_it_ver + 1,
      updated_at = now_ts
  WHERE id = pos_a_it AND version = a_it_ver AND quantity = a_it_qty;
  GET DIAGNOSTICS updated = ROW_COUNT;
  IF updated <> 1 THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: optimistic lock failed on A IN_TRANSIT';
  END IF;

  UPDATE warehouse.stock_positions
  SET quantity = a_av_qty + qty_a,
      version = a_av_ver + 1,
      updated_at = now_ts
  WHERE id = pos_a_av AND version = a_av_ver AND quantity = a_av_qty;
  GET DIAGNOSTICS updated = ROW_COUNT;
  IF updated <> 1 THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: optimistic lock failed on A AVAILABLE';
  END IF;

  INSERT INTO warehouse.warehouse_movements (
      id, stock_position_id, operation_type, quantity_delta, created_at
  ) VALUES
      (mov_a_av, pos_a_av, 'ADJUSTMENT', qty_a, now_ts),
      (mov_a_it, pos_a_it, 'ADJUSTMENT', -qty_a, now_ts);

  -- -------------------------------------------------------------------------
  -- Apply correction B
  -- -------------------------------------------------------------------------
  INSERT INTO warehouse.warehouse_operations (
      id, operation_type, status, warehouse_id, storage_cell_id, material_reference_id,
      quantity, stock_state, version, created_at, updated_at,
      actor_user_id, actor_login, comment_text
  ) VALUES (
      op_b, 'ADJUSTMENT', 'COMPLETED', wh_main, cell_d01, mat_spd,
      b_av_qty + qty_b, 'AVAILABLE', 1, now_ts, now_ts,
      NULL, actor_login_const, comment_b
  );

  UPDATE warehouse.stock_positions
  SET quantity = b_it_qty - qty_b,
      version = b_it_ver + 1,
      updated_at = now_ts
  WHERE id = pos_b_it AND version = b_it_ver AND quantity = b_it_qty;
  GET DIAGNOSTICS updated = ROW_COUNT;
  IF updated <> 1 THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: optimistic lock failed on B IN_TRANSIT';
  END IF;

  UPDATE warehouse.stock_positions
  SET quantity = b_av_qty + qty_b,
      version = b_av_ver + 1,
      updated_at = now_ts
  WHERE id = pos_b_av AND version = b_av_ver AND quantity = b_av_qty;
  GET DIAGNOSTICS updated = ROW_COUNT;
  IF updated <> 1 THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: optimistic lock failed on B AVAILABLE';
  END IF;

  INSERT INTO warehouse.warehouse_movements (
      id, stock_position_id, operation_type, quantity_delta, created_at
  ) VALUES
      (mov_b_av, pos_b_av, 'ADJUSTMENT', qty_b, now_ts),
      (mov_b_it, pos_b_it, 'ADJUSTMENT', -qty_b, now_ts);

  -- -------------------------------------------------------------------------
  -- Postconditions
  -- -------------------------------------------------------------------------
  SELECT quantity INTO a_it_qty FROM warehouse.stock_positions WHERE id = pos_a_it;
  SELECT quantity INTO a_av_qty FROM warehouse.stock_positions WHERE id = pos_a_av;
  SELECT quantity INTO b_it_qty FROM warehouse.stock_positions WHERE id = pos_b_it;
  SELECT quantity INTO b_av_qty FROM warehouse.stock_positions WHERE id = pos_b_av;

  IF a_it_qty <> 0 OR a_av_qty <> (a_total_before) THEN
    -- a_av after must equal prior AVAILABLE + qty_a; since a_it becomes 0, av = total_before
    RAISE EXCEPTION
      'B3-MA-11-FIX2: postcondition A failed: IT=% AV=% total_before=%',
      a_it_qty, a_av_qty, a_total_before;
  END IF;
  IF b_it_qty <> 0 OR b_av_qty <> (b_total_before) THEN
    RAISE EXCEPTION
      'B3-MA-11-FIX2: postcondition B failed: IT=% AV=% total_before=%',
      b_it_qty, b_av_qty, b_total_before;
  END IF;

  a_total_after := a_av_qty + a_it_qty;
  b_total_after := b_av_qty + b_it_qty;
  IF a_total_after <> a_total_before OR b_total_after <> b_total_before THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: quantity conservation violated';
  END IF;

  SELECT COALESCE(SUM(CASE WHEN stock_state = 'IN_TRANSIT' THEN quantity ELSE 0 END), 0),
         COALESCE(SUM(CASE WHEN stock_state = 'AVAILABLE' THEN quantity ELSE 0 END), 0)
    INTO global_it_after, global_av_after
  FROM warehouse.stock_positions;

  IF global_it_after <> 0 THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: expected global IN_TRANSIT=0 after correction, found %', global_it_after;
  END IF;
  IF global_av_after <> global_av_before + qty_a + qty_b THEN
    RAISE EXCEPTION
      'B3-MA-11-FIX2: global AVAILABLE conservation failed: before=% after=%',
      global_av_before, global_av_after;
  END IF;

  -- Historical legacy sends must remain untouched
  IF NOT EXISTS (
      SELECT 1 FROM warehouse.warehouse_operations
      WHERE id = legacy_send_a AND operation_type = 'TRANSFER_SEND' AND quantity = qty_a
  ) OR NOT EXISTS (
      SELECT 1 FROM warehouse.warehouse_operations
      WHERE id = legacy_send_b AND operation_type = 'TRANSFER_SEND' AND quantity = qty_b
  ) THEN
    RAISE EXCEPTION 'B3-MA-11-FIX2: legacy send operations altered unexpectedly';
  END IF;

  RAISE NOTICE
    'B3-MA-11-FIX2 OK: A IT 3→0 AV %+→%; B IT 0.5→0 AV %+→%; ops % / %',
    a_total_before - qty_a, a_av_qty,
    b_total_before - qty_b, b_av_qty,
    op_a, op_b;
END $$;

COMMIT;