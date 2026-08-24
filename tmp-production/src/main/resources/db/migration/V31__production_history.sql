-- STAGE7-015A: Production-owned immutable business history (Production Spec §22, ADR-021).
-- Append-only. No cross-capability FK. Security Audit / Analytics are out of scope.
CREATE TABLE production.production_history (
    entry_id UUID PRIMARY KEY,
    source_order_id UUID NOT NULL,
    history_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_order_item_id UUID NULL,
    source_document_id UUID NULL,
    business_reference_id UUID NULL,
    actor_ref VARCHAR(256) NULL,
    summary VARCHAR(1024) NULL,
    details_json JSONB NULL,
    CONSTRAINT chk_production_history_type
        CHECK (history_type IN (
            'ORDER_ACCEPTED',
            'MATERIALS_CHECKED',
            'MATERIAL_TRANSFER_CREATED',
            'MATERIAL_RECEIPT_CONFIRMED',
            'PRODUCTS_RELEASED',
            'PLAN_FACT_DEVIATION',
            'PRODUCTION_CANCELLED'))
);

CREATE INDEX idx_production_history_order_occurred
    ON production.production_history (source_order_id, occurred_at ASC, recorded_at ASC, entry_id ASC);

-- Document/reference-backed facts: one row per (type, business reference).
-- Material Check intentionally leaves business_reference_id NULL (each explicit check is a new fact).
CREATE UNIQUE INDEX uk_production_history_type_business_reference
    ON production.production_history (history_type, business_reference_id)
    WHERE business_reference_id IS NOT NULL;

CREATE OR REPLACE FUNCTION production.reject_production_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'production.production_history is append-only: % not allowed', TG_OP;
END;
$$;

CREATE TRIGGER trg_production_history_no_update
    BEFORE UPDATE ON production.production_history
    FOR EACH ROW
    EXECUTE FUNCTION production.reject_production_history_mutation();

CREATE TRIGGER trg_production_history_no_delete
    BEFORE DELETE ON production.production_history
    FOR EACH ROW
    EXECUTE FUNCTION production.reject_production_history_mutation();
