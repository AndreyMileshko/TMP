# Stage 6 Manifest — Warehouse

## Source

Warehouse Specification, Order public contracts, Document Engine API, Database Specification and warehouse ADR.

## Planning domains

1. warehouse and location model;
2. material identity/reference contracts;
3. balances;
4. receipt;
5. movement;
6. reservation rules;
7. issue/consumption rules;
8. immutable correction operations;
9. production warehouse rules;
10. persistence and migrations;
11. document types and posting handlers;
12. UI;
13. cross-module integration tests.

## Exit criteria

Warehouse owns and changes only warehouse state, with auditable document-driven operations and specified stock rules.
