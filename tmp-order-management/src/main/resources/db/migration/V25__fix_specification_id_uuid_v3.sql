-- STAGE7-004B: Fix SpecificationId stability — align migration with Java UUID v3 algorithm.
-- V24 used md5()::uuid which does NOT set UUID version/variant bits.
-- Java UUID.nameUUIDFromBytes() produces UUID v3: version=3 (nibble in byte 6),
-- variant=2 (top 2 bits of byte 8 = 10).
-- This migration recalculates all specification_id values using the same algorithm.

UPDATE order_management.item_specifications
SET specification_id = (
    SELECT
        -- Take raw MD5 hex, apply UUID v3 version (byte 6: high nibble = 3)
        -- and IETF variant (byte 8: top 2 bits = 10)
        (
            substring(h from 1 for 12) ||
            '3' || substring(h from 14 for 3) ||
            lpad(to_hex((get_byte(decode(substring(h from 17 for 2), 'hex'), 0) & 63 | 128)), 2, '0') ||
            substring(h from 19 for 14)
        )::uuid
    FROM (
        SELECT md5(order_item_id::text || ':' || revision_number::text) AS h
    ) sub
);
