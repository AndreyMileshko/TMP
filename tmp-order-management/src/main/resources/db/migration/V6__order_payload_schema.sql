-- Stage 5 Order Management: typed document payload physical model (Specification §11.5).
-- Next free Flyway version after V5. Aggregate tables and processing records are out of scope.
CREATE SCHEMA IF NOT EXISTS order_management;

CREATE TABLE order_management.order_document_payload (
    document_id UUID PRIMARY KEY,
    document_type_code VARCHAR(64) NOT NULL,
    payload_schema_version INTEGER NOT NULL,
    payload_revision BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_order_document_payload_type CHECK (document_type_code IN (
        'ORDER_CREATE',
        'ORDER_UPDATE',
        'ORDER_APPROVE',
        'ORDER_CANCEL',
        'ORDER_ITEM_CREATE',
        'ORDER_ITEM_UPDATE',
        'ORDER_ITEM_CANCEL',
        'ORDER_ITEM_REVISION_CREATE',
        'ORDER_ITEM_REVISION_UPDATE',
        'ORDER_ITEM_REVISION_APPROVE'
    )),
    CONSTRAINT chk_order_document_payload_schema_version CHECK (payload_schema_version >= 1),
    CONSTRAINT chk_order_document_payload_revision CHECK (payload_revision >= 0)
);

CREATE TABLE order_management.order_create_payload (
    document_id UUID PRIMARY KEY,
    order_number VARCHAR(64) NOT NULL,
    customer_ref VARCHAR(128),
    customer_name VARCHAR(255) NOT NULL,
    contract_ref VARCHAR(128),
    site_ref VARCHAR(128),
    responsible_manager VARCHAR(128),
    direction VARCHAR(32) NOT NULL,
    currency_code VARCHAR(16) NOT NULL,
    CONSTRAINT fk_order_create_payload_document
        FOREIGN KEY (document_id)
        REFERENCES order_management.order_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_order_create_payload_direction
        CHECK (direction IN ('PRIVATE', 'DEALER', 'CORPORATE'))
);

CREATE UNIQUE INDEX uk_order_create_payload_order_number
    ON order_management.order_create_payload (order_number);

CREATE TABLE order_management.order_update_payload (
    document_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_ref VARCHAR(128),
    customer_name VARCHAR(255) NOT NULL,
    contract_ref VARCHAR(128),
    site_ref VARCHAR(128),
    responsible_manager VARCHAR(128),
    direction VARCHAR(32) NOT NULL,
    currency_code VARCHAR(16) NOT NULL,
    CONSTRAINT fk_order_update_payload_document
        FOREIGN KEY (document_id)
        REFERENCES order_management.order_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_order_update_payload_direction
        CHECK (direction IN ('PRIVATE', 'DEALER', 'CORPORATE'))
);

CREATE TABLE order_management.order_status_payload (
    document_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    CONSTRAINT fk_order_status_payload_document
        FOREIGN KEY (document_id)
        REFERENCES order_management.order_document_payload (document_id)
        ON DELETE CASCADE
);

CREATE TABLE order_management.order_item_create_payload (
    document_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    comments TEXT,
    ordered_quantity NUMERIC(18, 4) NOT NULL,
    CONSTRAINT fk_order_item_create_payload_document
        FOREIGN KEY (document_id)
        REFERENCES order_management.order_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_order_item_create_payload_qty CHECK (ordered_quantity > 0),
    CONSTRAINT uk_order_item_create_payload_item UNIQUE (order_item_id)
);

CREATE TABLE order_management.order_item_update_payload (
    document_id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    comments TEXT,
    CONSTRAINT fk_order_item_update_payload_document
        FOREIGN KEY (document_id)
        REFERENCES order_management.order_document_payload (document_id)
        ON DELETE CASCADE
);

CREATE TABLE order_management.order_item_status_payload (
    document_id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL,
    CONSTRAINT fk_order_item_status_payload_document
        FOREIGN KEY (document_id)
        REFERENCES order_management.order_document_payload (document_id)
        ON DELETE CASCADE
);

CREATE TABLE order_management.order_item_revision_create_payload (
    document_id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    copy_from_revision_number INTEGER,
    CONSTRAINT fk_order_item_revision_create_payload_document
        FOREIGN KEY (document_id)
        REFERENCES order_management.order_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_order_item_revision_create_revision CHECK (revision_number >= 1),
    CONSTRAINT chk_order_item_revision_create_copy_from
        CHECK (copy_from_revision_number IS NULL OR copy_from_revision_number >= 1)
);

CREATE TABLE order_management.order_item_revision_update_payload (
    document_id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    ordered_quantity NUMERIC(18, 4) NOT NULL,
    CONSTRAINT fk_order_item_revision_update_payload_document
        FOREIGN KEY (document_id)
        REFERENCES order_management.order_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_order_item_revision_update_revision CHECK (revision_number >= 1),
    CONSTRAINT chk_order_item_revision_update_qty CHECK (ordered_quantity > 0)
);

CREATE TABLE order_management.order_item_revision_approve_payload (
    document_id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    CONSTRAINT fk_order_item_revision_approve_payload_document
        FOREIGN KEY (document_id)
        REFERENCES order_management.order_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_order_item_revision_approve_revision CHECK (revision_number >= 1)
);

CREATE TABLE order_management.order_item_revision_payload_line (
    document_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(255) NOT NULL,
    quantity NUMERIC(18, 4) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    consumption_norm NUMERIC(18, 4) NOT NULL,
    CONSTRAINT pk_order_item_revision_payload_line PRIMARY KEY (document_id, line_number),
    CONSTRAINT fk_order_item_revision_payload_line_document
        FOREIGN KEY (document_id)
        REFERENCES order_management.order_document_payload (document_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_order_item_revision_payload_line_number CHECK (line_number >= 1),
    CONSTRAINT chk_order_item_revision_payload_line_qty CHECK (quantity > 0),
    CONSTRAINT chk_order_item_revision_payload_line_norm CHECK (consumption_norm >= 0)
);
