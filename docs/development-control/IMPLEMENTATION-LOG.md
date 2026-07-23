# TMP Implementation Log

Новые записи добавляются в конец. История не переписывается.

---

# Entry Template

## `<TASK-ID>` — `<title>`

**Date:** YYYY-MM-DD  
**Stage:** ...  
**Status:** DONE | BLOCKED | FAILED

### Result

...

### Files created

- ...

### Files modified

- ...

### Tests added or changed

- ...

### Verification

| Check | Result |
|---|---|
| ... | PASSED/FAILED |

### Architecture review

- ...

### Documentation updated

- ...

### Next task

`<TASK-ID>`

## `CONTROL-001` — `Validate development sources`

**Date:** 2026-07-17  
**Stage:** Control  
**Status:** DONE

### Result

Собран реестр обязательных документов, подтверждено наличие требуемых источников и отсутствие явных конфликтов по статусу/назначению.

### Files created

- None.

### Files modified

- `docs/development-control/CONTEXT-MAP.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`

### Tests added or changed

- None.

### Verification

| Check | Result |
|---|---|
| Manual document path and status validation | PASSED |

### Architecture review

- Конфликтов между Constitution, ADR, Architecture Overview, Database Specification, Development Guide, Code Quality Standards, Master Implementation Plan и Cursor AI Guide не обнаружено на уровне обязательных правил и статусов Accepted.

### Documentation updated

- `docs/development-control/CONTEXT-MAP.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`

### Next task

`CONTROL-002`

## `CONTROL-002` — `Build Stage 0 task queue`

**Date:** 2026-07-17  
**Stage:** Control  
**Status:** DONE

### Result

Сформирована декомпозиция Stage 0 в автономные задачи с ограниченным scope, зависимостями, критериями приемки и командами проверки; первая задача Stage 0 установлена в READY.

### Files created

- None.

### Files modified

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`

### Tests added or changed

- None.

### Verification

| Check | Result |
|---|---|
| Task-size and Stage 0 manifest conformity review | PASSED |

### Architecture review

- Очередь не нарушает запреты Stage 0 (без доменной логики и бизнес-экранов), задачи ориентированы на технический фундамент.

### Documentation updated

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`

### Next task

`STAGE0-001`

## `STAGE0-001` — `Bootstrap repository reactor and parent pom`

**Date:** 2026-07-17  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Создан базовый Maven reactor (`tmp-parent`) и каркас модулей Stage 0. После восстановления терминальной среды верификация `validate` подтверждена через локальный portable Maven.

### Files created

- `pom.xml`
- `tmp-bootstrap-app/pom.xml`
- `tmp-ui-shell/pom.xml`
- `tmp-infra-db/pom.xml`
- `tmp-architecture-tests/pom.xml`

### Files modified

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/BLOCKERS.md`

### Tests added or changed

- None.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -DskipTests validate` | PASSED |

### Architecture review

- Изменения ограничены техническим foundation слоем и не вводят бизнес-логику.

### Documentation updated

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/BLOCKERS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task

`STAGE0-002`

## `STAGE0-002` — `Configure dependency and plugin management baseline`

**Date:** 2026-07-17  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

В `tmp-parent` добавлены централизованные версии и управление build plugins через `dependencyManagement` и `pluginManagement`, что создает единый baseline сборки для модулей Stage 0.

### Files created

- None.

### Files modified

- `pom.xml`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Tests added or changed

- None.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -DskipTests help:effective-pom` | PASSED |

### Architecture review

- Изменения ограничены инфраструктурой сборки, без добавления предметной логики и без выхода за границы Stage 0.

### Documentation updated

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task

`STAGE0-003`

## `STAGE0-003` — `Wire formatting and static analysis gates`

**Date:** 2026-07-17  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Подключены quality gates в `verify`: enforcer (версии Java/Maven), checkstyle и spotbugs. Базовая проверка запускается автоматически в жизненном цикле Maven.

### Files created

- None.

### Files modified

- `pom.xml`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Tests added or changed

- None.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q verify -DskipTests` | PASSED |

### Architecture review

- Изменения ограничены build quality pipeline, без реализации бизнес-функциональности.

### Documentation updated

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task

`STAGE0-004`

## `STAGE0-004` — `Establish test baseline and test module conventions`

**Date:** 2026-07-17  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Подготовлен baseline для тестов (JUnit dependency и smoke test). После подключения JDK 21 verification `mvn test` прошла успешно.

### Files created

- `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/BootstrapSmokeTest.java`

### Files modified

- `pom.xml`
- `tmp-bootstrap-app/pom.xml`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/BLOCKERS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Tests added or changed

- `BootstrapSmokeTest` (unit smoke test baseline)

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q test` (JDK 21) | PASSED |

### Architecture review

- Реализация не выходит за пределы Stage 0, но продолжение требует среды, соответствующей утвержденному Java 21 baseline.

### Documentation updated

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/BLOCKERS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task

`STAGE0-005`

## `STAGE0-005` — `Create Spring composition root skeleton`

**Date:** 2026-07-17  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Добавлен Spring Boot composition root в `tmp-bootstrap-app`: `TmpBootstrapApplication`, `BootstrapConfiguration`, `application.yml` (non-web).

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-bootstrap-app test` | PASSED |

### Next task

`STAGE0-006`

## `STAGE0-006` — `Implement JavaFX empty shell bootstrap`

**Date:** 2026-07-17  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Реализован JavaFX empty shell (`EmptyMainShell`, `JavaFxShellLauncher`, `DesktopBootstrap`) с отдельным bootstrap от Spring.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-ui-shell test` | PASSED |

### Next task

`STAGE0-007`

## `STAGE0-007` — `Configure PostgreSQL connectivity profiles`

**Date:** 2026-07-17  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Модуль `tmp-infra-db` настроен с PostgreSQL datasource profiles (`dev`/`test`) и auto-configuration.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db test` | PASSED |

### Next task

`STAGE0-008`

## `STAGE0-008` — `Add Flyway baseline migration flow`

**Date:** 2026-07-17  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Добавлена baseline Flyway-миграция `V1__platform_baseline.sql` и интеграционные smoke-тесты schema history.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db verify` | PASSED |

### Next task

`STAGE0-009`

## `STAGE0-009` — `Integrate Testcontainers for PostgreSQL tests`

**Date:** 2026-07-17  
**Stage:** Stage 0 — Development Foundation  
**Status:** BLOCKED

### Result

Добавлены Testcontainers dependencies и `FlywayPostgresIntegrationIT`, но verification заблокирована отсутствием Docker.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db verify` | FAILED (Docker not available) |

### Next task

`STAGE0-009` (resume after BLK-003 resolution)

## `STAGE0-009` — `Integrate Testcontainers for PostgreSQL tests` (resolved)

**Date:** 2026-07-20  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Docker Desktop + WSL2 подключены; Testcontainers обновлён до 1.21.4 с `api.version=1.44`. PostgreSQL container IT проходит.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db verify` | PASSED |

### Next task

`STAGE0-010`

## `STAGE0-010` — `Create ArchUnit baseline architecture tests`

**Date:** 2026-07-20  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Добавлен модуль `tmp-architecture-tests` с baseline ArchUnit-правилами границ UI/infra/Spring.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q verify -DskipITs` | PASSED |

### Next task

`STAGE0-011`

## `STAGE0-011` — `Configure logging, runtime profiles, and packaging`

**Date:** 2026-07-20  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Настроены logging (`logback-spring.xml`), профили `dev`/`test`/`package`, jpackage app-image в `dist/jpackage/TMP`.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -Ppackage verify` | PASSED |
| Package artifact `dist/jpackage/TMP/TMP.exe` | PRESENT |

### Next task

`STAGE0-012`

## `STAGE0-012` — `Run complete Stage 0 verification gate`

**Date:** 2026-07-20  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Полный Stage 0 verification gate пройден. Exit criteria Stage 0 подтверждены; Stage 0 закрыт.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q verify` | PASSED |
| Package artifact present | PASSED |
| No business functionality in Stage 0 | PASSED |

### Next task

Stage 0 complete — awaiting Stage 1 Start Gate

## Stage 2 — Document Engine (summary)

## `STAGE2-026` — `Final Stage 2 re-verification gate (re-review)`

**Date:** 2026-07-22  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

Stage 2 закрыт после residual re-review fixes (BLK-011 transaction-final registry compensation, BLK-013 after-commit handler policy, PostgreSQL ITs, intra-module FK). Stage 3 не начат.

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Manual `dist/jpackage/TMP/TMP.exe` | PASSED |
| BLK-011, BLK-013 | RESOLVED |
| Stage 3 start | NOT STARTED |

### Next task

Stage 2 complete — awaiting Stage 3 Start Gate

## `STAGE2-025` — `FK document_type_id decision and invariant`

**Date:** 2026-07-22  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

Добавлен intra-module FK `fk_documents_document_type` (`documents.document_type_id` → `document_types.id`) через `V3__documents_document_type_fk.sql`. Решение согласовано с Database Specification §12 (cross-module FK запрещены; обе таблицы в schema `documents`). Дополнительно `createDocument` проверяет `documentTypeExists`.

### Files created

- `V3__documents_document_type_fk.sql`

### Next task

`STAGE2-026`

## `STAGE2-024` — `PostgreSQL Testcontainers Document Engine ITs`

**Date:** 2026-07-22  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

Добавлен `DocumentEnginePostgresIntegrationIT` с покрытием registration/operation rollback, optimistic lock, concurrent post/update, after-commit/no-event-on-rollback, failing subscriber, snapshots/journal, file storage, FK presence. H2 component tests сохранены. Добавлен `docker-java.properties` (`api.version=1.44`) для Docker Engine 29.

### Files created

- `DocumentEnginePostgresIntegrationIT.java`
- `tmp-document-engine/src/test/resources/docker-java.properties`

### Next task

`STAGE2-025` / `STAGE2-026`

## `STAGE2-023` — `After-commit handler failure policy (BLK-013)`

**Date:** 2026-07-22  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

Best-effort after-commit delivery: handler failures логируются и не пробрасываются вызывающему коду. Документ/journal остаются committed. Platform Core EventBus contract не изменён. Callers must not retry document mutations on delivery failure.

### Files modified

- `TransactionAfterCommitEventPublisher.java`

### Tests added or changed

- `DefaultDocumentEngineTransactionEventTest.failingAfterCommitHandlerDoesNotFailDocumentOperation`

### Next task

`STAGE2-024`

## `STAGE2-022` — `Registry rollback compensation (BLK-011 reopen)`

**Date:** 2026-07-22  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

In-memory processor registry компенсируется при любом non-COMMITTED outcome через `TransactionSynchronization.afterCompletion`. Outer rollback / commit-failure path очищают registry; повторная регистрация успешна. Create блокируется при отсутствии типа в БД.

### Files modified

- `DefaultDocumentEngine.java`
- `DefaultDocumentProcessorRegistry.java`

### Tests added or changed

- `DefaultDocumentEngineRegistrationTransactionTest`

### Next task

`STAGE2-023`

## `STAGE2-021` — `Final Stage 2 re-verification gate`

**Date:** 2026-07-22  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

Stage 2 закрыт после устранения acceptance-review blockers BLK-010..012. Полная verification и ручной запуск TMP.exe успешны. Stage 3 не начат. (Позднее reopened для residual BLK-011/BLK-013; окончательно закрыт в STAGE2-026.)

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Manual `dist/jpackage/TMP/TMP.exe` | PASSED |
| BLK-010..012 | RESOLVED (BLK-011 later reopened) |
| Stage 2 exit criteria | CONFIRMED |
| Stage 3 start | NOT STARTED |

### Documentation updated

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG

### Next task

Stage 2 complete — awaiting Stage 3 Start Gate

## `STAGE2-020` — `Expanded lifecycle/rollback/concurrency tests`

**Date:** 2026-07-22  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

Расширено покрытие: processor failure rollback (create/post/unpost), invalid transitions, immutable POSTED/CLOSED, delete restrictions, optimistic locking, concurrent post/update, version snapshots, journal consistency, close allow/reject, file storage adapter.

### Tests added or changed

- `DefaultDocumentEngineLifecycleTest`
- `JdbcDocumentFileStorageAdapterTest`
- `ConfigurableDocumentProcessor` (test support)

### Verification

| Check | Result |
|---|---|
| Document engine + bootstrap tests | PASSED |

### Next task

`STAGE2-021`

## `STAGE2-019` — `Post-commit event publishing (BLK-012)`

**Date:** 2026-07-22  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

События DocumentCreated/Posted/Unposted/Closed/Deleted публикуются только после commit через Spring `TransactionSynchronization.afterCommit`. При rollback события не публикуются. Message broker не внедрялся.

### Files created

- `TransactionAfterCommitEventPublisher.java`

### Files modified

- `DefaultDocumentEngine.java`
- `DocumentEngineAutoConfiguration.java`

### Tests added or changed

- `DefaultDocumentEngineTransactionEventTest`

### Verification

| Check | Result |
|---|---|
| Transaction event integration tests | PASSED |
| BLK-012 | RESOLVED |

### Next task

`STAGE2-020`

## `STAGE2-018` — `Atomic processor registration (BLK-011)`

**Date:** 2026-07-22  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

Регистрация processor согласована с document type: DB `registerDocumentType` выполняется до in-memory `processorRegistry.register`. При DB failure partial state в registry не остаётся; повторная регистрация проходит.

### Files modified

- `DefaultDocumentEngine.java`

### Tests added or changed

- `DefaultDocumentEngineRegistrationTest` (duplicate, DB failure, retry)

### Verification

| Check | Result |
|---|---|
| Registration atomicity tests | PASSED |
| BLK-011 | RESOLVED |

### Next task

`STAGE2-019`

## `STAGE2-017` — `Fix duplicate DocumentEngine beans (BLK-010)`

**Date:** 2026-07-22  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

Оставлен ровно один Spring bean типа `DocumentEngine`. `DesktopBootstrap.getBean(DocumentEngine.class)` больше не получает ambiguous candidates.

### Files modified

- `DocumentEngineAutoConfiguration.java` (удалён `documentEngineFacade`)
- `DocumentEnginePlatformRegistrar.java`

### Tests added or changed

- `DocumentEngineBeanLookupTest`
- `DesktopBootstrapLookupSmokeTest`

### Verification

| Check | Result |
|---|---|
| Bean lookup + DesktopBootstrap smoke | PASSED |
| BLK-010 | RESOLVED |

### Next task

`STAGE2-018`

## `STAGE2-016` — `Complete Stage 2 Document Engine`

**Date:** 2026-07-21  
**Stage:** Stage 2 — Document Engine  
**Status:** DONE

### Result

Реализован модуль `tmp-document-engine` как domain-independent подсистема управления документами: типы документов, единый lifecycle, регистрация процессоров, storage/journal/version ports, JDBC adapters, миграция схемы `documents`, bootstrap integration и архитектурные ограничения.

### Key deliverables

| Component | Location |
|---|---|
| Public API | `com.tmp.document.api` (`DocumentEngine`, metadata/commands/query/status) |
| Processor contract | `DocumentProcessor` (one type → one processor) |
| Engine implementation | `DefaultDocumentEngine` |
| Persistence ports | `DocumentStoragePort`, `LifecycleJournalPort`, `DocumentVersionPort`, `DocumentFileStoragePort` |
| JDBC adapters | `JdbcDocumentStorageAdapter`, `JdbcLifecycleJournalAdapter`, `JdbcDocumentVersionAdapter`, `JdbcDocumentFileStorageAdapter` |
| Database migration | `V2__documents_schema.sql` (`documents` schema + tables/indexes/checks) |
| Spring wiring | `DocumentEngineAutoConfiguration`, platform component registrar |
| UI bridge | Bootstrap renders document panel text; UI shell remains independent |
| Architecture rules | `Stage2DocumentEngineArchitectureTest` |

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Stage 2 exit criteria | CONFIRMED |
| Stage 3 start | NOT STARTED |

### Next task

Stage 2 complete — awaiting Stage 3 Start Gate (later reopened for BLK-010..012; closed again in STAGE2-021)

## Stage 1 — Platform Core (summary)

## `STAGE1-016` — `Fix registration/lifecycle race condition (BLK-009)`

**Date:** 2026-07-21  
**Stage:** Stage 1 — Platform Core  
**Status:** DONE

### Result

Устранена race condition между registration и lifecycle. Единый monitor `DefaultLifecycleManager` для всех state transitions и `registerComponentWithRegistry()`. Deterministic concurrency test (200 iterations). Закреплена семантика STOPPED restart.

### Key deliverables

| Fix | Location |
|---|---|
| Unified synchronization | `DefaultLifecycleManager.registerComponentWithRegistry()` |
| Removed split lock | `DefaultPlatformCore` delegates to lifecycle manager |
| Safe startup iteration | `List.copyOf(components.values())` in `startAll()` |
| Concurrency + restart tests | `DefaultPlatformCoreRegistrationTest` |

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| BLK-009 | RESOLVED |

### Next task

Stage 1 complete — awaiting Stage 2 Start Gate

---

## `STAGE1-015` — `Fix Stage 1 re-review remaining defects (BLK-008)`

**Date:** 2026-07-21  
**Stage:** Stage 1 — Platform Core  
**Status:** DONE

### Result

Устранены оставшиеся дефекты повторной проверки Stage 1. Registration guard по platform state; platform events в `com.tmp.core.api.event.platform`; shutdown listener с `try/finally`; обобщённое ArchUnit правило api-only.

### Key deliverables

| Fix | Location |
|---|---|
| Registration lifecycle guard | `DefaultPlatformCore.isRegistrationAllowed()` |
| Public platform events | `com.tmp.core.api.event.platform` |
| Shutdown resilience | `PlatformCoreAutoConfiguration.PlatformCoreLifecycleListener` |
| Generic API boundary rule | `Stage1PlatformCoreArchitectureTest.externalModulesUseOnlyCorePublicApi` |

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| BLK-008 | RESOLVED |

### Next task

Stage 1 complete — awaiting Stage 2 Start Gate

---

## `STAGE1-014` — `Fix Stage 1 acceptance review blockers (BLK-005..007)`

**Date:** 2026-07-20  
**Stage:** Stage 1 — Platform Core  
**Status:** DONE

### Result

Устранены три блокирующих дефекта acceptance review. Event metadata теперь immutable через `AbstractPlatformEvent` / `AbstractDomainEvent`. `DefaultLifecycleManager` переписан с корректным rollback и platform state на failure. Единый атомарный registration path — `PlatformCore.registerComponent()`. Исправлен Service Registry count; расширены EventBus и architecture tests.

### Key deliverables

| Fix | Location |
|---|---|
| Stable event contract | `AbstractPlatformEvent`, `AbstractDomainEvent`, updated platform events |
| Lifecycle failure policy | `DefaultLifecycleManager`, `DefaultLifecycleManagerTest` |
| Atomic registration | `DefaultPlatformCore.registerComponent()`, `DefaultPlatformCoreRegistrationTest` |
| Service registry accuracy | `registeredServiceCount()`, `DefaultServiceRegistryTest` |
| API boundary enforcement | `Stage1PlatformCoreArchitectureTest` (com.tmp.core root blocked) |

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| BLK-005..007 | RESOLVED |
| Manual TMP.exe launch | PASSED (window opens, process stops cleanly) |

### Next task

Stage 1 complete — awaiting Stage 2 Start Gate

---

**Date:** 2026-07-20  
**Status:** DONE (STAGE1-001..STAGE1-013)

### Result

Реализован модуль `tmp-platform-core` с публичным API (`com.tmp.core.api`), registries (Platform/Service/Capability), синхронным Event Bus, lifecycle management, platform configuration, Spring auto-configuration, ArchUnit-границами и минимальной UI-видимостью статуса платформы.

### Key deliverables

| Component | Location |
|---|---|
| Public Core API | `tmp-platform-core/.../com/tmp/core/api/` |
| Registries | `DefaultPlatformRegistry`, `DefaultServiceRegistry`, `DefaultCapabilityRegistry` |
| Event Bus | `SynchronousEventBus` (sync, no broker) |
| Lifecycle | `DefaultLifecycleManager` |
| Configuration | `SpringPlatformConfiguration`, `PlatformCoreProperties` |
| Bootstrap wiring | `PlatformCoreAutoConfiguration`, `DesktopBootstrap` |
| Architecture tests | `Stage1PlatformCoreArchitectureTest` |
| UI status | `EmptyMainShell` bottom label via status string |

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Unit tests (platform-core) | PASSED |
| Integration tests (bootstrap) | PASSED |
| Architecture tests | PASSED |
| No domain/business logic in Core | CONFIRMED |

### Next task

Stage 1 complete — awaiting Stage 2 Start Gate

## `STAGE0-012` — `Run complete Stage 0 verification gate` (BLK-004 rework)

**Date:** 2026-07-20  
**Stage:** Stage 0 — Development Foundation  
**Status:** DONE

### Result

Исправлен блокирующий дефект acceptance review: `TmpBootstrapApplication` больше не исключает DataSource/Flyway auto-configuration. Настроены профили `dev`/`test`/`package`; package-профиль читает `TMP_DB_URL`, `TMP_DB_USERNAME`, `TMP_DB_PASSWORD`. Добавлен `TmpBootstrapPostgresIntegrationIT` на реальном entry point; усилены `SpringContextSmokeTest` и `PackagingSmokeIT`. Исправлен порядок jpackage (pre-integration-test после repackage).

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd clean verify` | PASSED |
| `.tools/apache-maven-3.9.9/bin/mvn.cmd clean verify -Ppackage` | PASSED |
| `TmpBootstrapPostgresIntegrationIT` (PostgreSQL Testcontainers) | PASSED |
| Package artifact `dist/jpackage/TMP/TMP.exe` | PRESENT |

### Next task

Stage 0 complete — awaiting Stage 1 Start Gate

## `Stage 3 Start Gate` — `Baseline verification before Capability Engine`

**Date:** 2026-07-22  
**Stage:** Stage 3 — Capability Engine  
**Status:** DONE

### Result

Выполнен обязательный Stage 3 Start Gate: подтверждено, что `git status` не содержит несвязанных изменений production-кода (только незакоммиченный `.gitignore` с добавлением `target/`, не относящийся к коду), `HEAD` совпадает с `origin/master` (`af0d2a1b86c3e340398face46dbdf3e8c537e452`), Stage 2 закрыт на 100%, все blockers Stage 0–2 в статусе RESOLVED, STAGE2-026 — последняя завершённая задача. JDK 21 (Temurin 21.0.11+10, portable `.tools/jdk-21.0.11+10`) и Maven 3.9.9 (portable `.tools/apache-maven-3.9.9`) доступны. `mvn clean verify` выполнен как baseline и прошёл полностью (105 тестов, 0 failures/errors во всех модулях, включая PostgreSQL Testcontainers ITs в `tmp-document-engine` и `tmp-bootstrap-app`).

Изучены обязательные источники: Capability Engine Specification, Platform Core Specification (публичные API `com.tmp.core.api.*`), Document Engine публичный API (`com.tmp.document.api.*`), релевантные ADR (ADR-001, ADR-002, ADR-003, ADR-019, ADR-022), UI/UX Specification (раздел навигации), governance rule, текущая структура `tmp-platform-core`/`tmp-document-engine`/`tmp-bootstrap-app`/`tmp-architecture-tests`.

### Design decision — external contribution atomicity (no blocker required)

Проанализирован риск: `DocumentEngine.registerProcessor()` (public API) и `ServiceRegistry.register()` (public API) не предоставляют публичный compensating unregister. Изучение реализации подтвердило:
- `DefaultDocumentProcessorRegistry`/`DefaultDocumentEngine.registerProcessor()` уже атомарны на своей стороне (Stage 2 BLK-011/018): при duplicate typeId или DB-ошибке ничего не регистрируется; частичное состояние невозможно.
- `DefaultServiceRegistry.register()` не содержит проверки duplicate и не бросает исключений (безусловно успешен по текущему контракту).

Принятое техническое решение: Capability Engine выполняет pre-validation (проверка уникальности через read-only `documentEngine.registeredTypes()` и `serviceRegistry.lookup()`) под единым registration lock ДО вызова внешних мутирующих методов, и располагает document-контрибуцию, а затем service-контрибуцию как последние шаги atomic registration (после всех internal-catalog шагов, которые полностью откатываемы). Поскольку service-регистрация безусловно успешна по текущему контракту, а document-регистрация гарантированно атомарна на своей стороне, комбинация исключает реалистичный сценарий "внешняя регистрация висит без компенсации". Остаточный теоретический риск (будущее изменение контракта `ServiceRegistry`) документируется как known limitation в Javadoc `DefaultCapabilityRegistrationService`, а не скрывается. Публичные API Platform Core/Document Engine не изменяются; blocker не требуется.

### Verification

| Check | Result |
|---|---|
| `git status --short` | clean production code (only unrelated `.gitignore` diff) |
| `git rev-parse HEAD` vs `origin/master` | MATCH (`af0d2a1b86c3e340398face46dbdf3e8c537e452`) |
| `mvn clean verify` | PASSED (BUILD SUCCESS, 105 tests, 0 failures) |

### Next task

STAGE3-001

## `STAGE3-001` — `Bootstrap tmp-capability-engine module and public API package skeleton`

**Date:** 2026-07-22  
**Stage:** Stage 3 — Capability Engine  
**Status:** DONE

### Result

Создан Maven-модуль `tmp-capability-engine`, подключён в root reactor (`<modules>` и `<dependencyManagement>`), зависит только от `tmp-platform-core`, `tmp-document-engine` (публичные API) и `spring-boot-starter`; test-scope дополнительно получил `tmp-infra-db`, `spring-boot-starter-jdbc`, `spring-boot-starter-test`, JUnit, Testcontainers (postgresql) — заранее, для будущих задач STAGE3-015/019, без использования в production-коде на этом шаге. Создан пустой пакет `com.tmp.capability.api` (`package-info.java`), без единого контракта — они появятся начиная с STAGE3-002.

### Files created

- `tmp-capability-engine/pom.xml`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/package-info.java`

### Files modified

- `pom.xml` (root reactor: added module + dependencyManagement entry)

### Tests added or changed

- none (pure structural task, matches STAGE1-001/STAGE2-001 precedent).

### Verification

| Check | Result |
|---|---|
| `mvn -q -DskipTests validate` (full reactor) | PASSED |
| `mvn -q -pl :tmp-capability-engine -am compile` | PASSED |
| `mvn -q -pl :tmp-capability-engine -am verify -DskipTests` (checkstyle + spotbugs gates) | PASSED |

### Architecture review

- Module depends only on `tmp-platform-core`, `tmp-document-engine` public APIs and Spring Boot starter; no business logic present.

### Next task

STAGE3-002

---

## `STAGE3-002` — `CapabilityId and CapabilityVersion value objects with version compatibility rule`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализованы immutable value objects `CapabilityId` (непустая строка, технический идентификатор без бизнес-смысла) и `CapabilityVersion` (`MAJOR.MINOR.PATCH`, без внешней SemVer-библиотеки). `CapabilityVersion` реализует `Comparable<CapabilityVersion>` с детерминированным сравнением по компонентам и метод `isCompatibleWith(CapabilityVersion required)`, реализующий зафиксированное в design decisions правило: совпадающий major обязателен (иначе несовместимо); при равном major актуальная minor/patch должна быть `>=` требуемой (лексикографически по (minor, patch)). Некорректные строки версии (пропущенный компонент, нечисловые/отрицательные значения) отклоняются с `IllegalArgumentException` через строгий regex `(\d+)\.(\d+)\.(\d+)` и `Matcher.matches()` (полное совпадение строки).

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityId.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityVersion.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/CapabilityIdTest.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/CapabilityVersionTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityIdTest`: валидный id, blank/empty/null отклонены, `equals`/`hashCode` по значению.
- `CapabilityVersionTest`: валидный парсинг компонентов; отклонение при пропущенном компоненте, нечисловом значении, отрицательном значении, `null`; совместимость при равном major и minor/patch выше или равно требуемому (включая равенство); несовместимость при другом major и при meньшем minor/patch с тем же major; детерминированность и транзитивность `compareTo`; `equals`/`hashCode` по числовым компонентам (не по исходной строке).

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-platform-core,:tmp-document-engine -am install -DskipTests` (upstream artifacts refreshed in local repo) | PASSED |
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityIdTest,CapabilityVersionTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine test` (full module test suite) | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (checkstyle + spotbugs gates) | PASSED |

### Architecture review

- Только новые публичные типы в `com.tmp.capability.api`; никаких изменений существующих контрактов Platform Core / Document Engine.
- Нет registry/lifecycle/discovery логики — контракт ограничен value objects, как и требовалось.
- Нет зависимости на внешние SemVer-библиотеки.

### Next task

STAGE3-003

---

## `STAGE3-003` — `Dependency descriptor and dependency validation error contract`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализован immutable `DependencyDescriptor` (пара `CapabilityId` целевой зависимости + минимально требуемая `CapabilityVersion`, null-проверки, `equals`/`hashCode` по обоим полям) и unchecked `DependencyValidationException` — типизированный контракт ошибок валидации зависимостей с nested enum `DependencyValidationReason` (`MISSING_DEPENDENCY, SELF_DEPENDENCY, DUPLICATE_DEPENDENCY, INCOMPATIBLE_VERSION, CYCLIC_DEPENDENCY`). Исключение создаётся только через типизированные статические фабрики (`missingDependency`, `selfDependency`, `duplicateDependency`, `incompatibleVersion`, `cyclicDependency`), каждая формирует точное человекочитаемое сообщение и хранит список затронутых `CapabilityId` (`offendingIds()`) для точной диагностики. Ни descriptor, ни exception не содержат логики валидации графа зависимостей — она относится к STAGE3-010.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/DependencyDescriptor.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/DependencyValidationException.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/DependencyDescriptorTest.java`

### Files modified

- none.

### Tests added or changed

- `DependencyDescriptorTest`: валидная конструкция и доступ к полям; `null` id отклонён; `null` версия отклонена; `equals`/`hashCode` по паре (id, minimumVersion), включая различие только по id и только по версии.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=DependencyDescriptorTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED |

### Architecture review

- Только новые публичные типы в `com.tmp.capability.api`; никаких изменений существующих контрактов.
- `DependencyDescriptor` и `DependencyValidationException` — чистые data/error-контракты без зависимости на registry/graph классы, как и требовалось.
- Нет ссылок на конкретные бизнес-capability — только технические идентификаторы.

### Next task

STAGE3-004

---

## `STAGE3-004` — `Command, View, Navigation and Permission descriptor contracts`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализованы четыре независимых, домен-независимых immutable metadata-контракта: `PermissionDescriptor` (id/displayName/description — только описание права, без пользователей/ролей/проверок доступа), `CommandDescriptor` (id/displayName/`requiredPermissionIds` — неизменяемый список через defensive `List.copyOf`), `ViewDescriptor` (id/displayName/`navigationTargetId` — чистая routing-метаданные, без ссылок на FXML/Controller/ViewModel), `NavigationContribution` (id/displayName/viewId/order — метаданные для построения дерева навигации). Все четыре типа валидируют непустой (non-blank) идентификатор и связанные строковые метаданные при конструировании через статическую фабрику `of(...)`, `equals`/`hashCode` определены по собственному идентификатору типа (permissionId/commandId/viewId/navigationId) согласно acceptance criteria. Ни один класс не содержит логики авторизации, не зависит от JavaFX/Spring/persistence.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/PermissionDescriptor.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CommandDescriptor.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/ViewDescriptor.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/NavigationContribution.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/ContributionDescriptorsTest.java`

### Files modified

- `tmp-capability-engine/pom.xml` (added `spotbugs-annotations` provided-scope dependency, matching `tmp-document-engine` precedent, to support `@SuppressFBWarnings` on the intentionally-safe immutable-list getter).

### Tests added or changed

- `ContributionDescriptorsTest` (nested per type): valid construction and field access; blank/null identifier rejected; `CommandDescriptor.requiredPermissionIds()` returns an unmodifiable list (`UnsupportedOperationException` on mutation) and is defensively copied from the constructor argument; `equals`/`hashCode` by identifier for all four types; all four types are `final` pure data classes.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=ContributionDescriptorsTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED (after adding `@SuppressFBWarnings("EI_EXPOSE_REP")` with justification on `CommandDescriptor.requiredPermissionIds()`, since SpotBugs cannot statically infer that a `List.copyOf`-produced list is immutable) |

### Architecture review

- Only new public types in `com.tmp.capability.api`; nothing existing changes.
- No FXML/Controller/ViewModel classes, no permission-checking/authorization logic, no user/role types — matches "Forbidden" scope exactly.
- No dependency on JavaFX, Spring, or persistence in any of the four new types.

### Next task

STAGE3-005

---

## `STAGE3-005` — `Public service, event, settings and document contribution contracts`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализованы четыре независимых, домен-независимых immutable metadata-контракта, необходимых для будущей интеграции с Platform Core/Document Engine: `PublicServiceContribution<T>` (generic-holder публичного сервиса; `serviceType`/`serviceInstance` non-null, instance должен быть assignable к declared type — проверяется в конструкторе через `Class.isInstance`); `EventContribution` (id+description публикуемого типа события, без интерпретации payload); `SettingsContribution` (key/displayName/description/defaultValue — только descriptor, без persistence); `DocumentContribution` (documentTypeId/displayName/description + ссылка на `com.tmp.document.api.DocumentProcessor`, с fail-fast проверкой, что `processor.documentTypeId()` совпадает с заявленным `documentTypeId`). Все четыре типа — чистые данные без побочных эффектов: ни один конструктор не обращается к Platform Core или Document Engine registration API. `equals`/`hashCode` для всех типов определены по идентифицирующему ключу (`serviceType`, `eventTypeId`, `settingKey`, `documentTypeId` соответственно), продолжая паттерн STAGE3-004.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/PublicServiceContribution.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/EventContribution.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/SettingsContribution.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/DocumentContribution.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/IntegrationContributionDescriptorsTest.java`

### Files modified

- none.

### Tests added or changed

- `IntegrationContributionDescriptorsTest` (nested per type): valid construction and field access for all four types; `PublicServiceContribution` rejects a service instance not assignable to the declared type (using a raw-cast test double to bypass compile-time generics), rejects `null` type/instance; `DocumentContribution` rejects a processor whose `documentTypeId()` does not match the declared id (using an inline `DocumentProcessor` test double), rejects blank/`null` document type id and `null` processor; `EventContribution`/`SettingsContribution` reject blank/`null` identifiers; `equals`/`hashCode` by identifying key for all four types.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=IntegrationContributionDescriptorsTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED |

### Architecture review

- Only new public types in `com.tmp.capability.api`; read-only usage of `com.tmp.document.api.DocumentProcessor` (no change to Document Engine).
- No constructor calls into Platform Core or Document Engine registration APIs — pure data contracts, consistent with "Forbidden" scope.
- No persistence of settings values; `SettingsContribution` is a registration-only descriptor.

### Next task

STAGE3-006

---

## `STAGE3-006` — `CapabilityDescriptor aggregate, CapabilityLifecycleState and Capability SPI contract`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализован `CapabilityLifecycleState` enum (фиксированный набор из 8 стадий: `DISCOVERED, VALIDATED, REGISTERED, INITIALIZED, ACTIVE, STOPPED, DEACTIVATED, FAILED`); `Capability` SPI-интерфейс (`descriptor()`, `onInitialize()`, `onActivate()`, `onDeactivate()`, `onStop()`) без единой ссылки на `com.tmp.core`/`com.tmp.document` — движок вызывает hooks, capability не обращается к внутренностям движка; и immutable `CapabilityDescriptor`, агрегирующий все контракты STAGE3-002..005, построенный исключительно через `Builder`. `Builder.build()` валидирует обязательные поля (`id` — `NullPointerException`, `name`/`version`/`description` — non-null/non-blank) и проверяет descriptor-level самосогласованность: отсутствие дублирующихся id в каждой из 8 категорий contributions — permission id, command id, view id, navigation id, event type id, settings key, document type id и dependency target id. Для дублирующейся зависимости переиспользован уже существующий `DependencyValidationException.duplicateDependency(...)` (типизированный контракт из STAGE3-003); для остальных 7 категорий добавлен новый nested `CapabilityDescriptor.DuplicateContributionException` (unchecked, с точным сообщением "Duplicate &lt;category&gt; '&lt;id&gt;' in capability descriptor"). Все 9 коллекций (`dependencies`, `permissions`, `commands`, `views`, `navigationContributions`, `documents`, `publicServices`, `events`, `settings`) — defensive-copied через `List.copyOf` и неизменяемы.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityLifecycleState.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/Capability.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityDescriptor.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/CapabilityDescriptorTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityDescriptorTest`: валидный descriptor со всеми 9 типами contributions заполненными; отсутствующий id отклонён (`NullPointerException`); отдельный тест на дубликат для каждой из 8 категорий (permission/command/view/navigation/event/settings/document/dependency), причём дублирующаяся зависимость проверяется на конкретный `DependencyValidationReason.DUPLICATE_DEPENDENCY`; отдельный тест на неизменяемость (`UnsupportedOperationException` при попытке `add`) для каждой из 9 коллекций; отдельная проверка, что `Capability` — интерфейс без параметров методов из `com.tmp.core`/`com.tmp.document`.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityDescriptorTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED |

### Architecture review

- Только новые публичные типы (`CapabilityDescriptor`, `CapabilityLifecycleState`, `Capability`); ничего существующего не меняется.
- Никакой registry/lifecycle-manager реализации — это чистый data-контракт (registry — STAGE3-007+).
- `Capability` SPI не зависит от `com.tmp.core`/`com.tmp.document` сверх того, что уже используется `DocumentContribution` (единственная ссылка — через тип `CapabilityDescriptor`, который сам ссылается на `DocumentContribution.processor(): com.tmp.document.api.DocumentProcessor`, что укладывается в разрешённые границы).

### Next task

STAGE3-007

---

## `STAGE3-007` — `Capability Registry (read-only catalog, uniqueness, immutable snapshots)`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализован внутренний (не в `.api`) thread-safe `CapabilityRegistry` в новом пакете `com.tmp.capability.registry`, отдельный от `com.tmp.core.api.CapabilityRegistry` (Stage 1). Двухфазный протокол регистрации: `reserve(id)` атомарно claim'ит id (использует `ConcurrentHashMap.newKeySet().add()` под глобальным `ReentrantLock` для устранения гонки между проверкой committed-карты и claim'ом), `release(id)` освобождает резервацию при rollback (retry-after-failure), `commit(CapabilityRegistration)` финализирует регистрацию и снимает резервацию, `updateState(id, newState)` атомарно заменяет snapshot новым (сам registry не проверяет легальность перехода — это STAGE3-008). `findById`/`findAll` читают из `ConcurrentHashMap` без блокировки; `findAll()` возвращает детерминированно отсортированный по id `List.copyOf`-снимок. Резервированный (но не committed) id не виден в `findById`/`findAll` — гарантия отсутствия partial state. `CapabilityRegistration` — immutable record-подобный класс (descriptor + state + owning `Capability`), с package-private `withState(...)` для использования только самим registry.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/registry/CapabilityRegistry.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/registry/CapabilityRegistration.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/registry/CapabilityRegistryTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityRegistryTest`: reserve/commit/findById/findAll happy path (including that a bare reservation is invisible to reads); duplicate id rejected at `reserve`; duplicate id rejected at `commit`; release-after-reserve allows a subsequent reserve to succeed; `updateState` reflected in both `findById` and `findAll`; `updateState` on an unregistered id throws; `findAll()` snapshot is immutable (`UnsupportedOperationException` on mutation); `findAll()` is sorted deterministically by id; a two-thread concurrent test (200 capabilities, `CyclicBarrier`-gated, matching `DefaultPlatformCoreRegistrationTest` precedent) exercising concurrent `reserve`/`commit` against concurrent `findAll`/`findById` produces no `ConcurrentModificationException` and ends with a fully consistent registry.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityRegistryTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED (after switching `CapabilityRegistration` from a `record` to an explicit immutable class with justified `@SuppressFBWarnings` on `EI_EXPOSE_REP`/`EI_EXPOSE_REP2`, since SpotBugs cannot statically infer that `CapabilityDescriptor` is immutable) |

### Architecture review

- `CapabilityRegistry`/`CapabilityRegistration` are intentionally internal (package `com.tmp.capability.registry`, not `.api`); no public contract changes.
- No dependency validation, discovery, or contribution-catalog logic added — pure id/state/snapshot bookkeeping, as required.
- In-memory only, no persistence added.

### Next task

STAGE3-008

---

## `STAGE3-008` — `Capability lifecycle state machine (allowed transitions)`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализован чистый, stateless утилитарный класс `CapabilityStateTransition` в новом пакете `com.tmp.capability.lifecycle`, с единственным методом `isAllowed(CapabilityLifecycleState from, CapabilityLifecycleState to)`, реализующим фиксированную таблицу переходов: `DISCOVERED->VALIDATED`, `VALIDATED->REGISTERED`, `REGISTERED->INITIALIZED`, `INITIALIZED->ACTIVE`, `ACTIVE->STOPPED`, `STOPPED->INITIALIZED` (restart), `STOPPED->DEACTIVATED`, и `FAILED` достижим из `DISCOVERED|VALIDATED|REGISTERED|INITIALIZED|ACTIVE`. `DEACTIVATED` и `FAILED` — терминальные состояния без исходящих переходов (задокументированное ограничение scope, а не упущение). Таблица реализована через `EnumMap<CapabilityLifecycleState, Set<CapabilityLifecycleState>>`, построенную один раз статически и обёрнутую в неизменяемый `Map.copyOf`. Никакой orchestration-логики или I/O — чистая функция.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/lifecycle/CapabilityStateTransition.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/lifecycle/CapabilityStateTransitionTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityStateTransitionTest`: параметризованный тест, перечисляющий все 12 разрешённых переходов (должны вернуть `true`); параметризованный тест с 32 представительными запрещёнными переходами, включая skip-ahead (`DISCOVERED -> ACTIVE`), reverse (`ACTIVE -> DISCOVERED`), self-transitions для каждого состояния, и полный набор terminal-state escapes из `DEACTIVATED`/`FAILED`; отдельный exhaustive-тест, что из `DEACTIVATED`/`FAILED` не разрешён переход ни в одно из 8 состояний; `null` `from`/`to` отклонены (`NullPointerException`).

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityStateTransitionTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED |

### Architecture review

- Внутренний, не публичный контракт (`com.tmp.capability.lifecycle`, не `.api`); ничего публичного не меняется.
- Никакой lifecycle orchestration (initialize/activate/deactivate execution) — это STAGE3-013; никакого I/O.
- `ACTIVE -> DEACTIVATED` напрямую не разрешён — деактивация обязана проходить через `STOPPED`, как и требовалось.

### Next task

STAGE3-009

---

## `STAGE3-009` — `Discovery of Capability beans (Spring composition, deterministic ordering)`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализован framework-agnostic `CapabilityDiscovery` в пакете `com.tmp.capability.discovery`: конструктор принимает `List<Capability>` (Spring/Java composition, без classloading/hot-deployment/plugin marketplace/network discovery), `discover()` возвращает список, отсортированный детерминированно по `CapabilityId`, и отклоняет дублирующиеся id через `IllegalStateException` с именами обоих классов Capability. Повторные вызовы `discover()` дают одинаковый результат. Класс не имеет Spring-аннотаций и не выполняет I/O.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/discovery/CapabilityDiscovery.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/discovery/CapabilityDiscoveryTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityDiscoveryTest`: zero → empty; one → singleton; multiple with two different input orders → same sorted output; duplicate id → `IllegalStateException` naming both classes; repeated `discover()` calls return equal deterministic results.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityDiscoveryTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- No ClassLoader, ServiceLoader, package scanning, jar scanning, or networking.
- Internal component only; consumed later by the facade (STAGE3-014).

### Next task

STAGE3-010

---

## `STAGE3-010` — `Dependency graph validation (missing/self/duplicate/version/cycles) and topological order`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализован stateless `DependencyGraphValidator` в пакете `com.tmp.capability.validation`: `validate(List<Capability>)` проверяет missing/self/duplicate/incompatible-version зависимости (через `DependencyValidationException` с соответствующим `DependencyValidationReason`) и строит детерминированный topological order (dependencies before dependents; ties broken by `CapabilityId` natural order via `TreeSet`). Циклы (direct и indirect) детектируются по остаточному in-degree после Kahn's algorithm. `reverse(List<Capability>)` возвращает точный reverse для shutdown order. Правило «зависимость только от публичного контракта» зафиксировано структурно в Javadoc (`DependencyDescriptor` ссылается только на `CapabilityId`, не на concrete class).

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/validation/DependencyGraphValidator.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/validation/DependencyGraphValidatorTest.java`

### Files modified

- none.

### Tests added or changed

- `DependencyGraphValidatorTest`: linear + diamond valid graphs; missing/self/duplicate/incompatible-version; direct + indirect cycle; deterministic order across repeated runs and input-order independence; `reverse` produces exact reverse of forward order. Duplicate-dependency case uses a package-private reflection bypass of `CapabilityDescriptor.Builder` (documented) because the public builder already rejects duplicates (STAGE3-006) — validator re-checks defensively against the discovered graph.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=DependencyGraphValidatorTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module + checkstyle + spotbugs) | PASSED |

### Architecture review

- No registry mutation, no contribution registration, pure graph algorithm.
- Internal validator only.

### Next task

STAGE3-011

---

## `STAGE3-011` — `Internal contribution catalogs (permission, command, view, navigation, settings, event descriptor)`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализован generic thread-safe `ContributionCatalog<T>` (ConcurrentHashMap + ReentrantLock; `add` отклоняет duplicate id across all owners; `removeAllForOwner` для rollback/deactivation; `activeEntries` immutable snapshot; `ownerOf` для owner tracking) и агрегат `CapabilityContributionCatalogs` из шести каталогов (permissions, commands, views, navigation, settings, events). `registerInternalContributions(CapabilityDescriptor)` регистрирует все contributions владельца и при любой ошибке откатывает все шесть каталогов через `removeAllForOwner` до rethrow — partial state не остаётся. Document/public-service contributions намеренно отсутствуют (внешняя регистрация — STAGE3-012).

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/contribution/ContributionCatalog.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/contribution/CapabilityContributionCatalogs.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/contribution/ContributionCatalogTest.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/contribution/CapabilityContributionCatalogsTest.java`

### Files modified

- none.

### Tests added or changed

- `ContributionCatalogTest`: add/retrieve; duplicate id across owners rejected; owner tracking; removeAllForOwner removes only target owner; concurrent add/read without ConcurrentModificationException.
- `CapabilityContributionCatalogsTest`: aggregate registration across all six catalogs; bulk rollback; conflict mid-registration rolls back all catalogs for the failing owner.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=ContributionCatalogTest,CapabilityContributionCatalogsTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED (after justified `@SuppressFBWarnings("EI_EXPOSE_REP")` on catalog accessors) |

### Architecture review

- No Document Engine / Platform Core calls; no authorization logic.
- Internal only; read-only exposure via facade comes later (STAGE3-014).

### Next task

STAGE3-012

---

## `STAGE3-012` — `Atomic registration orchestrator (Document Engine + Platform Core external contributions with rollback)`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализован `CapabilityRegistrationService` — единственная точка атомарной регистрации Capability с глобальным `ReentrantLock` и фиксированным порядком (design decisions §6): reserve → internal catalogs → Platform Core `capabilityRegistry` → Document Engine `registerProcessor` (с pre-check `registeredTypes`) → Platform Core `ServiceRegistry` → commit как `REGISTERED`. Event contributions — только cataloging (подписки создаёт сама Capability). При ошибке unwind CE-owned state (catalogs + reservation) с сохранением original cause и suppressed compensation failures через `CapabilityRegistrationException`. Known residual limitation (нет компенсации уже успешных ServiceRegistry/Document Engine вызовов) задокументирован в Javadoc.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/registration/CapabilityRegistrationService.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/registration/CapabilityRegistrationException.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/registration/CapabilityRegistrationServiceTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityRegistrationServiceTest`: happy path all contribution types; catalog conflict rollback + retry; document pre-check conflict rollback (Document Engine not mutated); duplicate processor rejection; service failure fixture rolls back CE-owned state while documenting residual external mutation; retry after corrected failure; concurrent same-id (exactly one winner); original exception preserved.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityRegistrationServiceTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- No modifications to `com.tmp.core.api.*` or `com.tmp.document.api.*`; public APIs only.
- No reflection/internal access; original failure cause never swallowed.

### Next task

STAGE3-013

---

## `STAGE3-013` — `Lifecycle manager: initialization order, activation/deactivation, dependents check, reverse shutdown`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализован `CapabilityLifecycleManager`: `initializeAll()` в topological order (hook затем transition; failure isolation — FAILED + transitive dependents FAILED с chained cause; независимые остаются); `activateAll()` только для INITIALIZED, отклоняет повторную активацию если есть ACTIVE; `deactivate(id)` проверяет active dependents (без cascade), ACTIVE→STOPPED→DEACTIVATED, `removeAllForOwner` из catalogs; `stopAll()` в reverse topological order. Все transitions через `CapabilityStateTransition.isAllowed`.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/lifecycle/CapabilityLifecycleManager.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/lifecycle/CapabilityLifecycleManagerTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityLifecycleManagerTest`: full init/activate chain order; invalid deactivate from REGISTERED; repeated activation rejected; stopAll reverse order (3-capability chain); normal deactivation removes catalogs without cascade; deactivation with active dependents rejected; failed init/activate isolate failed + dependents while independent remains ACTIVE; dependency-failed never reaches ACTIVE.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityLifecycleManagerTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- No cascading deactivation; no business data deletion.
- Internal only; facade exposure in STAGE3-014.

### Next task

STAGE3-014

---

## `STAGE3-014` — `CapabilityEngine public facade and status snapshot`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализован публичный API `CapabilityEngine` + `CapabilityEngineStatus` и `DefaultCapabilityEngine`: `discoverAndRegisterAll()` = discover → DependencyGraphValidator → register each → initializeAll; `activateAll`/`deactivate`/`stopAll` делегируют lifecycle manager; workplace queries фильтруют contributions только владельцев в состоянии `ACTIVE`; `status()` считает discovered/registered/active/failed. Интерфейс не экспонирует internal types.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityEngine.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityEngineStatus.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/DefaultCapabilityEngine.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/DefaultCapabilityEngineTest.java`

### Files modified

- none.

### Tests added or changed

- `DefaultCapabilityEngineTest`: e2e discover→register→init→activate с dependency graph (2 capabilities); active-only queries после deactivate; status counts на каждом этапе lifecycle.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=DefaultCapabilityEngineTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- No access-decision logic; only `.api` types in facade signatures.
- New public types only; Stage 1/2 contracts unchanged.

### Next task

STAGE3-015

---

## `STAGE3-015` — `Spring Boot auto-configuration and Platform Core component registration`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализованы `CapabilityEngineAutoConfiguration` (wiring всех internal collaborators + один `CapabilityEngine` bean), `CapabilityEnginePlatformComponent` (адаптер к `PlatformComponent` с `ComponentType.SERVICE`, id `"capability-engine"`; initialize→discoverAndRegisterAll, start→activateAll, stop→stopAll), inner `CapabilityEnginePlatformRegistrar` с `@PostConstruct` (паттерн Document Engine), и `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Внутренний registry bean переименован в `capabilityEngineRegistry`, чтобы не конфликтовать с Platform Core `capabilityRegistry`.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/CapabilityEngineAutoConfiguration.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/CapabilityEnginePlatformComponent.java`
- `tmp-capability-engine/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `tmp-capability-engine/src/test/java/com/tmp/capability/CapabilityEngineAutoConfigurationTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityEngineAutoConfigurationTest`: slice context (PlatformCore + CapabilityEngine auto-config + stub DocumentEngine + test Capability); exactly one CapabilityEngine bean; component registered in PlatformCore after startup; lifecycle delegation verified via ApplicationReadyEvent initialize/activate and explicit stop.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityEngineAutoConfigurationTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- Exactly one CapabilityEngine PlatformComponent registration; no dependency on tmp-ui-shell or tmp-bootstrap-app.
- `ComponentType.SERVICE` documented in Javadoc (not CAPABILITY).

### Next task

STAGE3-017

---

## `STAGE3-016` — `Sample technical Capability (end-to-end fixture, no business logic)`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Реализованы sample technical capabilities (`SampleTechnicalCapability`, `SampleDependentTechnicalCapability`) с полным набором contribution types (permission, command, view, navigation, setting, event, document, public service), trivial `SampleTechnicalDocumentProcessor` и `SampleTechnicalService`, probe `SampleLifecycleProbe` для порядка activation и service resolution. Интеграционный тест поднимает полный Spring-контекст (Database + Platform Core + Document Engine + Capability Engine) на H2 и проверяет discover→register→init→activate, dependency order, ServiceRegistry lookup, document creation и deactivation с active dependents.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalCapability.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleDependentTechnicalCapability.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalDocumentProcessor.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalService.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalServiceImpl.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleLifecycleProbe.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/sample/SampleTechnicalCapabilityIntegrationTest.java`

### Files modified

- `tmp-capability-engine/pom.xml` (test-scoped `h2` dependency for integration test datasource)

### Tests added or changed

- `SampleTechnicalCapabilityIntegrationTest`: full e2e for both sample capabilities; activation order; ServiceRegistry resolution; DocumentEngine.createDocument; deactivation rejected while dependent active, succeeds after dependent stopped first.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=SampleTechnicalCapabilityIntegrationTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- Sample capabilities are technical fixtures only; no business/domain naming.
- Dependent capability resolves public service exclusively via Platform Core ServiceRegistry (ADR-003).
- Sample beans registered via test `@Configuration` only; production bootstrap wiring deferred to STAGE3-017.

### Next task

STAGE3-017

---

## `STAGE3-017` — `Bootstrap integration and minimal technical capability status UI`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Подключён `tmp-capability-engine` к `tmp-bootstrap-app`: sample technical capabilities зарегистрированы как Spring beans в `BootstrapConfiguration`, `DesktopBootstrap` резолвит `CapabilityEngine` и добавляет `formatCapabilityStatus()` в нижнюю status-панель (discovered/active counts + id/state sample capabilities). UI shell не изменялся — plain-string bridge сохранён.

### Files created

- `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/CapabilityEngineBeanLookupTest.java`

### Files modified

- `tmp-bootstrap-app/pom.xml` (dependency `tmp-capability-engine`)
- `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/BootstrapConfiguration.java` (sample capability beans)
- `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/DesktopBootstrap.java` (`formatCapabilityStatus`, launch wiring)
- `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/DesktopBootstrapLookupSmokeTest.java` (capability lookup + formatting)
- `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/PlatformCoreIntegrationIT.java` (updated counts for capability-engine + sample capabilities)

### Tests added or changed

- `CapabilityEngineBeanLookupTest`: single bean lookup; `formatCapabilityStatus` includes counts and sample capability ACTIVE states.
- `PlatformCoreIntegrationIT`: capability/component/service counts adjusted for auto-registered sample capabilities and capability-engine component.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-bootstrap-app test -Dtest=CapabilityEngineBeanLookupTest,SpringContextSmokeTest,DocumentEngineBeanLookupTest` | PASSED |
| `mvn -q -pl :tmp-bootstrap-app verify` | PASSED |

### Architecture review

- `tmp-ui-shell` unchanged; no Capability Engine types in UI layer.
- Bootstrap owns all engine API calls and string formatting (same pattern as Platform Core / Document Engine).

### Next task

STAGE3-019

---

## `STAGE3-018` — `Stage 3 architecture tests`

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Добавлен `Stage3CapabilityEngineArchitectureTest` с 8 ArchUnit-правилами границ Capability Engine (business-module isolation, public-API-only для core/document/capability, reverse dependencies, sample package). Для прохождения `externalModulesUseOnlyCapabilityPublicApi`: sample beans перенесены из bootstrap в `SampleTechnicalCapabilitiesAutoConfiguration` (conditional on `tmp.capability.sample.diagnostic`); bootstrap использует только `com.tmp.capability.api`; `CapabilityEngineAutoConfiguration` переведён на name-based `@AutoConfigureAfter`.

### Files created

- `tmp-architecture-tests/src/test/java/com/tmp/architecture/Stage3CapabilityEngineArchitectureTest.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalCapabilitiesAutoConfiguration.java`

### Files modified

- `tmp-architecture-tests/pom.xml` (dependency `tmp-capability-engine`)
- `tmp-capability-engine/src/main/java/com/tmp/capability/CapabilityEngineAutoConfiguration.java` (name-based AutoConfigureAfter)
- `tmp-capability-engine/src/main/resources/META-INF/spring/...AutoConfiguration.imports`
- `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/BootstrapConfiguration.java` (removed sample beans)
- `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/DesktopBootstrap.java` (dynamic registeredCapabilities listing)
- `tmp-bootstrap-app/src/main/resources/application.yml`, `application-test.yml` (`tmp.capability.sample.diagnostic: true`)

### Tests added or changed

- `Stage3CapabilityEngineArchitectureTest`: 8 module-boundary rules.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-architecture-tests test -Dtest=Stage3CapabilityEngineArchitectureTest,Stage0ArchitectureBaselineTest,Stage1PlatformCoreArchitectureTest,Stage2DocumentEngineArchitectureTest` | PASSED |
| `mvn -q -pl :tmp-architecture-tests verify` | PASSED |

### Architecture review

- No Stage 0/1/2 rules modified.
- Bootstrap no longer depends on `com.tmp.capability.sample..`; only public API.

### Next task

STAGE3-021

---

## `STAGE3-019` — PostgreSQL Testcontainers integration test for document contribution

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Добавлен `CapabilityEngineDocumentPostgresIntegrationIT` с тремя сценариями: регистрация document type через полный путь Capability Engine на PostgreSQL, document lifecycle (create/post/query), rollback при duplicate document type. Исправлена версия Testcontainers: Spring Boot BOM (1.19.8) перекрывал parent `testcontainers.version` 1.21.4 — добавлены явные managed dependencies в parent pom для совместимости с Docker Desktop API 1.55.

### Files created

- `tmp-capability-engine/src/test/java/com/tmp/capability/CapabilityEngineDocumentPostgresIntegrationIT.java`
- `tmp-capability-engine/src/test/resources/testcontainers.properties`

### Files modified

- `pom.xml` (parent: explicit Testcontainers 1.21.4 managed deps override Spring Boot BOM)
- `tmp-capability-engine/pom.xml` (surefire DOCKER_HOST + docker.host for Windows Docker Desktop)

### Tests added or changed

- `CapabilityEngineDocumentPostgresIntegrationIT`: 3 ordered PostgreSQL-backed scenarios.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityEngineDocumentPostgresIntegrationIT` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- Reuses `postgres:16-alpine` and Flyway migrations from existing document-engine IT pattern.
- No production API changes.

### Next task

STAGE3-020

---

## `STAGE3-020` — Concurrency tests

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Добавлен `CapabilityLifecycleConcurrencyTest` с тремя детерминированными concurrency-сценариями: 8-поточная регистрация одного capability id (ровно один победитель), 200-итерационная гонка activateAll vs deactivate без утечки partial state, reader/writer snapshot safety для `registeredCapabilities()` и `activeCommands()`.

### Files created

- `tmp-capability-engine/src/test/java/com/tmp/capability/lifecycle/CapabilityLifecycleConcurrencyTest.java`

### Tests added or changed

- `CapabilityLifecycleConcurrencyTest`: concurrent registration, activation/deactivation race, snapshot consistency.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityLifecycleConcurrencyTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- No production code changes; latch/barrier synchronization only (no sleep-based sync).

### Next task

— (stop before Stage 4)

---

## `STAGE3-021` — Final Stage 3 verification gate

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Выполнена финальная комплексная верификация Stage 3: полный reactor `mvn clean verify`, package profile `mvn clean verify -Ppackage`, ручной запуск `dist/jpackage/TMP/TMP.exe` с PostgreSQL (Docker `postgres:16-alpine`, `TMP_DB_*` env vars). Spring context, Flyway migrations и DesktopBootstrap стартуют без ошибок.

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` (full reactor) | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Manual `dist/jpackage/TMP/TMP.exe` + PostgreSQL | PASSED |

### Architecture review

- Stage 3 exit criteria satisfied; no Stage 4 work started.

### Next task

— (stop before Stage 4)

---

## `STAGE3-022` — Stage 3 acceptance rework (BLK-014)

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

Устранены блокирующие дефекты acceptance review: reversible public API Stage 1–2, атомарная регистрация с compensation handles, корректная деактивация и lifecycle failure handling, отслеживание event subscriptions.

### Verification

| Check | Result |
|---|---|
| `mvn -q test -pl :tmp-capability-engine` | PASSED |
| SpotBugs lock-path fix | PASSED |

### Next task

STAGE3-023

---

## `STAGE3-023` — Re-verification gate after acceptance rework

**Date:** 2026-07-22
**Stage:** Stage 3 — Capability Engine
**Status:** DONE

### Result

`mvn clean verify`, `mvn clean verify -Ppackage`, manual `TMP.exe` с PostgreSQL — все PASSED.

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Manual `TMP.exe` | PASSED |

### Next task

— (stop before Stage 4)



---

## `STAGE3-024` - Lifecycle contribution cleanup (BLK-015)

**Date:** 2026-07-23
**Stage:** Stage 3 - Capability Engine
**Status:** DONE

### Result

Unified `cleanupContributions` / `cleanupFailedCapability` for initialize/activation/stop/deactivation failures. `DEACTIVATED` only after successful full cleanup. `unsubscribeAll` aggregates failures. Original lifecycle exceptions are preserved; cleanup failures are suppressed.

### Verification

| Check | Result |
|---|---|
| Lifecycle cleanup acceptance tests | PASSED |
| PostgreSQL IT (init failure cleanup) | PASSED |

### Next task

STAGE3-025

---

## `STAGE3-025` - Re-verification gate after BLK-015

**Date:** 2026-07-23
**Stage:** Stage 3 - Capability Engine
**Status:** DONE

### Result

`mvn clean verify`, `mvn clean verify -Ppackage`, manual `TMP.exe` with PostgreSQL - all PASSED.

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Manual `TMP.exe` | PASSED |

### Next task

- (stop before Stage 4)

---

## `STAGE4-000` - Stage 4 Start Gate and decomposition (planning only)

**Date:** 2026-07-23
**Stage:** Stage 4 - Security
**Status:** DONE (planning); implementation of `STAGE4-001` not started, per explicit user instruction to stop after planning

### Result

Start Gate checks performed by reading only file contents (no Git commands used, per absolute Git prohibition): confirmed Stage 0-3 DONE 100%, last completed task `STAGE3-025`, all Stage 0-3 blockers RESOLVED, Stage 4 was `PLANNED 0%`, Java 21 (`.tools/jdk-21.0.11+10`) and portable Maven (`.tools/apache-maven-3.9.9`) available, Docker available (`DOCKER_HOST=npipe:////./pipe/docker_engine`). Ran the mandatory baseline `mvn clean verify` for the full reactor (log: `stage4-000-baseline-verify.log`, gitignored) — BUILD SUCCESS, including PostgreSQL Testcontainers integration tests (`FlywayPostgresIntegrationIT`, `DocumentEnginePostgresIntegrationIT`, `CapabilityEngineDocumentPostgresIntegrationIT`). No regression found; Stage 4 was safe to start.

Read the mandatory source set for Stage 4 planning: `Security-Specification.md` (full), relevant `Database-Specification.md` sections (schema-per-module, identifiers, technical fields, optimistic locking, transactions, Flyway, naming, module boundaries, JPA/persistence, audit, appendices), relevant `TMP-Architecture-Decisions.md` ADRs (ADR-001..003, ADR-019..022 — none are Security-specific; no Security/audit/transaction-specific ADR exists), relevant `UI-UX-Specification.md` sections (main window, navigation, screens, FXML, Controller, ViewModel, messages, mandatory technical screens), `Capability-Engine-Specification.md`'s public API in full (`com.tmp.capability.api.*` — `Capability`, `CapabilityEngine`, `CapabilityDescriptor`, `PermissionDescriptor`, `CommandDescriptor`, `NavigationContribution`, `ViewDescriptor`, `PublicServiceContribution`, `DocumentContribution`, `EventContribution`, `SettingsContribution`, `CapabilityId`, `CapabilityVersion`, `CapabilityLifecycleState`, `CapabilityRuntimeAccess`), `Platform-Core` public API in full (`com.tmp.core.api.*` — `PlatformCore`, `ServiceRegistry`, `PlatformComponent`, `PlatformComponentMetadata`, `LifecycleManager`), plus the current (pre-Stage-4) state of `tmp-ui-shell` (`JavaFxShellApplication`/`JavaFxShellLauncher`/`EmptyMainShell`), `tmp-bootstrap-app` (`DesktopBootstrap`/`BootstrapConfiguration`/`TmpBootstrapApplication`), `tmp-infra-db` (Flyway/datasource wiring), and the existing Flyway migration numbering across `tmp-infra-db`/`tmp-document-engine` (highest existing version `V3`; `tmp-capability-engine` has no persistence) to determine the next free migration version (`V4`) for `tmp-security`. Did not load full internal implementations of Platform Core, Document Engine, or Capability Engine beyond what was needed to confirm lifecycle-ordering and discovery mechanics (`DefaultLifecycleManager.startAll()` ordering, `CapabilityEngineAutoConfiguration`'s `List<Capability>` discovery wiring) cited in the Design decisions.

Produced the full Stage 4 decomposition directly in `WORK-QUEUE.md`: a "Design decisions fixed for this Stage" preamble (14 points covering module/package layout, persistence technology choice matching Stage 2 precedent, Flyway version numbering, `PermissionId` format resolution, Capability Engine integration pattern, startup ordering proof, UI-screen module placement decision and navigation-permission-gating convention, `JavaFxShellLauncher` static hand-off extension, bootstrap config convention, BCrypt technology choice, case-insensitive login uniqueness mechanism, audit-ownership justification, and explicit confirmation that all Stage 4 §4.18 exclusions remain absent) followed by 40 fully-specified tasks (`STAGE4-001`..`STAGE4-040`) covering: Domain value objects and aggregates (`STAGE4-002`..`009`); Flyway migration and JDBC persistence (`STAGE4-010`..`014`); BCrypt hashing (`STAGE4-015`); Capability integration and permission synchronization (`STAGE4-016`..`017`); bootstrap administrator (`STAGE4-018`..`019`); session/authentication/authorization (`STAGE4-020`..`022`); user/role/permission/audit Application Services (`STAGE4-023`..`027`); the public `com.tmp.security.api` facade (`STAGE4-028`); Spring auto-configuration and startup wiring (`STAGE4-029`); an end-to-end PostgreSQL IT (`STAGE4-030`); `tmp-ui-shell` Navigation Service foundation and all five mandatory screens plus bootstrap integration (`STAGE4-031`..`038`); Stage 4 architecture tests (`STAGE4-039`); and the Final Stage 4 verification gate (`STAGE4-040`). No blocker was raised — every design ambiguity encountered (persistence technology, `PermissionId` format vs. the spec's informal examples, UI-screen module placement, navigation-permission-gating mechanism, and audit ownership vs. the Database Specification's non-binding recommendation) was resolvable from existing approved documents/precedent and is recorded as a documented design decision rather than a blocker, per governance §5 ("Обычные технические решения, однозначно следующие из документации, принимай самостоятельно").

Updated `STATUS.md` (Stage 4 → IN_PROGRESS 0%, Current Task → `STAGE4-001` READY) per the user's explicit final instruction: stopped immediately after planning and filling in the work plan, before starting `STAGE4-001` implementation.

### Verification

| Check | Result |
|---|---|
| Start Gate file-content checks (STATUS/WORK-QUEUE/BLOCKERS/STAGE-4-SECURITY.md/root `pom.xml`/existing modules/Stage 0-3 sources) | PASSED |
| `mvn clean verify` (full reactor, Stage 4 baseline) | PASSED (BUILD SUCCESS, incl. PostgreSQL Testcontainers ITs) |
| Stage 4 decomposition completeness against governance required task fields | PASSED (all 40 tasks include Goal/Required documents/Code context/Allowed scope/Forbidden/Implementation requirements/Public contracts/Acceptance criteria/Required tests/Verification commands/Documentation updates/Expected result) |

### Next task

STAGE4-001 (not started — awaiting next user instruction to resume autonomous execution)

---

## `STAGE4-001`..`STAGE4-018` - Domain, persistence, Capability, bootstrap (autonomous batch)

**Date:** 2026-07-23
**Stage:** Stage 4 - Security
**Status:** DONE through STAGE4-018 (implementation); STAGE4-019+ pending

### Result

Created Maven module `tmp-security` (reactor + dependencyManagement). Implemented public identity VOs; domain aggregates/ports (User, Role, PermissionDefinition, assignments, overrides, EffectivePermissionCalculator, SecurityAuditEvent); Flyway `V4__security_schema.sql`; JDBC adapters; BCrypt hasher; Security Administration Capability (12 permissions); PermissionSynchronizationApplicationService; BootstrapAdministratorApplicationService + `TMP_SECURITY_*` properties. JDBC repository tests use PostgreSQL Testcontainers (H2 cannot apply `lower(login)` unique index).

### Next task

STAGE4-019 / session / authentication / authorization / UI / final gate

---

## `STAGE4-024`..`STAGE4-032` - Password/roles/API/wiring/E2E/UI login (autonomous)

**Date:** 2026-07-23
**Stage:** Stage 4 - Security
**Status:** DONE through STAGE4-032

### Result

Completed password/role/permission/audit application services; public `com.tmp.security.api` facades + credential-leak reflection test; `SecurityAutoConfiguration` / `SecurityPlatformComponent` (non-final `@Transactional` services for CGLIB; `Clock` `@ConditionalOnMissingBean`); `SecurityEndToEndPostgresIntegrationIT` (bootstrap>permissions, mutation+audit rollback via controllable audit repo, inactive-capability denial); `tmp-ui-shell` Navigation Service + Login Screen/FXML/ViewModel; `UiShellEntryPoint` hand-off; bootstrap tests moved from H2 to PostgreSQL Testcontainers (V4 `lower(login)` index).

### Next task

STAGE4-033 (Main Window permission-filtered navigation)

---

## `STAGE4-033`..`STAGE4-039` - Main Window, admin screens, bootstrap finalization, architecture (autonomous)

**Date:** 2026-07-23
**Stage:** Stage 4 - Security
**Status:** DONE through STAGE4-039

### Result

Completed Main Window (permission-filtered nav), Access Denied + `SecuredOperationDemo` bypass-prevention proof, User/Role/Audit admin screens (ViewModels + FX controller tests), login-gated bootstrap (`UiShellEntryPoint` only; logout/stop clear session). Moved UI Spring wiring to `com.tmp.bootstrap.UiShellAutoConfiguration` and introduced `ShellNavigationCatalogue` so `com.tmp.ui..` stays free of Spring/Capability (Stage 0/3 rules). Promoted `SecurityPermissions` and `RoleInUseException` to `com.tmp.security.api`. Added `Stage4SecurityArchitectureTest` (ArchUnit + pom scan).

### Next task

STAGE4-040 (final Stage 4 gate)

