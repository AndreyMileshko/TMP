-- B3-MA-13-02-FIX1: Persist SuperOkna «Дата готовности» as planned manufacturing date.
-- UI label: «Дата изготовления». Independent of created_at (TMP creation timestamp).
-- Nullable: manual orders and legacy imports without a ready date remain valid.
-- No default, no backfill. Existing created_at / updated_at / lifecycle unchanged.

ALTER TABLE order_management.orders
    ADD COLUMN ready_date DATE NULL;

-- Required so ORDER_CREATE document drafts retain per-order readyDate through post.
ALTER TABLE order_management.order_create_payload
    ADD COLUMN ready_date DATE NULL;