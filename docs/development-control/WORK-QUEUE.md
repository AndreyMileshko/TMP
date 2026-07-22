# TMP Work Queue

## Правила

- Очередь выполняется сверху вниз.
- Одновременно `IN_PROGRESS` может быть только одна задача.
- Новые задачи текущего Stage Cursor добавляет на основании Stage Manifest и утверждённых спецификаций.
- Задача будущего Stage не может получить READY до закрытия предыдущего Stage.
- Каждая задача должна использовать шаблон из `templates/TASK-TEMPLATE.md`.

---

# Control Initialization

## CONTROL-001 — Validate development sources

**Status:** DONE  
**Stage:** Control  
**Goal:** Проверить наличие и непротиворечивость обязательных документов проекта перед генерацией полной очереди Stage 0.  
**Required documents:** Constitution, ADR, Architecture Overview, Database Specification, Development Guide, Code Quality Standards, Master Implementation Plan, Cursor AI Guide.  
**Allowed code scope:** none.  
**Acceptance criteria:** сформирован реестр найденных документов; отсутствующие или конфликтующие документы зафиксированы как blockers; `CONTEXT-MAP.md` актуализирован.  
**Verification:** ручная сверка путей и статусов документов.  
**Next on success:** CONTROL-002.

## CONTROL-002 — Build Stage 0 task queue

**Status:** DONE  
**Stage:** Control  
**Goal:** Декомпозировать Stage 0 в готовые автономные задачи на основании архитектуры и Master Implementation Plan.  
**Required documents:** Stage 0 Manifest и документы, подтверждённые CONTROL-001.  
**Allowed code scope:** none.  
**Acceptance criteria:** все задачи Stage 0 имеют Scope, контекст, критерии, проверки и зависимости; первая задача Stage 0 получила READY.  
**Verification:** проверка соответствия task-size rules и Stage 0 exit criteria.  
**Next on success:** первая READY-задача Stage 0.

---

# Stage 0 — Development Foundation

## STAGE0-001 — Bootstrap repository reactor and parent pom

**Status:** DONE  
**Stage:** 0  
**Depends on:** CONTROL-002  
**Module:** root reactor

### Goal

Создать минимальный Maven reactor с родительским pom и корректной структурой модулей без бизнес-логики.

### Required documents

- `Master-Implementation-Plan.md` (stage order and start rules);
- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (planning domains 1-3);
- `Development-Guide.md` (task scope limits);
- `Code Quality Standards.md` (project structure and naming).

### Required code context

- корневые `pom.xml` и настройки сборки в репозитории.

### Allowed code scope

- `pom.xml`;
- `*/pom.xml` в новых технических модулях Stage 0;
- файлы Maven wrapper при необходимости синхронизации.

### Forbidden

- бизнес-модули и предметные пакеты;
- implementation Java-классов вне минимального bootstrap.

### Implementation requirements

- определить parent pom с едиными properties;
- объявить reactor modules для Stage 0 foundation;
- зафиксировать Java/Maven baseline.

### Acceptance criteria

- [ ] `mvn -q -DskipTests validate` проходит на чистом проекте;
- [ ] реактор собирает все пустые Stage 0 модули;
- [ ] нет доменной функциональности.

### Required tests

- smoke validate сборки reactor.

### Verification commands

```bash
mvn -q -DskipTests validate
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-002 — Configure dependency and plugin management baseline

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-001  
**Module:** root reactor

### Goal

Централизовать версии зависимостей и плагинов для Stage 0.

### Required documents

- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (domain 3);
- `Code Quality Standards.md` (dependency hygiene);
- `Development-Guide.md` (DoR/DoD checks).

### Required code context

- root `pom.xml`;
- module `pom.xml`, созданные в STAGE0-001.

### Allowed code scope

- root `pom.xml`;
- `.mvn/**` при необходимости.

### Forbidden

- добавление неутвержденных runtime-зависимостей.

### Implementation requirements

- вынести версии в `dependencyManagement` и `pluginManagement`;
- подключить обязательные build plugins для quality gates.

### Acceptance criteria

- [ ] все module poms используют управление версиями из parent;
- [ ] `mvn -q -DskipTests help:effective-pom` проходит для ключевого модуля.

### Required tests

- effective-pom smoke check.

### Verification commands

```bash
mvn -q -DskipTests help:effective-pom
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-003 — Wire formatting and static analysis gates

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-002  
**Module:** build quality

### Goal

Подключить форматирование и статический анализ в `mvn verify`.

### Required documents

- `Code Quality Standards.md` (format, warnings, quality);
- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (domain 4).

### Required code context

- build plugin config в root `pom.xml`.

### Allowed code scope

- root `pom.xml`;
- конфиги quality tools в корне проекта.

### Forbidden

- отключение проверок ради прохождения сборки.

### Implementation requirements

- добавить formatter/checkstyle/spotbugs (утвержденный набор);
- привязать проверки к lifecycle verify.

### Acceptance criteria

- [ ] `mvn -q verify -DskipTests` запускает quality gates;
- [ ] нарушения дают fail.

### Required tests

- verify gate execution.

### Verification commands

```bash
mvn -q verify -DskipTests
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-004 — Establish test baseline and test module conventions

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-002  
**Module:** test baseline

### Goal

Подготовить единый baseline для unit/integration тестов без бизнес-логики.

### Required documents

- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (domain 5);
- `Database-Specification.md` (test DB constraints);
- `Code Quality Standards.md` (test rules).

### Required code context

- root и module pom test dependencies.

### Allowed code scope

- root `pom.xml`;
- test source roots в foundation модулях.

### Forbidden

- создание доменных тестов с бизнес-сценариями.

### Implementation requirements

- стандартизировать JUnit 5 baseline;
- настроить surefire/failsafe разделение.

### Acceptance criteria

- [ ] `mvn -q test` успешно выполняет базовые test suites;
- [ ] есть пример smoke test для bootstrap.

### Required tests

- базовый unit smoke test.

### Verification commands

```bash
mvn -q test
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-005 — Create Spring composition root skeleton

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-001  
**Module:** app-bootstrap

### Goal

Собрать каркас composition root для Spring без доменной логики.

### Required documents

- `TMP-004-Architecture-Overview.md` (Platform Core boundaries);
- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (domain 6).

### Required code context

- bootstrap module structure и entry-point package.

### Allowed code scope

- bootstrap module Java classes;
- `application*.yml`.

### Forbidden

- бизнес-сервисы и use case logic.

### Implementation requirements

- добавить Spring bootstrapping configuration;
- определить минимальные инфраструктурные beans.

### Acceptance criteria

- [ ] Spring context стартует в smoke test;
- [ ] отсутствуют бизнес-компоненты.

### Required tests

- Spring context load test.

### Verification commands

```bash
mvn -q -pl :app-bootstrap test
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-006 — Implement JavaFX empty shell bootstrap

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-005  
**Module:** ui-shell

### Goal

Запустить пустое JavaFX окно с корректным lifecycle вместе со Spring.

### Required documents

- `TMP-004-Architecture-Overview.md` (Desktop Application layer);
- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (domain 7).

### Required code context

- bootstrap entry point;
- JavaFX launcher classes.

### Allowed code scope

- `ui-shell` module;
- startup classes в app module.

### Forbidden

- любые бизнес-экраны.

### Implementation requirements

- интегрировать JavaFX startup;
- отрисовать пустой главный shell.

### Acceptance criteria

- [ ] приложение открывает пустое окно;
- [ ] lifecycle shutdown корректный.

### Required tests

- UI bootstrap smoke test/manual run.

### Verification commands

```bash
mvn -q -pl :ui-shell test
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-007 — Configure PostgreSQL connectivity profiles

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-005  
**Module:** infra-db

### Goal

Подготовить профили и базовую конфигурацию подключения PostgreSQL.

### Required documents

- `Database-Specification.md` (storage architecture and constraints);
- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (domain 8).

### Required code context

- `application*.yml`;
- datasource wiring.

### Allowed code scope

- infra-db module;
- конфигурационные файлы.

### Forbidden

- создание бизнес-таблиц и доменных сущностей.

### Implementation requirements

- добавить dev/test профили БД;
- определить datasource properties.

### Acceptance criteria

- [ ] приложение поднимает datasource в тестовом контексте;
- [ ] profile override работает.

### Required tests

- datasource configuration test.

### Verification commands

```bash
mvn -q -pl :infra-db test
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-008 — Add Flyway baseline migration flow

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-007  
**Module:** infra-db

### Goal

Настроить baseline миграцию Flyway и её запуск на старте.

### Required documents

- `Database-Specification.md` (Flyway rules);
- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (domain 9).

### Required code context

- datasource and migration config;
- db migration folders.

### Allowed code scope

- `infra-db/src/main/resources/db/migration/**`;
- infra-db configuration classes.

### Forbidden

- доменная схема и таблицы бизнес-модулей.

### Implementation requirements

- создать baseline migration;
- включить Flyway в bootstrap.

### Acceptance criteria

- [ ] baseline миграция применяется в integration test;
- [ ] schema history создается корректно.

### Required tests

- Flyway integration smoke test.

### Verification commands

```bash
mvn -q -pl :infra-db verify
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-009 — Integrate Testcontainers for PostgreSQL tests

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-008  
**Module:** integration-test infra

### Goal

Подключить Testcontainers для reproducible DB интеграционных тестов.

### Required documents

- `Database-Specification.md` (test DB approach);
- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (domain 10).

### Required code context

- integration test setup;
- infra-db test classes.

### Allowed code scope

- `src/test/**` в infra/bootstrap модулях;
- test dependencies in poms.

### Forbidden

- внешние ручные зависимости от локальной БД в CI test path.

### Implementation requirements

- создать общий testcontainer bootstrap;
- перевести integration tests на containerized PostgreSQL.

### Acceptance criteria

- [ ] integration tests стартуют PostgreSQL контейнер автоматически;
- [ ] тесты не требуют локальной установленной БД.

### Required tests

- integration container smoke test.

### Verification commands

```bash
mvn -q -pl :infra-db failsafe:integration-test failsafe:verify
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-010 — Create ArchUnit baseline architecture tests

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-001  
**Module:** architecture-tests

### Goal

Ввести baseline ArchUnit-правила на слои и границы модулей.

### Required documents

- `TMP-Constitution.md` (module ownership principles);
- `TMP-Architecture-Decisions.md` (core without business logic);
- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (domain 11).

### Required code context

- package structure bootstrap modules;
- existing test conventions.

### Allowed code scope

- dedicated architecture test module or `src/test/**`;
- parent pom test config.

### Forbidden

- проверка бизнес-правил через ArchUnit на Stage 0.

### Implementation requirements

- добавить базовые ArchUnit tests;
- включить их в verify.

### Acceptance criteria

- [ ] архитектурные тесты выполняются в `mvn verify`;
- [ ] нарушение границ даёт fail.

### Required tests

- ArchUnit baseline tests.

### Verification commands

```bash
mvn -q verify -DskipITs
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-011 — Configure logging, runtime profiles, and packaging

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-006  
**Module:** runtime-packaging

### Goal

Настроить логирование, профили запуска и jlink/jpackage pipeline.

### Required documents

- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (domains 12-13);
- `Development-Guide.md` (verification discipline).

### Required code context

- runtime configs;
- packaging plugin config.

### Allowed code scope

- root/build poms;
- app module runtime resources;
- packaging scripts.

### Forbidden

- platform-specific hacks без profile isolation.

### Implementation requirements

- определить profiles (dev/test/package);
- добавить jlink/jpackage configuration.

### Acceptance criteria

- [x] package artifact создается для целевой ОС;
- [x] логирование работает в startup.

### Required tests

- packaging smoke run.

### Verification commands

```bash
mvn -q -Ppackage verify
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

## STAGE0-012 — Run complete Stage 0 verification gate

**Status:** DONE  
**Stage:** 0  
**Depends on:** STAGE0-003, STAGE0-004, STAGE0-006, STAGE0-008, STAGE0-009, STAGE0-010, STAGE0-011  
**Module:** cross-stage

### Goal

Выполнить финальную комплексную верификацию Stage 0 против exit criteria.

### Required documents

- `STAGE-0-DEVELOPMENT-FOUNDATION.md` (exit criteria);
- `Master-Implementation-Plan.md` (stage completion rules).

### Required code context

- итоговый reactor build;
- verification scripts/logs.

### Allowed code scope

- CI/build scripts;
- development-control documentation.

### Forbidden

- новые feature-изменения вне исправления verification defects.

### Implementation requirements

- запустить полный verify pipeline;
- зафиксировать результаты и готовность Stage 0.

### Acceptance criteria

- [x] все exit criteria Stage 0 подтверждены;
- [x] полный `mvn verify` успешен;
- [x] статус Stage 0 готов к закрытию.

### Required tests

- full stage verification suite.

### Verification commands

```bash
mvn -q verify
```

### Documentation updates

- WORK-QUEUE;
- STATUS;
- IMPLEMENTATION-LOG;
- VERIFICATION-LOG.

---

# Stage 1 — Platform Core

## STAGE1-001 — Bootstrap tmp-platform-core module and Core API boundaries

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE0-012  
**Module:** tmp-platform-core

### Goal

Создать Maven-модуль `tmp-platform-core` с пакетом стабильного публичного API `com.tmp.core.api` без бизнес-логики.

### Required documents

- `Platform-Core-Specification.md`; `TMP-004-Architecture-Overview.md`; `STAGE-1-PLATFORM-CORE.md`; `Master-Implementation-Plan.md`.

### Required code context

- root `pom.xml` reactor; Stage 0 module conventions.

### Allowed code scope

- `tmp-platform-core/pom.xml`; root `pom.xml`; `com.tmp.core.api` skeleton.

### Forbidden

- бизнес-модули; Document Engine; Capability Engine; Security.

### Implementation requirements

- модуль в reactor; API отделён от implementation; только Spring Boot starter.

### Acceptance criteria

- [x] `mvn -q -DskipTests validate` проходит; [x] `com.tmp.core.api` без бизнес-логики.

### Required tests

- module validate smoke.

### Verification commands

```bash
mvn -q -DskipTests validate
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-002 — Define PlatformComponent contract and module metadata

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-001  
**Module:** tmp-platform-core

### Goal

Определить контракт `PlatformComponent`, метаданные и `CapabilityDescriptor`.

### Required documents

- `Platform-Core-Specification.md`; `TMP-003-Glossary.md`; `STAGE-1-PLATFORM-CORE.md`.

### Required code context

- `com.tmp.core.api` из STAGE1-001.

### Allowed code scope

- `com.tmp.core.api.component.*`; `com.tmp.core.api.capability.*`.

### Forbidden

- загрузка Capability internals; JPA/SQL в Core API.

### Implementation requirements

- lifecycle hooks initialize/start/stop; immutable metadata types.

### Acceptance criteria

- [x] контракты компилируются; [x] нет предметных типов.

### Required tests

- registry/metadata unit tests.

### Verification commands

```bash
mvn -q -pl :tmp-platform-core test
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-003 — Implement Platform Registry

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-002  
**Module:** tmp-platform-core

### Goal

Реализовать регистрацию и поиск platform components.

### Required documents

- `Platform-Core-Specification.md`; `ADR-001`.

### Required code context

- `PlatformRegistry` API; `PlatformComponent`.

### Allowed code scope

- `DefaultPlatformRegistry`; `PlatformRegistry` interface.

### Forbidden

- прямой доступ к Capability internals.

### Implementation requirements

- thread-safe register/find/list; reject duplicates.

### Acceptance criteria

- [x] register/find/list работают; [x] duplicate → fail.

### Required tests

- `DefaultPlatformRegistryTest`.

### Verification commands

```bash
mvn -q -pl :tmp-platform-core test -Dtest=DefaultPlatformRegistryTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-004 — Implement Service Registry

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-002  
**Module:** tmp-platform-core

### Goal

Реализовать регистрацию и lookup инфраструктурных сервисов.

### Required documents

- `Platform-Core-Specification.md`; `Master-Implementation-Plan.md`.

### Required code context

- `ServiceRegistry` API.

### Allowed code scope

- `DefaultServiceRegistry`; `ServiceRegistry` interface.

### Forbidden

- доменные use-case сервисы.

### Implementation requirements

- lookup/lookupAll by type; track owner metadata.

### Acceptance criteria

- [x] register + lookup работает.

### Required tests

- `DefaultServiceRegistryTest`.

### Verification commands

```bash
mvn -q -pl :tmp-platform-core test -Dtest=DefaultServiceRegistryTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-005 — Implement Capability Registry

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-002  
**Module:** tmp-platform-core

### Goal

Регистрация metadata Capability без Capability Engine.

### Required documents

- `Platform-Core-Specification.md`; `Master-Implementation-Plan.md`.

### Required code context

- `CapabilityRegistry`; `CapabilityDescriptor`.

### Allowed code scope

- `DefaultCapabilityRegistry`.

### Forbidden

- Capability Loader/Discovery (Stage 3).

### Implementation requirements

- metadata-only register/find; reject duplicates.

### Acceptance criteria

- [x] capability metadata регистрируется и читается.

### Required tests

- `DefaultCapabilityRegistryTest`.

### Verification commands

```bash
mvn -q -pl :tmp-platform-core test -Dtest=DefaultCapabilityRegistryTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-006 — Define Event Bus contracts

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-001  
**Module:** tmp-platform-core

### Goal

Контракты Platform Events и Domain Events.

### Required documents

- `Database-Specification.md` §9; `Code Quality Standards.md`.

### Required code context

- `com.tmp.core.api.event.*`.

### Allowed code scope

- event marker interfaces; `EventBus` API.

### Forbidden

- Kafka/RabbitMQ/ActiveMQ.

### Implementation requirements

- `PlatformEvent`, `DomainEvent`, `EventHandler`, `EventSubscription`.

### Acceptance criteria

- [x] контракты без broker-зависимостей.

### Required tests

- compile + EventBus impl tests.

### Verification commands

```bash
mvn -q -pl :tmp-platform-core compile
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-007 — Implement synchronous Event Bus

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-006  
**Module:** tmp-platform-core

### Goal

Синхронный in-process publish/subscribe.

### Required documents

- `Database-Specification.md` §9.

### Required code context

- `EventBus` API.

### Allowed code scope

- `SynchronousEventBus`; `PlatformStartedEvent`; `PlatformStoppingEvent`.

### Forbidden

- async broker; domain business handlers.

### Implementation requirements

- sync dispatch; subscribe by type; unsubscribe.

### Acceptance criteria

- [x] platform/domain events delivered synchronously.

### Required tests

- `SynchronousEventBusTest`.

### Verification commands

```bash
mvn -q -pl :tmp-platform-core test -Dtest=SynchronousEventBusTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-008 — Implement Lifecycle Management

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-002, STAGE1-003  
**Module:** tmp-platform-core

### Goal

Управление жизненным циклом platform components.

### Required documents

- `Platform-Core-Specification.md`; `ADR-001`.

### Required code context

- `PlatformComponent`; `LifecycleManager`.

### Allowed code scope

- `DefaultLifecycleManager`.

### Forbidden

- управление бизнес-состоянием Capability.

### Implementation requirements

- startAll/stopAll; per-component states; reverse shutdown.

### Acceptance criteria

- [x] initialize → start → stop lifecycle works.

### Required tests

- `DefaultLifecycleManagerTest`.

### Verification commands

```bash
mvn -q -pl :tmp-platform-core test -Dtest=DefaultLifecycleManagerTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-009 — Implement Platform Configuration access

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-001  
**Module:** tmp-platform-core

### Goal

Read-only доступ к конфигурации через Core API.

### Required documents

- `Platform-Core-Specification.md`; `STAGE-1-PLATFORM-CORE.md`.

### Required code context

- Spring Environment; `application.yml`.

### Allowed code scope

- `SpringPlatformConfiguration`; `PlatformCoreProperties`; `tmp.platform.*` in yml.

### Forbidden

- secrets management (Stage 4).

### Implementation requirements

- getString/getBoolean; platform name/version properties.

### Acceptance criteria

- [x] PlatformConfiguration bean in context.

### Required tests

- `PlatformCoreAutoConfigurationTest`.

### Verification commands

```bash
mvn -q -pl :tmp-platform-core test -Dtest=PlatformCoreAutoConfigurationTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-010 — Wire Platform Core into bootstrap

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-003..STAGE1-009  
**Module:** tmp-bootstrap-app, tmp-platform-core

### Goal

Подключить Platform Core к composition root.

### Required documents

- `Development-Guide.md`; `STAGE-1-PLATFORM-CORE.md`.

### Required code context

- `TmpBootstrapApplication`; `PlatformCoreAutoConfiguration`.

### Allowed code scope

- auto-config; bootstrap pom dependency; lifecycle listener.

### Forbidden

- изменение Stage 0 DB wiring; business beans.

### Implementation requirements

- PlatformCore bean; start/stop on context events; platform events published.

### Acceptance criteria

- [x] PlatformCore in bootstrap context; [x] lifecycle hooks work.

### Required tests

- `SpringContextSmokeTest`; `PlatformCoreIntegrationIT`.

### Verification commands

```bash
mvn -q -pl :tmp-bootstrap-app verify
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-011 — Add Stage 1 architecture tests

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-010  
**Module:** tmp-architecture-tests

### Goal

ArchUnit-правила границ Platform Core.

### Required documents

- `Platform-Core-Specification.md`; Stage 0 ArchUnit baseline.

### Required code context

- `Stage0ArchitectureBaselineTest`.

### Allowed code scope

- `Stage1PlatformCoreArchitectureTest`; architecture-tests pom.

### Forbidden

- ослабление Stage 0 rules.

### Implementation requirements

- core ⊥ ui/infra; external → api only; ui ⊥ core.

### Acceptance criteria

- [x] Stage 1 + Stage 0 arch tests pass in verify.

### Required tests

- `Stage1PlatformCoreArchitectureTest`; `Stage0ArchitectureBaselineTest`.

### Verification commands

```bash
mvn -q -pl :tmp-architecture-tests test
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-012 — Minimal platform status UI visibility

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-010  
**Module:** tmp-ui-shell, tmp-bootstrap-app

### Goal

Технический статус Platform Core в empty shell без coupling UI к Core.

### Required documents

- `Master Implementation Checklist.md`; Stage 0 UI rules.

### Required code context

- `EmptyMainShell`; `JavaFxShellLauncher`; `DesktopBootstrap`.

### Allowed code scope

- status label in ui-shell; bootstrap passes status string.

### Forbidden

- ui-shell dependency on tmp-platform-core; business screens.

### Implementation requirements

- `platformCore.status().summary()` → launcher → bottom label.

### Acceptance criteria

- [x] status rendered; [x] ui-shell has no core dependency.

### Required tests

- `JavaFxShellSmokeTest.rendersPlatformStatusLabelWhenProvided`.

### Verification commands

```bash
mvn -q -pl :tmp-ui-shell test
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-013 — Run complete Stage 1 verification gate

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-003..STAGE1-012  
**Module:** cross-stage

### Goal

Финальная верификация Stage 1 против exit criteria.

### Required documents

- `STAGE-1-PLATFORM-CORE.md`; `Master-Implementation-Plan.md`.

### Required code context

- full reactor; all Stage 1 tests.

### Allowed code scope

- development-control documentation only.

### Forbidden

- feature changes; переход к Stage 2.

### Implementation requirements

- full verify + package verify; PackagingSmokeIT.

### Acceptance criteria

- [x] exit criteria confirmed; [x] `mvn verify` + `-Ppackage` PASSED; [x] no domain logic in Core.

### Required tests

- full stage verification suite.

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-014 — Fix Stage 1 acceptance review blockers (BLK-005..007)

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-013  
**Module:** tmp-platform-core, tmp-bootstrap-app, tmp-architecture-tests

### Goal

Устранить три блокирующих дефекта acceptance review Stage 1 без перехода к Stage 2.

### Required documents

- `STAGE-1-PLATFORM-CORE.md`; Platform Core Specification; acceptance review blockers BLK-005..007.

### Required code context

- Event contracts; `DefaultLifecycleManager`; registries; `PlatformCore`; architecture tests.

### Allowed code scope

- `tmp-platform-core`; integration/architecture tests; development-control documentation.

### Forbidden

- Stage 2 features; Document Engine; business logic in Core.

### Implementation requirements

- Stable immutable event metadata contract.
- Lifecycle failure consistency with rollback.
- Atomic single-path component registration.
- Service registry count fixes; expanded EventBus and architecture tests.

### Acceptance criteria

- [x] BLK-005 RESOLVED — stable eventId/occurredAt, expanded EventBus tests.
- [x] BLK-006 RESOLVED — lifecycle failure/rollback tests.
- [x] BLK-007 RESOLVED — `PlatformCore.registerComponent()` only public path.
- [x] Service registry and architecture test enhancements.
- [x] `mvn clean verify` and `mvn clean verify -Ppackage` PASSED.
- [x] Manual TMP.exe launch verified.

### Required tests

- `SynchronousEventBusTest`, `DefaultLifecycleManagerTest`, `DefaultPlatformCoreRegistrationTest`, `DefaultServiceRegistryTest`, `Stage1PlatformCoreArchitectureTest`, `PlatformCoreIntegrationIT`.

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
```

Manual: `dist/jpackage/TMP/TMP.exe` with `TMP_DB_URL`, `TMP_DB_USERNAME`, `TMP_DB_PASSWORD`.

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## Stage 2 — Document Engine (decomposition)

| Task | Status | Scope |
|---|---|---|
| STAGE2-001 | DONE | Create `tmp-document-engine` module and API boundaries |
| STAGE2-002 | DONE | Document type/metadata/lifecycle contracts |
| STAGE2-003 | DONE | Document processor registry (one processor per type) |
| STAGE2-004 | DONE | Document engine public facade contract |
| STAGE2-005 | DONE | Storage/journal/version/file ports |
| STAGE2-006 | DONE | Flyway migration `V2__documents_schema.sql` |
| STAGE2-007 | DONE | JDBC adapters for documents/journal/version/files |
| STAGE2-008 | DONE | `DefaultDocumentEngine` lifecycle implementation |
| STAGE2-009 | DONE | Processor invocation rules and status transitions |
| STAGE2-010 | DONE | Lifecycle journal and version snapshots |
| STAGE2-011 | DONE | Search/query support and status API |
| STAGE2-012 | DONE | Spring auto-configuration and PlatformCore registration |
| STAGE2-013 | DONE | Bootstrap integration and document panel text rendering |
| STAGE2-014 | DONE | Integration tests (document engine + bootstrap + DB) |
| STAGE2-015 | DONE | Stage 2 architecture rules |
| STAGE2-016 | DONE | Final Stage 2 verification gate (`verify`, `verify -Ppackage`) |
| STAGE2-017 | DONE | Fix duplicate DocumentEngine beans (BLK-010) |
| STAGE2-018 | DONE | Atomic processor registration (BLK-011) |
| STAGE2-019 | DONE | Post-commit event publishing (BLK-012) |
| STAGE2-020 | DONE | Expanded lifecycle/rollback/concurrency tests |
| STAGE2-021 | DONE | Final Stage 2 re-verification gate |
| STAGE2-022 | DONE | Registry rollback compensation (BLK-011 reopen) |
| STAGE2-023 | DONE | After-commit handler failure policy (BLK-013) |
| STAGE2-024 | DONE | PostgreSQL Testcontainers Document Engine ITs |
| STAGE2-025 | DONE | FK document_type_id decision and invariant |
| STAGE2-026 | DONE | Final Stage 2 re-verification gate (re-review) |

### Stage 2 completion notes

- Document Engine remains domain-independent and contains no business module logic.
- One document type maps to exactly one registered `DocumentProcessor`.
- Module depends on Platform Core public API only.
- STAGE2-022..026 closed residual BLK-011/BLK-013; Stage 2 CLOSED — stop before Stage 3.

## STAGE2-022 — Registry rollback compensation (BLK-011 reopen)

**Status:** DONE  
**Stage:** 2  
**Depends on:** STAGE2-021  
**Module:** tmp-document-engine

### Goal

Сделать итог `registerProcessor` согласованным с финальным outcome транзакции: при любом rollback processor не остаётся в registry.

### Required documents

- `Document-Engine-Specification.md`; acceptance re-review BLK-011.

### Required code context

- `DefaultDocumentEngine.registerProcessor()`; `DefaultDocumentProcessorRegistry`; Spring `TransactionSynchronization`.

### Allowed code scope

- `DefaultDocumentEngine.java`; `DefaultDocumentProcessorRegistry.java`; `tmp-document-engine/src/test/**`.

### Forbidden

- Stage 3; изменение Platform Core EventBus; message broker.

### Implementation requirements

- После DB write + registry register: compensating `unregister` в `afterCompletion` если status != COMMITTED.
- Guard create: тип должен существовать в `documents.document_types`.
- Deterministic `TransactionTemplate` test: register → rollbackOnly → no DB type, no registry entry → retry succeeds.
- Покрыть commit-failure path (`beforeCommit` throw).

### Acceptance criteria

- [x] Outer rollback leaves neither DB type nor in-memory processor.
- [x] Retry registration after rollback succeeds.
- [x] Create document rejected when type absent from DB.
- [x] BLK-011 RESOLVED.

### Required tests

- `DefaultDocumentEngineRegistrationTransactionTest` (H2 component).
- Coverage included in STAGE2-024 PostgreSQL IT.

### Verification commands

```bash
mvn -q -pl :tmp-document-engine test -Dtest=DefaultDocumentEngineRegistration*
```

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE2-023 — After-commit handler failure policy (BLK-013)

**Status:** DONE  
**Stage:** 2  
**Depends on:** STAGE2-022  
**Module:** tmp-document-engine

### Goal

Документная операция не должна выглядеть откатившейся из-за падения after-commit подписчика; зафиксировать best-effort delivery policy без изменения Platform Core.

### Required documents

- `Document-Engine-Specification.md`; BLK-013.

### Required code context

- `TransactionAfterCommitEventPublisher`; `SynchronousEventBus` (read-only contract).

### Allowed code scope

- `TransactionAfterCommitEventPublisher.java`; related tests/docs in document-engine.

### Forbidden

- Изменение Platform Core EventBus failure contract; message broker; unsafe auto-retry API.

### Implementation requirements

- Catch handler/delivery failures in after-commit publish; log error; do not rethrow to caller.
- Document policy in class javadoc.
- Test: failing subscriber; document + journal persisted; create returns successfully.

### Acceptance criteria

- [x] Failing handler does not fail document operation after commit.
- [x] Delivery failure logged.
- [x] Document and lifecycle journal remain committed.
- [x] BLK-013 RESOLVED; Platform Core unchanged.

### Required tests

- Extend `DefaultDocumentEngineTransactionEventTest`.

### Verification commands

```bash
mvn -q -pl :tmp-document-engine test -Dtest=DefaultDocumentEngineTransactionEventTest
```

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE2-024 — PostgreSQL Testcontainers Document Engine ITs

**Status:** DONE  
**Stage:** 2  
**Depends on:** STAGE2-023  
**Module:** tmp-document-engine

### Goal

Подтвердить PostgreSQL semantics для rollback/concurrency/events/storage; H2 оставить как быстрые component tests.

### Required documents

- `STAGE-2-DOCUMENT-ENGINE.md`; Database Specification § naming/FK module rules.

### Required code context

- Existing H2 component tests; Testcontainers setup patterns from bootstrap ITs.

### Allowed code scope

- `tmp-document-engine/src/test/**` PostgreSQL IT classes.

### Forbidden

- Removing H2 component tests; Stage 3 features.

### Implementation requirements

Cover on PostgreSQL Testcontainers:

- processor registration rollback;
- processor operation rollback;
- optimistic locking conflict;
- concurrent post; concurrent update;
- event after commit; no event after rollback;
- failing event subscriber;
- version snapshots; lifecycle journal;
- document file storage.

### Acceptance criteria

- [x] All listed scenarios pass on PostgreSQL Testcontainers.
- [x] H2 component tests retained.

### Required tests

- `DocumentEnginePostgresIntegrationIT`.

### Verification commands

```bash
mvn -q -pl :tmp-document-engine test -Dtest=*Postgres*
```

### Documentation updates

- STATUS; WORK-QUEUE; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE2-025 — FK document_type_id decision and invariant

**Status:** DONE  
**Stage:** 2  
**Depends on:** STAGE2-022  
**Module:** tmp-document-engine

### Goal

Зафиксировать решение по FK `documents.document_type_id → document_types.id` и обеспечить инвариант отсутствия orphan types.

### Required documents

- Database Specification §12 (inter-module FK ban; intra-module FK allowed).

### Required code context

- `V2__documents_schema.sql`; `JdbcDocumentStorageAdapter.insert`.

### Allowed code scope

- Flyway migration under document-engine; create guard; tests.

### Forbidden

- Cross-module PostgreSQL FKs.

### Implementation requirements

- Add intra-schema FK via new Flyway migration (or document intentional absence).
- If FK added: migration + IT asserting constraint.
- Application-level `documentTypeExists` guard on create.

### Acceptance criteria

- [x] Decision documented in BLOCKERS/IMPLEMENTATION-LOG.
- [x] Orphan document_type_id cannot be inserted (FK and/or guard).
- [x] Test covers invariant.

### Required tests

- FK/invariant assertion in H2 and PostgreSQL suites.

### Verification commands

```bash
mvn -q -pl :tmp-document-engine test -Dtest=*Registration*,*Postgres*,*Lifecycle*
```

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE2-026 — Final Stage 2 re-verification gate (re-review)

**Status:** DONE  
**Stage:** 2  
**Depends on:** STAGE2-022..STAGE2-025  
**Module:** cross-stage

### Goal

Закрыть Stage 2 после residual blockers, full verify и ручного TMP.exe; не начинать Stage 3.

### Required documents

- `STAGE-2-DOCUMENT-ENGINE.md`; `RUN-DEVELOPMENT.md`.

### Required code context

- full reactor; packaged application.

### Allowed code scope

- development-control documentation only (unless last-minute defect fixes).

### Forbidden

- Stage 3 features.

### Implementation requirements

- `mvn clean verify` and `mvn clean verify -Ppackage` PASSED.
- Manual `dist/jpackage/TMP/TMP.exe`.
- BLK-011 and BLK-013 RESOLVED.

### Acceptance criteria

- [x] Full verify PASSED.
- [x] Package verify PASSED.
- [x] TMP.exe starts.
- [x] Stage 2 exit criteria confirmed; Stage 3 not started.

### Required tests

- full stage verification suite.

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
```

Manual: `dist/jpackage/TMP/TMP.exe`

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE2-017 — Fix duplicate DocumentEngine beans (BLK-010)

**Status:** DONE  
**Stage:** 2  
**Depends on:** STAGE2-016  
**Module:** tmp-document-engine, tmp-bootstrap-app

### Goal

Оставить ровно один Spring bean типа `DocumentEngine` и подтвердить уникальный lookup из bootstrap context.

### Required documents

- `STAGE-2-DOCUMENT-ENGINE.md`; acceptance review BLK-010; `Document-Engine-Specification.md`.

### Required code context

- `DocumentEngineAutoConfiguration`; `DocumentEnginePlatformRegistrar`; `DesktopBootstrap`; `SpringContextSmokeTest`.

### Allowed code scope

- `tmp-document-engine/src/main/java/com/tmp/document/DocumentEngineAutoConfiguration.java`;
- `tmp-document-engine/src/main/java/com/tmp/document/DocumentEnginePlatformRegistrar.java`;
- `tmp-bootstrap-app/src/test/**`.

### Forbidden

- Stage 3 features; business logic; ослабление quality gates.

### Implementation requirements

- Удалить `documentEngineFacade` bean; единственный `@Bean DocumentEngine`.
- Обновить `DocumentEnginePlatformRegistrar` на `DocumentEngine`.
- Добавить тест `getBean(DocumentEngine.class)` в bootstrap context.
- Добавить DesktopBootstrap lookup smoke test.

### Acceptance criteria

- [x] Ровно один bean типа `DocumentEngine` в контексте.
- [x] `applicationContext.getBean(DocumentEngine.class)` возвращает bean без ambiguity.
- [x] DesktopBootstrap lookup smoke test проходит.
- [x] BLK-010 RESOLVED.

### Required tests

- `DocumentEngineBeanLookupTest` или расширение `SpringContextSmokeTest`.
- `DesktopBootstrapLookupSmokeTest`.

### Verification commands

```bash
mvn -q -pl :tmp-bootstrap-app test -Dtest=SpringContextSmokeTest,DocumentEngineBeanLookupTest,DesktopBootstrapLookupSmokeTest
```

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE2-018 — Atomic processor registration (BLK-011)

**Status:** DONE  
**Stage:** 2  
**Depends on:** STAGE2-017  
**Module:** tmp-document-engine

### Goal

Сделать регистрацию processor + document type атомарной без partial in-memory state при DB failure.

### Required documents

- `Document-Engine-Specification.md`; acceptance review BLK-011.

### Required code context

- `DefaultDocumentEngine.registerProcessor()`; `DefaultDocumentProcessorRegistry`; `DocumentStoragePort`.

### Allowed code scope

- `DefaultDocumentEngine.java`; `tmp-document-engine/src/test/**`.

### Forbidden

- Изменение Platform Core; message broker.

### Implementation requirements

- DB `registerDocumentType` перед in-memory `processorRegistry.register`.
- Метод в одной `@Transactional` границе.
- Тест с намеренной DB failure; повторная регистрация после failure проходит.

### Acceptance criteria

- [x] При DB failure processor не остаётся в registry.
- [x] Повторная корректная регистрация успешна.
- [x] Duplicate processor registration по-прежнему отклоняется.
- [x] BLK-011 RESOLVED.

### Required tests

- `DefaultDocumentEngineRegistrationTest` (DB failure, retry, duplicate).

### Verification commands

```bash
mvn -q -pl :tmp-document-engine test -Dtest=DefaultDocumentEngineRegistrationTest
```

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE2-019 — Post-commit event publishing (BLK-012)

**Status:** DONE  
**Stage:** 2  
**Depends on:** STAGE2-018  
**Module:** tmp-document-engine

### Goal

Публиковать DocumentCreated/Posted/Unposted/Closed/Deleted только после успешного transaction commit.

### Required documents

- `Document-Engine-Specification.md`; `Database-Specification.md` §9; acceptance review BLK-012.

### Required code context

- `DefaultDocumentEngine.publishAfterCommit()`; `EventBus`; Spring transaction management.

### Allowed code scope

- `tmp-document-engine/src/main/java/com/tmp/document/**`; `tmp-document-engine/src/test/**`.

### Forbidden

- Message broker; async event infrastructure beyond Spring synchronization.

### Implementation requirements

- `TransactionAfterCommitEventPublisher` через `TransactionSynchronizationManager.afterCommit`.
- События не публикуются при rollback.
- Transaction integration tests.

### Acceptance criteria

- [x] Event emitted once after commit.
- [x] Event not emitted on rollback.
- [x] Stable event metadata preserved.
- [x] BLK-012 RESOLVED.

### Required tests

- `DefaultDocumentEngineTransactionEventTest`.

### Verification commands

```bash
mvn -q -pl :tmp-document-engine test -Dtest=DefaultDocumentEngineTransactionEventTest
```

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE2-020 — Expanded lifecycle/rollback/concurrency tests

**Status:** DONE  
**Stage:** 2  
**Depends on:** STAGE2-019  
**Module:** tmp-document-engine, tmp-bootstrap-app

### Goal

Покрыть обязательные сценарии acceptance review: rollback, lifecycle guards, optimistic locking, concurrency, file storage.

### Required documents

- `STAGE-2-DOCUMENT-ENGINE.md`; `Document-Engine-Specification.md`; acceptance review test list.

### Required code context

- `DefaultDocumentEngine`; processor hooks; JDBC adapters; `DocumentFileStoragePort`.

### Allowed code scope

- `tmp-document-engine/src/test/**`; `tmp-bootstrap-app/src/test/**`.

### Forbidden

- Ослабление существующих тестов; business document types.

### Implementation requirements

- Processor failure rollback (create/post/unpost/close/delete).
- Invalid lifecycle transitions; immutable POSTED/CLOSED; delete restrictions.
- Optimistic locking conflict; concurrent post/update.
- Version snapshot persistence; lifecycle journal consistency.
- Close allowed/rejected by processor; file storage adapter integration.

### Acceptance criteria

- [x] Все перечисленные сценарии покрыты тестами.
- [x] `mvn -q -pl :tmp-document-engine,:tmp-bootstrap-app test` PASSED.

### Required tests

- `DefaultDocumentEngineLifecycleTest`; `JdbcDocumentFileStorageAdapterTest`; concurrency tests.

### Verification commands

```bash
mvn -q -pl :tmp-document-engine,:tmp-bootstrap-app test
```

### Documentation updates

- STATUS; WORK-QUEUE; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE2-021 — Final Stage 2 re-verification gate

**Status:** DONE  
**Stage:** 2  
**Depends on:** STAGE2-017..STAGE2-020  
**Module:** cross-stage

### Goal

Закрыть Stage 2 после устранения всех blockers, полной verification и ручного запуска TMP.exe.

### Required documents

- `STAGE-2-DOCUMENT-ENGINE.md`; `RUN-DEVELOPMENT.md`.

### Required code context

- full reactor; packaged application.

### Allowed code scope

- development-control documentation only.

### Forbidden

- Stage 3 features; новые feature-изменения вне defect fixes.

### Implementation requirements

- `mvn clean verify` и `mvn clean verify -Ppackage` PASSED.
- Ручной запуск `dist/jpackage/TMP/TMP.exe`.
- Все blockers BLK-010..012 RESOLVED.

### Acceptance criteria

- [x] Full verify PASSED.
- [x] Package verify PASSED.
- [x] TMP.exe запускается.
- [x] Stage 2 exit criteria подтверждены.

### Required tests

- full stage verification suite.

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
```

Manual: `dist/jpackage/TMP/TMP.exe`

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-016 — Fix registration/lifecycle race condition (BLK-009)

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-015  
**Module:** tmp-platform-core

### Goal

Устранить race condition между `registerComponent()` и `startAll()`/`stopAll()` через единый synchronization boundary.

### Required documents

- `STAGE-1-PLATFORM-CORE.md`; STAGE1-015 re-review BLK-009.

### Required code context

- `DefaultPlatformCore`; `DefaultLifecycleManager`; registration and lifecycle tests.

### Allowed code scope

- `tmp-platform-core`; development-control documentation.

### Forbidden

- Stage 2 features; Document Engine; business logic in Core.

### Implementation requirements

- Unified lifecycle monitor for state, registration, startAll, stopAll.
- Atomic registry+lifecycle registration with rollback.
- Deterministic concurrency test; STOPPED restart semantics test.

### Acceptance criteria

- [x] No split `registrationLock`; single synchronization boundary.
- [x] Registration and lifecycle state checks atomic vs startAll/stopAll.
- [x] No REGISTERED components inside STARTED platform; no ConcurrentModificationException.
- [x] STOPPED restart: registration + startAll starts all components.
- [x] `mvn clean verify` and `mvn clean verify -Ppackage` PASSED.

### Required tests

- `DefaultPlatformCoreRegistrationTest` (concurrency, restart-after-STOPPED).

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
```

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

## STAGE1-015 — Fix Stage 1 re-review remaining defects (BLK-008)

**Status:** DONE  
**Stage:** 1  
**Depends on:** STAGE1-014  
**Module:** tmp-platform-core, tmp-bootstrap-app, tmp-architecture-tests

### Goal

Устранить оставшиеся дефекты повторной проверки Stage 1 без перехода к Stage 2.

### Required documents

- `STAGE-1-PLATFORM-CORE.md`; acceptance review BLK-008 items.

### Required code context

- `DefaultPlatformCore.registerComponent()`; platform events; shutdown listener; ArchUnit rules.

### Allowed code scope

- `tmp-platform-core`; integration/architecture tests; development-control documentation.

### Forbidden

- Stage 2 features; Document Engine; business logic in Core.

### Implementation requirements

- Registration guard by platform lifecycle state.
- Move platform events to `com.tmp.core.api.event.platform`.
- Shutdown listener `try/finally` for guaranteed `stopAll()`.
- Generic ArchUnit api-only dependency rule.

### Acceptance criteria

- [x] Registration allowed only in REGISTERED/STOPPED; forbidden in INITIALIZING/STARTED/STOPPING/FAILED without partial state.
- [x] Platform events in public API package; no external imports from `com.tmp.core.event`.
- [x] Failing stopping handler does not prevent `stopAll()`.
- [x] ArchUnit: external modules depend on `com.tmp.core..` only via `com.tmp.core.api..`.
- [x] `mvn clean verify` and `mvn clean verify -Ppackage` PASSED.

### Required tests

- `DefaultPlatformCoreRegistrationTest` (registration guard scenarios).
- `PlatformCoreLifecycleListenerTest`.
- `Stage1PlatformCoreArchitectureTest`.

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
```

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

---

# Stage 3 — Capability Engine (decomposition)

## Design decisions fixed for this Stage

1. Module: `tmp-capability-engine`. Public API package: `com.tmp.capability.api` (mirrors `com.tmp.document.api`). Internal implementation packages: `com.tmp.capability.*` (registry, lifecycle, validation, discovery, contribution).
2. Capability Engine registers **itself** as exactly one `PlatformComponent` in Platform Core (same pattern as `DocumentEnginePlatformRegistrar`). Individual Capabilities are **not** registered as `PlatformComponent`s; they are discovered/managed internally by Capability Engine.
3. Discovery mechanism: Spring beans implementing `com.tmp.capability.api.Capability` are collected via constructor injection of `List<Capability>` into the discovery component — explicit, deterministic, no classloading, no plugin framework.
4. `CapabilityLifecycleState`: `DISCOVERED, VALIDATED, REGISTERED, INITIALIZED, ACTIVE, STOPPED, DEACTIVATED, FAILED` — fixed set of allowed transitions defined and unit-tested in STAGE3-008.
5. Version compatibility rule (no ADR/spec-defined semantics found; minimal domain-independent technical contract, self-implemented, no external SemVer library): `CapabilityVersion` is immutable, parsed from `MAJOR.MINOR.PATCH` (non-negative integers only, no pre-release/build metadata). A dependency requirement declares a minimum required `CapabilityVersion`. A declared capability version is **compatible** with a requirement if and only if `actual.major() == required.major() && (actual.minor() > required.minor() || (actual.minor() == required.minor() && actual.patch() >= required.patch()))`. Different major version ⇒ incompatible (breaking-change boundary). This rule is documented in `CapabilityVersion` Javadoc and covered by dedicated tests. It is an internal algorithm behind a stable method signature (`isCompatibleWith`), so it can be revisited later without breaking the public shape of the value object.
6. **Atomic registration/compensation design (resolves potential blocker without changing Stage 1/2 public API):** verified from actual Stage 1/2 implementations that (a) `DocumentEngine.registerProcessor()` is self-atomic (Stage 2 BLK-011/018 — throws before mutating on duplicate, never partially registers); (b) `ServiceRegistry.register()` and `EventBus.subscribe*()` are unconditionally successful by current contract (pure in-memory add, no duplicate rejection); (c) `PlatformCore.capabilityRegistry().register()` only mutates on success (`putIfAbsent`-based), and Capability Engine already enforces global ID uniqueness in its own registry before reaching this call. Given these verified facts, atomic registration is achieved by performing all Capability-Engine-owned catalog mutations first (always reversible), then external calls in the fixed order [Platform Core `CapabilityRegistry.register` → Document Engine `registerProcessor` (the only call with genuine, pre-validated failure risk) → Platform Core `ServiceRegistry.register` → Platform Core `EventBus.subscribe*`], with pre-validation (duplicate lookups) performed before every external mutating call under Capability Engine's single registration lock. This eliminates the realistic failure window without requiring any Stage 1/2 public API change. The residual theoretical risk (a future Platform Core contract change making `ServiceRegistry.register`/`EventBus.subscribe*` fail after Document Engine registration already succeeded) is documented as a known limitation in `CapabilityRegistrationService` Javadoc, not hidden. No blocker raised for this design choice.
7. Sample technical Capability lives in `tmp-capability-engine` under a clearly-named sample package (not a business module), used for architecture, unit, integration and Testcontainers tests, and for bootstrap/UI smoke visibility.

## STAGE3-001 — Bootstrap `tmp-capability-engine` module and public API package skeleton

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE2-026  
**Module:** tmp-capability-engine (new)

### Goal

Создать Maven-модуль `tmp-capability-engine`, подключить его в root reactor, объявить зависимости только на публичные API Platform Core и Document Engine, создать пустой пакет `com.tmp.capability.api` без бизнес-логики и без единого класса контракта (контракты появляются в следующих задачах).

### Required documents

- `Capability-Engine-Specification.md` (Назначение, Определение Capability, Ответственность, Что не входит, Архитектура);
- `Platform-Core-Specification.md` (Зависимости, Architecture Rules AR-001..AR-005);
- Stage 1/2 module bootstrap precedent (`tmp-platform-core/pom.xml`, `tmp-document-engine/pom.xml`).

### Required code context

- root `pom.xml` (`<modules>`, `<dependencyManagement>`);
- `tmp-document-engine/pom.xml` as the structural template (module depending only on `tmp-platform-core`).

### Allowed code scope

- root `pom.xml` (`<modules>`, `<dependencyManagement>` entry for `tmp-capability-engine`);
- `tmp-capability-engine/pom.xml` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/package-info.java` (new, documents package purpose only).

### Forbidden

- any interface/class beyond `package-info.java`;
- dependency on any module other than `tmp-platform-core` and `tmp-document-engine` public APIs (test scope may add JUnit/ArchUnit per parent `dependencyManagement`);
- business packages.

### Implementation requirements

- `tmp-capability-engine/pom.xml`: `<parent>` = `tmp-parent`; dependencies = `tmp-platform-core`, `tmp-document-engine`, `spring-boot-starter` (matching Stage 1/2 convention), test-scope JUnit;
- add module to root `pom.xml` `<modules>` (after `tmp-document-engine`) and to `<dependencyManagement>` (own artifact + used by later architecture-tests module);
- no other production code in this task.

### Public contracts that may change

- none exist yet; this task only creates the module shell.

### Acceptance criteria

- [ ] `mvn -q -DskipTests validate` passes for the full reactor with the new module present;
- [ ] `mvn -q -pl :tmp-capability-engine compile` passes with zero business logic;
- [ ] module has no dependency other than `tmp-platform-core`, `tmp-document-engine`, Spring Boot starter.

### Required tests

- none (pure structural task; validated by build commands only, matching STAGE1-001/STAGE2-001 precedent).

### Verification commands

```bash
mvn -q -DskipTests validate
mvn -q -pl :tmp-capability-engine compile
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

`tmp-capability-engine` exists in the reactor, compiles, and has zero implementation — a clean starting point for the public API contracts.

---

## STAGE3-002 — CapabilityId and CapabilityVersion value objects with version compatibility rule

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-001  
**Module:** tmp-capability-engine

### Goal

Определить immutable value objects `CapabilityId` и `CapabilityVersion` (с deterministic сравнением и правилом совместимости версий), являющиеся основой всех остальных контрактов Capability.

### Required documents

- `Capability-Engine-Specification.md` (Контракт Capability — id/version; раздел "Зависимости" — совместимость версии упомянута, но не детализирована);
- this file's "Design decisions" §5 (version compatibility rule fixed above — no ADR/spec-defined semantics exist, so this task implements the documented minimal technical contract).

### Required code context

- `com.tmp.core.api.capability.CapabilityDescriptor` (Stage 1, for naming/style precedent only — not reused directly, Capability Engine needs a richer descriptor).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityId.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityVersion.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/CapabilityIdTest.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/CapabilityVersionTest.java` (new).

### Forbidden

- registry, lifecycle, or discovery logic;
- any external SemVer library dependency.

### Implementation requirements

- `CapabilityId`: immutable wrapper over a non-blank `String`, validated (not null, not blank, trimmed equality semantics documented), `equals`/`hashCode`/`toString`.
- `CapabilityVersion`: immutable, parsed via a static factory `CapabilityVersion.of(String)` from `MAJOR.MINOR.PATCH` (regex-validated non-negative integers only); implements `Comparable<CapabilityVersion>` with deterministic ordering; exposes `isCompatibleWith(CapabilityVersion required)` implementing the rule fixed in "Design decisions" §5; malformed input throws `IllegalArgumentException` with a precise message.
- Both classes documented with Javadoc explaining they carry no business meaning (technical identity/version only).

### Public contracts that may change

- New public types only (`CapabilityId`, `CapabilityVersion`); nothing existing changes.

### Acceptance criteria

- [ ] valid id/version construct successfully;
- [ ] malformed version string rejected with clear exception;
- [ ] compatible/incompatible dependency version pairs behave per the fixed rule (same major, minor/patch ordering);
- [ ] comparison is deterministic and consistent with `equals`;
- [ ] both types are immutable (no setters, final fields, no exposed mutable state).

### Required tests

- `CapabilityIdTest`: valid id, blank id rejected, null rejected, equals/hashCode.
- `CapabilityVersionTest`: valid version; malformed version (missing part, non-numeric, negative); compatible dependency (same major, higher/equal minor.patch); incompatible dependency (different major, lower minor.patch); deterministic comparison (`compareTo` ordering, transitivity); immutability.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityIdTest,CapabilityVersionTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Stable, fully-tested identity and version value objects usable by every later descriptor.

---

## STAGE3-003 — Dependency descriptor and dependency validation error contract

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-002  
**Module:** tmp-capability-engine

### Goal

Определить immutable `DependencyDescriptor` (target capability id + minimum required version) и типизированный контракт ошибок валидации зависимостей, используемый последующей dependency-validation задачей.

### Required documents

- `Capability-Engine-Specification.md` (раздел "Зависимости": явное объявление, запрет циклов, зависимость только от публичного контракта);
- Stage 3 manifest domain 4 (`dependency validation`).

### Required code context

- `CapabilityId`, `CapabilityVersion` (STAGE3-002).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/DependencyDescriptor.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/DependencyValidationException.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/DependencyDescriptorTest.java` (new).

### Forbidden

- actual graph validation algorithm (belongs to STAGE3-010);
- any reference to concrete business capabilities.

### Implementation requirements

- `DependencyDescriptor(CapabilityId dependencyId, CapabilityVersion minimumVersion)` — immutable, null-checked;
- `DependencyValidationException` — unchecked, carries a `DependencyValidationReason` enum (`MISSING_DEPENDENCY, SELF_DEPENDENCY, DUPLICATE_DEPENDENCY, INCOMPATIBLE_VERSION, CYCLIC_DEPENDENCY`) and the offending `CapabilityId`(s) for precise diagnostics.

### Public contracts that may change

- New public types only.

### Acceptance criteria

- [ ] descriptor immutable and null-validated;
- [ ] exception carries a machine-readable reason plus human-readable message;
- [ ] no dependency on registry/graph classes (pure data contract).

### Required tests

- `DependencyDescriptorTest`: valid construction; null id rejected; null version rejected; equals/hashCode by (id, minimumVersion).

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=DependencyDescriptorTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A reusable, precise dependency contract ready for graph validation.

---

## STAGE3-004 — Command, View, Navigation and Permission descriptor contracts

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-002  
**Module:** tmp-capability-engine

### Goal

Определить доменно-независимые immutable metadata contracts для команд, представлений, навигации и прав — без реальных бизнес-экранов и без логики авторизации.

### Required documents

- `Capability-Engine-Specification.md` (Контракт Capability; "Права доступа"; "Рабочие места"; command/view/navigation contribution scope items);
- `UI-UX-Specification.md` (раздел "Навигация" — навигация строится автоматически на основании зарегистрированных Capability; один экран открыт одновременно — informs that `NavigationContribution` carries only routing metadata, not screen implementation).

### Required code context

- `CapabilityId` (STAGE3-002).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/PermissionDescriptor.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CommandDescriptor.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/ViewDescriptor.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/NavigationContribution.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/ContributionDescriptorsTest.java` (new).

### Forbidden

- FXML, Controller, ViewModel classes;
- permission checking/authorization logic;
- user/role types.

### Implementation requirements

- `PermissionDescriptor(String permissionId, String displayName, String description)` — immutable, non-blank id;
- `CommandDescriptor(String commandId, String displayName, List<String> requiredPermissionIds)` — immutable, defensive copy of list;
- `ViewDescriptor(String viewId, String displayName, String navigationTargetId)` — immutable metadata only (no FXML reference resolution, no screen instantiation — that is deferred to future UI stages);
- `NavigationContribution(String navigationId, String displayName, String viewId, int order)` — immutable metadata for building the navigation tree.
- All four types validate non-blank identifiers and are pure data (no behavior beyond accessors/equality).

### Public contracts that may change

- New public types only.

### Acceptance criteria

- [ ] all four types immutable, validated, with correct `equals`/`hashCode` by identifier;
- [ ] `CommandDescriptor.requiredPermissionIds()` returns an unmodifiable list;
- [ ] no class in this task depends on JavaFX, Spring, or persistence.

### Required tests

- `ContributionDescriptorsTest`: valid construction for each type; blank/null identifier rejected for each type; immutable collection exposure (`UnsupportedOperationException` on mutation attempt); equals/hashCode by id.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=ContributionDescriptorsTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Four independent, fully-tested UI/command metadata contracts with zero business or UI-framework coupling.

---

## STAGE3-005 — Public service, event, settings and document contribution contracts

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-002  
**Module:** tmp-capability-engine

### Goal

Определить доменно-независимые immutable metadata contracts для публичных сервисов, публикуемых событий, настроек и документных contributions.

### Required documents

- `Capability-Engine-Specification.md` ("Документы и Document Processor"; "Публичный API"; "События"; document/service/event/settings contribution scope items);
- `Document-Engine-Specification.md` public API section (already read: `DocumentProcessor`, `DocumentTypeDescriptor` — this task's `DocumentContribution` wraps a `DocumentProcessor` reference plus owning capability, it does not redefine Document Engine's own contracts).

### Required code context

- `CapabilityId` (STAGE3-002);
- `com.tmp.document.api.DocumentProcessor` (Document Engine public API, read-only dependency).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/PublicServiceContribution.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/EventContribution.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/SettingsContribution.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/DocumentContribution.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/IntegrationContributionDescriptorsTest.java` (new).

### Forbidden

- calling any Platform Core or Document Engine registration API from these types (pure data contracts, no side effects);
- persistence of settings values (descriptor only).

### Implementation requirements

- `PublicServiceContribution<T>(Class<T> serviceType, T serviceInstance)` — immutable holder; `serviceType` and `serviceInstance` non-null; `serviceInstance` must be assignable to `serviceType` (validated in constructor);
- `EventContribution(String eventTypeId, String description)` — immutable, describes a published domain event type (no payload interpretation, per spec "Capability Engine не интерпретирует содержимое событий");
- `SettingsContribution(String settingKey, String displayName, String description, String defaultValue)` — immutable descriptor only, no storage;
- `DocumentContribution(String documentTypeId, String displayName, String description, com.tmp.document.api.DocumentProcessor processor)` — immutable, `processor.documentTypeId()` must equal `documentTypeId` (validated in constructor, fail fast on mismatch).

### Public contracts that may change

- New public types only; read-only usage of existing `com.tmp.document.api.DocumentProcessor` (no change to Document Engine).

### Acceptance criteria

- [ ] all four types immutable and validated as specified;
- [ ] `PublicServiceContribution` rejects an instance not assignable to the declared type;
- [ ] `DocumentContribution` rejects a processor whose `documentTypeId()` does not match;
- [ ] no side effects (no calls into Platform Core/Document Engine) inside these constructors.

### Required tests

- `IntegrationContributionDescriptorsTest`: valid construction for each type; type-mismatch rejected for `PublicServiceContribution`; documentTypeId-mismatch rejected for `DocumentContribution`; blank identifiers rejected; equals/hashCode by identifying key.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=IntegrationContributionDescriptorsTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Complete, fully-tested set of integration-facing contribution contracts ready to be aggregated by `CapabilityDescriptor`.

---

## STAGE3-006 — CapabilityDescriptor aggregate, CapabilityLifecycleState and Capability SPI contract

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-003, STAGE3-004, STAGE3-005  
**Module:** tmp-capability-engine

### Goal

Собрать immutable `CapabilityDescriptor`, объединяющий все контракты из STAGE3-002..005, определить `CapabilityLifecycleState` enum и SPI-интерфейс `Capability`, который реализуют технические Capability (включая sample Capability из STAGE3-016).

### Required documents

- `Capability-Engine-Specification.md` ("Контракт Capability" — полный список обязательных полей; "Жизненный цикл Capability" — 7 стадий + FAILED per DISCOVERED..DEACTIVATED model);
- this file's "Design decisions" §4 (fixed lifecycle state set).

### Required code context

- all types from STAGE3-002..005.

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityDescriptor.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityLifecycleState.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/Capability.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/CapabilityDescriptorTest.java` (new).

### Forbidden

- registry/lifecycle-manager implementation (STAGE3-007..013);
- business capability implementations.

### Implementation requirements

- `CapabilityLifecycleState` enum: `DISCOVERED, VALIDATED, REGISTERED, INITIALIZED, ACTIVE, STOPPED, DEACTIVATED, FAILED`.
- `CapabilityDescriptor`: immutable, built via a `Builder`; required fields — `CapabilityId id`, `String name`, `CapabilityVersion version`, `String description`; collection fields (defensive-copied, unmodifiable) — `List<DependencyDescriptor> dependencies`, `List<PermissionDescriptor> permissions`, `List<CommandDescriptor> commands`, `List<ViewDescriptor> views`, `List<NavigationContribution> navigationContributions`, `List<DocumentContribution> documents`, `List<PublicServiceContribution<?>> publicServices`, `List<EventContribution> events`, `List<SettingsContribution> settings`;
- constructor/builder validates: `id` non-null; **duplicate contribution IDs within the same descriptor are rejected** (duplicate permission id, duplicate command id, duplicate view id, duplicate navigation id, duplicate event type id, duplicate settings key, duplicate document type id, duplicate dependency target id) — this is descriptor-level self-consistency, distinct from cross-capability uniqueness (STAGE3-007/010);
- `Capability` SPI interface: `CapabilityDescriptor descriptor(); void onInitialize(); void onActivate(); void onDeactivate(); void onStop();` — lifecycle hooks a capability implementation provides; Capability Engine calls these, never the reverse — no `PlatformCore`/`DocumentEngine` reference is injected into this SPI (a capability obtains public services exclusively through the same public registries any other module would use, per ADR-003/AR rules).

### Public contracts that may change

- New public types only (`CapabilityDescriptor`, `CapabilityLifecycleState`, `Capability`).

### Acceptance criteria

- [ ] valid descriptor builds successfully with all contribution types populated;
- [ ] missing id rejected;
- [ ] duplicate contribution id (each of the 8 categories above) rejected with a precise exception;
- [ ] all exposed collections are immutable (`List.copyOf` / unmodifiable wrapper, mutation attempt throws);
- [ ] `Capability` interface has no dependency on `com.tmp.core` or `com.tmp.document` beyond what is already used by `DocumentContribution`.

### Required tests

- `CapabilityDescriptorTest`: valid descriptor; missing id; duplicate permission id; duplicate command id; duplicate view id; duplicate navigation id; duplicate event id; duplicate settings key; duplicate document type id; duplicate dependency; immutable collections (attempt to mutate each returned list throws).

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityDescriptorTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

The complete, immutable Capability contract required by the specification, ready for registry/discovery/lifecycle implementation.

---

## STAGE3-007 — Capability Registry (read-only catalog, uniqueness, immutable snapshots)

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-006  
**Module:** tmp-capability-engine

### Goal

Реализовать внутренний (Capability-Engine-owned) реестр Capability: уникальность ID, поиск по ID, список всех, текущее lifecycle state, immutable snapshot, защита от partial state — как основу для discovery/registration/lifecycle задач.

### Required documents

- `Capability-Engine-Specification.md` ("Ответственность Capability Engine" — обнаружение/регистрация/уникальность/каталог).

### Required code context

- `CapabilityDescriptor`, `CapabilityLifecycleState`, `Capability` (STAGE3-006);
- `com.tmp.core.registry.DefaultCapabilityRegistry` (Stage 1, read for precedent only — different registry, not reused/extended).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/registry/CapabilityRegistry.java` (new, internal — not in `.api`, this is Capability Engine's own richer registry, distinct from `com.tmp.core.api.CapabilityRegistry`);
- `tmp-capability-engine/src/main/java/com/tmp/capability/registry/CapabilityRegistration.java` (new — immutable snapshot record: descriptor + current state + owning `Capability` instance);
- `tmp-capability-engine/src/test/java/com/tmp/capability/registry/CapabilityRegistryTest.java` (new).

### Forbidden

- dependency validation, discovery, or contribution-catalog logic (later tasks);
- persistence (in-memory only — no persistence added without a direct specification requirement).

### Implementation requirements

- Thread-safe (`ConcurrentHashMap`/synchronized, matching Stage 1 registry style);
- `reserve(CapabilityId id)` — atomically claims an id for in-flight registration, throws on duplicate (used later for concurrent-registration protection);
- `release(CapabilityId id)` — releases a reservation on rollback;
- `commit(CapabilityRegistration registration)` — finalizes a registration, replacing the reservation;
- `updateState(CapabilityId id, CapabilityLifecycleState newState)` — atomic state transition record (transition legality enforced by STAGE3-008, this registry only stores the result);
- `findById(CapabilityId id) -> Optional<CapabilityRegistration>`;
- `findAll() -> List<CapabilityRegistration>` — returns an immutable snapshot (`List.copyOf`), sorted deterministically by id, safe to iterate while registrations change concurrently (no `ConcurrentModificationException`);
- duplicate id at `reserve` or `commit` throws `IllegalStateException` with the offending id.

### Public contracts that may change

- None (this class is intentionally internal, not part of `com.tmp.capability.api`).

### Acceptance criteria

- [ ] duplicate id rejected at `reserve`;
- [ ] `findAll()` returns an immutable, consistent snapshot even if mutated concurrently in another thread;
- [ ] `release` after `reserve` allows a subsequent `reserve` with the same id to succeed (retry-after-failure semantics);
- [ ] no partial state visible between `reserve` and `commit`.

### Required tests

- `CapabilityRegistryTest`: register/find/list; duplicate id rejected; release-then-retry succeeds; state update reflected in snapshot; snapshot immutability; concurrent `findAll()` during concurrent `reserve`/`commit` produces no `ConcurrentModificationException` (deterministic multi-thread test, matching `DefaultPlatformCoreRegistrationTest` precedent).

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityRegistryTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A thread-safe, in-memory, immutable-snapshot capability catalog ready to back discovery and lifecycle management.

---

## STAGE3-008 — Capability lifecycle state machine (allowed transitions)

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-006  
**Module:** tmp-capability-engine

### Goal

Зафиксировать и протестировать допустимые переходы между `CapabilityLifecycleState` в виде явного, независимого от остальной логики контракта, используемого регистрацией и lifecycle-менеджером.

### Required documents

- `Capability-Engine-Specification.md` ("Жизненный цикл Capability" — 7 стадий; "Ошибка при инициализации одной Capability не должна приводить к повреждению конфигурации других" — implies `FAILED` is reachable from `INITIALIZED`/`ACTIVE`).

### Required code context

- `CapabilityLifecycleState` (STAGE3-006).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/lifecycle/CapabilityStateTransition.java` (new — internal transition-table utility);
- `tmp-capability-engine/src/test/java/com/tmp/capability/lifecycle/CapabilityStateTransitionTest.java` (new).

### Forbidden

- actual lifecycle orchestration (initialize/activate/deactivate execution — STAGE3-013);
- any I/O or external call.

### Implementation requirements

- Fixed transition table (documented in Javadoc as the technical contract, mirroring `DefaultLifecycleManager.isRegistrationAllowed` precedent style):
  - `DISCOVERED -> VALIDATED`
  - `VALIDATED -> REGISTERED`
  - `REGISTERED -> INITIALIZED`
  - `INITIALIZED -> ACTIVE`
  - `ACTIVE -> STOPPED`
  - `STOPPED -> INITIALIZED` (restart after stop, mirrors Platform Core `STOPPED -> registration allowed` precedent)
  - `STOPPED -> DEACTIVATED`
  - `DISCOVERED|VALIDATED|REGISTERED|INITIALIZED -> FAILED` (validation/registration/initialization failure);
  - `ACTIVE -> FAILED` (runtime/operational failure surfaced by lifecycle manager);
  - no transition is allowed out of `DEACTIVATED` or `FAILED` (terminal states for this Stage — re-activation of a deactivated capability is out of scope per specification without a defined re-activation rule; this is a deliberate, documented scope limitation, not an oversight).
- Deactivation from `ACTIVE` must pass through `STOPPED` first (enforced by the lifecycle manager orchestration in STAGE3-013, not by relaxing this table) — this table only allows `STOPPED -> DEACTIVATED` directly.
- `CapabilityStateTransition.isAllowed(CapabilityLifecycleState from, CapabilityLifecycleState to)` — pure function, exhaustively tested.

### Public contracts that may change

- None (internal utility).

### Acceptance criteria

- [ ] every allowed transition listed above returns `true`;
- [ ] every other pair (including self-transitions and reverse of allowed ones, except explicitly listed) returns `false`;
- [ ] no transition allowed from `DEACTIVATED` or `FAILED`.

### Required tests

- `CapabilityStateTransitionTest`: parameterized test enumerating all allowed transitions (must return true) and a representative set of disallowed transitions including skip-ahead (`DISCOVERED -> ACTIVE`), reverse (`ACTIVE -> DISCOVERED`), and terminal-state escapes (`DEACTIVATED -> *`, `FAILED -> *`).

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityStateTransitionTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

An explicit, exhaustively-tested state transition contract that the lifecycle manager (STAGE3-013) will enforce rather than re-derive.

---

## STAGE3-009 — Discovery of Capability beans (Spring composition, deterministic ordering)

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-007  
**Module:** tmp-capability-engine

### Goal

Реализовать discovery доступных Capability через явную Spring-композицию (`List<Capability>` beans), без classloading/hot-deployment/plugin marketplace, с детерминированным результатом обнаружения.

### Required documents

- `Capability-Engine-Specification.md` (Stage 3 explicit-prohibition list: no external JAR loading, no hot deployment, no plugin marketplace, no network discovery, no custom classloader, no microservice discovery).

### Required code context

- `Capability` SPI (STAGE3-006);
- `CapabilityRegistry` (STAGE3-007).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/discovery/CapabilityDiscovery.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/discovery/CapabilityDiscoveryTest.java` (new).

### Forbidden

- `ClassLoader`, `ServiceLoader` over external jars, reflection-based package scanning, file-system jar scanning, any networking.

### Implementation requirements

- `CapabilityDiscovery(List<Capability> discoveredCapabilities)` — plain constructor injection (Spring supplies the list; this class itself has no Spring annotation, keeping it framework-agnostic and unit-testable);
- `discover() -> List<Capability>` — returns capabilities sorted deterministically by `CapabilityId` (stable ordering regardless of Spring bean injection order);
- duplicate discovered `CapabilityId` across two distinct `Capability` instances is detected here and reported as a distinct, precise error (`IllegalStateException`) — this is discovery-time detection, independent of the later registry-level `reserve` duplicate check, so a duplicate is caught with capability-discovery context rather than a generic registry error.

### Public contracts that may change

- None (internal discovery component; consumed by the facade in STAGE3-014).

### Acceptance criteria

- [ ] zero capabilities discovered ⇒ empty list, no error;
- [ ] one capability discovered ⇒ singleton list;
- [ ] multiple capabilities discovered ⇒ deterministic sorted list;
- [ ] duplicate discovered id ⇒ `IllegalStateException` naming both capability ids/classes;
- [ ] repeated calls to `discover()` return an equal, deterministically-ordered result (no reliance on `HashMap`/injection-order iteration).

### Required tests

- `CapabilityDiscoveryTest`: zero; one; multiple (order-independence — feed list in two different input orders, assert same sorted output); duplicate discovered id; deterministic repeat calls.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityDiscoveryTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A minimal, framework-light discovery step producing a deterministic, duplicate-checked list of `Capability` instances for validation/registration.

---

## STAGE3-010 — Dependency graph validation (missing/self/duplicate/version/cycles) and topological order

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-003, STAGE3-009  
**Module:** tmp-capability-engine

### Goal

Реализовать проверку зависимостей между обнаруженными Capability и построение детерминированного topological order для инициализации, а также reverse order для остановки.

### Required documents

- `Capability-Engine-Specification.md` ("Зависимости" — явное объявление, запрет циклов, только публичный контракт).

### Required code context

- `DependencyDescriptor`, `DependencyValidationException` (STAGE3-003);
- `CapabilityDescriptor` (STAGE3-006);
- output of `CapabilityDiscovery.discover()` (STAGE3-009).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/validation/DependencyGraphValidator.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/validation/DependencyGraphValidatorTest.java` (new).

### Forbidden

- registration/contribution logic;
- any mutation of the capability registry.

### Implementation requirements

- `DependencyGraphValidator.validate(List<Capability> discovered) -> List<Capability>` returning capabilities in deterministic topological order (dependencies before dependents; ties broken by `CapabilityId` natural ordering for full determinism);
- checks performed, each raising `DependencyValidationException` with the matching `DependencyValidationReason`:
  - missing dependency (target id not present among discovered capabilities);
  - self-dependency (a capability depends on its own id);
  - duplicate dependency declaration (same target id listed twice in one descriptor — re-verified here defensively against the actual discovered graph, not just the descriptor in isolation);
  - incompatible version (`CapabilityVersion.isCompatibleWith` returns false against the dependency's actual declared version);
  - direct cycle (`A -> B -> A`);
  - indirect cycle (`A -> B -> C -> A`);
- "dependency only on a public contract" rule (spec: "Capability не должна зависеть от внутренней реализации другой Capability") is enforced structurally, not at runtime: `DependencyDescriptor` only ever references a `CapabilityId`, never a concrete class, so this rule is satisfied by construction and documented as such in Javadoc (no separate runtime check needed or possible without reflection, which is forbidden).

### Public contracts that may change

- None (internal validator).

### Acceptance criteria

- [ ] valid acyclic graph with satisfied dependencies produces a correct topological order;
- [ ] missing dependency detected with the correct reason and offending ids;
- [ ] self-dependency detected;
- [ ] duplicate dependency detected;
- [ ] incompatible version detected;
- [ ] direct cycle detected;
- [ ] indirect cycle detected;
- [ ] topological order is deterministic across repeated runs with the same input.

### Required tests

- `DependencyGraphValidatorTest`: valid graph (linear + diamond dependency shapes); missing dependency; self-dependency; duplicate dependency; incompatible version; direct cycle; indirect cycle; deterministic order (repeat + input-order-independence); reverse-order helper produces exact reverse of forward order.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=DependencyGraphValidatorTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A fully-tested, deterministic dependency validator and topological sorter, the foundation for correct initialization/shutdown ordering.

---

## STAGE3-011 — Internal contribution catalogs (permission, command, view, navigation, settings, event descriptor)

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-004, STAGE3-005, STAGE3-007  
**Module:** tmp-capability-engine

### Goal

Реализовать Capability-Engine-owned каталоги активных contributions (permissions, commands, views, navigation, settings, event descriptors) с owner tracking, atomic add/remove-by-owner и active-only выборкой.

### Required documents

- `Capability-Engine-Specification.md` (metadata contracts, catalog of active contributions, owner linkage, id conflict exclusion, deactivated capability exclusion; settings descriptor-only rule).

### Required code context

- `PermissionDescriptor`, `CommandDescriptor`, `ViewDescriptor`, `NavigationContribution` (STAGE3-004);
- `EventContribution`, `SettingsContribution` (STAGE3-005);
- `CapabilityId` (STAGE3-002).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/contribution/ContributionCatalog.java` (new — single generic-ish catalog abstraction reused per contribution type, OR one dedicated class per catalog if generics reduce clarity; implementer chooses the clearer of the two, documented in Javadoc);
- `tmp-capability-engine/src/main/java/com/tmp/capability/contribution/CapabilityContributionCatalogs.java` (new — aggregates the six catalogs behind one cohesive component used by the registration orchestrator);
- `tmp-capability-engine/src/test/java/com/tmp/capability/contribution/ContributionCatalogTest.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/contribution/CapabilityContributionCatalogsTest.java` (new).

### Forbidden

- Document Engine or Platform Core calls (those are external registrations, handled in STAGE3-012);
- authorization/permission-check logic.

### Implementation requirements

- Each catalog: `add(CapabilityId owner, <Descriptor> descriptor)` — rejects duplicate descriptor id across **all** owners (cross-capability id conflict), returns nothing on success, throws `IllegalStateException` on conflict;
- `removeAllForOwner(CapabilityId owner)` — atomic bulk removal used both for rollback-on-failure and for deactivation;
- `activeEntries() -> List<Descriptor>` — immutable snapshot, excludes anything removed;
- `ownerOf(String descriptorId) -> Optional<CapabilityId>` — owner tracking query;
- thread-safe (`ConcurrentHashMap`-backed), no `ConcurrentModificationException` under concurrent add/remove/read.

### Public contracts that may change

- None (internal catalogs; exposed read-only via the facade in STAGE3-014).

### Acceptance criteria

- [ ] registration of each contribution type succeeds and is retrievable;
- [ ] duplicate descriptor id across two different owners rejected;
- [ ] owner tracking correct for every entry;
- [ ] `removeAllForOwner` removes exactly and only that owner's entries, atomically;
- [ ] after removal, `activeEntries()` no longer includes the removed entries (models "deactivated capability contributions are not active").

### Required tests

- `ContributionCatalogTest`: add/retrieve; duplicate id rejected; owner tracking; removeAllForOwner removes only target owner's entries; concurrent add/read produces no `ConcurrentModificationException`.
- `CapabilityContributionCatalogsTest`: aggregate behavior across all six catalogs for one owner; bulk rollback removes entries from all six catalogs atomically.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=ContributionCatalogTest,CapabilityContributionCatalogsTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Six consistent, owner-tracked, atomically-rollback-able contribution catalogs ready to be driven by the registration orchestrator.

---

## STAGE3-012 — Atomic registration orchestrator (Document Engine + Platform Core external contributions with rollback)

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-010, STAGE3-011  
**Module:** tmp-capability-engine

### Goal

Реализовать единственную точку атомарной регистрации Capability: internal catalogs → Platform Core `CapabilityRegistry` → Document Engine document contribution → Platform Core `ServiceRegistry` → Platform Core `EventBus` subscriptions, с полным rollback при ошибке любого шага, точно в порядке, зафиксированном в "Design decisions" §6 этого файла.

### Required documents

- `Capability-Engine-Specification.md` ("Регистрация" — 11 обязательных шагов; "Активация и деактивация"; atomic contribution registration requirements from the governing task brief §6/§13);
- `com.tmp.core.api.CapabilityRegistry`, `com.tmp.core.api.ServiceRegistry`, `com.tmp.core.api.EventBus` (Platform Core public API, already verified non-partially-failing by current implementation);
- `com.tmp.document.api.DocumentEngine.registerProcessor` (Document Engine public API, already verified self-atomic by Stage 2 BLK-011/018).

### Required code context

- `CapabilityRegistry` (STAGE3-007), `CapabilityContributionCatalogs` (STAGE3-011), `CapabilityDescriptor` (STAGE3-006);
- `com.tmp.core.api.PlatformCore` (facade providing `capabilityRegistry()`, `serviceRegistry()`, `eventBus()`);
- `com.tmp.document.api.DocumentEngine`.

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/registration/CapabilityRegistrationService.java` (new — the orchestrator, internal, not `.api`);
- `tmp-capability-engine/src/main/java/com/tmp/capability/registration/CapabilityRegistrationException.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/registration/CapabilityRegistrationServiceTest.java` (new).

### Forbidden

- modifying `com.tmp.core.api.*` or `com.tmp.document.api.*` in any way;
- reflection or internal-class access into Platform Core/Document Engine;
- swallowing the original failure cause.

### Implementation requirements

- Single public entry point `register(Capability capability)` executing exactly the sequence fixed in "Design decisions" §6:
  1. reserve id in `CapabilityRegistry` (STAGE3-007) — fails fast on duplicate/concurrent registration;
  2. register internal contribution catalogs (permissions, commands, views, navigation, settings, event descriptors) via `CapabilityContributionCatalogs` — fully reversible;
  3. register basic descriptor (id/name/version) into Platform Core `capabilityRegistry()` — pre-checked uniqueness makes this call safe;
  4. if a `DocumentContribution` is present: pre-check via `documentEngine.registeredTypes()` for an existing conflicting type id, then call `documentEngine.registerProcessor(...)` — the one call with genuine (pre-validated) failure risk;
  5. if `PublicServiceContribution`s are present: register each via `platformCore.serviceRegistry().register(...)`;
  6. if `EventContribution`s are present: no separate action beyond cataloging (event **publishing** metadata only, per spec "Capability Engine не интерпретирует содержимое событий"; actual subscriptions in this Stage are created directly by capabilities through the public `EventBus` inside their own `onActivate()`/`onInitialize()` hooks, tracked by the capability itself via `EventSubscription.unsubscribe()` on `onStop()`/`onDeactivate()` — Capability Engine does not intermediate subscriptions);
  7. on success: `commit` the registration in `CapabilityRegistry`, transition state to `REGISTERED`.
- On failure at any step: unwind exactly the steps that mutated Capability-Engine-owned state (contribution catalogs, registry reservation) using `try/finally`-based compensation (mirrors `DefaultLifecycleManager.registerComponentWithRegistry` rollback style); preserve the original exception as the thrown cause, attach any compensation failure as a suppressed exception (mirrors `DefaultLifecycleManager.rollbackStartedComponents`); transition state to `FAILED`.
- Document in Javadoc, verbatim, the residual known limitation from "Design decisions" §6 (no compensation possible for `ServiceRegistry`/Document Engine calls that already succeeded before a later step fails, mitigated by ordering + pre-validation, not eliminated by contract).

### Public contracts that may change

- None (internal orchestrator; exposed only via the facade in STAGE3-014).

### Acceptance criteria

- [ ] full successful registration path: all contribution categories end up active and owner-tracked;
- [ ] failure at internal catalog step rolls back registry reservation only;
- [ ] failure at document contribution step (duplicate type pre-check) rolls back internal catalogs and registry reservation, and Document Engine has no trace of the attempted registration;
- [ ] duplicate document processor rejected with no partial state;
- [ ] failure at service contribution step rolls back internal catalogs and registry reservation, and is proven via test fixture (no false claim of removing an already-committed external registration; test proves detection and diagnostics for the ordering chosen);
- [ ] retry after a corrected failure succeeds;
- [ ] concurrent registration attempts for the same capability id: exactly one succeeds, others fail with a precise duplicate error, no partial state.

### Required tests

- `CapabilityRegistrationServiceTest`: happy path (all contribution types); failure at each internal step (catalog conflict); failure at document contribution (pre-existing duplicate type); duplicate document processor rejected; failure at service contribution (duplicate service marker via test fixture); repeated registration after corrected failure succeeds; concurrent registration of the same capability id (deterministic multi-thread test, exactly one winner); original exception preserved and inspectable after failure; registry/catalog state fully consistent (no stale entries) after every failure scenario above.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityRegistrationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A single, fully-tested atomic registration entry point satisfying the specification's "fully registered or fully absent" invariant using only existing Stage 1/2 public APIs.

---

## STAGE3-013 — Lifecycle manager: initialization order, activation/deactivation, dependents check, reverse shutdown

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-008, STAGE3-010, STAGE3-012  
**Module:** tmp-capability-engine

### Goal

Реализовать управление жизненным циклом зарегистрированных Capability: инициализация в порядке зависимостей, активация только после успешной регистрации+инициализации, деактивация с проверкой активных dependents, остановка в обратном порядке.

### Required documents

- `Capability-Engine-Specification.md` ("Активация и деактивация"; initialization-order and failure-isolation requirements from the governing task brief §10-12);
- `DefaultLifecycleManager` (Stage 1, read for structural precedent — reverse-order stop, rollback-on-failure style, not reused directly since it operates on `PlatformComponent`, not `Capability`).

### Required code context

- `CapabilityStateTransition` (STAGE3-008);
- `DependencyGraphValidator` topological/reverse order (STAGE3-010);
- `CapabilityRegistrationService` (STAGE3-012);
- `Capability` SPI hooks `onInitialize/onActivate/onDeactivate/onStop` (STAGE3-006).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/lifecycle/CapabilityLifecycleManager.java` (new);
- `tmp-capability-engine/src/test/java/com/tmp/capability/lifecycle/CapabilityLifecycleManagerTest.java` (new).

### Forbidden

- automatic cascading deactivation of dependents (explicitly forbidden unless required by the specification — it is not);
- deletion of business data/documents (not applicable — this module owns no business data).

### Implementation requirements

- `initializeAll()`: iterates capabilities in topological order (STAGE3-010); for each, calls `onInitialize()`, transitions `REGISTERED -> INITIALIZED`; on failure, transitions the failing capability to `FAILED`, does **not** roll back already-initialized independent capabilities (failure isolation), and skips (never initializes/activates) any capability that transitively depends on the failed one, recording those as `FAILED` too with a distinct "dependency failed" cause chained to the original;
- `activateAll()`: for each `INITIALIZED` capability in topological order, calls `onActivate()`, transitions to `ACTIVE`; repeated activation of an already-`ACTIVE` capability is rejected (`IllegalStateException`, per "повторная недопустимая активация отклоняется");
- `deactivate(CapabilityId id)`: rejects if any other **active** capability depends on `id` (queries the dependency graph for active dependents, throws `IllegalStateException` naming the blocking dependents, per "деактивация проверяет активные dependents"); on success, calls `onStop()` then `onDeactivate()`, removes the capability's contributions from all catalogs (`CapabilityContributionCatalogs.removeAllForOwner`, STAGE3-011), transitions `ACTIVE -> STOPPED -> DEACTIVATED`;
- `stopAll()`: stops all `ACTIVE` capabilities in **reverse** topological order, calling `onStop()`, transitioning to `STOPPED` (used for platform shutdown, distinct from single-capability `deactivate`, which also proceeds to `DEACTIVATED`);
- every transition uses `CapabilityStateTransition.isAllowed` as a guard before mutating state (defensive, matches STAGE3-008 contract instead of re-deriving rules).

### Public contracts that may change

- None (internal lifecycle manager; exposed via the facade in STAGE3-014).

### Acceptance criteria

- [ ] initialization order matches topological dependency order;
- [ ] a capability activates only after successful registration and initialization;
- [ ] repeated activation of an already-active capability is rejected;
- [ ] deactivation of a capability with an active dependent is rejected, naming the dependent;
- [ ] deactivation removes the capability's commands/views/navigation/permissions from active catalogs;
- [ ] deactivation does not cascade to dependents automatically;
- [ ] `stopAll()` stops in exact reverse order of the initialization order;
- [ ] a capability whose dependency failed never reaches `ACTIVE`.

### Required tests

- `CapabilityLifecycleManagerTest`: successful discovery→validation→registration→initialization→activation chain (using STAGE3-009/010/012 collaborators, or light fakes matching their contracts); invalid transition attempts rejected; repeated activation rejected; normal stop; normal deactivation; deactivation with active dependents rejected; failed initialization isolates only the failed capability and its dependents; failed activation isolates only the failed capability and its dependents; independent capability remains valid/active after another capability fails; reverse shutdown order verified against a 3+ capability dependency chain.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityLifecycleManagerTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A fully-tested lifecycle manager enforcing correct ordering, activation guards, dependent-aware deactivation, and failure isolation exactly as specified.

---

## STAGE3-014 — CapabilityEngine public facade and status snapshot

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-009, STAGE3-013  
**Module:** tmp-capability-engine

### Goal

Определить и реализовать стабильный публичный API `com.tmp.capability.api.CapabilityEngine`, объединяющий discovery/dependency-validation/registration/lifecycle в единую точку входа, плюс read-only каталог для формирования рабочих мест (permissions/commands/views/navigation активных Capability) и `CapabilityEngineStatus` для технической UI-видимости.

### Required documents

- `Capability-Engine-Specification.md` ("Рабочие места" — read-only данные для формирования рабочих мест без принятия решений о доступе);
- `Platform-Core-Specification.md` (AR-005 — новая Capability подключается без изменения Platform Core, informs that this facade must be self-sufficient).

### Required code context

- `CapabilityDiscovery` (STAGE3-009);
- `DependencyGraphValidator` (STAGE3-010);
- `CapabilityRegistrationService` (STAGE3-012);
- `CapabilityLifecycleManager` (STAGE3-013);
- `CapabilityContributionCatalogs` (STAGE3-011);
- `CapabilityRegistry` (STAGE3-007).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityEngine.java` (new — interface);
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityEngineStatus.java` (new — record);
- `tmp-capability-engine/src/main/java/com/tmp/capability/DefaultCapabilityEngine.java` (new — implementation, internal package `com.tmp.capability`, not `.api`);
- `tmp-capability-engine/src/test/java/com/tmp/capability/DefaultCapabilityEngineTest.java` (new).

### Forbidden

- any access-decision logic ("не принимать решения о доступе конкретного пользователя");
- exposing internal collaborator types (`CapabilityRegistrationService`, etc.) through the interface — only `.api` types in method signatures.

### Implementation requirements

- `CapabilityEngine` interface: `void discoverAndRegisterAll(); void activateAll(); void deactivate(CapabilityId id); void stopAll(); Optional<CapabilityDescriptor> findById(CapabilityId id); List<CapabilityDescriptor> registeredCapabilities(); CapabilityLifecycleState stateOf(CapabilityId id); List<PermissionDescriptor> activePermissions(); List<CommandDescriptor> activeCommands(); List<ViewDescriptor> activeViews(); List<NavigationContribution> activeNavigation(); CapabilityEngineStatus status();`
- `CapabilityEngineStatus(int discoveredCount, int registeredCount, int activeCount, int failedCount)` — matches the minimal technical UI visibility requirement (counts + per-capability listing via `registeredCapabilities()`/`stateOf`);
- `DefaultCapabilityEngine` wires the four internal collaborators; `discoverAndRegisterAll()` = discover → validate dependencies (topological order) → register each in order (STAGE3-012) → initialize each in order (STAGE3-013); `activateAll()` delegates to the lifecycle manager.

### Public contracts that may change

- New public types only (`CapabilityEngine`, `CapabilityEngineStatus`); no change to any Stage 1/2 contract.

### Acceptance criteria

- [ ] facade correctly sequences discovery → validation → registration → initialization → activation for a multi-capability dependency graph;
- [ ] `activePermissions()`/`activeCommands()`/`activeViews()`/`activeNavigation()` reflect only currently-active capabilities' contributions;
- [ ] `status()` counts match actual registry/catalog state at call time;
- [ ] no method on the interface exposes an internal (non-`.api`) type.

### Required tests

- `DefaultCapabilityEngineTest`: end-to-end discover→register→initialize→activate with 2+ capabilities (one depending on the other); active-only queries exclude deactivated capability's contributions; status counts correct at each lifecycle stage; deactivate then re-query catalogs shows contributions removed.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=DefaultCapabilityEngineTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A stable, minimal public `CapabilityEngine` API — the only type external modules and the bootstrap layer are allowed to depend on for capability orchestration.

---

## STAGE3-015 — Spring Boot auto-configuration and Platform Core component registration

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-014  
**Module:** tmp-capability-engine

### Goal

Подключить Capability Engine к Spring Boot через auto-configuration и зарегистрировать его как единственный `PlatformComponent` в Platform Core, повторяя проверенный паттерн `DocumentEngineAutoConfiguration`/`DocumentEnginePlatformRegistrar`.

### Required documents

- `Platform-Core-Specification.md` ("Компоненты платформы" — единый контракт `PlatformComponent`);
- Stage 2 precedent: `DocumentEngineAutoConfiguration.java`, `DocumentEnginePlatformRegistrar.java` (structural template).

### Required code context

- `CapabilityEngine`, `DefaultCapabilityEngine` (STAGE3-014);
- `com.tmp.core.api.component.PlatformComponent`, `PlatformComponentMetadata`, `ComponentType` (Stage 1 — note: the Capability Engine infrastructural component registers with `ComponentType.SERVICE` since it is infrastructure, not a business capability instance; `ComponentType.CAPABILITY` remains unused by this Stage, documented in Javadoc to avoid ambiguity for later stages).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/CapabilityEngineAutoConfiguration.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/CapabilityEnginePlatformComponent.java` (new — adapts `DefaultCapabilityEngine` to `PlatformComponent`: `initialize()` calls `discoverAndRegisterAll()`, `start()` calls `activateAll()`, `stop()` calls `stopAll()`);
- `tmp-capability-engine/src/test/java/com/tmp/capability/CapabilityEngineAutoConfigurationTest.java` (new).

### Forbidden

- registering more than one `CapabilityEngine`/`PlatformComponent` bean;
- any dependency on `tmp-ui-shell` or `tmp-bootstrap-app` from this module.

### Implementation requirements

- `@AutoConfiguration` class exposes exactly one `CapabilityEngine` bean (`DefaultCapabilityEngine`) built from an injected `List<Capability>` (Spring-discovered beans) plus `PlatformCore` and `DocumentEngine` beans;
- `CapabilityEnginePlatformComponent` is registered into `PlatformCore` via `@PostConstruct`, mirroring `DocumentEnginePlatformRegistrar` exactly (same pattern, adapted class names);
- component metadata id fixed as a constant, e.g. `"capability-engine"`.

### Public contracts that may change

- None.

### Acceptance criteria

- [ ] Spring context (module-level slice test or `ApplicationContextRunner`) contains exactly one `CapabilityEngine` bean;
- [ ] the component is registered into `PlatformCore.platformRegistry()` after context startup;
- [ ] `initialize()`/`start()`/`stop()` on the adapter correctly delegate to `discoverAndRegisterAll()`/`activateAll()`/`stopAll()`.

### Required tests

- `CapabilityEngineAutoConfigurationTest`: exactly one `CapabilityEngine` bean present; component registered in `PlatformCore`; adapter lifecycle delegation verified with a test `Capability` bean.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityEngineAutoConfigurationTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Capability Engine is a properly auto-configured, singly-registered Platform Core component, ready for bootstrap wiring.

---

## STAGE3-016 — Sample technical Capability (end-to-end fixture, no business logic)

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-015  
**Module:** tmp-capability-engine

### Goal

Реализовать sample technical Capability, покрывающую discovery, dependencies (двух Capability, одна зависит от другой), lifecycle и все обязательные contribution types (включая один document contribution через публичный API Document Engine), без бизнес-логики и без предметных сущностей Warehouse/Production/Order/Cutting.

### Required documents

- `Capability-Engine-Specification.md` (explicit sample-capability constraints from the governing task brief §26).

### Required code context

- `Capability` SPI, `CapabilityDescriptor` builder (STAGE3-006);
- `DocumentProcessor` (Document Engine public API);
- `CapabilityEngineAutoConfiguration` (STAGE3-015).

### Allowed code scope

- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalCapability.java` (new);
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleDependentTechnicalCapability.java` (new — declares a dependency on `SampleTechnicalCapability`);
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalDocumentProcessor.java` (new — trivial processor, no business rules);
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalService.java` (new — trivial public service interface + implementation used to prove cross-capability service resolution);
- `tmp-capability-engine/src/test/java/com/tmp/capability/sample/SampleTechnicalCapabilityIntegrationTest.java` (new).

### Forbidden

- any business/domain naming or behavior resembling Warehouse/Production/Order Management/Cutting Optimization;
- registration of these sample beans outside test/sample scope reaching production `tmp-bootstrap-app` navigation as if it were a real module (bootstrap wiring in STAGE3-017 explicitly labels this as a technical diagnostic capability, not a workplace module).

### Implementation requirements

- `SampleTechnicalCapability`: no dependencies; contributes one permission, one command, one view, one navigation entry, one setting, one event descriptor, one document contribution (`SampleTechnicalDocumentProcessor`, type id e.g. `"sample.technical.document"`), one public service contribution (`SampleTechnicalService`);
- `SampleDependentTechnicalCapability`: declares a `DependencyDescriptor` on `SampleTechnicalCapability`'s id with a compatible minimum version; resolves `SampleTechnicalService` through the Platform Core `ServiceRegistry` (public lookup only, never a direct reference to the other capability's internals) inside `onActivate()`, proving cross-capability public-API-only interaction (ADR-003);
- `SampleTechnicalDocumentProcessor`: minimal no-op `DocumentProcessor` implementation (validate/onPost/onUnpost/onClose/onDelete do nothing beyond returning), clearly Javadoc'd as a technical fixture.

### Public contracts that may change

- None (these are internal sample implementations of existing `.api` contracts).

### Acceptance criteria

- [x] both sample capabilities discover, validate, register, initialize and activate successfully together;
- [x] the dependent capability activates strictly after its dependency;
- [x] the dependent capability successfully resolves the public service exposed by its dependency exclusively through Platform Core's `ServiceRegistry`;
- [x] the document type contributed by the sample capability is registered and creatable through `DocumentEngine.createDocument` using only the Document Engine public API.

### Required tests

- `SampleTechnicalCapabilityIntegrationTest`: full discover→register→initialize→activate for both sample capabilities; dependency order assertion; service resolution assertion; document creation through `DocumentEngine` for the contributed type; deactivation of the independent capability rejected while the dependent is active; deactivation succeeds after the dependent is stopped/deactivated first.

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=SampleTechnicalCapabilityIntegrationTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A complete, non-business, end-to-end proof that a new Capability can be discovered, validated, registered, initialized and activated using only public APIs, satisfying the Stage 3 Manifest exit criterion in an automated test.

---

## STAGE3-017 — Bootstrap integration and minimal technical capability status UI

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-016  
**Module:** tmp-bootstrap-app, tmp-ui-shell

### Goal

Подключить `tmp-capability-engine` к `tmp-bootstrap-app` и добавить минимальный технический статус (discovered/active count, список id и state sample Capability) в существующую техническую панель, без создания рабочих мест бизнес-модулей.

### Required documents

- `UI-UX-Specification.md` ("Навигация" — навигация строится автоматически на основании зарегистрированных Capability, но полная навигация — вне Stage 3 scope);
- Stage 1/2 precedent: `DesktopBootstrap.java` (`formatDocumentPanel` pattern), `JavaFxShellLauncher`/`EmptyMainShell` status label wiring.

### Required code context

- `DesktopBootstrap.java`, `EmptyMainShell`/`JavaFxShellLauncher` (existing, from `tmp-ui-shell`);
- `com.tmp.capability.api.CapabilityEngine`, `CapabilityEngineStatus` (STAGE3-014).

### Allowed code scope

- `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/DesktopBootstrap.java` (modify — add capability status formatting, additive only, do not remove existing document panel logic);
- `tmp-bootstrap-app/pom.xml` (add `tmp-capability-engine` dependency);
- `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/CapabilityEngineBeanLookupTest.java` (new, mirrors `DocumentEngineBeanLookupTest`).

### Forbidden

- business workplace screens;
- ui-shell dependency on `tmp-capability-engine` or `tmp-core`/`tmp-document` internals (architecture rule from Stage 1/2 preserved — only bootstrap bridges plain strings to the shell, exactly like the existing document panel).

### Implementation requirements

- `DesktopBootstrap.main()` fetches the `CapabilityEngine` bean and appends a formatted technical status block (discovered count, active count, and the sample capabilities' ids + states) to what is passed into `JavaFxShellLauncher.launch(...)`, following the exact `formatDocumentPanel` precedent (a static formatting method, no business logic, no FXML changes required since the shell already renders arbitrary status text);
- exactly one `CapabilityEngine` bean resolvable from the Spring context (verified by the new test, mirroring `DocumentEngineBeanLookupTest`).

### Public contracts that may change

- None.

### Acceptance criteria

- [x] `tmp-bootstrap-app` compiles and starts with `tmp-capability-engine` on the classpath;
- [x] exactly one `CapabilityEngine` bean is resolvable;
- [x] the technical status text includes discovered/active counts and the sample capability ids/states;
- [x] `tmp-ui-shell` has zero new dependency on Capability Engine types (architecture rule preserved, verified in STAGE3-018).

### Required tests

- `CapabilityEngineBeanLookupTest`: single bean lookup; `DesktopBootstrap` formatting helper unit-testable in isolation (extracted static method, matching `formatDocumentPanel` test-ability precedent).

### Verification commands

```bash
mvn -q -pl :tmp-bootstrap-app test -Dtest=CapabilityEngineBeanLookupTest,SpringContextSmokeTest,DocumentEngineBeanLookupTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

The packaged desktop application exposes Capability Engine's technical status alongside the existing Platform Core/Document Engine status, with no UI-layer coupling violations.

---

## STAGE3-018 — Stage 3 architecture tests

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-017  
**Module:** tmp-architecture-tests

### Goal

Зафиксировать архитектурные границы Capability Engine в ArchUnit-тестах, аналогично `Stage1PlatformCoreArchitectureTest`/`Stage2DocumentEngineArchitectureTest`, без ослабления существующих правил.

### Required documents

- Governing task brief §7 "Architecture" required-test list;
- `Capability-Engine-Specification.md` ("Что не входит в Capability Engine"; "Ограничения").

### Required code context

- `Stage1PlatformCoreArchitectureTest.java`, `Stage2DocumentEngineArchitectureTest.java` (structural precedent, exact style to follow).

### Allowed code scope

- `tmp-architecture-tests/src/test/java/com/tmp/architecture/Stage3CapabilityEngineArchitectureTest.java` (new);
- `tmp-architecture-tests/pom.xml` (add `tmp-capability-engine` dependency).

### Forbidden

- modifying any existing Stage 0/1/2 architecture test rule.

### Implementation requirements

Rules (ArchUnit, `noClasses()...should()...because(...)` style):

- `capabilityEngineDoesNotDependOnBusinessModules` — no class in `com.tmp.capability..` depends on `com.tmp.order..`, `com.tmp.warehouse..`, `com.tmp.production..`, `com.tmp.cutting..`, `com.tmp.analytics..`, `com.tmp.security..`;
- `capabilityEngineUsesOnlyCorePublicApi` — no class in `com.tmp.capability..` depends on `com.tmp.core..` outside `com.tmp.core.api..`;
- `capabilityEngineUsesOnlyDocumentPublicApi` — no class in `com.tmp.capability..` depends on `com.tmp.document..` outside `com.tmp.document.api..`;
- `externalModulesUseOnlyCapabilityPublicApi` — no class outside `com.tmp.capability..` depends on `com.tmp.capability..` outside `com.tmp.capability.api..`;
- `platformCoreDoesNotDependOnCapabilityEngine` — no class in `com.tmp.core..` depends on `com.tmp.capability..`;
- `documentEngineDoesNotDependOnCapabilityEngine` — no class in `com.tmp.document..` depends on `com.tmp.capability..`;
- `uiShellDoesNotDependOnCapabilityEngine` — no class in `com.tmp.ui..` depends on `com.tmp.capability..`;
- `sampleCapabilityUsesOnlyPublicApis` — no class in `com.tmp.capability.sample..` depends on `com.tmp.core..` outside `com.tmp.core.api..`, nor on `com.tmp.document..` outside `com.tmp.document.api..`.

### Public contracts that may change

- None.

### Acceptance criteria

- [x] all eight rules pass against the current codebase;
- [x] `mvn -q verify -DskipITs` runs Stage 0-3 architecture tests together with no failures;
- [x] no existing Stage 0/1/2 architecture rule is weakened or removed.

### Required tests

- `Stage3CapabilityEngineArchitectureTest` (the eight rules above).

### Verification commands

```bash
mvn -q -pl :tmp-architecture-tests test -Dtest=Stage3CapabilityEngineArchitectureTest,Stage0ArchitectureBaselineTest,Stage1PlatformCoreArchitectureTest,Stage2DocumentEngineArchitectureTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Automated, permanent enforcement of every module-boundary rule mandated for Stage 3.

---

## STAGE3-019 — PostgreSQL Testcontainers integration test for document contribution

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-016  
**Module:** tmp-capability-engine

### Goal

Подтвердить реальную транзакционную семантику регистрации document contribution через Document Engine на PostgreSQL (Testcontainers), а не только на H2/fake-адаптерах, используемых в unit-тестах STAGE3-012/016.

### Required documents

- Governing task brief §30 (PostgreSQL Testcontainers only where document contribution + real transactional check is required);
- Stage 2 precedent: `DocumentEnginePostgresIntegrationIT` (structural template for Testcontainers setup in this codebase).

### Required code context

- `DocumentEnginePostgresIntegrationIT.java` (Testcontainers bootstrap pattern);
- `SampleTechnicalCapability`/`SampleTechnicalDocumentProcessor` (STAGE3-016);
- `CapabilityRegistrationService` (STAGE3-012).

### Allowed code scope

- `tmp-capability-engine/src/test/java/com/tmp/capability/CapabilityEngineDocumentPostgresIntegrationIT.java` (new).

### Forbidden

- removing or weakening the existing H2/fake-based unit tests;
- introducing a new Testcontainers image/version not already used by `tmp-document-engine`/`tmp-infra-db`.

### Implementation requirements

- Spin up the same PostgreSQL Testcontainers configuration already used by `tmp-document-engine`'s ITs (reuse Flyway migrations, real `DocumentEngine`/JDBC adapters, real `DataSource`);
- register `SampleTechnicalCapability` through the full `CapabilityEngine`/`CapabilityRegistrationService` path against this real `DocumentEngine`;
- verify: document type persisted in `documents.document_types`; a document of the contributed type can be created, posted, and queried; a deliberate registration failure (duplicate document type pre-existing in the DB) leaves neither a DB row nor an active Capability Engine registration (full-stack rollback proof, not just in-memory).

### Public contracts that may change

- None.

### Acceptance criteria

- [x] real PostgreSQL-backed document type registration succeeds through the full Capability Engine path;
- [x] document lifecycle operations succeed against the real database for the contributed type;
- [x] duplicate-type registration failure leaves no DB row and no Capability Engine registration (verified via a fresh query, not assumption).

### Required tests

- `CapabilityEngineDocumentPostgresIntegrationIT` (the three scenarios above).

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityEngineDocumentPostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Verified, real-database proof of atomic document contribution registration and rollback, closing the one required Testcontainers scenario for this Stage.

---

## STAGE3-020 — Concurrency tests (registration race, activation/deactivation race, snapshot safety)

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-013  
**Module:** tmp-capability-engine

### Goal

Покрыть обязательные concurrency-сценарии Stage 3, не покрытые точечно в STAGE3-007/012/013 (тем задачам разрешалось включать по одному concurrency-тесту как минимум; здесь собирается недостающая, более сложная комбинация: activation-vs-deactivation race и стабильность read-only snapshot во время lifecycle-изменений).

### Required documents

- Governing task brief §7 "Concurrency" required-test list.

### Required code context

- `CapabilityLifecycleManager` (STAGE3-013);
- `CapabilityRegistry` (STAGE3-007);
- `DefaultPlatformCoreRegistrationTest` (Stage 1, precedent for deterministic concurrency test structure using fixed thread counts and `CountDownLatch`/`ExecutorService`).

### Allowed code scope

- `tmp-capability-engine/src/test/java/com/tmp/capability/lifecycle/CapabilityLifecycleConcurrencyTest.java` (new).

### Forbidden

- flaky/non-deterministic sleep-based synchronization (must use latches/barriers, matching Stage 1 precedent);
- weakening any existing lock/synchronization in production code to make a test pass.

### Implementation requirements

- Test 1: N threads attempt to register the same capability id concurrently — exactly one succeeds, others fail deterministically, no partial state afterward;
- Test 2: one thread calls `activateAll()`/`activate` while another concurrently calls `deactivate` on the same capability — the outcome is deterministic given the lifecycle manager's single synchronization boundary (no `ACTIVE` capability with incomplete contributions is ever observable, and no `ConcurrentModificationException` is thrown);
- Test 3: a reader thread repeatedly calls `registeredCapabilities()`/`activeCommands()` (or equivalent catalog snapshot methods) while a writer thread registers/deactivates capabilities — reader never observes a `ConcurrentModificationException` and never observes a torn/partial snapshot (each snapshot is a valid state that existed at some point in time).

### Public contracts that may change

- None.

### Acceptance criteria

- [x] duplicate concurrent registration: exactly one winner, deterministic;
- [x] activation-vs-deactivation race: no exception leaks a partial state, deterministic final state per run;
- [x] read-only snapshot reads during concurrent lifecycle changes never throw `ConcurrentModificationException`;
- [x] no `ACTIVE` capability is ever observed with contributions missing from its declared descriptor.

### Required tests

- `CapabilityLifecycleConcurrencyTest` (the three scenarios above, each run with a fixed, repeatable thread/iteration count).

### Verification commands

```bash
mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityLifecycleConcurrencyTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Deterministic proof that Capability Engine's shared state survives concurrent registration and lifecycle operations without corruption.

---

## STAGE3-021 — Final Stage 3 verification gate

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-001..STAGE3-020  
**Module:** cross-stage

### Goal

Выполнить финальную комплексную верификацию Stage 3 против exit criteria `STAGE-3-CAPABILITY-ENGINE.md`, включая package profile и ручной запуск `TMP.exe`, без перехода к Stage 4.

### Required documents

- `STAGE-3-CAPABILITY-ENGINE.md` (exit criteria: "A Capability can be registered and initialized without modifying Platform Core");
- `RUN-DEVELOPMENT.md`.

### Required code context

- full reactor; packaged application.

### Allowed code scope

- development-control documentation only, unless a last-minute defect fix is required within Stage 3 scope (in which case it is logged as a residual defect fix, not a new feature).

### Forbidden

- Stage 4 — Security features;
- any new Capability Engine feature not already covered by STAGE3-001..020.

### Implementation requirements

- `mvn clean verify` (full reactor, all stages);
- `mvn clean verify -Ppackage` (jpackage app-image build);
- manual run of `dist/jpackage/TMP/TMP.exe` confirming: Spring context starts; PostgreSQL/Flyway operational; Platform Core `STARTED`; Document Engine available; Capability Engine registered and started; sample technical Capability discovered and `ACTIVE`; technical UI shows current capability status; application closes cleanly; capability lifecycle stops in reverse order; no hidden startup/shutdown errors in logs.

### Public contracts that may change

- None.

### Acceptance criteria

- [x] `mvn clean verify` PASSED for the full reactor (Stage 0-3 tests all green);
- [x] `mvn clean verify -Ppackage` PASSED;
- [x] all Stage 3 exit criteria in `STAGE-3-CAPABILITY-ENGINE.md` confirmed;
- [x] manual `TMP.exe` run confirms every item listed above;
- [x] Stage 3 not declared complete solely because code compiles and one happy-path test passes.

### Required tests

- full stage verification suite (all STAGE3-* automated tests + architecture tests + Testcontainers ITs).

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
```

Manual: `dist/jpackage/TMP/TMP.exe`

### Documentation updates

- STATUS; WORK-QUEUE; IMPLEMENTATION-LOG; VERIFICATION-LOG; BLOCKERS (if any residual defect surfaces).

### Expected result

Stage 3 fully verified end-to-end; `STATUS.md` updated to `Stage 3: DONE 100%`; explicit stop before Stage 4 per user instruction.
