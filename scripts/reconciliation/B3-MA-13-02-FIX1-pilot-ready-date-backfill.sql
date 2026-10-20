-- =============================================================================
-- B3-MA-13-02-FIX1: One-time pilot ready_date backfill
-- =============================================================================
-- MODE: MANUAL EXECUTION ONLY — after V54 is applied to tmp_pilot and after
--       explicit user approval of this correction.
-- DO NOT run from Flyway, application startup, or automated tests.
-- DO NOT execute in this implementation task.
--
-- Target DB: tmp_pilot ONLY.
-- Stage DB tmp_gui_stage5: FORBIDDEN.
--
-- Intent:
--   Persist SuperOkna ready date for three already-imported ACTIVE pilot
--   orders whose ready_date is currently NULL (value was discarded at import).
--   Leave created_at, statuses, specifications, warehouse and production data
--   unchanged.
--
-- Approved mapping (from original STXT order blocks; raw STXT files were not
-- available in the repository at preparation time — use this approved table):
--
--   | order_number | ready_date |
--   | 26106009     | 2026-11-03 |
--   | 26106066     | 2026-11-09 |
--   | 26106063     | 2026-11-12 |
--
-- Prerequisites:
--   1. Flyway V54 applied on tmp_pilot (column order_management.orders.ready_date).
--   2. Backup of tmp_pilot completed and verified.
--   3. Application package matching this codebase is ready.
--
-- Idempotency:
--   Refuses if any of the three rows already has a non-NULL ready_date.
--
-- All-or-nothing: single transaction; any mismatch RAISE EXCEPTION -> ROLLBACK.
-- =============================================================================

BEGIN;

DO $$
DECLARE
  expected_count constant integer := 3;
  found_count integer;
  null_ready_count integer;
  updated_count integer;
BEGIN
  IF current_database() IS DISTINCT FROM 'tmp_pilot' THEN
    RAISE EXCEPTION
      'B3-MA-13-02-FIX1 refuses execution: current_database()=% (required tmp_pilot)',
      current_database();
  END IF;

  IF NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'order_management'
           AND table_name = 'orders'
           AND column_name = 'ready_date'
  ) THEN
    RAISE EXCEPTION
      'B3-MA-13-02-FIX1 refuses execution: ready_date column missing (apply Flyway V54 first)';
  END IF;

  SELECT COUNT(*) INTO found_count
    FROM order_management.orders
   WHERE order_number IN ('26106009', '26106066', '26106063');

  IF found_count <> expected_count THEN
    RAISE EXCEPTION
      'B3-MA-13-02-FIX1 refuses execution: expected % orders, found %',
      expected_count, found_count;
  END IF;

  IF EXISTS (
        SELECT order_number
          FROM order_management.orders
         WHERE order_number IN ('26106009', '26106066', '26106063')
         GROUP BY order_number
        HAVING COUNT(*) <> 1
  ) THEN
    RAISE EXCEPTION
      'B3-MA-13-02-FIX1 refuses execution: duplicate order_number among targets';
  END IF;

  SELECT COUNT(*) INTO null_ready_count
    FROM order_management.orders
   WHERE order_number IN ('26106009', '26106066', '26106063')
     AND ready_date IS NULL;

  IF null_ready_count <> expected_count THEN
    RAISE EXCEPTION
      'B3-MA-13-02-FIX1 refuses repeat/partial execution: expected % NULL ready_date rows, found %',
      expected_count, null_ready_count;
  END IF;

  UPDATE order_management.orders
     SET ready_date = CASE order_number
           WHEN '26106009' THEN DATE '2026-11-03'
           WHEN '26106066' THEN DATE '2026-11-09'
           WHEN '26106063' THEN DATE '2026-11-12'
         END
   WHERE order_number IN ('26106009', '26106066', '26106063')
     AND ready_date IS NULL;

  GET DIAGNOSTICS updated_count = ROW_COUNT;
  IF updated_count <> expected_count THEN
    RAISE EXCEPTION
      'B3-MA-13-02-FIX1 refuses execution: updated % rows, expected %',
      updated_count, expected_count;
  END IF;

  IF EXISTS (
        SELECT 1
          FROM order_management.orders
         WHERE order_number = '26106009' AND ready_date IS DISTINCT FROM DATE '2026-11-03'
  ) OR EXISTS (
        SELECT 1
          FROM order_management.orders
         WHERE order_number = '26106066' AND ready_date IS DISTINCT FROM DATE '2026-11-09'
  ) OR EXISTS (
        SELECT 1
          FROM order_management.orders
         WHERE order_number = '26106063' AND ready_date IS DISTINCT FROM DATE '2026-11-12'
  ) THEN
    RAISE EXCEPTION 'B3-MA-13-02-FIX1 verification failed: resulting ready_date mismatch';
  END IF;

  RAISE NOTICE 'B3-MA-13-02-FIX1: updated % pilot orders; created_at untouched', updated_count;
END $$;

COMMIT;