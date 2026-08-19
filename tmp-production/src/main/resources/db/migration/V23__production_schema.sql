-- Stage 7 Production persistence schema (Production Spec §5, §8).
-- Item-owned state only. No Production Order, Revision, Warehouse or Order tables.
CREATE SCHEMA IF NOT EXISTS production;

CREATE TABLE production.production_item_states (
    id UUID PRIMARY KEY,
    source_order_id UUID NOT NULL,
    source_order_item_id UUID NOT NULL,
    specification_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    ordered_quantity BIGINT NOT NULL,
    launched_quantity BIGINT NOT NULL,
    active_production_quantity BIGINT NOT NULL,
    released_quantity BIGINT NOT NULL,
    last_material_check_at TIMESTAMP WITH TIME ZONE,
    last_status_changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_production_item_states_identity
        UNIQUE (source_order_id, source_order_item_id, specification_id),
    CONSTRAINT chk_production_item_states_status
        CHECK (status IN ('IN_PRODUCTION', 'PARTIALLY_RELEASED', 'RELEASED', 'CANCELLED')),
    CONSTRAINT chk_production_item_states_ordered_quantity
        CHECK (ordered_quantity > 0),
    CONSTRAINT chk_production_item_states_launched_quantity
        CHECK (launched_quantity >= 0),
    CONSTRAINT chk_production_item_states_active_production_quantity
        CHECK (active_production_quantity >= 0),
    CONSTRAINT chk_production_item_states_released_quantity
        CHECK (released_quantity >= 0),
    CONSTRAINT chk_production_item_states_version
        CHECK (version >= 0)
);

CREATE INDEX idx_production_item_states_source_order_id
    ON production.production_item_states (source_order_id);

CREATE INDEX idx_production_item_states_source_order_item_id
    ON production.production_item_states (source_order_item_id);

CREATE INDEX idx_production_item_states_specification_id
    ON production.production_item_states (specification_id);
