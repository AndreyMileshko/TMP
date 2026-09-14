-- Stage 3.5.15: Warehouse-managed production destination assignment.
-- Does not auto-assign any warehouse (including SECOND / by code / by UUID).
ALTER TABLE warehouse.warehouses
    ADD COLUMN is_production BOOLEAN NOT NULL DEFAULT FALSE;

-- At most one production warehouse in the system.
CREATE UNIQUE INDEX uk_warehouses_single_production
    ON warehouse.warehouses (is_production)
    WHERE is_production = TRUE;
