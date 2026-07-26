-- Stage 5 Order Management: aggregate tables (Specification §19).
-- Next free Flyway version after V7. Payload/processing tables are not duplicated.
CREATE TABLE order_management.orders (
    order_id UUID PRIMARY KEY,
    order_number VARCHAR(64) NOT NULL,
    customer_ref VARCHAR(128),
    customer_name VARCHAR(255) NOT NULL,
    contract_ref VARCHAR(128),
    site_ref VARCHAR(128),
    responsible_manager VARCHAR(128),
    direction VARCHAR(32) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT chk_orders_direction CHECK (direction IN ('PRIVATE', 'DEALER', 'CORPORATE')),
    CONSTRAINT chk_orders_status CHECK (status IN ('DRAFT', 'APPROVED', 'CANCELLED')),
    CONSTRAINT chk_orders_version CHECK (version >= 0)
);

CREATE TABLE order_management.order_items (
    order_item_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    comments VARCHAR(2000),
    status VARCHAR(32) NOT NULL,
    active_revision_number INTEGER,
    draft_revision_number INTEGER,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES order_management.orders (order_id),
    CONSTRAINT chk_order_items_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CANCELLED')),
    CONSTRAINT chk_order_items_version CHECK (version >= 0),
    CONSTRAINT chk_order_items_active_revision
        CHECK (active_revision_number IS NULL OR active_revision_number >= 1),
    CONSTRAINT chk_order_items_draft_revision
        CHECK (draft_revision_number IS NULL OR draft_revision_number >= 1)
);

CREATE INDEX idx_order_items_order_id
    ON order_management.order_items (order_id);

CREATE TABLE order_management.order_item_revisions (
    order_item_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    revision_status VARCHAR(32) NOT NULL,
    ordered_quantity NUMERIC(19, 6) NOT NULL,
    previous_revision_number INTEGER,
    CONSTRAINT pk_order_item_revisions PRIMARY KEY (order_item_id, revision_number),
    CONSTRAINT fk_order_item_revisions_item
        FOREIGN KEY (order_item_id)
        REFERENCES order_management.order_items (order_item_id),
    CONSTRAINT chk_order_item_revisions_number CHECK (revision_number >= 1),
    CONSTRAINT chk_order_item_revisions_quantity CHECK (ordered_quantity > 0),
    CONSTRAINT chk_order_item_revisions_status
        CHECK (revision_status IN ('DRAFT', 'APPROVED')),
    CONSTRAINT chk_order_item_revisions_previous
        CHECK (previous_revision_number IS NULL OR previous_revision_number >= 1)
);

CREATE TABLE order_management.item_specifications (
    order_item_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    immutable BOOLEAN NOT NULL,
    CONSTRAINT pk_item_specifications PRIMARY KEY (order_item_id, revision_number),
    CONSTRAINT fk_item_specifications_revision
        FOREIGN KEY (order_item_id, revision_number)
        REFERENCES order_management.order_item_revisions (order_item_id, revision_number)
);

CREATE TABLE order_management.item_specification_lines (
    order_item_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    line_number INTEGER NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(255) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    consumption_norm NUMERIC(19, 6) NOT NULL,
    CONSTRAINT pk_item_specification_lines
        PRIMARY KEY (order_item_id, revision_number, line_number),
    CONSTRAINT fk_item_specification_lines_spec
        FOREIGN KEY (order_item_id, revision_number)
        REFERENCES order_management.item_specifications (order_item_id, revision_number),
    CONSTRAINT chk_item_specification_lines_line_number CHECK (line_number >= 1),
    CONSTRAINT chk_item_specification_lines_quantity CHECK (quantity > 0),
    CONSTRAINT chk_item_specification_lines_norm CHECK (consumption_norm >= 0)
);
