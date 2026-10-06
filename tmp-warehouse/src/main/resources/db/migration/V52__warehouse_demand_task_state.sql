-- B3B-3C1: informational Demand supply-task worker assignment.
-- Missing row = NEW; present row = IN_WORK. No task_status column.
-- demand_id FK stays inside Warehouse. Security userId is opaque UUID.

CREATE TABLE warehouse.demand_task_state (
    demand_id UUID PRIMARY KEY,
    working_user_id UUID NOT NULL,
    working_since TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_demand_task_state_demand
        FOREIGN KEY (demand_id)
        REFERENCES warehouse.warehouse_demands (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_demand_task_state_working_user
    ON warehouse.demand_task_state (working_user_id);
