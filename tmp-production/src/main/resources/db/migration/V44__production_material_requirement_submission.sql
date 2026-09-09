-- Stage 3.5.10: Production-owned Material Requirement Submit lifecycle + traceability.
-- Production-owned persistence only. No FK to Security user, Warehouse or Document Engine schema.
-- Submit moves no physical stock; these tables record Production state + the routing snapshot.

-- 1. Requirement lifecycle: DRAFT -> SUBMITTED + submission metadata.
ALTER TABLE production.material_requirements
    ADD COLUMN submitted_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN submitted_by VARCHAR(256) NULL;

ALTER TABLE production.material_requirements
    DROP CONSTRAINT chk_material_requirements_status;

ALTER TABLE production.material_requirements
    ADD CONSTRAINT chk_material_requirements_status
        CHECK (status IN ('DRAFT', 'SUBMITTED'));

-- DRAFT: no submission metadata; SUBMITTED: both present.
ALTER TABLE production.material_requirements
    ADD CONSTRAINT chk_material_requirements_submission_metadata
        CHECK (
            (status = 'DRAFT' AND submitted_at IS NULL AND submitted_by IS NULL)
            OR (status = 'SUBMITTED' AND submitted_at IS NOT NULL AND submitted_by IS NOT NULL)
        );

-- 2. Generated Warehouse Transfer Document links (one initial document per source warehouse).
CREATE TABLE production.material_requirement_generated_documents (
    requirement_id UUID NOT NULL,
    warehouse_document_id UUID NOT NULL,
    source_warehouse_id UUID NOT NULL,
    destination_warehouse_id UUID NOT NULL,
    document_order INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_material_requirement_generated_documents
        PRIMARY KEY (requirement_id, warehouse_document_id),
    CONSTRAINT fk_material_requirement_generated_documents_requirement
        FOREIGN KEY (requirement_id)
        REFERENCES production.material_requirements (id),
    CONSTRAINT uk_material_requirement_generated_documents_document
        UNIQUE (warehouse_document_id),
    CONSTRAINT uk_material_requirement_generated_documents_source
        UNIQUE (requirement_id, source_warehouse_id),
    CONSTRAINT uk_material_requirement_generated_documents_order
        UNIQUE (requirement_id, document_order)
);

CREATE INDEX idx_material_requirement_generated_documents_requirement
    ON production.material_requirement_generated_documents (requirement_id);

-- 3. Immutable routing snapshot at Submit time: one row per requirement line.
--    uncovered_quantity is audit-only; it does NOT create continuation or reduce document quantity.
CREATE TABLE production.material_requirement_routing_snapshot (
    requirement_id UUID NOT NULL,
    requirement_line_id UUID NOT NULL,
    material_reference_id UUID NOT NULL,
    source_warehouse_id UUID NOT NULL,
    source_warehouse_code VARCHAR(128) NOT NULL,
    warehouse_document_id UUID NOT NULL,
    warehouse_transfer_line_id UUID NOT NULL,
    available_at_routing NUMERIC(19, 6) NOT NULL,
    routed_quantity NUMERIC(19, 6) NOT NULL,
    uncovered_quantity NUMERIC(19, 6) NOT NULL,
    CONSTRAINT pk_material_requirement_routing_snapshot
        PRIMARY KEY (requirement_id, requirement_line_id),
    CONSTRAINT fk_material_requirement_routing_snapshot_requirement
        FOREIGN KEY (requirement_id)
        REFERENCES production.material_requirements (id),
    CONSTRAINT fk_material_requirement_routing_snapshot_line
        FOREIGN KEY (requirement_line_id)
        REFERENCES production.material_requirement_lines (id),
    CONSTRAINT chk_material_requirement_routing_snapshot_quantities
        CHECK (available_at_routing >= 0 AND routed_quantity >= 0 AND uncovered_quantity >= 0)
);

CREATE INDEX idx_material_requirement_routing_snapshot_requirement
    ON production.material_requirement_routing_snapshot (requirement_id);
