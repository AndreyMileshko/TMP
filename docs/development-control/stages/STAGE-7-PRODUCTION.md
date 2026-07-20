# Stage 7 Manifest — Production

## Source

Production Specification, Order and Warehouse public APIs, Document Engine API and Database Specification.

## Planning domains

1. production state of order position;
2. production planning contracts;
3. operations/routes/stages as specified;
4. material recommendation integration;
5. production reservation requests;
6. release/issue integration;
7. status transitions;
8. documents and events;
9. persistence and migrations;
10. UI/workplaces;
11. integration tests with Order and Warehouse.

## Exit criteria

Production controls only its own state and uses approved public contracts for order and warehouse interactions.
