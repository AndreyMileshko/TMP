-- Stage 3.5.2: Warehouse-owned multi-line Transfer Document payload foundation.
-- Document Engine type warehouse.transfer; no stock mutation in this stage.
-- DocumentId is a value reference (no cross-schema FK to documents.documents).

INSERT INTO documents.document_types (id, display_name, description, registered_at, version)
VALUES (
    'warehouse.transfer',
    'warehouse.transfer',
    'Warehouse multi-line Transfer Document',
    CURRENT_TIMESTAMP,
    0
)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE warehouse.transfer_document_payload (
    document_id UUID PRIMARY KEY,
    source_warehouse_id UUID NOT NULL
        REFERENCES warehouse.warehouses (id),
    destination_warehouse_id UUID NOT NULL
        REFERENCES warehouse.warehouses (id),
    payload_schema_version INTEGER NOT NULL,
    payload_revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_transfer_document_payload_warehouses_distinct
        CHECK (source_warehouse_id <> destination_warehouse_id),
    CONSTRAINT chk_transfer_document_payload_schema_version
        CHECK (payload_schema_version >= 1),
    CONSTRAINT chk_transfer_document_payload_revision
        CHECK (payload_revision >= 0)
);

CREATE INDEX idx_transfer_document_payload_source
    ON warehouse.transfer_document_payload (source_warehouse_id);

CREATE INDEX idx_transfer_document_payload_destination
    ON warehouse.transfer_document_payload (destination_warehouse_id);

CREATE TABLE warehouse.transfer_document_lines (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    material_reference_id UUID NOT NULL
        REFERENCES warehouse.material_references (id),
    quantity NUMERIC(19, 6) NOT NULL,
    line_order INTEGER NOT NULL,
    CONSTRAINT fk_transfer_document_lines_payload
        FOREIGN KEY (document_id)
        REFERENCES warehouse.transfer_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_transfer_document_lines_quantity
        CHECK (quantity > 0),
    CONSTRAINT uk_transfer_document_lines_document_material
        UNIQUE (document_id, material_reference_id),
    CONSTRAINT uk_transfer_document_lines_document_order
        UNIQUE (document_id, line_order)
);

CREATE INDEX idx_transfer_document_lines_document_id
    ON warehouse.transfer_document_lines (document_id);
