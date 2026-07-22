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
