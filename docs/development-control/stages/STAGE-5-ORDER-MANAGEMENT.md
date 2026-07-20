# Stage 5 Manifest — Order Management

## Source

Order Management Specification, Document Engine public API, Database Specification and related ADR.

## Planning domains

1. customer order aggregate/container;
2. order position aggregate/root according to specification;
3. value objects and invariants;
4. statuses and transitions;
5. commands and queries;
6. document-driven state changes;
7. persistence ports and adapters;
8. migrations;
9. events/public contracts;
10. order and position UI;
11. unit/integration/architecture tests.

## Exit criteria

Orders and positions are managed only through approved APIs and document-based state transitions.
