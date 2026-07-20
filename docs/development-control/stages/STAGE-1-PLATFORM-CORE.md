# Stage 1 Manifest — Platform Core

## Source

Platform Core Specification, Constitution, core ADR and stable Stage 0 contracts.

## Planning domains

1. Core API boundaries;
2. Platform Registry;
3. Service Registry;
4. Event Bus contracts;
5. event publication and subscription;
6. lifecycle management;
7. module metadata;
8. configuration;
9. architecture tests;
10. minimal technical UI visibility if specified;
11. Stage integration verification.

## Restrictions

Platform Core contains no business rules and knows no domain Capability internals.

## Exit criteria

All core services are accessible only through stable public APIs, tested, and free of domain logic.
