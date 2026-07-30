-- STAGE5-051 fix: whole-number ordered_quantity constraints; align NUMERIC(19,6) precision.

DO $$
DECLARE
    fractional_count integer;
BEGIN
    SELECT COUNT(*) INTO fractional_count
    FROM (
        SELECT ordered_quantity
        FROM order_management.order_item_revisions
        WHERE ordered_quantity <> trunc(ordered_quantity)
        UNION ALL
        SELECT ordered_quantity
        FROM order_management.order_item_create_payload
        WHERE ordered_quantity <> trunc(ordered_quantity)
        UNION ALL
        SELECT ordered_quantity
        FROM order_management.order_item_revision_update_payload
        WHERE ordered_quantity <> trunc(ordered_quantity)
    ) fractional_rows;

    IF fractional_count > 0 THEN
        RAISE EXCEPTION
            'V10 migration blocked: % row(s) with fractional ordered_quantity. '
            'Fix data manually before upgrading to V10.',
            fractional_count;
    END IF;
END $$;

-- Align payload numeric precision with aggregate tables (NUMERIC(19,6)).
ALTER TABLE order_management.order_item_create_payload
    ALTER COLUMN ordered_quantity TYPE NUMERIC(19, 6);

ALTER TABLE order_management.order_item_revision_update_payload
    ALTER COLUMN ordered_quantity TYPE NUMERIC(19, 6);

ALTER TABLE order_management.order_item_revision_payload_line
    ALTER COLUMN line_quantity TYPE NUMERIC(19, 6);

ALTER TABLE order_management.order_item_revision_payload_line
    ALTER COLUMN length_mm TYPE NUMERIC(19, 6);

-- productQuantity (ordered_quantity) must be a positive whole number.
ALTER TABLE order_management.order_item_revisions
    ADD CONSTRAINT chk_order_item_revisions_quantity_whole
        CHECK (ordered_quantity = trunc(ordered_quantity));

ALTER TABLE order_management.order_item_create_payload
    ADD CONSTRAINT chk_order_item_create_payload_qty_whole
        CHECK (ordered_quantity = trunc(ordered_quantity));

ALTER TABLE order_management.order_item_revision_update_payload
    ADD CONSTRAINT chk_order_item_revision_update_qty_whole
        CHECK (ordered_quantity = trunc(ordered_quantity));
