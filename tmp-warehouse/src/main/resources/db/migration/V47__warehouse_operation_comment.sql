-- Stage 3.5.15: generic immutable Warehouse operation comment.
-- Historical rows remain NULL. Adjustment requires non-blank comment at application level.
ALTER TABLE warehouse.warehouse_operations
    ADD COLUMN comment_text VARCHAR(1000) NULL;
