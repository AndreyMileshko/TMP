-- STAGE7-004A: stable opaque SpecificationId for Production-facing read contract.
-- Each immutable approved specification gets a unique UUID so Production can reference
-- it without knowing RevisionNumber.

ALTER TABLE order_management.item_specifications
    ADD COLUMN specification_id UUID;

-- Populate existing rows with deterministic UUIDs derived from (order_item_id, revision_number).
-- uuid_generate_v5 requires the uuid-ossp extension; use md5-based generation for portability.
UPDATE order_management.item_specifications
SET specification_id = md5(order_item_id::text || ':' || revision_number::text)::uuid
WHERE specification_id IS NULL;

ALTER TABLE order_management.item_specifications
    ALTER COLUMN specification_id SET NOT NULL;

CREATE UNIQUE INDEX idx_item_specifications_specification_id
    ON order_management.item_specifications (specification_id);
