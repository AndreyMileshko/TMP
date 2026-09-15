-- Stage 3.5.15: persist Warehouse operation actor for History audit.
-- Historical rows remain NULL (UI shows «—»). No FK to Security.
ALTER TABLE warehouse.warehouse_operations
    ADD COLUMN actor_user_id UUID NULL,
    ADD COLUMN actor_login VARCHAR(128) NULL;
