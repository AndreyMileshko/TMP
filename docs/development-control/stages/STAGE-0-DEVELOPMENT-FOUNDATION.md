# Stage 0 Manifest — Development Foundation

## Goal

Создать собираемый, тестируемый и запускаемый технический фундамент без бизнес-логики.

## Planning domains

Cursor обязан сформировать маленькие задачи минимум по следующим направлениям:

1. repository and Maven parent;
2. approved module skeleton;
3. dependency and plugin management;
4. formatting and static analysis;
5. test baseline;
6. Spring composition root;
7. JavaFX bootstrap and empty shell;
8. PostgreSQL configuration;
9. Flyway baseline;
10. Testcontainers integration;
11. ArchUnit baseline;
12. logging and profiles;
13. jlink/jpackage;
14. complete Stage verification.

## Forbidden

- domain entities;
- business use cases;
- real permissions;
- real documents;
- module business screens.

## Exit criteria

- approved Reactor exists;
- application opens empty main window;
- Spring and JavaFX lifecycle is correct;
- PostgreSQL/Flyway/Testcontainers work;
- quality gates are wired to Maven verify;
- package can be produced for approved target OS;
- no business functionality exists.
