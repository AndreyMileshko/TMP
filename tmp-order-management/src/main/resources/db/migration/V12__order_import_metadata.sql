-- STAGE5-053: capability-owned order import metadata (Specification §27.6).
-- Upgrade from V11; existing aggregate data preserved. No JSON/bytea/file content.

CREATE TABLE order_management.order_import_metadata (
    import_id UUID PRIMARY KEY,
    source_type VARCHAR(64) NOT NULL,
    source_reference VARCHAR(512) NOT NULL,
    content_checksum VARCHAR(128) NOT NULL,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    imported_by UUID NOT NULL,
    order_id UUID NOT NULL,
    CONSTRAINT fk_order_import_metadata_order
        FOREIGN KEY (order_id)
        REFERENCES order_management.orders (order_id),
    CONSTRAINT uk_order_import_metadata_source_checksum
        UNIQUE (source_type, content_checksum)
);

CREATE INDEX idx_order_import_metadata_order_id
    ON order_management.order_import_metadata (order_id);

CREATE INDEX idx_order_import_metadata_imported_at
    ON order_management.order_import_metadata (imported_at DESC);
