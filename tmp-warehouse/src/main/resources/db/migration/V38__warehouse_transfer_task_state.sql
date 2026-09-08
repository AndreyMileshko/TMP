-- Stage 3.5.5: informational Transfer preparation task worker assignment.
-- Missing row = NEW; present row = IN_WORK. No task_status column.
-- document_id FK stays inside Warehouse (payload cascade). Security userId is opaque UUID.

CREATE TABLE warehouse.transfer_task_state (
    document_id UUID PRIMARY KEY,
    working_user_id UUID NOT NULL,
    working_since TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transfer_task_state_payload
        FOREIGN KEY (document_id)
        REFERENCES warehouse.transfer_document_payload (document_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_transfer_task_state_working_user
    ON warehouse.transfer_task_state (working_user_id);
