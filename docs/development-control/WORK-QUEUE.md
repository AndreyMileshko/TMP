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

---

## STAGE3-022 — Stage 3 acceptance rework (BLK-014)

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-021  
**Module:** `tmp-platform-core`, `tmp-document-engine`, `tmp-capability-engine`

### Goal

Устранить блокирующие дефекты acceptance review: полностью атомарная регистрация Capability с compensation handles, корректная деактивация и lifecycle failure handling, reversible public API Stage 1–2.

### Required documents

- `STAGE-3-CAPABILITY-ENGINE.md`;
- `BLOCKERS.md` (`BLK-014`).

### Implementation requirements

- Owner-aware reversible registrations (`ServiceRegistration`, `CapabilityRegistry.unregister`, `DocumentProcessorRegistration`);
- Atomic registration with reverse-order compensation; original exception preserved; compensation failures suppressed;
- Deactivation removes commands/views/navigation/services/processor ops/events/settings; preserves existing documents;
- Lifecycle: STOPPED/DEACTIVATED only after successful callbacks; FAILED on error; `stopAll` continues with suppressed errors;
- Capability Engine tracks event subscription handles;
- Mandatory acceptance tests per user acceptance review list.

### Acceptance criteria

- [x] No test documents partial external state as acceptable;
- [x] Registration failure at each contribution step rolls back all internal and external state;
- [x] Retry after rollback succeeds;
- [x] Deactivation acceptance tests pass;
- [x] Lifecycle failure acceptance tests pass;
- [x] PostgreSQL IT covers document contribution lifecycle on deactivation;
- [x] `BLK-014` registered and resolved.

### Required tests

- `CapabilityRegistrationServiceTest` (extended);
- `CapabilityDeactivationAcceptanceTest`;
- `CapabilityLifecycleFailureAcceptanceTest`;
- `DefaultServiceRegistryTest`, `DefaultCapabilityRegistryTest`, `DefaultDocumentProcessorRegistryTest`, `DefaultDocumentEngineRegistrationTest` (extended);
- `CapabilityEngineDocumentPostgresIntegrationIT` (deactivation scenario).

### Expected result

Stage 3 returned to IN_PROGRESS during rework; ready for `STAGE3-023` full re-verification.

---

## STAGE3-023 — Re-verification gate after acceptance rework

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-022  
**Module:** cross-stage

### Goal

Повторная полная верификация Stage 3 после устранения `BLK-014`, без перехода к Stage 4.

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
```

Manual: `dist/jpackage/TMP/TMP.exe`

### Acceptance criteria

- [x] `mvn clean verify` PASSED (full reactor);
- [x] `mvn clean verify -Ppackage` PASSED;
- [x] manual `TMP.exe` run PASSED (launched with `TMP_DB_*` + Docker PostgreSQL; exit 0);
- [x] Stage 3 re-closed at 100%; stop before Stage 4.

### Expected result

`STATUS.md` → Stage 3 DONE 100%; `STAGE3-023` DONE.

---

## STAGE3-024 — Lifecycle contribution cleanup (BLK-015)

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-023  
**Module:** `tmp-capability-engine`

### Goal

Устранить residual lifecycle cleanup defect: при initialize/activate/stop/deactivation failure снимать все contributions; `DEACTIVATED` только после успешной полной cleanup; не терять исходные exceptions; агрегировать unsubscribe/cleanup failures как suppressed.

### Implementation requirements

- Единый `cleanupFailedCapability` / `cleanupContributions` для initialize, activation, stop, deactivation failures;
- Cleanup: event subscriptions, internal catalogs, public services, document processors, Platform Core capability metadata;
- Continue after cleanup step failures; preserve original lifecycle exception; attach cleanup failures as suppressed;
- `DEACTIVATED` only after successful callbacks and successful full cleanup; cleanup failure → `FAILED`;
- `unsubscribeAll` must not silently swallow errors.

### Acceptance criteria

- [x] initialize failure removes public service, Document Processor, Platform Core metadata, internal catalogs;
- [x] independent Capability still becomes ACTIVE;
- [x] activation failure preserves original exception; cleanup errors suppressed; remaining cleanup steps execute;
- [x] deactivation cleanup failure → FAILED (not DEACTIVATED); no stale service/subscription;
- [x] unsubscribe failure is observable;
- [x] stopAll continues reverse shutdown after cleanup failure;
- [x] PostgreSQL IT: failed init after document registration; new ops rejected; existing data preserved;
- [x] `BLK-015` RESOLVED.

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
```

Manual: `dist/jpackage/TMP/TMP.exe`

### Expected result

Stage 3 re-verified; stop before Stage 4.

---

## STAGE3-025 — Re-verification gate after BLK-015

**Status:** DONE  
**Stage:** 3  
**Depends on:** STAGE3-024  
**Module:** cross-stage

### Goal

Повторная полная верификация Stage 3 после устранения `BLK-015`, без перехода к Stage 4.

### Acceptance criteria

- [x] `mvn clean verify` PASSED (full reactor);
- [x] `mvn clean verify -Ppackage` PASSED;
- [x] manual `TMP.exe` run PASSED (alive with `TMP_DB_*` + Docker PostgreSQL);
- [x] Stage 3 re-closed at 100%; stop before Stage 4.

### Expected result

`STATUS.md` → Stage 3 DONE 100%; stop before Stage 4.

---

# Stage 4 — Security (decomposition)

## Design decisions fixed for this Stage

1. Module: `tmp-security` (new). Public API package: `com.tmp.security.api` (mirrors `com.tmp.capability.api` / `com.tmp.document.api`). Internal packages: `com.tmp.security.domain` (+ `com.tmp.security.domain.repository` ports), `com.tmp.security.application`, `com.tmp.security.persistence`, `com.tmp.security.capability`, `com.tmp.security` (auto-configuration + `PlatformComponent`). Identity/value-object types that other modules must reference at call sites (`UserId`, `RoleId`, `PermissionId`, `AuditEventId`, `SessionId`, `Login`, `DisplayName`) are defined directly in `com.tmp.security.api`, matching the precedent of `com.tmp.capability.api.CapabilityId`. Richer mutable-behaviour aggregates (`User`, `Role`, `PermissionDefinition`, `RoleAssignment`, `IndividualPermissionOverride`, `SecurityAuditEvent`) stay in `com.tmp.security.domain`, exposed to callers only through DTOs/services in `com.tmp.security.api`.
2. Persistence technology follows the Stage 2 precedent, **not** the Database Specification §13 JPA/Hibernate section literally: `tmp-document-engine` already uses plain `spring-boot-starter-jdbc` (`JdbcTemplate`) with hand-written Repository ports/adapters/mappers, no Hibernate anywhere in the reactor. `tmp-security` follows the same established pattern for consistency (Repository interface in Domain, `Jdbc*Repository` implementation in `com.tmp.security.persistence`, manual row-mapping, no JPA entities). This is a normal technical decision following existing in-repo precedent, not a deviation requiring a blocker.
3. Flyway: single global `classpath:db/migration` scan (existing `tmp-infra-db` configuration, unchanged) is shared by all modules; each module ships its own numbered scripts. Existing highest version is `V3` (`tmp-document-engine`); `tmp-capability-engine` has no persistence. Security's migration starts at `V4__security_schema.sql` in `tmp-security/src/main/resources/db/migration/`.
4. `PermissionId` format: `<area>.<resource>.<action>` (3 dot-separated lowercase segments, `[a-z][a-z0-9-]*` per segment), exactly as stated in the Security Specification's format rule and in the Stage 4 task's permission catalogue (`security.users.view`, `security.roles.assign`, `security.permissions.assign`, `security.audit.view`, etc.). The Security Specification's illustrative examples (`order.view`, `warehouse.issue` — 2 segments) are informal shorthand from an earlier doc revision and do not override the explicit 3-segment rule stated in the same document and repeated in the Stage 4 task; validation implements the 3-segment rule. Not a blocker: the authoritative rule text is consistent across both sources, only the illustrative examples are imprecise.
5. Capability Engine integration: Security registers exactly one Spring bean implementing `com.tmp.capability.api.Capability` ("Security Administration Capability"), discovered the same way `SampleTechnicalCapability` is (constructor injection into `List<Capability>` in `CapabilityDiscovery`). Its `onInitialize/onActivate/onDeactivate/onStop` hooks are no-ops (it contributes only `PermissionDescriptor`/`CommandDescriptor`/`NavigationContribution`/`ViewDescriptor` metadata — no `PublicServiceContribution`, no `DocumentContribution` — Security's own services are consumed directly as ordinary Spring beans by `tmp-ui-shell`/`tmp-bootstrap-app`, exactly like `PlatformCore`/`DocumentEngine`/`CapabilityEngine` are today, **not** through the Capability Engine's public-service mechanism, because Security is itself a platform-level component, not a business Capability providing services to other Capabilities).
6. Permission-definition catalogue and "active" status: Security never reads Capability Engine internals. It uses only `CapabilityEngine.registeredCapabilities()` (full declared catalogue, any lifecycle state) + `CapabilityDescriptor.permissions()` to learn every declared `PermissionDescriptor`, and `CapabilityEngine.activePermissions()` (or `stateOf(id) == ACTIVE`) to know which are currently active. `Authorization.requirePermission()` denies whenever the given `PermissionId` is absent from `CapabilityEngine.activePermissions()`, regardless of role/individual grants (Stage 4 task §7/§11). No Capability Engine API change is needed for this; if a real gap is later found, a blocker will be raised rather than a workaround.
7. Startup ordering (no Capability Engine/Platform Core change needed): `DefaultLifecycleManager.startAll()` iterates registered `PlatformComponent`s in **registration order**, calling `initialize()` then `start()` on each before moving to the next (verified in `tmp-platform-core` source). `SecurityAutoConfiguration` declares `@AutoConfigureAfter(name = {"com.tmp.core.PlatformCoreAutoConfiguration", "com.tmp.infra.db.DatabaseAutoConfiguration", "com.tmp.capability.CapabilityEngineAutoConfiguration"})`, so Security's `@PostConstruct` component registrar runs after Capability Engine's, and Security's `PlatformComponent` therefore lands later in the registration-ordered map. Consequently, when `startAll()` reaches Security, Capability Engine's `initialize()` (discovery/registration) **and** `start()` (`activateAll()`) have already completed — safe point to run permission-catalogue synchronization and bootstrap-administrator creation inside Security's own `initialize()`/`start()`. Security's own Capability bean is created eagerly by Spring because `CapabilityEngineAutoConfiguration.capabilityDiscovery(List<Capability> discoveredCapabilities)` depends on the full `Capability` bean collection — ordinary Spring DI, unaffected by `@AutoConfigureAfter` (which only orders auto-configuration *class* processing, not bean instantiation).
8. UI screens (Login, Main Window, Access Denied, User Administration, Role Administration, Security Audit) are **all** implemented in `tmp-ui-shell` (FXML + Controller + ViewModel), never inside `tmp-security`. Rationale: (a) `tmp-security`'s allowed dependencies are limited to `com.tmp.core.api..`/`com.tmp.capability.api..` (Stage 4 task §4.10) — it must not depend on JavaFX/`tmp-ui-shell`; (b) `tmp-ui-shell` has no such restriction and may freely depend on `com.tmp.security.api` (an "external module" per Stage 4 task §7, exactly as `tmp-bootstrap-app` already depends on `com.tmp.capability.api`/`com.tmp.document.api`); (c) `ViewDescriptor`/`NavigationContribution` are documented in `tmp-capability-engine` as pure metadata that intentionally does **not** reference any FXML/Controller/ViewModel class ("resolving this metadata into an actual screen is deferred to future UI stages") — Stage 4 is that future stage, and the concrete FXML resource path ↔ screen-id mapping is owned entirely by `tmp-ui-shell`'s new Navigation Service (a small internal registry, not a new public contract). This keeps `tmp-ui-shell` as the only JavaFX-aware module (unchanged Stage 0–3 precedent: `EmptyMainShell`/`JavaFxShellLauncher` have zero business-module dependencies) while satisfying "Controller is not a Spring Bean" (FXMLLoader instantiates Controllers by reflection via `fx:controller`) and "ViewModel is created by Spring" (ViewModels become ordinary `@Bean`s inside a new `UiShellAutoConfiguration` in `tmp-ui-shell`, calling `com.tmp.security.api`/`com.tmp.capability.api` services). Navigation-item-to-permission gating convention (needed because `NavigationContribution`/`ViewDescriptor` carry no permission field): Security declares one `CommandDescriptor` per admin screen whose `commandId()` equals the corresponding `NavigationContribution.navigationId()` (e.g. both `"security.nav.users"`); the Navigation Service looks up that command among `CapabilityEngine.activeCommands()` and hides the navigation item unless `Authorization.hasPermission(...)` holds for every id in `requiredPermissionIds()`. This is a local `tmp-ui-shell` implementation convention, not a Capability Engine API change.
9. `JavaFxShellLauncher`/`JavaFxShellApplication` keep the existing "static hand-off" pattern (JavaFX requires a no-arg-constructible `Application` subclass, so Spring cannot construct it): `DesktopBootstrap` now looks up a `UiShellEntryPoint` bean (defined in `tmp-ui-shell`, exposing the Navigation Service + initial screen id) and passes it into `JavaFxShellLauncher.launch(...)` the same way it already passes `onStopCallback`/status strings, so `JavaFxShellApplication.start(Stage)` can build the real Login → Main Window flow with full Spring-backed ViewModels.
10. Config: bootstrap administrator credentials are read via `@ConfigurationProperties(prefix = "tmp.security.bootstrap")` bound from `TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN` / `TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME` / `TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` env vars (Spring relaxed binding), matching the existing `TMP_DB_*` → `spring.datasource.*` convention in `tmp-bootstrap-app/application-package.yml`. No default/fallback password is ever hard-coded.
11. `BCryptPasswordHasher` (infrastructure adapter of the Domain `PasswordHasher` port) is the single implementation, backed by `org.springframework.security:spring-security-crypto:BCryptPasswordEncoder` (version managed transitively by the already-imported `spring-boot-dependencies` BOM — no new version property needed). No `spring-security-core`, no servlet/web starter is added anywhere.
12. Login case-insensitive uniqueness is enforced at the database via a unique index on `lower(login)` (named `uk_users_login`, functional index), not a stored normalized column; the Domain `Login` value object preserves the user's original casing for display and trims/validates non-blank input only.
13. Audit is Security's own append-only table (`security.security_audit_events`), per the Stage 4 task's explicit table list. The Database Specification §14 "единый Audit Service Platform Core" phrase is a non-binding recommendation ("рекомендуется... либо отдельный платформенный модуль") and Platform Core currently exposes no audit facility at all — implementing Security's own audit inside its own schema is one of the two options the specification itself allows, and is what the Stage 4 task explicitly mandates. Not a conflict, not a blocker.
14. Out of scope, per explicit Stage 4 restrictions (§4.18) and confirmed absent from the Security Specification: LDAP/AD/OAuth/OIDC/JWT/2FA/email password recovery/external IdP/auto user lockout/password expiry/password history/remember-me/session timeout/network session. No such code, dependency, or table column is introduced anywhere in this Stage.

## STAGE4-001 — Bootstrap `tmp-security` module and public API package skeleton

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE3-025
**Module:** tmp-security (new)

### Goal

Создать Maven-модуль `tmp-security`, подключить его в root reactor и `dependencyManagement`, объявить зависимости только на `tmp-platform-core` и `tmp-capability-engine` (публичные API) плюс `spring-boot-starter`/`spring-boot-starter-jdbc`/`spring-security-crypto`/test dependencies (JUnit, H2, Testcontainers postgresql — mirroring `tmp-document-engine/pom.xml`), создать пустой пакет `com.tmp.security.api` с `package-info.java` без единого класса контракта.

### Required documents

- `Security-Specification.md` (Назначение; Основные принципы);
- `TMP-Architecture-Decisions.md` ADR-001..003, ADR-019 (module boundaries, public-API-only interaction);
- this file's "Design decisions" §1–2, §11 above.

### Required code context

- `tmp-document-engine/pom.xml` as structural template (module depending only on `tmp-platform-core` public API + `spring-boot-starter-jdbc`);
- root `pom.xml` (`<modules>`, `<dependencyManagement>`).

### Allowed code scope

- root `pom.xml` (`<modules>` entry for `tmp-security`, `<dependencyManagement>` entry);
- `tmp-security/pom.xml` (new);
- `tmp-security/src/main/java/com/tmp/security/api/package-info.java` (new, documents package purpose only).

### Forbidden

- any interface/class beyond `package-info.java`;
- dependency on any module other than `tmp-platform-core`, `tmp-capability-engine` (test scope may add JUnit/H2/Testcontainers/ArchUnit per parent `dependencyManagement`);
- adding `tmp-security` dependency to `tmp-ui-shell`/`tmp-bootstrap-app`/`tmp-architecture-tests` yet (later tasks).

### Implementation requirements

- `tmp-security/pom.xml`: `<parent>` = `tmp-parent`; dependencies = `tmp-platform-core`, `tmp-capability-engine`, `spring-boot-starter`, `spring-boot-starter-jdbc`, `spring-security-crypto` (no explicit version — BOM-managed), `tmp-infra-db` (for shared datasource/profile convention, matching `tmp-document-engine`), test-scope `spring-boot-starter-test`, `junit-jupiter`, `h2`, `testcontainers` (`junit-jupiter`, `postgresql`), `spotbugs-annotations` (`provided`, matching `tmp-document-engine`);
- add module to root `pom.xml` `<modules>` (after `tmp-capability-engine`, before `tmp-bootstrap-app`) and to `<dependencyManagement>`;
- no other production code in this task.

### Public contracts that may change

- none exist yet; this task only creates the module shell.

### Acceptance criteria

- [x] `mvn -q -DskipTests validate` passes for the full reactor with the new module present;
- [x] `mvn -q -pl :tmp-security compile` passes with zero business logic;
- [x] module has no dependency other than `tmp-platform-core`, `tmp-capability-engine`, declared technology starters.

### Required tests

- none (pure structural task, matching STAGE1-001/STAGE2-001/STAGE3-001 precedent).

### Verification commands

```bash
mvn -q -DskipTests validate
mvn -q -pl :tmp-security compile
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

`tmp-security` exists in the reactor, compiles, and has zero implementation.

---

## STAGE4-002 — Identity value objects: `UserId`, `RoleId`, `PermissionId`, `AuditEventId`, `SessionId`, `Login`, `DisplayName`

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-001
**Module:** tmp-security

### Goal

Определить immutable value objects идентичности в `com.tmp.security.api`, включая формат-валидацию `PermissionId` (`<area>.<resource>.<action>`).

### Required documents

- `Security-Specification.md` (Право — формат идентификатора; Пользователь);
- Stage 4 task §5 (перечень value objects, требование неизменяемости `PermissionId` после регистрации);
- this file's "Design decisions" §4, §12 above.

### Required code context

- `com.tmp.capability.api.CapabilityId` (style precedent: immutable String wrapper, static factory, `equals`/`hashCode`/`toString`).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/api/UserId.java` (new);
- `tmp-security/src/main/java/com/tmp/security/api/RoleId.java` (new);
- `tmp-security/src/main/java/com/tmp/security/api/PermissionId.java` (new);
- `tmp-security/src/main/java/com/tmp/security/api/AuditEventId.java` (new);
- `tmp-security/src/main/java/com/tmp/security/api/SessionId.java` (new);
- `tmp-security/src/main/java/com/tmp/security/api/Login.java` (new);
- `tmp-security/src/main/java/com/tmp/security/api/DisplayName.java` (new);
- matching test classes under `tmp-security/src/test/java/com/tmp/security/api/`.

### Forbidden

- any domain aggregate/entity logic;
- any persistence/Spring annotation on these types.

### Implementation requirements

- `UserId`/`RoleId`/`AuditEventId`/`SessionId`: immutable `UUID`-backed wrappers, static factory `of(UUID)` + `generate()` (random), `equals`/`hashCode`/`toString`;
- `PermissionId`: immutable `String`-backed wrapper; `of(String)` validates against `^[a-z][a-z0-9-]*\.[a-z][a-z0-9-]*\.[a-z][a-z0-9-]*$`, throws `IllegalArgumentException` with precise message otherwise; no setter, no mutation — identifiers are immutable by construction (registration-time immutability is an application-layer invariant, enforced in STAGE4-006/017, not by this type);
- `Login`: immutable `String`-backed wrapper; `of(String)` trims, rejects blank/null, rejects length > 128, preserves original case; documents that DB-level uniqueness is case-insensitive (Design decision §12) but this type does not itself lowercase;
- `DisplayName`: immutable `String`-backed wrapper; `of(String)` trims, rejects blank/null, rejects length > 255.
- Every type's Javadoc states it carries no password/credential data.

### Public contracts that may change

- new public types only (`UserId`, `RoleId`, `PermissionId`, `AuditEventId`, `SessionId`, `Login`, `DisplayName`); nothing existing changes.

### Acceptance criteria

- [x] valid inputs construct successfully for every type;
- [x] `PermissionId` accepts exactly the 12 catalogue ids from Stage 4 task §7 and rejects 1-segment/2-segment/4-segment/uppercase/blank input;
- [x] blank/null input rejected for every type with `IllegalArgumentException`;
- [x] `equals`/`hashCode` consistent with wrapped value; `toString` never leaks anything beyond the wrapped identity value.

### Required tests

- `UserIdTest`, `RoleIdTest`, `AuditEventIdTest`, `SessionIdTest`: generate/of, null/equals/hashCode.
- `PermissionIdTest`: all 12 catalogue ids accepted; malformed formats rejected (missing segment, extra segment, uppercase, digit-leading segment, blank).
- `LoginTest`: valid, blank/null rejected, over-length rejected, case preserved.
- `DisplayNameTest`: valid, blank/null rejected, over-length rejected.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=UserIdTest,RoleIdTest,PermissionIdTest,AuditEventIdTest,SessionIdTest,LoginTest,DisplayNameTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Stable, fully-tested identity value objects usable by every later Domain/Application/API type.

---

## STAGE4-003 — `PasswordHash` value object and `PasswordHasher` domain port

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-002
**Module:** tmp-security

### Goal

Определить `PasswordHash` (никогда не содержит plaintext, безопасный `toString`) и порт `PasswordHasher` (Domain-интерфейс, без знания о BCrypt/Spring), не позволяющие паролю попасть в DTO/log/audit.

### Required documents

- `Security-Specification.md` (Пароль);
- Stage 4 task §5, §9 (password never a plaintext field; hash differs from plaintext; port abstraction implied by "Domain не зависит от Spring/JDBC/persistence/JavaFX");
- Database Specification §13 (Domain independent of the persistence technology — same principle applied to the hashing technology).

### Required code context

- none beyond STAGE4-002 value objects (self-contained).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/domain/PasswordHash.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/PasswordHasher.java` (new, port interface);
- `tmp-security/src/main/java/com/tmp/security/domain/package-info.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/domain/`.

### Forbidden

- importing `org.springframework.security.*`, `jakarta.persistence.*`, `javafx.*` in this package;
- exposing the wrapped hash string via any public getter without an explicit, clearly-named accessor documented as infrastructure-only.

### Implementation requirements

- `PasswordHash`: immutable wrapper over a non-blank hash `String`; static factory `of(String encodedHash)` (used by infrastructure adapters only, never by application code with plaintext); `value()` accessor documented as "infrastructure-only, never logged/audited/serialized"; `toString()` returns a fixed redacted marker (e.g. `"PasswordHash[REDACTED]"`), never the hash itself; `equals`/`hashCode` based on the wrapped value (needed for persistence round-trip tests only, not for business comparison).
- `PasswordHasher` port: `PasswordHash hash(char[] plaintextPassword)`; `boolean matches(char[] plaintextPassword, PasswordHash hash)`. Uses `char[]` (not `String`) for plaintext parameters so callers can zero the array after use; Javadoc documents this intent explicitly.

### Public contracts that may change

- none exist yet; new internal Domain types only (not in `com.tmp.security.api`).

### Acceptance criteria

- [ ] `PasswordHash.toString()` never contains the wrapped value;
- [ ] `PasswordHash` has no method that returns the raw value under an easily-misused name (only a clearly infrastructure-flagged accessor);
- [ ] `PasswordHasher` is a pure interface, zero implementation, zero dependency beyond `java.*`.

### Required tests

- `PasswordHashTest`: construction, `toString()` redaction (given several distinct hash values, assert none appear in `toString()`), equality.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=PasswordHashTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A safe password-hash carrier and a Domain-owned hashing port ready for a BCrypt adapter (STAGE4-015) and the `User` aggregate (STAGE4-004).

---

## STAGE4-004 — `User` aggregate, `UserStatus`, `UserRepository` port, domain exceptions

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-003
**Module:** tmp-security

### Goal

Реализовать immutable-snapshot `User` aggregate (создание, смена пароля/hash, логическое удаление) со статусами `ACTIVE`/`DELETED`, без физического удаления, и Domain repository port.

### Required documents

- `Security-Specification.md` (Пользователь; Пароль);
- Stage 4 task §5 (допустимые состояния; запрет физического удаления; поведение удалённого пользователя);
- Database Specification §5 (`id/created_at/updated_at/version/created_by/updated_by`), §7 (Optimistic Locking).

### Required code context

- `PasswordHash` (STAGE4-003); `UserId`/`Login`/`DisplayName` (STAGE4-002).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/domain/User.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/UserStatus.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/repository/UserRepository.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/UserAlreadyDeletedException.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/domain/`.

### Forbidden

- any Spring/JPA/JDBC import;
- any UI/JavaFX import;
- exposing `PasswordHash` via `toString()`.

### Implementation requirements

- `UserStatus` enum: `ACTIVE`, `DELETED`.
- `User`: immutable aggregate (`UserId id, Login login, DisplayName displayName, PasswordHash passwordHash, UserStatus status, long version, Instant createdAt, Instant updatedAt`); factory `User.createActive(UserId, Login, DisplayName, PasswordHash, Clock)`; behaviour methods return **new** `User` snapshots (`withDisplayName`, `withPasswordHash`, `deleted(Clock)` — throws `UserAlreadyDeletedException` if already `DELETED`); `isActive()`/`isDeleted()` helpers; `toString()` never includes `passwordHash`'s raw value (relies on `PasswordHash.toString()` redaction, and does not print `passwordHash` field name/value pair with anything but the redacted marker).
- `UserRepository` port (Domain interface): `save(User)` (insert-or-update, optimistic-lock aware — throws a dedicated `OptimisticLockException`-style exception, defined here or reused from a shared location — define `com.tmp.security.domain.OptimisticLockConflictException` in this task since it's the first aggregate needing it), `findById(UserId)`, `findByLoginIgnoreCase(Login)`, `existsByLoginIgnoreCase(Login)`, `findAll(paging/filter DTO placeholder deferred to STAGE4-023 if needed)` — for this task only `save/findById/findByLoginIgnoreCase/existsByLoginIgnoreCase` are required; a paging query method is added in STAGE4-023 together with its concrete use, to avoid speculative API.

### Public contracts that may change

- none exist yet; new internal Domain types only.

### Acceptance criteria

- [ ] a newly created `User` is `ACTIVE`;
- [ ] `deleted()` transitions to `DELETED`; calling it twice throws `UserAlreadyDeletedException`;
- [ ] no setter exists; every mutation returns a new instance; `version`/timestamps are immutable fields set only through the constructor/factory;
- [ ] `User.toString()` contains no BCrypt hash characters (verified via a hash value substring check in the test).

### Required tests

- `UserTest`: creation invariants, `withDisplayName`/`withPasswordHash` produce new instances leaving the original unchanged, `deleted()` transition + double-delete rejection, `toString()` redaction.
- `UserStatusTest`: exhaustive enum values.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=UserTest,UserStatusTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A fully-tested, persistence-agnostic `User` aggregate ready for the JDBC adapter (STAGE4-011) and Application Services (STAGE4-023/024).

---

## STAGE4-005 — `Role` aggregate and `RoleRepository` port

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-002
**Module:** tmp-security

### Goal

Реализовать `Role` aggregate как шаблон набора разрешений (immutable snapshot, добавление/отзыв `PermissionId`, изменение имени/описания) и Domain repository port.

### Required documents

- `Security-Specification.md` (Роль);
- Stage 4 task §6 (создание/изменение/назначение/отзыв разрешений роли; удаление роли только при отсутствии назначенных пользователей — the "no assigned users" check itself belongs to the Application Service, STAGE4-025, since it requires cross-aggregate knowledge; this task only models the Role's own permission set).

### Required code context

- `RoleId`/`PermissionId` (STAGE4-002).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/domain/Role.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/repository/RoleRepository.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/domain/`.

### Forbidden

- any Spring/JPA/JDBC import;
- any reference to `User`/`UserId` (Role does not know which users hold it — that link is `RoleAssignment`, STAGE4-007).

### Implementation requirements

- `Role`: immutable aggregate (`RoleId id, String name, String description, Set<PermissionId> permissions, long version, Instant createdAt, Instant updatedAt`); factory `Role.create(RoleId, String name, String description, Clock)`; behaviour methods return new snapshots: `withName`, `withDescription`, `grantPermission(PermissionId)` (idempotent — no-op if already present), `revokePermission(PermissionId)` (idempotent); `permissions()` returns an unmodifiable `Set`.
- `RoleRepository` port: `save(Role)` (optimistic-lock aware, reuses `OptimisticLockConflictException` from STAGE4-004), `findById(RoleId)`, `findAll()`, `deleteById(RoleId)` (physical delete is allowed here — roles are configuration, not business history; the "cannot delete while assigned" rule is enforced by the Application Service before calling this, per Stage 4 task §6).

### Public contracts that may change

- none exist yet; new internal Domain types only.

### Acceptance criteria

- [ ] `grantPermission`/`revokePermission` are idempotent and return new immutable snapshots;
- [ ] `name`/`description` mutation methods do not mutate the permission set;
- [ ] no setter exists anywhere on `Role`.

### Required tests

- `RoleTest`: creation, grant/revoke idempotency, name/description change independence, immutability (original snapshot unaffected by derived snapshot mutation).

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=RoleTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A fully-tested `Role` aggregate ready for persistence (STAGE4-011) and Role Administration Application Services (STAGE4-025/026).

---

## STAGE4-006 — `PermissionDefinition` domain concept and `PermissionDefinitionRepository` port

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-002
**Module:** tmp-security

### Goal

Моделировать зарегистрированное разрешение (`PermissionId` + метаданные + признак активности), без знания о Capability Engine внутри Domain.

### Required documents

- `Security-Specification.md` (Право; Capability; Проверка доступа);
- Stage 4 task §7 (Security регистрирует определения разрешений; деактивация Capability не удаляет назначения; identifier immutable after registration).

### Required code context

- `PermissionId` (STAGE4-002).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/domain/PermissionDefinition.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/repository/PermissionDefinitionRepository.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/domain/`.

### Forbidden

- any reference to `com.tmp.capability.api.*` from this package (that mapping happens only in the Application layer, STAGE4-017 — Domain stays capability-agnostic, knowing only "a permission id with an active flag");
- allowing `permissionId` to be replaced/changed after construction (only `active`/`displayName`/`description` may evolve).

### Implementation requirements

- `PermissionDefinition`: immutable snapshot (`PermissionId permissionId, String displayName, String description, boolean active, Instant registeredAt, long version`); factory `PermissionDefinition.register(PermissionId, String displayName, String description, Clock)` (starts `active = true`); `withDisplayName`, `withDescription`, `activated()`, `deactivated()` — all return new snapshots; `permissionId()` never changes across snapshots derived from the same original (enforced by construction, not by a runtime check, since the id is only ever set once via the factory and copy methods never take a new id parameter).
- `PermissionDefinitionRepository` port: `save(PermissionDefinition)` (optimistic-lock aware), `findById(PermissionId)`, `findAll()`.

### Public contracts that may change

- none exist yet; new internal Domain types only.

### Acceptance criteria

- [ ] `activated()`/`deactivated()` toggle only the `active` flag, nothing else;
- [ ] no method allows constructing a `PermissionDefinition` with a different `permissionId` from an existing snapshot (copy methods take no id parameter);
- [ ] no setter exists anywhere.

### Required tests

- `PermissionDefinitionTest`: registration defaults to active, activate/deactivate toggling, display/description change independence, id immutability across derived snapshots.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=PermissionDefinitionTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A capability-agnostic permission-registry Domain concept ready for persistence (STAGE4-012) and Permission Synchronization (STAGE4-017).

---

## STAGE4-007 — `RoleAssignment`, `IndividualPermissionOverride`, and their repository ports

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-004, STAGE4-005, STAGE4-006
**Module:** tmp-security

### Goal

Моделировать назначение ролей пользователям и индивидуальные GRANT/REVOKE разрешений, как отдельные association aggregates (не поля `User`/`Role`, чтобы избежать конкурентных конфликтов версий при массовом назначении).

### Required documents

- `Security-Specification.md` (Роль; Право);
- Stage 4 task §6 (индивидуальные разрешения: отсутствие решения / GRANT / REVOKE).

### Required code context

- `UserId`, `RoleId`, `PermissionId` (STAGE4-002).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/domain/RoleAssignment.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/PermissionOverrideDecision.java` (new, enum `GRANT`/`REVOKE`);
- `tmp-security/src/main/java/com/tmp/security/domain/IndividualPermissionOverride.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/repository/RoleAssignmentRepository.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/repository/PermissionOverrideRepository.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/domain/`.

### Forbidden

- storing a *set* of overrides inside `User`/`Role` (would create false optimistic-lock conflicts between unrelated administrative actions — this is a deliberate modelling choice, not a spec requirement, documented here to keep the reviewer from expecting it inside `User`).

### Implementation requirements

- `RoleAssignment`: immutable value (`UserId userId, RoleId roleId, Instant assignedAt`); no version field (assignment either exists or does not — deletion is physical removal of the pairing row, not a lifecycle).
- `PermissionOverrideDecision`: enum `GRANT`, `REVOKE`.
- `IndividualPermissionOverride`: immutable value (`UserId userId, PermissionId permissionId, PermissionOverrideDecision decision, Instant updatedAt, long version`); factory `IndividualPermissionOverride.of(UserId, PermissionId, PermissionOverrideDecision, Clock)`; `withDecision(PermissionOverrideDecision, Clock)` returns a new snapshot (used when flipping GRANT↔REVOKE without a remove/re-add round trip).
- `RoleAssignmentRepository`: `assign(RoleAssignment)` (idempotent — no-op if already present), `revoke(UserId, RoleId)`, `findRoleIdsForUser(UserId)`, `findUserIdsForRole(RoleId)`, `countUsersForRole(RoleId)` (used by the "cannot delete role while assigned" rule in STAGE4-025).
- `PermissionOverrideRepository`: `save(IndividualPermissionOverride)` (optimistic-lock aware — insert-or-update on the natural key), `remove(UserId, PermissionId)`, `findByUser(UserId)`, `findByUserAndPermission(UserId, PermissionId)`.

### Public contracts that may change

- none exist yet; new internal Domain types only.

### Acceptance criteria

- [ ] `RoleAssignment`/`IndividualPermissionOverride` are immutable value objects with no setters;
- [ ] repository ports expose exactly the query shapes needed by later application services (no speculative methods beyond what STAGE4-008/018/026 will call).

### Required tests

- `RoleAssignmentTest`, `IndividualPermissionOverrideTest`: construction, `withDecision` snapshot independence, equality on natural key.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=RoleAssignmentTest,IndividualPermissionOverrideTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Association-level Domain types ready for the effective-permission calculator (STAGE4-008), persistence (STAGE4-012), and role/permission application services (STAGE4-025/026).

---

## STAGE4-008 — `EffectivePermissionCalculator` domain service

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-005, STAGE4-007
**Module:** tmp-security

### Goal

Реализовать чистую функцию расчёта effective permission (individual REVOKE > individual GRANT > union ролей > deny), без сохранения вычисленного множества.

### Required documents

- Stage 4 task §6 (алгоритм расчёта, 4 шага, порядок приоритета);
- `TMP-Architecture-Decisions.md` ADR-020 (вычисляемые состояния не хранятся — reinforces "no caching of the computed set as source of truth").

### Required code context

- `Role` (STAGE4-005), `IndividualPermissionOverride`/`PermissionOverrideDecision` (STAGE4-007).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/domain/EffectivePermissionCalculator.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/domain/`.

### Forbidden

- any persistence call inside this class (pure function of its input parameters only — inputs are supplied already-loaded by the caller, per ADR-020: never stored, always recomputed);
- any Capability Engine reference (inactive-permission filtering is applied by the *caller*, `AuthorizationApplicationService` in STAGE4-022, before/after this calculator — this class only implements the role/override precedence algorithm from the Security Specification, treating "declared and active" as an external input).

### Implementation requirements

- Static method `boolean isGranted(PermissionId permissionId, Set<IndividualPermissionOverride> overrides, Set<Role> assignedRoles)`: 1) if an override for `permissionId` with decision `REVOKE` exists → `false`; 2) else if an override with decision `GRANT` exists → `true`; 3) else if any role in `assignedRoles` has `permissions().contains(permissionId)` → `true`; 4) else `false`.
- Additional method `Set<PermissionId> effectivePermissions(Set<PermissionId> declaredActivePermissionIds, Set<IndividualPermissionOverride> overrides, Set<Role> assignedRoles)`: applies the same 4-step rule per id, restricted to `declaredActivePermissionIds` (the caller passes only currently-active ids, so an individual GRANT for a currently-inactive permission correctly yields no access — Stage 4 task §7 "новые операции с ними запрещаются").

### Public contracts that may change

- none exist yet; new internal Domain type only.

### Acceptance criteria

- [ ] individual REVOKE wins over any role grant;
- [ ] individual GRANT wins when no REVOKE exists, even with no matching role;
- [ ] union of multiple roles grants a permission if *any* role grants it;
- [ ] no override and no role ⇒ denied;
- [ ] a permission absent from `declaredActivePermissionIds` is never returned as effective, even with an individual GRANT override present.

### Required tests

- `EffectivePermissionCalculatorTest`: one test per acceptance-criteria bullet above, plus a combined multi-role/multi-permission scenario.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=EffectivePermissionCalculatorTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A fully-tested, persistence-free effective-permission algorithm, the single source of truth used later by `AuthorizationApplicationService` (STAGE4-022).

---

## STAGE4-009 — `SecurityAuditEvent` aggregate, `AuditOperation`, `SecurityAuditRepository` port

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-002
**Module:** tmp-security

### Goal

Моделировать append-only событие аудита Security с безопасным описанием (без пароля/hash), фиксированным набором операций.

### Required documents

- `Security-Specification.md` (Аудит);
- Stage 4 task §12 (что фиксируется; что запрещено сохранять; append-only);
- Database Specification §14 (минимальный состав записи аудита; допустимый набор операций; append-only, только INSERT).

### Required code context

- `UserId`, `AuditEventId` (STAGE4-002).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/domain/AuditOperation.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/AuditResult.java` (new, enum `SUCCESS`/`FAILURE`);
- `tmp-security/src/main/java/com/tmp/security/domain/SecurityAuditEvent.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/repository/SecurityAuditRepository.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/domain/`.

### Forbidden

- any field or constructor parameter capable of carrying a password/hash (no `String password`, no `PasswordHash`, no `char[]` parameter anywhere in this class — enforced by the class simply never declaring such a parameter);
- any `update`/`delete` method — this type has no mutation methods at all beyond construction (append-only by construction, not just by convention).

### Implementation requirements

- `AuditOperation` enum: `LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, USER_CREATED, USER_UPDATED, USER_DELETED, PASSWORD_CHANGED, PASSWORD_RESET, ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED, ROLE_ASSIGNED, ROLE_REVOKED, ROLE_PERMISSIONS_CHANGED, PERMISSION_GRANTED, PERMISSION_REVOKED, PERMISSION_OVERRIDE_REMOVED, PERMISSION_DEFINITION_REGISTERED` (exact set required by Stage 4 task §12, mapped onto Database Specification §14's generic operation vocabulary where applicable).
- `SecurityAuditEvent`: immutable, single constructor only (no builder mutation), fields `AuditEventId id, Instant occurredAt, UserId actorUserId (nullable), String actorLoginSnapshot, AuditOperation operation, String targetType, String targetIdentifier (nullable), String safeDescription, AuditResult result`; static factory `SecurityAuditEvent.record(...)` performs `Objects.requireNonNull` on all non-nullable params and rejects a `safeDescription` containing suspicious markers is **not** attempted (impossible to generically detect) — instead the Javadoc explicitly documents that callers (Application layer, later tasks) must never pass password/hash material into `safeDescription`, and a dedicated test in STAGE4-021/023/024/026 asserts no audit call site ever does.
- `SecurityAuditRepository` port: `append(SecurityAuditEvent)` only (no update/delete method exists on the port at all) plus read methods `findPage(AuditQueryFilter, int pageIndex, int pageSize)` and `count(AuditQueryFilter)` — define a minimal `AuditQueryFilter` record here (`Instant from, Instant to, UserId actorUserId, AuditOperation operation` — all nullable/optional) since the query capability is required by Stage 4 task §18 ("pagination и filtering").

### Public contracts that may change

- none exist yet; new internal Domain types only.

### Acceptance criteria

- [ ] `SecurityAuditEvent` has no setter and no update/delete method reachable from its own type or from `SecurityAuditRepository`;
- [ ] constructing an event with `null` operation/targetType/safeDescription/result throws `NullPointerException`;
- [ ] `actorUserId`/`targetIdentifier` may be `null` (covers pre-authentication failed-login and system-triggered events).

### Required tests

- `SecurityAuditEventTest`: construction success/failure paths, immutability (no reflection-discoverable setter — a simple check that the class declares no non-final fields).
- `AuditOperationTest`: all 17 enum constants present (guards against accidental removal).

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=SecurityAuditEventTest,AuditOperationTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

An append-only-by-construction audit Domain model, ready for the JDBC adapter (STAGE4-013) and every mutating Application Service (STAGE4-017/018/021/023/024/025/026).

---

## STAGE4-010 — Flyway migration `V4__security_schema.sql`

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-009
**Module:** tmp-security

### Goal

Создать единственную Flyway-миграцию, создающую схему `security` со всеми обязательными таблицами, constraints и индексами.

### Required documents

- Database Specification §3 (Schema per Module), §4 (UUID identifiers), §5 (общие технические поля), §7 (Optimistic Locking), §10 (Flyway), §11 (правила именования), §12 (связи между модулями — internal FK only), Приложение А/Б;
- Stage 4 task §13 (список таблиц; case-insensitive unique login; отсутствие plaintext password columns; password_hash NOT NULL).

### Required code context

- `tmp-document-engine/src/main/resources/db/migration/V2__documents_schema.sql` (naming/structure precedent);
- confirmed next free version: `V4` (highest existing is `V3`, `tmp-capability-engine` has none).

### Allowed code scope

- `tmp-security/src/main/resources/db/migration/V4__security_schema.sql` (new).

### Forbidden

- editing any existing `V1`/`V2`/`V3` migration in any module;
- any cross-schema foreign key;
- any plaintext password column.

### Implementation requirements

- `CREATE SCHEMA IF NOT EXISTS security;`
- `security.users(id UUID PK, login VARCHAR(128) NOT NULL, display_name VARCHAR(255) NOT NULL, password_hash VARCHAR(255) NOT NULL, status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','DELETED')), version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL)`; `CREATE UNIQUE INDEX uk_users_login ON security.users (lower(login));`
- `security.roles(id UUID PK, name VARCHAR(128) NOT NULL, description TEXT NOT NULL DEFAULT '', version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL)`; `CREATE INDEX idx_roles_name ON security.roles (name);`
- `security.permission_definitions(permission_id VARCHAR(160) PK, display_name VARCHAR(255) NOT NULL, description TEXT NOT NULL DEFAULT '', active BOOLEAN NOT NULL DEFAULT TRUE, registered_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0)`.
- `security.role_permissions(role_id UUID NOT NULL, permission_id VARCHAR(160) NOT NULL, granted_at TIMESTAMPTZ NOT NULL, CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id), CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES security.roles(id), CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES security.permission_definitions(permission_id))`.
- `security.user_roles(user_id UUID NOT NULL, role_id UUID NOT NULL, assigned_at TIMESTAMPTZ NOT NULL, CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id), CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES security.users(id), CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES security.roles(id))`.
- `security.user_permission_overrides(user_id UUID NOT NULL, permission_id VARCHAR(160) NOT NULL, decision VARCHAR(16) NOT NULL CHECK (decision IN ('GRANT','REVOKE')), updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0, CONSTRAINT pk_user_permission_overrides PRIMARY KEY (user_id, permission_id), CONSTRAINT fk_user_permission_overrides_user FOREIGN KEY (user_id) REFERENCES security.users(id), CONSTRAINT fk_user_permission_overrides_permission FOREIGN KEY (permission_id) REFERENCES security.permission_definitions(permission_id))`.
- `security.security_audit_events(id UUID PK, occurred_at TIMESTAMPTZ NOT NULL, actor_user_id UUID, actor_login VARCHAR(128), operation VARCHAR(64) NOT NULL, target_type VARCHAR(64) NOT NULL, target_id VARCHAR(160), safe_description TEXT NOT NULL DEFAULT '', result VARCHAR(16) NOT NULL CHECK (result IN ('SUCCESS','FAILURE')), CONSTRAINT fk_security_audit_events_actor FOREIGN KEY (actor_user_id) REFERENCES security.users(id))`; `CREATE INDEX idx_security_audit_events_occurred_at ON security.security_audit_events (occurred_at DESC);`; `CREATE INDEX idx_security_audit_events_target ON security.security_audit_events (target_type, target_id);`; `CREATE INDEX idx_security_audit_events_actor ON security.security_audit_events (actor_user_id);`.
- all PK/FK/UK/CHECK/index names follow Appendix A exactly (`pk_*`, `fk_*`, `uk_*`, `idx_*`, `chk_*` — CHECK constraints above are inline `CHECK` without explicit name; add explicit `CONSTRAINT chk_users_status`/`CONSTRAINT chk_user_permission_overrides_decision`/`CONSTRAINT chk_security_audit_events_result` names to match the naming appendix precisely).

### Public contracts that may change

- none (pure SQL DDL).

### Acceptance criteria

- [ ] `mvn -q -pl :tmp-infra-db,:tmp-security test` (or the dedicated Flyway IT below) applies `V4` cleanly on a fresh PostgreSQL Testcontainers instance, after `V1`..`V3`;
- [ ] no `public` schema object created;
- [ ] no cross-schema FK exists.

### Required tests

- none new in this task (verified by STAGE4-014's Testcontainers IT, which is the first to exercise this schema end-to-end); a minimal smoke check may be added there, not here, to avoid duplicate Testcontainers bring-up cost.

### Verification commands

```bash
mvn -q -pl :tmp-security -DskipTests compile
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

`security` schema fully defined via Flyway, ready for JDBC adapters (STAGE4-011/012/013).

---

## STAGE4-011 — JDBC `UserRepository` and `RoleRepository` adapters

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-010
**Module:** tmp-security

### Goal

Реализовать `JdbcUserRepository`/`JdbcRoleRepository` с optimistic locking и case-insensitive проверкой login.

### Required documents

- Database Specification §7 (Optimistic Locking — conflict must fail, no auto-overwrite), §11 (naming);
- Stage 4 task §13 (uniqueness без учёта регистра; password_hash NOT NULL).

### Required code context

- `tmp-document-engine/src/main/java/com/tmp/document/persistence/JdbcDocumentFileStorageAdapter.java` (JdbcTemplate row-mapping precedent);
- `User`/`UserStatus`/`UserRepository` (STAGE4-004); `Role`/`RoleRepository` (STAGE4-005); `PasswordHash` (STAGE4-003).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/persistence/JdbcUserRepository.java` (new);
- `tmp-security/src/main/java/com/tmp/security/persistence/JdbcRoleRepository.java` (new);
- `tmp-security/src/main/java/com/tmp/security/persistence/package-info.java` (new);
- matching unit tests (H2, same convention as `tmp-document-engine`'s H2-based adapter tests) under `tmp-security/src/test/java/com/tmp/security/persistence/`.

### Forbidden

- JPA/Hibernate annotations anywhere;
- catching and swallowing SQL unique-violation exceptions silently (must translate to a domain-meaningful exception, e.g. a `DuplicateLoginException` in `com.tmp.security.domain`, added in this task since it is first needed here).

### Implementation requirements

- `JdbcUserRepository implements UserRepository`: `save()` performs `INSERT ... ON CONFLICT (id) DO UPDATE ... WHERE users.version = :expectedVersion` style optimistic update (or explicit `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?` then check affected-rows == 1, throwing `OptimisticLockConflictException` on 0 rows for existing records); unique-login violations from the DB translate to `DuplicateLoginException`; `findByLoginIgnoreCase` uses `WHERE lower(login) = lower(?)`.
- `JdbcRoleRepository implements RoleRepository`: same optimistic-locking pattern; `role_permissions` rows are fully replaced (`DELETE` then re-`INSERT`) inside the same `save()` call for simplicity and correctness (small permission sets, no need for diffing).
- Both adapters are package-private classes wired only through `SecurityAutoConfiguration` (STAGE4-029), matching `tmp-document-engine`'s adapter visibility convention.

### Public contracts that may change

- new internal Domain exception `DuplicateLoginException` (in `com.tmp.security.domain`) and `OptimisticLockConflictException` (already introduced in STAGE4-004) — both internal, not in `com.tmp.security.api`.

### Acceptance criteria

- [ ] saving a new `User`/`Role` and reading it back round-trips all fields exactly;
- [ ] saving with a stale `version` throws `OptimisticLockConflictException`;
- [ ] inserting two users with logins differing only by case throws `DuplicateLoginException`;
- [ ] `Role.save()` correctly replaces the full permission set on update.

### Required tests

- `JdbcUserRepositoryTest` (H2): round-trip, optimistic-lock conflict, case-insensitive duplicate rejection, `findByLoginIgnoreCase`/`existsByLoginIgnoreCase`.
- `JdbcRoleRepositoryTest` (H2): round-trip incl. permission set, optimistic-lock conflict, permission-set replace-on-update.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=JdbcUserRepositoryTest,JdbcRoleRepositoryTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Working, unit-tested persistence for `User`/`Role`, ready for PostgreSQL confirmation in STAGE4-014.

---

## STAGE4-012 — JDBC `PermissionDefinitionRepository`, `RoleAssignmentRepository`, `PermissionOverrideRepository` adapters

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-011
**Module:** tmp-security

### Goal

Реализовать оставшиеся JDBC-адаптеры для реестра разрешений, назначений ролей и индивидуальных override.

### Required documents

- Database Specification §7 (Optimistic Locking), §11 (naming);
- Stage 4 task §6/§7 (assignment/override semantics).

### Required code context

- `PermissionDefinition`/`PermissionDefinitionRepository` (STAGE4-006); `RoleAssignment`/`IndividualPermissionOverride`/repositories (STAGE4-007).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/persistence/JdbcPermissionDefinitionRepository.java` (new);
- `tmp-security/src/main/java/com/tmp/security/persistence/JdbcRoleAssignmentRepository.java` (new);
- `tmp-security/src/main/java/com/tmp/security/persistence/JdbcPermissionOverrideRepository.java` (new);
- matching unit tests (H2) under `tmp-security/src/test/java/com/tmp/security/persistence/`.

### Forbidden

- JPA/Hibernate annotations;
- any query joining across the `security` schema into another module's schema.

### Implementation requirements

- `JdbcPermissionDefinitionRepository`: optimistic-lock aware `save()` (PK is `permission_id`, not a surrogate UUID — update by PK+version, insert on first registration).
- `JdbcRoleAssignmentRepository`: `assign()` = `INSERT ... ON CONFLICT (user_id, role_id) DO NOTHING`; `revoke()` = `DELETE`; `countUsersForRole()` = `SELECT COUNT(*) ... WHERE role_id = ?`.
- `JdbcPermissionOverrideRepository`: `save()` = `INSERT ... ON CONFLICT (user_id, permission_id) DO UPDATE SET decision = ?, updated_at = ?, version = version + 1 WHERE user_permission_overrides.version = ?` (optimistic, translate 0-row update on an existing key to `OptimisticLockConflictException`); `remove()` = `DELETE`.

### Public contracts that may change

- none beyond internal persistence classes.

### Acceptance criteria

- [ ] permission-definition round-trip incl. `active` flag toggle with optimistic locking;
- [ ] role assignment is idempotent (`assign()` called twice does not error and does not duplicate);
- [ ] `countUsersForRole` reflects assignments accurately after assign/revoke;
- [ ] override save/remove round-trips correctly, including flipping `GRANT`↔`REVOKE` on the same key.

### Required tests

- `JdbcPermissionDefinitionRepositoryTest`, `JdbcRoleAssignmentRepositoryTest`, `JdbcPermissionOverrideRepositoryTest` (all H2).

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=JdbcPermissionDefinitionRepositoryTest,JdbcRoleAssignmentRepositoryTest,JdbcPermissionOverrideRepositoryTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Full persistence coverage for the permission/assignment/override side of the model.

---

## STAGE4-013 — JDBC `SecurityAuditRepository` adapter (append-only insert, paginated/filtered read)

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-012
**Module:** tmp-security

### Goal

Реализовать append-only персистентность аудита с фильтрацией/пагинацией для чтения.

### Required documents

- Database Specification §14 (append-only; только INSERT);
- Stage 4 task §12, §18 (pagination и filtering).

### Required code context

- `SecurityAuditEvent`/`AuditOperation`/`AuditResult`/`SecurityAuditRepository`/`AuditQueryFilter` (STAGE4-009).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/persistence/JdbcSecurityAuditRepository.java` (new);
- matching unit tests (H2) under `tmp-security/src/test/java/com/tmp/security/persistence/`.

### Forbidden

- any `UPDATE`/`DELETE` SQL statement anywhere in this class (append-only enforced structurally — the class simply never issues such statements);
- storing `password`/`password_hash`/raw credentials in `safe_description` (verified by test, not by runtime filtering — this class has no way to know what a caller puts in the string, so the guarantee comes from upstream Application Services, tested there).

### Implementation requirements

- `append(SecurityAuditEvent)`: single `INSERT`.
- `findPage(AuditQueryFilter, pageIndex, pageSize)`: dynamic `WHERE` clause built from non-null filter fields (`occurred_at BETWEEN`, `actor_user_id =`, `operation =`), `ORDER BY occurred_at DESC`, `LIMIT`/`OFFSET`.
- `count(AuditQueryFilter)`: same `WHERE` clause, `SELECT COUNT(*)`.

### Public contracts that may change

- none beyond internal persistence class.

### Acceptance criteria

- [ ] `append` followed by `findPage`/`count` returns the inserted event correctly mapped;
- [ ] filtering by `actorUserId`/`operation`/date range narrows results correctly;
- [ ] pagination returns disjoint pages covering all rows with no duplicates/gaps for a fixed filter.

### Required tests

- `JdbcSecurityAuditRepositoryTest` (H2): append+read round-trip, each filter dimension individually, pagination correctness across 3+ pages.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=JdbcSecurityAuditRepositoryTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Working append-only audit persistence, ready for PostgreSQL confirmation (STAGE4-014) and every audited Application Service.

---

## STAGE4-014 — PostgreSQL Testcontainers IT: schema, constraints, optimistic locking, case-insensitive uniqueness, logical deletion

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-013
**Module:** tmp-security

### Goal

Подтвердить всю схему `security` и её адаптеры на реальном PostgreSQL через Testcontainers.

### Required documents

- Stage 4 task §18 (полный список обязательных проверок Testcontainers для схемы/constraints/locking/deletion, за исключением bootstrap admin и BCrypt/audit end-to-end, которые проверяются в STAGE4-019/030).

### Required code context

- `tmp-document-engine/src/test/java/com/tmp/document/DocumentEnginePostgresIntegrationIT.java` (Testcontainers setup precedent: container lifecycle, Flyway trigger, `@Testcontainers`/`@Container` usage).

### Allowed code scope

- `tmp-security/src/test/java/com/tmp/security/SecuritySchemaPostgresIntegrationIT.java` (new).

### Forbidden

- relying on H2 as the sole confirmation for any of these checks (H2 tests from STAGE4-011/012/013 remain as fast feedback, but this IT is mandatory and must run against real PostgreSQL);
- modifying `V4__security_schema.sql` to make a test pass (a genuine schema defect found here would require revisiting STAGE4-010, not patching around it in the test).

### Implementation requirements

- `@Testcontainers` PostgreSQL container (same image/version as `tmp-document-engine`'s IT), Flyway migrates `V1..V4` on start;
- test cases: table/constraint presence (via `information_schema` queries) for all 7 tables; no plaintext password column exists (`information_schema.columns` does not contain any column named like `password` other than `password_hash`, and `password_hash` is `NOT NULL`); case-insensitive unique login (`INSERT` two users differing only by case inside two separate transactions → second fails with a unique-violation, not silently succeeding); concurrent duplicate user creation (two threads/transactions racing to insert the same login → exactly one succeeds); optimistic locking on `users` and `roles` (stale-version update affects 0 rows); logical deletion (`User.deleted()` persists as `status = 'DELETED'`, row still physically present, still readable by `findById`); deleted-user-not-authenticatable is verified in STAGE4-021's IT, not here (needs the Authentication Application Service).

### Public contracts that may change

- none.

### Acceptance criteria

- [ ] every checklist item above passes against a real PostgreSQL Testcontainers instance;
- [ ] test suite is deterministic (no flaky timing assumptions for the concurrency case — uses an explicit synchronization barrier, matching the pattern already used in `CapabilityLifecycleConcurrencyTest`).

### Required tests

- `SecuritySchemaPostgresIntegrationIT` (the task's whole deliverable).

### Verification commands

```bash
mvn -q -pl :tmp-security verify -Dit.test=SecuritySchemaPostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Schema-level correctness confirmed against real PostgreSQL.

---

## STAGE4-015 — `BCryptPasswordHasher` infrastructure adapter

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-003
**Module:** tmp-security

### Goal

Реализовать единственную реализацию `PasswordHasher` на основе `spring-security-crypto` `BCryptPasswordEncoder`, как единственный централизованный `PasswordEncoder` bean.

### Required documents

- Stage 4 task §4.20 (approved `PasswordEncoder`, `spring-security-crypto`, no servlet/web stack), §9 (hash differs from plaintext; same password → different hashes).

### Required code context

- `PasswordHasher`/`PasswordHash` (STAGE4-003).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/infrastructure/BCryptPasswordHasher.java` (new — new `com.tmp.security.infrastructure` package for this single technology-facing adapter; `package-info.java` added alongside);
- matching tests under `tmp-security/src/test/java/com/tmp/security/infrastructure/`.

### Forbidden

- any `spring-security-web`/`spring-security-config`/servlet dependency (none added to `pom.xml` — `spring-security-crypto` was already added in STAGE4-001);
- exposing the underlying `BCryptPasswordEncoder` instance through any public method beyond the `PasswordHasher` port methods.

### Implementation requirements

- `BCryptPasswordHasher implements PasswordHasher`: wraps a single `BCryptPasswordEncoder` instance (default strength); `hash(char[] plaintext)` calls `encode(new String(plaintext))` and wraps the result in `PasswordHash.of(...)`; `matches(char[] plaintext, PasswordHash hash)` calls `encoder.matches(new String(plaintext), hash.value())`; the class does not itself clear the input `char[]` (caller's responsibility, documented in `PasswordHasher`'s Javadoc from STAGE4-003) but never retains the `String` conversion beyond the call.

### Public contracts that may change

- none (internal infrastructure adapter, wired as a bean in STAGE4-029).

### Acceptance criteria

- [ ] `matches()` returns `true` for the correct plaintext and `false` for an incorrect one;
- [ ] hashing the same plaintext twice yields two different `PasswordHash` values (BCrypt salting) that both `matches()` the original plaintext;
- [ ] the produced hash string is never equal to the plaintext.

### Required tests

- `BCryptPasswordHasherTest`: hash≠plaintext, same-password-different-hashes, matches-true/false.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=BCryptPasswordHasherTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A working, tested BCrypt adapter ready to be the sole `PasswordEncoder`-backed bean in Security's Spring wiring.

---

## STAGE4-016 — Security Administration Capability descriptor and permission-id constants

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-002, STAGE4-001
**Module:** tmp-security

### Goal

Объявить единственный `Capability` bean "Security Administration", предоставляющий 12 разрешений Stage 4 §7 и связанные Command/Navigation/View метаданные для будущих UI-экранов, зависимый только от публичного API Capability Engine.

### Required documents

- Stage 4 task §7 (точный список 12 разрешений), §15 (обязательные экраны — навигация нужна для User Administration/Role Administration/Security Audit screens);
- this file's "Design decisions" §5, §8 (no-op lifecycle hooks; navigation-id ↔ command-id convention for permission gating).

### Required code context

- `Capability`/`CapabilityDescriptor`/`PermissionDescriptor`/`CommandDescriptor`/`NavigationContribution`/`ViewDescriptor`/`CapabilityId`/`CapabilityVersion` (`com.tmp.capability.api`, Stage 3, already read in full during Stage 4 planning);
- `SampleTechnicalCapability` (`tmp-capability-engine/src/main/java/com/tmp/capability/sample/`) as the minimal-Capability-implementation precedent.

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/capability/SecurityPermissions.java` (new — `public final class` of `public static final PermissionId` constants for the 12 catalogue ids, placed in `com.tmp.security.capability` since it is an internal wiring detail, **not** re-exported from `com.tmp.security.api` — Application Services in later tasks reference these constants directly within the module);
- `tmp-security/src/main/java/com/tmp/security/capability/SecurityAdministrationCapability.java` (new);
- `tmp-security/src/main/java/com/tmp/security/capability/package-info.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/capability/`.

### Forbidden

- declaring any dependency (`DependencyDescriptor`) on another Capability (Security Administration Capability has none);
- declaring a `PublicServiceContribution` or `DocumentContribution` (per Design decision §5);
- inventing any permission id beyond the 12 listed in Stage 4 task §7.

### Implementation requirements

- `SecurityPermissions`: 12 `public static final PermissionId` constants named `USERS_VIEW, USERS_CREATE, USERS_UPDATE, USERS_DELETE, USERS_RESET_PASSWORD, ROLES_VIEW, ROLES_CREATE, ROLES_UPDATE, ROLES_DELETE, ROLES_ASSIGN, PERMISSIONS_ASSIGN, AUDIT_VIEW`, values exactly `security.users.view` … `security.audit.view`.
- `SecurityAdministrationCapability implements Capability`: `descriptor()` returns a `CapabilityDescriptor` with `id = CapabilityId.of("security-administration")`, `version = CapabilityVersion.of("1.0.0")`, no dependencies, `permissions()` = the 12 `PermissionDescriptor`s (one per constant, human-readable display name/description), `commands()` = one `CommandDescriptor` per admin screen (`"security.nav.users"` requiring `USERS_VIEW`; `"security.nav.roles"` requiring `ROLES_VIEW`; `"security.nav.audit"` requiring `AUDIT_VIEW`), `navigationContributions()` = matching `NavigationContribution`s (same ids, pointing at `viewId`s `"security.view.users"`/`"security.view.roles"`/`"security.view.audit"`), `views()` = matching `ViewDescriptor`s; `onInitialize/onActivate/onDeactivate/onStop` are no-ops (per Design decision §5).

### Public contracts that may change

- none in `com.tmp.security.api`; these are internal wiring types.

### Acceptance criteria

- [ ] `SecurityAdministrationCapability.descriptor()` builds without throwing (no duplicate contribution ids, per `CapabilityDescriptor.Builder` validation);
- [ ] exactly the 12 required permission ids are present, no more, no fewer;
- [ ] each navigation contribution's id matches a command's `commandId()` exactly (the STAGE4-033 gating convention depends on this).

### Required tests

- `SecurityAdministrationCapabilityTest`: descriptor builds, permission id set equality against `SecurityPermissions` constants, navigation↔command id matching, no dependencies declared, lifecycle hooks are no-ops (call each, assert no exception/no side effect observable).

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=SecurityAdministrationCapabilityTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A ready-to-discover Capability bean (wired in STAGE4-029) declaring Security's own permission catalogue through the public Capability Engine mechanism, as required by Stage 4 task §7.

---

## STAGE4-017 — Permission Synchronization Application Service

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-006, STAGE4-012, STAGE4-016
**Module:** tmp-security

### Goal

Синхронизировать `PermissionDefinition` registry с полным каталогом разрешений, объявленных активными и зарегистрированными Capability, включая пометку неактивных.

### Required documents

- Stage 4 task §7 (регистрация; деактивация не удаляет назначения; повторная активация восстанавливает применимость);
- this file's "Design decisions" §6.

### Required code context

- `CapabilityEngine.registeredCapabilities()`, `CapabilityEngine.stateOf(CapabilityId)`, `CapabilityDescriptor.permissions()`, `CapabilityLifecycleState` (`com.tmp.capability.api`);
- `PermissionDefinition`/`PermissionDefinitionRepository` (STAGE4-006); `SecurityAuditEvent`/`AuditOperation.PERMISSION_DEFINITION_REGISTERED`/`SecurityAuditRepository` (STAGE4-009).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/application/PermissionSynchronizationApplicationService.java` (new);
- `tmp-security/src/main/java/com/tmp/security/application/package-info.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/application/`.

### Forbidden

- reflection or internal-registry access into `tmp-capability-engine` (only `com.tmp.capability.api` types used);
- deleting a `PermissionDefinition` row when its Capability becomes inactive (only the `active` flag toggles — assignments referencing it via FK must remain valid).

### Implementation requirements

- `synchronize()` (called once at startup by `SecurityPlatformComponent`, STAGE4-029, and safe to call again idempotently): for every `CapabilityDescriptor` in `capabilityEngine.registeredCapabilities()`, for every `PermissionDescriptor` in `descriptor.permissions()` — if no `PermissionDefinition` exists for that `PermissionId`, register it (`active` = `stateOf(descriptor.id()) == ACTIVE`), audit `PERMISSION_DEFINITION_REGISTERED`; if one exists, reconcile only its `active` flag to match current capability state (never touch `permissionId`, `displayName`/`description` are only updated if they actually differ, to avoid pointless optimistic-lock churn); permissions belonging to a capability not currently `ACTIVE` are marked `active = false` but never removed.
- Whole `synchronize()` call runs inside one `@Transactional` boundary per Stage 4 task §14 ("mutating operation ... фиксируется одной транзакцией") — since this may touch many rows, this is the one documented exception where "one business operation" legitimately spans many aggregate instances of the *same* aggregate type (`PermissionDefinition`), not a cross-aggregate-type violation.

### Public contracts that may change

- none in `com.tmp.security.api`; internal Application Service.

### Acceptance criteria

- [ ] first sync run registers exactly the permissions declared by all currently-registered capabilities (Security's own 12 plus the diagnostic sample capability's, if enabled — test asserts by explicit id set, not by count, to stay independent of unrelated sample-capability changes);
- [ ] re-running sync after a capability is deactivated flips only that capability's permissions to `active = false`, leaves rows intact;
- [ ] re-activating flips them back to `active = true`;
- [ ] every registration is audited exactly once (no duplicate audit rows on repeated `synchronize()` calls for an already-registered permission).

### Required tests

- `PermissionSynchronizationApplicationServiceTest`: initial registration, deactivation reconciliation, reactivation reconciliation, idempotent re-sync (no duplicate audit), uses an in-memory fake `CapabilityEngine`/repositories (unit-level, no Spring context) matching the style of `DefaultDocumentEngineRegistrationTest`.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=PermissionSynchronizationApplicationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A working synchronization service ready to be invoked from `SecurityPlatformComponent.initialize()` (STAGE4-029), immediately after Capability Engine has activated.

---

## STAGE4-018 — Bootstrap Administrator Application Service and `TMP_SECURITY_*` configuration

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-004, STAGE4-011, STAGE4-015, STAGE4-016
**Module:** tmp-security

### Goal

Создать первого администратора один раз при первом запуске, транзакционно, идемпотентно и защищённо от конкурентного запуска, с ролью Security Administrator, ограниченной 12 разрешениями Security Administration Capability.

### Required documents

- Stage 4 task §8 (полные требования: fail-fast без пароля, идемпотентность, конкурентная защита, роль ограничена только Security Administration Capability);
- this file's "Design decisions" §7, §10.

### Required code context

- `User`/`UserRepository` (STAGE4-004); `Role`/`RoleRepository` (STAGE4-005); `RoleAssignmentRepository` (STAGE4-007); `PasswordHasher` (STAGE4-003); `SecurityPermissions` (STAGE4-016); audit types (STAGE4-009).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/application/SecurityBootstrapProperties.java` (new, `@ConfigurationProperties(prefix = "tmp.security.bootstrap")`);
- `tmp-security/src/main/java/com/tmp/security/application/BootstrapAdministratorApplicationService.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/MissingBootstrapConfigurationException.java` (new, unchecked, fail-fast signal);
- matching tests under `tmp-security/src/test/java/com/tmp/security/application/`.

### Forbidden

- any default/example password value, anywhere (including test fixtures using a clearly-fake, never-shipped value only inside test code, never in `main`);
- logging the configured password at any log level.

### Implementation requirements

- `SecurityBootstrapProperties`: `String adminLogin, String adminDisplayName, String adminPassword` (no defaults); binds from `TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN`/`_DISPLAY_NAME`/`_PASSWORD` via Spring relaxed binding on prefix `tmp.security.bootstrap` (property names `admin-login`/`admin-display-name`/`admin-password`).
- `BootstrapAdministratorApplicationService.ensureBootstrapAdministrator()`: if `userRepository` reports any user exists at all (any status) → no-op, return; else if any of the three properties is blank/missing → throw `MissingBootstrapConfigurationException` with a clear technical message (no password value in the message); else, in one transaction: create+save the `Role` "Security Administrator" granted exactly the 12 `SecurityPermissions` constants (create-or-reuse-if-already-exists-by-name is **not** needed since this only runs when zero users exist — a fresh role is always created); create+save the admin `User` (`ACTIVE`, hashed password via `PasswordHasher`); assign the role to the user; append `SecurityAuditEvent` (`operation = USER_CREATED`, actor = the new admin's own id or a `null` system actor — choose `null` actor with `actorLoginSnapshot = "system-bootstrap"`, documented as the one legitimate `null`-actor case besides failed pre-auth login).
- Concurrency safety: the "any user exists" check plus the multi-row insert must be safe under two JVM instances racing at first startup. Achieved via the DB-level unique constraint on `users.login` (STAGE4-010) as the ultimate arbiter — if a race causes two bootstrap attempts, the second's `User` insert fails with `DuplicateLoginException` (STAGE4-011), which this service catches and treats as "another instance already bootstrapped" (logs at INFO, does not fail startup), rather than relying solely on the initial existence check (which has a race window by itself, hence the DB constraint back-stop). Document this reasoning in the class Javadoc.

### Public contracts that may change

- none in `com.tmp.security.api`; internal Application Service + config type.

### Acceptance criteria

- [ ] with zero users and complete config: exactly one `ACTIVE` admin user + "Security Administrator" role (12 permissions) + one `RoleAssignment` + one audit row are created;
- [ ] with zero users and missing/blank config: throws `MissingBootstrapConfigurationException`, no partial `User`/`Role` row persisted (verified via repository state after the exception, using a fake in-memory repository at unit level; real transactional rollback confirmed in STAGE4-019's Postgres IT);
- [ ] with at least one existing user (regardless of status): no-op, no exception, nothing created;
- [ ] simulated race (two service instances against the same fake repository state) results in exactly one successful bootstrap and the other treated as a benign duplicate.

### Required tests

- `SecurityBootstrapPropertiesTest`: binding from env-var-shaped property names.
- `BootstrapAdministratorApplicationServiceTest`: all four acceptance-criteria scenarios above, unit-level with fakes.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=SecurityBootstrapPropertiesTest,BootstrapAdministratorApplicationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A safe, idempotent bootstrap service ready for real-transaction/real-concurrency confirmation in STAGE4-019 and startup wiring in STAGE4-029.

---

## STAGE4-019 — PostgreSQL Testcontainers IT: bootstrap administrator exactly-once, concurrent bootstrap, missing-config fail-fast, permission-sync inactive denial

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-017, STAGE4-018, STAGE4-014
**Module:** tmp-security

### Goal

Подтвердить bootstrap admin и permission synchronization на реальном PostgreSQL с реальной транзакционностью и конкурентностью.

### Required documents

- Stage 4 task §8, §18 (bootstrap admin exactly-once; concurrent bootstrap; missing bootstrap config fail-fast; permission synchronization; inactive permission denial — the "inactive permission denial" behavioural check itself is completed in STAGE4-022/030 once `Authorization` exists; here only the *data* side — `active` flag correctness after sync — is confirmed).

### Required code context

- `SecuritySchemaPostgresIntegrationIT` (STAGE4-014) as the container-setup precedent within this module.

### Allowed code scope

- `tmp-security/src/test/java/com/tmp/security/SecurityBootstrapPostgresIntegrationIT.java` (new).

### Forbidden

- weakening `BootstrapAdministratorApplicationService`'s transaction boundary to make the test pass.

### Implementation requirements

- Real Spring context (`@SpringBootTest`-style, using `tmp-security`'s auto-configuration once available from STAGE4-029 — if this task lands before STAGE4-029 completes, construct the service graph manually with real JDBC repositories against the Testcontainers datasource, without full Spring Boot autoconfiguration, and revisit with `@SpringBootTest` wiring once STAGE4-029 exists; record whichever approach is actually used in the Implementation Log) against a real PostgreSQL container with `V1..V4` migrated;
- test cases: (1) bootstrap with valid config on empty DB creates admin+role+assignment+audit exactly once; (2) two concurrent bootstrap attempts (real threads, real transactions) against the same schema result in exactly one admin user, no duplicate-login DB error escaping as a startup failure for the "loser" thread; (3) missing config on empty DB throws and leaves zero rows in `users`/`roles`; (4) permission synchronization after this module's own registered Capability produces the exact 12 active `permission_definitions` rows.

### Public contracts that may change

- none.

### Acceptance criteria

- [ ] all four scenarios pass against real PostgreSQL;
- [ ] scenario (3)'s rollback leaves the DB in exactly its pre-test state (`SELECT COUNT(*) FROM security.users` = 0).

### Required tests

- `SecurityBootstrapPostgresIntegrationIT` (the task's whole deliverable).

### Verification commands

```bash
mvn -q -pl :tmp-security verify -Dit.test=SecurityBootstrapPostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Bootstrap correctness confirmed under real transactions and real concurrency.

---

## STAGE4-020 — `Session` model and `SessionContext`

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-002
**Module:** tmp-security

### Goal

Реализовать immutable in-memory `Session` и thread-safe application-wide `SessionContext` (не персистентный, без timeout).

### Required documents

- Security Specification (Пользовательская сессия);
- Stage 4 task §10 (сессия действует до logout/закрытия приложения; не хранится в PostgreSQL; не содержит password hash; thread-safe для чтения; очищается при logout/shutdown).

### Required code context

- `SessionId`/`UserId`/`Login` (STAGE4-002).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/domain/Session.java` (new);
- `tmp-security/src/main/java/com/tmp/security/application/SessionContext.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/domain/` and `.../application/`.

### Forbidden

- any field on `Session` carrying `PasswordHash`;
- persisting `Session`/`SessionContext` state to any repository/table.

### Implementation requirements

- `Session`: immutable value (`SessionId id, UserId userId, Login login, Instant startedAt`); no permission snapshot stored on `Session` itself (per Design decision reinforcing ADR-020 — effective permissions are always recomputed live via `AuthorizationApplicationService`, STAGE4-022, never cached on the session).
- `SessionContext`: a single application-scoped bean holding at most one current `Session` (single logged-in user per running desktop process, consistent with "Пользователь входит в систему при запуске приложения" — one session per process); `open(Session)`, `close()`, `current()` returns `Optional<Session>`; internally uses a `volatile` reference or `AtomicReference` for thread-safe reads without needing a lock (writes only happen on login/logout/shutdown, all rare, serialized by the caller).

### Public contracts that may change

- none in `com.tmp.security.api` yet (session read-only view exposed publicly in STAGE4-028 via a DTO, not this raw type).

### Acceptance criteria

- [ ] `SessionContext.current()` is empty before any `open()` call and after `close()`;
- [ ] concurrent reads from multiple threads while a single writer calls `open`/`close` never observe a torn/partial state (visibility guaranteed by `AtomicReference`/`volatile`).

### Required tests

- `SessionTest`: construction/immutability.
- `SessionContextTest`: open/close/current lifecycle, concurrent-read visibility (a simple multi-thread read-after-write test).

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=SessionTest,SessionContextTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A safe, non-persistent session holder ready for Authentication (STAGE4-021) and Authorization (STAGE4-022).

---

## STAGE4-021 — Authentication Application Service (login/logout/currentSession/isAuthenticated)

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-004, STAGE4-011, STAGE4-015, STAGE4-020, STAGE4-009, STAGE4-013
**Module:** tmp-security

### Goal

Реализовать вход/выход с единым generic сообщением об ошибке и аудитом обоих исходов, без раскрытия существования пользователя.

### Required documents

- Security Specification (Пароль; Пользовательская сессия);
- Stage 4 task §10 (точные требования к успешному/неуспешному входу; §12 audit — "исключение составляют события безопасности" per Database Spec §14, meaning login audit is written even though the session itself is not part of a "business" rollback boundary).

### Required code context

- `User`/`UserRepository` (STAGE4-004); `PasswordHasher` (STAGE4-003/015); `Session`/`SessionContext` (STAGE4-020); `SecurityAuditEvent`/`AuditOperation.LOGIN_SUCCESS`/`LOGIN_FAILURE`/`LOGOUT`/`SecurityAuditRepository` (STAGE4-009).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/domain/AuthenticationFailedException.java` (new, in Domain since it's a Domain-meaningful failure, thrown to the caller after the generic-message contract is applied);
- `tmp-security/src/main/java/com/tmp/security/application/AuthenticationApplicationService.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/application/`.

### Forbidden

- any branch of the failure path that returns a *different* message for "user not found" vs "wrong password" vs "user deleted" (single generic message string for all three, per Stage 4 task §10);
- logging the attempted password anywhere, success or failure.

### Implementation requirements

- `login(Login login, char[] password)`: looks up `findByLoginIgnoreCase`; if absent, or `status != ACTIVE`, or `passwordHasher.matches(password, user.passwordHash())` is `false` → append `SecurityAuditEvent(LOGIN_FAILURE, actorUserId = user's id if found else null, actorLoginSnapshot = the attempted login text, ...)` in its own transaction (audited even though authentication "failed" — Stage 4 task explicitly requires failed-login audit) and throw `AuthenticationFailedException("Неверный логин или пароль")`; if all checks pass → `sessionContext.open(new Session(...))`, append `SecurityAuditEvent(LOGIN_SUCCESS, actorUserId = user.id(), ...)` in the same transaction as the audit write (the session itself is never persisted, so "same transaction" here means the DB audit INSERT commits atomically — there is nothing else to roll back), return a session view.
- `logout()`: if a session is open, append `SecurityAuditEvent(LOGOUT, ...)`, then `sessionContext.close()`.
- `currentSession()`/`isAuthenticated()`: thin delegations to `SessionContext`.
- Every code path clears any local `char[]` copy of the password as soon as it is no longer needed (best-effort defensive clearing, documented, not a strict guarantee against a debugger, but present).

### Public contracts that may change

- none in `com.tmp.security.api` yet (the public-facing `AuthenticationService` façade is assembled in STAGE4-028 by delegating to this Application Service).

### Acceptance criteria

- [ ] unknown login, wrong password, and deleted-user login all produce the exact same exception message;
- [ ] successful login opens exactly one session and audits `LOGIN_SUCCESS`;
- [ ] every failure path audits `LOGIN_FAILURE`, including for a login that does not exist at all (audit row has `actor_user_id = NULL`, `actor_login = <attempted text>`);
- [ ] `logout()` on a session-less context is a safe no-op (no exception, no audit row);
- [ ] no test assertion or production code path ever compares the plaintext password to a logged/audited string.

### Required tests

- `AuthenticationApplicationServiceTest`: success, unknown-login failure, wrong-password failure, deleted-user failure (all three failures asserted to share the identical exception message), logout with/without active session, audit row content for each case (via a fake in-memory `SecurityAuditRepository`).

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=AuthenticationApplicationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A fully-tested Authentication Application Service ready for the public façade (STAGE4-028) and the Login Screen (STAGE4-032).

---

## STAGE4-022 — Authorization Application Service, `AccessDeniedException`, secured-operation fixture

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-008, STAGE4-020, STAGE4-017
**Module:** tmp-security

### Goal

Реализовать централизованную проверку доступа (`hasPermission`/`requirePermission`/`effectivePermissions`) с учётом активности разрешения, плюс технический fixture, демонстрирующий, что скрытие UI-команды не заменяет проверку.

### Required documents

- Stage 4 task §11 (полные требования к Authorization API; secured-operation fixture);
- this file's "Design decisions" §6.

### Required code context

- `EffectivePermissionCalculator` (STAGE4-008); `SessionContext` (STAGE4-020); `CapabilityEngine.activePermissions()` (`com.tmp.capability.api`); `UserRepository`/`RoleRepository`/`RoleAssignmentRepository`/`PermissionOverrideRepository` (loading the current user's roles/overrides for the live calculation).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/domain/AccessDeniedException.java` (new; Domain-level, since "access denied" is a Domain-meaningful outcome, not an infrastructure error);
- `tmp-security/src/main/java/com/tmp/security/application/AuthorizationApplicationService.java` (new);
- `tmp-security/src/main/java/com/tmp/security/application/securedfixture/SecuredOperationFixture.java` (new — a tiny technical demo class: one method `void performSecuredOperation(PermissionId required)` that calls `requirePermission(required)` then returns a fixed success marker; used only by tests, not wired into any real screen);
- matching tests under `tmp-security/src/test/java/com/tmp/security/application/`.

### Forbidden

- caching/storing the computed effective-permission set anywhere beyond the single method call that computed it (per ADR-020 / Design decision — always recomputed, never a session field);
- letting `requirePermission` succeed when there is no open session (absence of session ⇒ deny, per Stage 4 task §11).

### Implementation requirements

- `hasPermission(PermissionId id)`: `false` if no open session; else compute `EffectivePermissionCalculator.isGranted(id, currentUserOverrides, currentUserRoles)` **and** `id` is present in `capabilityEngine.activePermissions()` (both conditions required — inactive permission always denies, per Design decision §6); never throws.
- `requirePermission(PermissionId id)`: calls `hasPermission(id)`; if `false`, throws `AccessDeniedException` with a message that names the permission id but never leaks other users' data or password material (permission ids are not secret — they are public metadata already visible via `CapabilityEngine.activePermissions()`).
- `effectivePermissions()`: returns the full computed `Set<PermissionId>` for the current session's user (empty set if no session).
- `SecuredOperationFixture`: constructor takes an `AuthorizationApplicationService`; `performSecuredOperation(PermissionId required)` unconditionally calls `authorization.requirePermission(required)` before doing anything else — used by the fixture test (and later, STAGE4-034's UI test) to prove that even if a caller bypasses a hidden UI command, the direct call still enforces the check.

### Public contracts that may change

- none in `com.tmp.security.api` yet (public façade assembled in STAGE4-028).

### Acceptance criteria

- [ ] no session ⇒ `hasPermission` is `false` for every id, `requirePermission` always throws;
- [ ] a permission granted via role but currently inactive (its owning Capability deactivated) ⇒ denied;
- [ ] individual REVOKE overrides a role grant ⇒ denied even though role grants it and the permission is active;
- [ ] `SecuredOperationFixture.performSecuredOperation(...)` throws `AccessDeniedException` when the required permission is missing, and succeeds when granted — proving direct invocation is always checked regardless of any UI-level hiding decision made elsewhere.

### Required tests

- `AuthorizationApplicationServiceTest`: no-session denial, active+granted allow, inactive-permission denial, individual-REVOKE-over-role-grant denial, individual-GRANT-without-role allow, multi-role union allow.
- `SecuredOperationFixtureTest`: allowed vs denied direct-call behaviour.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=AuthorizationApplicationServiceTest,SecuredOperationFixtureTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A fully-tested Authorization Application Service and a reusable secured-operation fixture, ready for the public façade (STAGE4-028) and the Access Denied Screen / navigation-gating UI (STAGE4-033/034).

---

## STAGE4-023 — User Administration Application Service (create/update/logical delete)

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-004, STAGE4-011, STAGE4-015, STAGE4-022, STAGE4-009
**Module:** tmp-security

### Goal

Реализовать транзакционные операции администрирования пользователей с обязательной проверкой прав и аудитом в одной транзакции.

### Required documents

- Stage 4 task §14 (mutating operation pipeline: validate → authorize → mutate domain → persist → audit → one transaction);
- Security Specification (Администрирование).

### Required code context

- `User`/`UserRepository`/`DuplicateLoginException` (STAGE4-004/011); `PasswordHasher` (STAGE4-015); `AuthorizationApplicationService`/`SecurityPermissions.USERS_*` (STAGE4-022/016); audit types (STAGE4-009).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/application/UserAdministrationApplicationService.java` (new);
- extend `UserRepository` (STAGE4-004) with a paging/listing query method now that a real caller needs it: `findPage(int pageIndex, int pageSize, UserStatus statusFilter)` and matching `JdbcUserRepository` implementation update;
- matching tests under `tmp-security/src/test/java/com/tmp/security/application/` and updated `tmp-security/src/test/java/com/tmp/security/persistence/JdbcUserRepositoryTest.java`.

### Forbidden

- creating/updating a user without first calling `authorization.requirePermission(...)` (no operation may skip this — even for the very first bootstrap admin, which is created by a separate, non-`requirePermission`-gated bootstrap path in STAGE4-018, explicitly documented as the one exception since no session/permission exists yet at first boot);
- returning `User`/`PasswordHash` directly from any method (only DTOs, defined together with the public façade in STAGE4-028 — this task may return the Domain `User` internally to callers *within the module* for now, with the DTO mapping added in STAGE4-028, since the public API package does not exist as consumable yet outside the module boundary at this point in the sequence).

### Implementation requirements

- `createUser(Login, DisplayName, char[] initialPassword)`: `requirePermission(USERS_CREATE)`; construct `User.createActive(...)` with hashed password; `save()` (translates `DuplicateLoginException` to a clear caller-facing exception); audit `USER_CREATED`; all in one `@Transactional` method.
- `updateUser(UserId, DisplayName newDisplayName)`: `requirePermission(USERS_UPDATE)`; load, `withDisplayName`, save (optimistic lock surfaces as-is to the caller), audit `USER_UPDATED`; one transaction.
- `deleteUser(UserId)`: `requirePermission(USERS_DELETE)`; load, `deleted(clock)`, save, audit `USER_DELETED`; one transaction. (Login change is intentionally not offered as a separate operation — not required by the Security Specification and would complicate the case-insensitive-uniqueness invariant without a stated business need; noted here so the omission is a documented decision, not an oversight.)
- `listUsers(int pageIndex, int pageSize, UserStatus statusFilter)`: `requirePermission(USERS_VIEW)`; delegates to the new `UserRepository.findPage(...)`.

### Public contracts that may change

- `UserRepository` (internal Domain port) gains `findPage(...)` — internal contract, not yet public.

### Acceptance criteria

- [ ] every method throws `AccessDeniedException` and performs zero persistence when the caller lacks the required permission (verified with a fake `AuthorizationApplicationService` returning denial);
- [ ] `createUser` with a duplicate login surfaces a clear exception and persists nothing;
- [ ] `deleteUser` never physically removes the row (status becomes `DELETED`, row still present, still returned by `listUsers` when `statusFilter = DELETED` or `null`);
- [ ] each successful mutation writes exactly one audit row with the correct operation.

### Required tests

- `UserAdministrationApplicationServiceTest`: one test per acceptance-criteria bullet, using fakes for repository/authorization/audit.
- `JdbcUserRepositoryTest` (extended): `findPage` pagination/filter correctness (H2).

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=UserAdministrationApplicationServiceTest,JdbcUserRepositoryTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Fully-tested user administration, ready for password operations (STAGE4-024), the public façade (STAGE4-028), and the User Administration Screen (STAGE4-035).

---

## STAGE4-024 — Password change / reset Application Services

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-023
**Module:** tmp-security

### Goal

Реализовать самостоятельную смену пароля (с проверкой старого) и административный сброс (с проверкой прав, без старого пароля), с аудитом в одной транзакции.

### Required documents

- Stage 4 task §9 (точные требования: old-password check for self-change; reset needs no old password but needs permission; hash differs each time; никогда в audit/exception/log).

### Required code context

- `User`/`UserRepository` (STAGE4-004); `PasswordHasher` (STAGE4-015); `AuthorizationApplicationService`/`SecurityPermissions.USERS_RESET_PASSWORD` (STAGE4-022/016); `SessionContext` (STAGE4-020, to identify "self" for self-change); audit types (STAGE4-009).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/application/PasswordApplicationService.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/InvalidCurrentPasswordException.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/application/`.

### Forbidden

- accepting an old-password parameter on the admin-reset method (its absence *is* the enforcement — no parameter to check means no old-password verification path exists at all, matching the spec exactly);
- including any password/hash value in `InvalidCurrentPasswordException`'s message.

### Implementation requirements

- `changeOwnPassword(char[] currentPassword, char[] newPassword)`: requires an open session (self-service, no explicit `requirePermission` call — every authenticated user may change their own password, per Security Specification "Пользователь может самостоятельно изменить пароль"); loads the current session's user, verifies `passwordHasher.matches(currentPassword, user.passwordHash())`, else throws `InvalidCurrentPasswordException`; on success, `withPasswordHash(hash(newPassword))`, save, audit `PASSWORD_CHANGED` (actor = self); one transaction.
- `resetPassword(UserId targetUserId, char[] newPassword)`: `requirePermission(USERS_RESET_PASSWORD)`; loads target user (any caller, including resetting another admin's password — no additional restriction stated in the spec), `withPasswordHash(hash(newPassword))`, save, audit `PASSWORD_RESET` (actor = the acting admin, target = `targetUserId`); one transaction; no old-password parameter exists.

### Public contracts that may change

- none new beyond this task's internal types.

### Acceptance criteria

- [ ] `changeOwnPassword` with a wrong current password throws `InvalidCurrentPasswordException` and persists nothing;
- [ ] `changeOwnPassword` with the correct current password updates the hash and audits `PASSWORD_CHANGED`;
- [ ] `resetPassword` without `USERS_RESET_PASSWORD` throws `AccessDeniedException` and persists nothing;
- [ ] `resetPassword` with permission updates the hash and audits `PASSWORD_RESET`, with no old-password check performed (verified by the method signature itself having no such parameter, and by a test that resets successfully without ever supplying/knowing the old password);
- [ ] neither method's exception message nor audit `safeDescription` ever contains a password or hash value (property-based-style test: construct with several distinct password values, assert none appear in any thrown exception's message or in the captured audit event).

### Required tests

- `PasswordApplicationServiceTest`: all acceptance-criteria bullets, using fakes.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=PasswordApplicationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Fully-tested password operations, ready for the public façade (STAGE4-028) and the User Administration Screen (STAGE4-035).

---

## STAGE4-025 — Role Administration Application Service (create/update/delete-if-unassigned)

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-005, STAGE4-011, STAGE4-012, STAGE4-022
**Module:** tmp-security

### Goal

Реализовать транзакционные операции администрирования ролей, включая запрет удаления назначенной роли.

### Required documents

- Stage 4 task §6 (удаление роли только при отсутствии назначенных пользователей), §14 (transaction pipeline).

### Required code context

- `Role`/`RoleRepository` (STAGE4-005); `RoleAssignmentRepository.countUsersForRole` (STAGE4-007/012); `AuthorizationApplicationService`/`SecurityPermissions.ROLES_*` (STAGE4-022/016); audit types (STAGE4-009).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/application/RoleAdministrationApplicationService.java` (new);
- `tmp-security/src/main/java/com/tmp/security/domain/RoleInUseException.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/application/`.

### Forbidden

- deleting a role's `role_permissions` rows without deleting the role itself in the same operation (no orphaned permission rows — handled naturally by the FK `ON DELETE` default `NO ACTION`, meaning delete must be blocked at the application layer *before* attempting the SQL delete, which this task implements; no cascading delete is introduced).

### Implementation requirements

- `createRole(String name, String description)`: `requirePermission(ROLES_CREATE)`; `Role.create(...)`, save, audit `ROLE_CREATED`; one transaction.
- `updateRole(RoleId, String name, String description)`: `requirePermission(ROLES_UPDATE)`; load, `withName`/`withDescription`, save, audit `ROLE_UPDATED`; one transaction.
- `grantPermissionToRole(RoleId, PermissionId)` / `revokePermissionFromRole(RoleId, PermissionId)`: `requirePermission(PERMISSIONS_ASSIGN)`; load, `grantPermission`/`revokePermission`, save, audit `ROLE_PERMISSIONS_CHANGED`; one transaction each.
- `deleteRole(RoleId)`: `requirePermission(ROLES_DELETE)`; if `roleAssignmentRepository.countUsersForRole(id) > 0` → throw `RoleInUseException` (no persistence performed); else `roleRepository.deleteById(id)`, audit `ROLE_DELETED`; one transaction.

### Public contracts that may change

- none new beyond this task's internal types.

### Acceptance criteria

- [ ] each method enforces its stated permission and performs zero persistence on denial;
- [ ] `deleteRole` on a role with ≥1 assigned user throws `RoleInUseException` and does not delete the row;
- [ ] `deleteRole` on an unassigned role succeeds and audits `ROLE_DELETED`;
- [ ] `grantPermissionToRole` is idempotent (calling twice with the same permission does not error, per `Role.grantPermission`'s idempotency from STAGE4-005) and audits once per call regardless.

### Required tests

- `RoleAdministrationApplicationServiceTest`: one test per acceptance-criteria bullet, using fakes.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=RoleAdministrationApplicationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Fully-tested role administration, ready for the public façade (STAGE4-028) and the Role Administration Screen (STAGE4-036).

---

## STAGE4-026 — Role assignment/revocation and individual permission grant/revoke/remove-override Application Services

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-007, STAGE4-012, STAGE4-022, STAGE4-025
**Module:** tmp-security

### Goal

Реализовать назначение/отзыв ролей пользователю и индивидуальные GRANT/REVOKE/удаление override, с аудитом в одной транзакции.

### Required documents

- Stage 4 task §6 (assign/revoke role; individual grant/revoke; §12 audit list — assignment, revocation, individual GRANT, individual REVOKE, override removal).

### Required code context

- `RoleAssignmentRepository`/`PermissionOverrideRepository` (STAGE4-007/012); `AuthorizationApplicationService`/`SecurityPermissions.ROLES_ASSIGN`/`PERMISSIONS_ASSIGN` (STAGE4-022/016); `UserRepository`/`RoleRepository` (existence checks); audit types (STAGE4-009).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/application/RoleAssignmentApplicationService.java` (new);
- `tmp-security/src/main/java/com/tmp/security/application/PermissionOverrideApplicationService.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/application/`.

### Forbidden

- assigning a role or granting a permission to a `DELETED` user (validated by loading the `User` and checking `isActive()` before mutating the assignment/override tables — a deleted user "остаётся доступным для аудита и исторических ссылок" but not for new grants).

### Implementation requirements

- `RoleAssignmentApplicationService.assignRole(UserId, RoleId)`: `requirePermission(ROLES_ASSIGN)`; verify target user is `ACTIVE` and role exists; `roleAssignmentRepository.assign(...)`; audit `ROLE_ASSIGNED`; one transaction.
- `RoleAssignmentApplicationService.revokeRole(UserId, RoleId)`: `requirePermission(ROLES_ASSIGN)`; `roleAssignmentRepository.revoke(...)`; audit `ROLE_REVOKED`; one transaction.
- `PermissionOverrideApplicationService.grantIndividualPermission(UserId, PermissionId)` / `revokeIndividualPermission(UserId, PermissionId)`: `requirePermission(PERMISSIONS_ASSIGN)`; verify target user is `ACTIVE`; `permissionOverrideRepository.save(IndividualPermissionOverride.of(..., GRANT|REVOKE, clock))`; audit `PERMISSION_GRANTED`/`PERMISSION_REVOKED`; one transaction.
- `PermissionOverrideApplicationService.removeOverride(UserId, PermissionId)`: `requirePermission(PERMISSIONS_ASSIGN)`; `permissionOverrideRepository.remove(...)`; audit `PERMISSION_OVERRIDE_REMOVED`; one transaction.

### Public contracts that may change

- none new beyond this task's internal types.

### Acceptance criteria

- [ ] each method enforces its stated permission and performs zero persistence on denial;
- [ ] assigning a role to a `DELETED` user is rejected with a clear domain exception, no row written;
- [ ] flipping an override from `GRANT` to `REVOKE` (or vice versa) on the same user/permission updates the single existing row (no duplicate row), audited each time it's called;
- [ ] `removeOverride` on a non-existent override is a safe no-op (no exception), still audits `PERMISSION_OVERRIDE_REMOVED` only if a row actually existed to remove (verified by return-value/repository state, not by audit-count alone, to avoid over-specifying "no-op audit" behaviour beyond what's testable).

### Required tests

- `RoleAssignmentApplicationServiceTest`, `PermissionOverrideApplicationServiceTest`: one test per acceptance-criteria bullet, using fakes.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=RoleAssignmentApplicationServiceTest,PermissionOverrideApplicationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Fully-tested assignment/override administration, ready for the public façade (STAGE4-028) and the Role Administration Screen (STAGE4-036).

---

## STAGE4-027 — Audit Query Application Service (read-only, paginated/filtered)

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-013, STAGE4-022
**Module:** tmp-security

### Goal

Реализовать read-only просмотр аудита, доступный только с разрешением `security.audit.view`.

### Required documents

- Stage 4 task §12 ("Audit API read-only"), §18 (pagination/filtering).

### Required code context

- `SecurityAuditRepository`/`AuditQueryFilter` (STAGE4-009/013); `AuthorizationApplicationService`/`SecurityPermissions.AUDIT_VIEW` (STAGE4-022/016).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/application/AuditQueryApplicationService.java` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/application/`.

### Forbidden

- any mutating method on this class (query-only, matching "Audit API read-only" literally — the class has no method that writes).

### Implementation requirements

- `queryAuditEvents(AuditQueryFilter filter, int pageIndex, int pageSize)`: `requirePermission(AUDIT_VIEW)`; delegates to `securityAuditRepository.findPage(...)`, returns Domain `SecurityAuditEvent` list to internal callers (DTO mapping added in STAGE4-028) plus `countAuditEvents(filter)` for total-count/pagination UI needs, also `requirePermission`-gated.

### Public contracts that may change

- none new beyond this task's internal type.

### Acceptance criteria

- [ ] both methods throw `AccessDeniedException` without `AUDIT_VIEW`;
- [ ] with permission, results match the underlying repository's filtered/paginated output exactly.

### Required tests

- `AuditQueryApplicationServiceTest`: denial without permission, pass-through correctness with permission, using fakes.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=AuditQueryApplicationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A fully-tested, read-only audit query service, ready for the public façade (STAGE4-028) and the Security Audit Screen (STAGE4-037).

---

## STAGE4-028 — Public `com.tmp.security.api` facade types, DTOs, and exceptions

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-021, STAGE4-022, STAGE4-023, STAGE4-024, STAGE4-025, STAGE4-026, STAGE4-027
**Module:** tmp-security

### Goal

Собрать окончательные публичные контракты `com.tmp.security.api`, делегирующие ко всем ранее реализованным Application Services, без единого поля/метода, раскрывающего пароль или его хеш.

### Required documents

- Stage 4 task §3 (публичный API `com.tmp.security.api..`; внутренняя реализация недоступна другим модулям), §10/§11 (Authentication/Authorization API shapes), §14 (application services list).

### Required code context

- every Application Service produced in STAGE4-017..027 (this task only wires/wraps, adds no new business logic — "Application Services будущих модулей должны использовать публичный Authorization API Security" means these wrappers are the *only* thing external modules ever call).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/api/AuthenticationService.java` (new, interface: `login`, `logout`, `currentSession`, `isAuthenticated`);
- `tmp-security/src/main/java/com/tmp/security/api/AuthorizationService.java` (new, interface: `hasPermission`, `requirePermission`, `effectivePermissions`);
- `tmp-security/src/main/java/com/tmp/security/api/UserAdministrationService.java` (new, interface wrapping STAGE4-023/024);
- `tmp-security/src/main/java/com/tmp/security/api/RoleAdministrationService.java` (new, interface wrapping STAGE4-025/026);
- `tmp-security/src/main/java/com/tmp/security/api/AuditQueryService.java` (new, interface wrapping STAGE4-027);
- `tmp-security/src/main/java/com/tmp/security/api/AccessDeniedException.java` (new, public re-throwable type — or expose the Domain one directly if its package placement already satisfies "public API"; **decision**: move `AccessDeniedException`/`AuthenticationFailedException` from `com.tmp.security.domain` into `com.tmp.security.api` in this task, since external callers must be able to catch them by type, matching how `com.tmp.capability.api.DependencyValidationException` lives in the Capability Engine's `api` package rather than an internal one);
- DTOs: `UserSummary`, `RoleSummary`, `PermissionSummary`, `AuditEventSummary`, `SessionSummary` (new, in `com.tmp.security.api`, immutable records/classes, **no** `PasswordHash`/password field anywhere);
- `tmp-security/src/main/java/com/tmp/security/api/package-info.java` update (finalize documentation);
- adjust STAGE4-021/022 files to move the two exception classes as described (small, mechanical, within this task's allowed scope since it directly serves this task's goal).

### Forbidden

- any DTO field typed `PasswordHash`, `char[]` password, or raw hash `String`;
- any internal Domain/Application/Persistence type appearing in a public method signature (only `com.tmp.security.api` types + `java.*`/`com.tmp.core.api`/`com.tmp.capability.api` types are allowed in public method signatures).

### Implementation requirements

- Each public interface's implementation is a thin adapter class in `com.tmp.security.application` (e.g. `DefaultAuthenticationService implements AuthenticationService`) that maps DTOs ↔ Domain types and delegates to the corresponding Application Service — no new business rule is introduced here.
- `UserSummary`/etc. carry only display-safe fields (`UserId`, `Login`, `DisplayName`, `UserStatus`, `version`, timestamps for `UserSummary` — explicitly no `passwordHash` field, not even a redacted placeholder field, since the type should not even hint at internal hashing details).

### Public contracts that may change

- this task **is** the introduction of the stable public API surface: `AuthenticationService`, `AuthorizationService`, `UserAdministrationService`, `RoleAdministrationService`, `AuditQueryService`, `AccessDeniedException`, `AuthenticationFailedException`, `UserSummary`, `RoleSummary`, `PermissionSummary`, `AuditEventSummary`, `SessionSummary`, plus the identity value objects already introduced in STAGE4-002.

### Acceptance criteria

- [ ] reflective scan of every class in `com.tmp.security.api` finds no field/method whose declared type is `PasswordHash`, `char[]`, or contains the substring "hash"/"password" in a way that could return credential material (a dedicated ArchUnit-style or reflection-based unit test enforces this, run now and again in STAGE4-039's architecture test);
- [ ] every public interface method compiles and delegates correctly to its backing Application Service (verified by adapter unit tests);
- [ ] `toString()` of every DTO never contains a password/hash value (there is none to contain, by construction).

### Required tests

- `SecurityApiSurfaceNoCredentialLeakTest` (reflection-based, scans `com.tmp.security.api` classes/records for any field/return type matching a small denylist of type names — `PasswordHash`, `char[]`, "Hash" in a suspicious position).
- `DefaultAuthenticationServiceTest`, `DefaultAuthorizationServiceTest`, `DefaultUserAdministrationServiceTest`, `DefaultRoleAdministrationServiceTest`, `DefaultAuditQueryServiceTest`: adapter delegation correctness (thin, mostly mapping-verification tests).

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=SecurityApiSurfaceNoCredentialLeakTest,DefaultAuthenticationServiceTest,DefaultAuthorizationServiceTest,DefaultUserAdministrationServiceTest,DefaultRoleAdministrationServiceTest,DefaultAuditQueryServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

The complete, stable `com.tmp.security.api` surface that `tmp-ui-shell` and any future module will depend on.

---

## STAGE4-029 — Security Spring auto-configuration, `PlatformComponent`, and startup wiring

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-028, STAGE4-018, STAGE4-016
**Module:** tmp-security

### Goal

Собрать единственные beans для facade/services/`PasswordEncoder`/`SessionContext`, зарегистрировать Security как Platform Component, обеспечить порядок запуска (persistence → permission sync → bootstrap admin).

### Required documents

- Stage 4 task §16 (все требования к auto-configuration и bean cardinality);
- this file's "Design decisions" §7.

### Required code context

- `CapabilityEngineAutoConfiguration`/`CapabilityEnginePlatformComponent` (`tmp-capability-engine`, precedent for `@AutoConfigureAfter` + `@PostConstruct` component registration pattern);
- `DocumentEngineAutoConfiguration`/`DocumentEnginePlatformRegistrar` (secondary precedent).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/SecurityAutoConfiguration.java` (new);
- `tmp-security/src/main/java/com/tmp/security/SecurityPlatformComponent.java` (new, package-private);
- `tmp-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (new);
- matching tests under `tmp-security/src/test/java/com/tmp/security/`.

### Forbidden

- defining more than one bean of any public façade/service/`PasswordEncoder`/`SessionContext` type (enforced by simply not declaring duplicates — verified by a Spring context test asserting exactly one bean per type, same style as `CapabilityEngineAutoConfigurationTest`);
- calling `bootstrapAdministratorApplicationService.ensureBootstrapAdministrator()` from anywhere other than `SecurityPlatformComponent.initialize()`/`start()` (single, well-defined trigger point).

### Implementation requirements

- `SecurityAutoConfiguration`: `@AutoConfiguration` `@AutoConfigureAfter(name = {"com.tmp.core.PlatformCoreAutoConfiguration", "com.tmp.infra.db.DatabaseAutoConfiguration", "com.tmp.capability.CapabilityEngineAutoConfiguration"})`; `@Bean` methods for: `BCryptPasswordHasher` (as the sole `PasswordHasher`), all `Jdbc*Repository` adapters, `SessionContext`, every Application Service (STAGE4-017..027), every public façade adapter (STAGE4-028), `SecurityAdministrationCapability` (STAGE4-016, so it's discoverable in `List<Capability>`), `SecurityPlatformComponent`, and a `@PostConstruct`-driven registrar (mirroring `CapabilityEnginePlatformRegistrar`) that calls `platformCore.registerComponent(securityPlatformComponent)`.
- `SecurityPlatformComponent implements PlatformComponent`: `metadata()` = `ComponentType.SERVICE`, id `"security"`; `initialize(PlatformCore)` calls `permissionSynchronizationApplicationService.synchronize()` then `bootstrapAdministratorApplicationService.ensureBootstrapAdministrator()`; `start()`/`stop()` are no-ops (nothing further to start/stop — sessions are cleared explicitly on logout/shutdown by the UI/bootstrap layer, STAGE4-038, not by this component's lifecycle).
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` lists `com.tmp.security.SecurityAutoConfiguration`.

### Public contracts that may change

- none (pure wiring task).

### Acceptance criteria

- [ ] full Spring context (`tmp-bootstrap-app`-style test, or a focused `@SpringBootTest` within `tmp-security` using H2/Testcontainers) starts with exactly one bean of each public façade/service type;
- [ ] `SecurityPlatformComponent` registers and initializes strictly after `CapabilityEnginePlatformComponent` (verified via `LifecycleManager.allStates()`/ordering assertion, or via a synchronization-sensitive fake `CapabilityEngine` that records whether `activateAll()` was already called when Security's `initialize()` runs).

### Required tests

- `SecurityAutoConfigurationTest` (bean-cardinality + ordering, mirroring `CapabilityEngineAutoConfigurationTest`).

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=SecurityAutoConfigurationTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A fully-wired, single-bean-per-contract Security module, ready to be added as a dependency of `tmp-bootstrap-app`/`tmp-ui-shell`/`tmp-architecture-tests`.

---

## STAGE4-030 — `tmp-security` end-to-end PostgreSQL Testcontainers IT (mutation+audit same-transaction rollback, full role/permission flow)

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-029
**Module:** tmp-security

### Goal

Подтвердить полный поток (bootstrap → login → создание пользователя/роли → назначение → эффективные права → аудит) на реальном PostgreSQL с реальным Spring-контекстом, включая rollback mutation+audit при ошибке.

### Required documents

- Stage 4 task §18 (mutation+audit rollback; permission synchronization; inactive permission denial — full behavioural confirmation, closing out what STAGE4-014/019 left to Authorization/Authentication).

### Required code context

- `SecurityBootstrapPostgresIntegrationIT` (STAGE4-019) as the real-Spring-context precedent (revisited here with the now-complete `SecurityAutoConfiguration` from STAGE4-029).

### Allowed code scope

- `tmp-security/src/test/java/com/tmp/security/SecurityEndToEndPostgresIntegrationIT.java` (new).

### Forbidden

- any production code change to make this IT pass (a genuine defect found here re-opens the relevant earlier task, it is not patched ad hoc in this test-only task).

### Implementation requirements

- Full `@SpringBootTest`-style context against a real PostgreSQL Testcontainers instance with the complete `tmp-security` auto-configuration active;
- scenarios: bootstrap admin creation → admin login succeeds; admin creates a second user + a role with 2 permissions, assigns role, grants one individual override → `effectivePermissions()` for that user matches the expected union; forcing a simulated failure between the domain mutation and the audit write (e.g. a role-permission grant where the audit insert is made to fail via a test-only hook/spy) rolls back **both** the mutation and the audit row (transaction atomicity, per Stage 4 task §14); deactivating Security's own Capability (test-only toggle) causes a previously-granted `security.audit.view`-style check to be denied even though the role still lists it — demonstrating inactive-permission denial end-to-end.

### Public contracts that may change

- none.

### Acceptance criteria

- [ ] all scenarios above pass against real PostgreSQL with the real Spring wiring from STAGE4-029.

### Required tests

- `SecurityEndToEndPostgresIntegrationIT` (the task's whole deliverable).

### Verification commands

```bash
mvn -q -pl :tmp-security verify -Dit.test=SecurityEndToEndPostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

End-to-end Security correctness confirmed against real PostgreSQL with the fully wired module — closes out all `tmp-security`-internal Stage 4 work before UI tasks begin.

---

## STAGE4-031 — `tmp-ui-shell` Spring wiring and Navigation Service foundation

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-028
**Module:** tmp-ui-shell

### Goal

Ввести Spring в `tmp-ui-shell` впервые, создать generic Navigation Service (screen registry + FXML loader утилита), не создавая ни одного конкретного экрана в этой задаче.

### Required documents

- UI/UX Specification (Архитектура UI; FXML; Controller; ViewModel; JavaFX и Spring);
- this file's "Design decisions" §8, §9.

### Required code context

- current `tmp-ui-shell` classes (`JavaFxShellApplication`, `JavaFxShellLauncher`, `EmptyMainShell` — all read in full during Stage 4 planning);
- `CapabilityEngineAutoConfiguration`/`DocumentEngineAutoConfiguration` as `@AutoConfiguration` + `META-INF/spring/...imports` precedent.

### Allowed code scope

- `tmp-ui-shell/pom.xml` (add `spring-boot-starter`, `com.tmp:tmp-security`, `com.tmp:tmp-capability-engine`, `com.tmp:tmp-platform-core` dependencies; test-scope `spring-boot-starter-test`);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/navigation/ScreenRegistration.java` (new — record: `screenId, fxmlClasspathResource, Supplier<Object> viewModelSupplier`);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/navigation/NavigationService.java` (new — interface: `register(ScreenRegistration)`, `Parent load(String screenId)`; a small `ViewModelAware<T>` marker interface `setViewModel(T viewModel)` that Controllers may implement);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/navigation/DefaultNavigationService.java` (new, package-private);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/UiShellAutoConfiguration.java` (new, `@AutoConfiguration` `@AutoConfigureAfter(name = "com.tmp.security.SecurityAutoConfiguration")`, defines the `NavigationService` bean);
- `tmp-ui-shell/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (new);
- matching tests under `tmp-ui-shell/src/test/java/com/tmp/ui/shell/navigation/`.

### Forbidden

- creating any concrete `.fxml` file or screen-specific Controller/ViewModel in this task (pure infrastructure — screens follow in STAGE4-032..037);
- making `NavigationService` depend on any concrete screen type.

### Implementation requirements

- `DefaultNavigationService.load(String screenId)`: looks up the registered `ScreenRegistration`, creates an `FXMLLoader` for the classpath resource, calls `load()` (default controller factory — reflection via `fx:controller`), then if the resulting controller implements `ViewModelAware`, calls `setViewModel(viewModelSupplier.get())`; returns the loaded `Parent`.
- `register(ScreenRegistration)` rejects a duplicate `screenId` (`IllegalStateException`) to catch wiring mistakes early.

### Public contracts that may change

- none (internal `tmp-ui-shell` infrastructure, no `tmp-ui-shell` public API package exists/is required by the Stage 4 task).

### Acceptance criteria

- [ ] `tmp-ui-shell` now has a Spring `ApplicationContext` bootable in tests (`spring-boot-starter` present, auto-configuration imports file present);
- [ ] `NavigationService.load(...)` on a registered screen id returns a non-null `Parent` and, for a test Controller implementing `ViewModelAware`, correctly injects the supplied ViewModel;
- [ ] registering a duplicate screen id throws.

### Required tests

- `DefaultNavigationServiceTest`: a tiny in-test FXML fixture (a trivial `.fxml` + Controller under `tmp-ui-shell/src/test/resources`/`src/test/java`) proving load + ViewModel injection + duplicate-id rejection.

### Verification commands

```bash
mvn -q -pl :tmp-ui-shell test -Dtest=DefaultNavigationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A generic, screen-agnostic Navigation Service ready for the six concrete screens (STAGE4-032..037).

---

## STAGE4-032 — Login Screen (FXML/Controller/ViewModel) and login-gated startup

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-031, STAGE4-028
**Module:** tmp-ui-shell (+ tmp-bootstrap-app wiring)

### Goal

Реализовать экран входа (FXML/Controller/ViewModel), интегрированный с `AuthenticationService`, отображаемый до открытия рабочего места.

### Required documents

- UI/UX Specification (Обязательные технические экраны — экран входа; Сообщения пользователю — ошибки без stack trace);
- Security Specification (Пользовательская сессия); Stage 4 task §15 (Login Screen requirements exactly).

### Required code context

- `AuthenticationService`/`SessionSummary`/`AuthenticationFailedException` (`com.tmp.security.api`, STAGE4-028);
- `NavigationService`/`ScreenRegistration`/`ViewModelAware` (STAGE4-031).

### Allowed code scope

- `tmp-ui-shell/src/main/resources/com/tmp/ui/shell/screen/login/LoginScreen.fxml` (new);
- `tmp-ui-shell/src/main/resources/com/tmp/ui/shell/screen/login/LoginScreen.css` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/login/LoginController.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/login/LoginViewModel.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/UiShellAutoConfiguration.java` (extend: register the login screen + `LoginViewModel` bean);
- matching tests under `tmp-ui-shell/src/test/java/com/tmp/ui/shell/screen/login/`.

### Forbidden

- `LoginController` implementing `ApplicationContextAware`, being annotated `@Component`/`@Controller`, or holding any Spring reference (per UI/UX Spec Controller rules — it only implements `ViewModelAware<LoginViewModel>` and talks to the injected ViewModel);
- displaying the underlying `AuthenticationFailedException`'s stack trace or any technical detail beyond its message.

### Implementation requirements

- `LoginScreen.fxml`: login `TextField`, password `PasswordField`, login `Button`, an error `Label` (hidden by default), no self-registration/password-recovery control anywhere on the screen (matching Stage 4 task §15 exactly).
- `LoginViewModel` (Spring `@Bean`, not `@Component`, defined explicitly in `UiShellAutoConfiguration`): exposes JavaFX properties (`StringProperty login`, `StringProperty errorMessage`) and a `submit(char[] password)` method that calls `authenticationService.login(...)`; on `AuthenticationFailedException`, sets `errorMessage` to the caught exception's message (already the generic, safe message from STAGE4-021) and returns `false`; on success returns `true` and exposes the resulting `SessionSummary`.
- `LoginController implements ViewModelAware<LoginViewModel>`: wires FXML fields to the ViewModel's properties/bindings, calls `submit(...)` on button click, shows/hides the error label based on `errorMessage` being blank.
- Bootstrap integration: `DesktopBootstrap` now looks up the `NavigationService` bean (via the Spring context it already holds) and passes a small `UiShellEntryPoint` (new tiny record/interface in `tmp-ui-shell`, containing the `NavigationService` and the initial screen id `"login"`) into `JavaFxShellLauncher.launch(...)` (extending its static-field pattern per Design decision §9); `JavaFxShellApplication.start(Stage)` now calls `navigationService.load("login")` and sets it as the scene root, instead of `EmptyMainShell.attach(...)` directly (the Main Window flow itself is completed in STAGE4-033 — for this task, a successful login may temporarily just show a placeholder "Login OK" state, finalized in STAGE4-033).

### Public contracts that may change

- none in `com.tmp.security.api`; `tmp-ui-shell` internal additions only.

### Acceptance criteria

- [ ] wrong credentials show the exact generic message from STAGE4-021, no stack trace visible anywhere in the UI;
- [ ] correct credentials call `authenticationService.login(...)` successfully and the ViewModel reports success;
- [ ] `LoginController` has zero Spring imports and is not annotated as a Spring bean.

### Required tests

- `LoginViewModelTest`: success/failure paths against a fake `AuthenticationService`, error-message propagation, no stack-trace-string leakage into `errorMessage`.
- `LoginControllerFxTest` (JavaFX unit test, matching `JavaFxShellSmokeTest`'s existing JavaFX-test-harness convention in this module): field bindings and button-click wiring against a fake `LoginViewModel`.

### Verification commands

```bash
mvn -q -pl :tmp-ui-shell test -Dtest=LoginViewModelTest,LoginControllerFxTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A working Login Screen gating application startup, ready for the Main Window redesign (STAGE4-033).

---

## STAGE4-033 — Main Window redesign: permission-filtered navigation and logout

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-032, STAGE4-022
**Module:** tmp-ui-shell

### Goal

Заменить `EmptyMainShell` реальным главным окном с панелью навигации, построенной из активных Capability и отфильтрованной по правам, плюс действие "выход".

### Required documents

- UI/UX Specification (Главное окно; Навигация; Architecture Rules AR-005); Stage 4 task §15/§16 (navigation filtered by permissions; logout returns Login Screen).

### Required code context

- `CapabilityEngine.activeNavigation()`/`activeCommands()`/`activeViews()` (`com.tmp.capability.api`); `AuthorizationService.hasPermission(...)` (`com.tmp.security.api`, STAGE4-028); `AuthenticationService.logout()`; this file's "Design decisions" §8 (navigation-id ↔ command-id gating convention).

### Allowed code scope

- `tmp-ui-shell/src/main/resources/com/tmp/ui/shell/screen/main/MainWindow.fxml` (new);
- `tmp-ui-shell/src/main/resources/com/tmp/ui/shell/screen/main/MainWindow.css` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/main/MainWindowController.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/main/MainWindowViewModel.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/UiShellAutoConfiguration.java` (extend: register main window screen + ViewModel);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/EmptyMainShell.java` (remove — fully superseded; confirm no remaining production reference before deleting; test file removed alongside if it becomes dead code);
- matching tests under `tmp-ui-shell/src/test/java/com/tmp/ui/shell/screen/main/`.

### Forbidden

- hardcoding a static list of navigation items (must be built from `CapabilityEngine.activeNavigation()` every time the window opens/refreshes, per UI/UX Spec "Навигация строится автоматически");
- allowing a hidden (permission-denied) navigation item to still be reachable via any enabled control on this screen (the Access Denied Screen's own bypass-prevention proof is STAGE4-034's concern, using the fixture; this screen's own job is simply to not render/enable what the user cannot see).

### Implementation requirements

- `MainWindowViewModel`: on construction/refresh, iterates `capabilityEngine.activeNavigation()`; for each item, looks up the matching `CommandDescriptor` (by `navigationId == commandId`, per Design decision §8) among `activeCommands()`; if found, item is shown only if `authorizationService.hasPermission(...)` holds for every `requiredPermissionIds()`; if no matching command exists, item is shown unconditionally (unrestricted navigation item); exposes an observable list of visible nav items (id + display name) and a `selectNavigation(String navigationId)` method that resolves the item's `viewId` and calls `navigationService.load(viewId)` to swap the content area; also exposes `logout()` delegating to `authenticationService.logout()`.
- `MainWindowController`: BorderPane layout (top bar, left navigation list, center content area, bottom status bar — reusing the existing `EmptyMainShell` visual structure as a starting point, per UI/UX Spec's main-window diagram), wires the nav list to `selectNavigation`, wires a logout button to `logout()` and then instructs the shell (via a callback passed at construction, not via Spring) to return to the Login Screen.
- Remove `EmptyMainShell`/its test once `MainWindowController` fully supersedes it and `JavaFxShellApplication` no longer references it.

### Public contracts that may change

- none in `com.tmp.security.api`/`com.tmp.capability.api`.

### Acceptance criteria

- [ ] a navigation item whose command's required permission is missing does not appear in the rendered list;
- [ ] an unrestricted navigation item (no matching command) always appears;
- [ ] logout clears the session (`SessionContext.current()` becomes empty after) and the shell returns to the Login Screen;
- [ ] `EmptyMainShell` is fully removed with no leftover references (verified by compilation).

### Required tests

- `MainWindowViewModelTest`: permission-filtered visibility (granted/denied/unrestricted item), navigation selection triggers the correct `NavigationService.load` call, logout delegates correctly.
- `MainWindowControllerFxTest`: layout wiring against a fake ViewModel.

### Verification commands

```bash
mvn -q -pl :tmp-ui-shell test -Dtest=MainWindowViewModelTest,MainWindowControllerFxTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A real, permission-aware Main Window replacing the Stage 0 placeholder, ready to host the three admin screens (STAGE4-035..037) and the Access Denied Screen (STAGE4-034).

---

## STAGE4-034 — Access Denied Screen and secured-operation UI bypass-prevention proof

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-033, STAGE4-022
**Module:** tmp-ui-shell

### Goal

Реализовать экран отсутствия доступа и продемонстрировать, что скрытие UI-команды не заменяет проверку доступа (прямой вызов защищённой операции всё равно проверяется).

### Required documents

- UI/UX Specification (Обязательные технические экраны — экран отсутствия доступа); Stage 4 task §11 (secured-operation fixture: UI hides command; direct call still enforces; bypass not possible), §15 (Access Denied Screen).

### Required code context

- `AccessDeniedException` (`com.tmp.security.api`, STAGE4-028); `SecuredOperationFixture` (`com.tmp.security.application`, STAGE4-022 — used here as the concrete demonstration target, exposed to `tmp-ui-shell` tests through a small public wrapper if needed, or referenced directly since it is package-private to `tmp-security` — **decision**: add a thin public `com.tmp.security.api.SecuredOperationDemo` wrapping the fixture, since `tmp-ui-shell`, as an external module, cannot reach `com.tmp.security.application` at all).

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/api/SecuredOperationDemo.java` (new, thin public wrapper delegating to `SecuredOperationFixture` — small addition to `tmp-security`, justified because this task's core goal is proving the UI cannot bypass authorization, which requires a public entry point);
- `tmp-security/src/main/java/com/tmp/security/SecurityAutoConfiguration.java` (extend: register `SecuredOperationDemo` bean);
- `tmp-ui-shell/src/main/resources/com/tmp/ui/shell/screen/accessdenied/AccessDeniedScreen.fxml` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/accessdenied/AccessDeniedController.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/accessdenied/AccessDeniedViewModel.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/UiShellAutoConfiguration.java` (extend: register access-denied screen);
- matching tests under `tmp-security/src/test/java/com/tmp/security/api/` and `tmp-ui-shell/src/test/java/com/tmp/ui/shell/screen/accessdenied/`.

### Forbidden

- making the Access Denied Screen itself perform any authorization check (it only displays a message — the check already happened before this screen was shown, exactly matching "Скрытие UI-команды не является проверкой доступа" / "Окончательная проверка выполняется непосредственно перед защищённой операцией").

### Implementation requirements

- `SecuredOperationDemo` (public, `com.tmp.security.api`): single method `performSecuredOperation(PermissionId required)`, delegates straight to the internal fixture, throwing `AccessDeniedException` on denial exactly like the fixture.
- `AccessDeniedScreen.fxml`/`Controller`/`ViewModel`: displays a fixed, non-technical message (e.g. "У вас нет доступа к этой операции.") and a "Back" action; `AccessDeniedViewModel` takes the triggering `AccessDeniedException`'s message as display text, no stack trace shown.
- Bypass-prevention proof test (the real deliverable of this task): a test that (a) as a user without the required permission, confirms `MainWindowViewModel` hides the corresponding navigation item (reusing STAGE4-033's mechanism), **and** (b) directly calls `SecuredOperationDemo.performSecuredOperation(sameRequiredPermission)` bypassing the UI entirely, and asserts it still throws `AccessDeniedException` — proving hiding the command is cosmetic only.

### Public contracts that may change

- new public type `com.tmp.security.api.SecuredOperationDemo`.

### Acceptance criteria

- [ ] the navigation item is hidden for a permission-lacking user (reusing STAGE4-033 behaviour);
- [ ] `SecuredOperationDemo.performSecuredOperation(...)` called directly (no UI involved) still throws `AccessDeniedException` for that same user/permission;
- [ ] the Access Denied Screen shows no stack trace and no technical detail.

### Required tests

- `SecuredOperationDemoTest` (`tmp-security`): denial/allow behaviour.
- `AccessDeniedBypassPreventionTest` (`tmp-ui-shell`): the combined (a)+(b) proof described above.
- `AccessDeniedViewModelTest`: message display, no stack trace text.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=SecuredOperationDemoTest
mvn -q -pl :tmp-ui-shell test -Dtest=AccessDeniedBypassPreventionTest,AccessDeniedViewModelTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A working Access Denied Screen and a concrete, tested proof that UI-level hiding never substitutes for the real authorization check, satisfying Stage 4 task §11's explicit requirement.

---

## STAGE4-035 — User Administration Screen

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-033, STAGE4-028
**Module:** tmp-ui-shell

### Goal

Реализовать минимальный экран администрирования пользователей: список, создание, изменение, логическое удаление, сброс пароля.

### Required documents

- UI/UX Specification (Экраны; FXML; Controller; ViewModel; Длительные операции); Stage 4 task §15/§17 (minimal user/role admin UI); Security Specification (Администрирование).

### Required code context

- `UserAdministrationService`/`UserSummary` (`com.tmp.security.api`, STAGE4-028); `AuthorizationService` (permission-gated buttons); navigation/view ids `"security.view.users"` (STAGE4-016).

### Allowed code scope

- `tmp-ui-shell/src/main/resources/com/tmp/ui/shell/screen/useradmin/UserAdministrationScreen.fxml` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/useradmin/UserAdministrationController.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/useradmin/UserAdministrationViewModel.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/UiShellAutoConfiguration.java` (extend: register screen bound to `"security.view.users"`);
- matching tests under `tmp-ui-shell/src/test/java/com/tmp/ui/shell/screen/useradmin/`.

### Forbidden

- calling any repository/persistence type directly (only `UserAdministrationService`);
- performing the permission check only in the ViewModel and skipping it in the Application Service (defence stays in `tmp-security`; the UI-level check here is only for control enabling/disabling, matching Stage 4 task §11).

### Implementation requirements

- `UserAdministrationViewModel`: observable list of `UserSummary` (loaded via `listUsers(...)`, paginated); `createUser(...)`, `updateUser(...)`, `deleteUser(...)`, `resetPassword(...)` methods delegating to `UserAdministrationService`/`PasswordApplicationService`-backed façade methods (via `com.tmp.security.api` only), each wrapped to surface `AccessDeniedException`/validation failures as a bound error message (no stack trace).
- `UserAdministrationController`: a `TableView<UserSummary>` (login, display name, status) + a simple create/edit form + reset-password action + delete action, with create/edit/delete/reset buttons' `disableProperty()` bound to the corresponding `AuthorizationService.hasPermission(...)` result (cosmetic convenience only — the real enforcement remains in `tmp-security`).

### Public contracts that may change

- none.

### Acceptance criteria

- [ ] table lists users with correct status;
- [ ] create/update/delete/reset actions call the correct façade methods and refresh the list on success;
- [ ] a denied action surfaces the `AccessDeniedException` message without a stack trace;
- [ ] buttons are disabled when the corresponding permission is missing.

### Required tests

- `UserAdministrationViewModelTest`: CRUD delegation correctness, error surfacing, list refresh.
- `UserAdministrationControllerFxTest`: table/form wiring against a fake ViewModel.

### Verification commands

```bash
mvn -q -pl :tmp-ui-shell test -Dtest=UserAdministrationViewModelTest,UserAdministrationControllerFxTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A working, minimal User Administration Screen.

---

## STAGE4-036 — Role Administration Screen

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-033, STAGE4-028
**Module:** tmp-ui-shell

### Goal

Реализовать минимальный экран администрирования ролей: список, создание/изменение, назначение/отзыв разрешений, назначение/отзыв ролей пользователям, удаление с учётом ограничения.

### Required documents

- Same as STAGE4-035, applied to roles; Stage 4 task §6 (deletion restriction) surfaced as a UI-level error message, not re-implemented.

### Required code context

- `RoleAdministrationService`/`RoleSummary`/`PermissionSummary` (`com.tmp.security.api`, STAGE4-028); navigation/view id `"security.view.roles"` (STAGE4-016).

### Allowed code scope

- `tmp-ui-shell/src/main/resources/com/tmp/ui/shell/screen/roleadmin/RoleAdministrationScreen.fxml` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/roleadmin/RoleAdministrationController.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/roleadmin/RoleAdministrationViewModel.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/UiShellAutoConfiguration.java` (extend: register screen bound to `"security.view.roles"`);
- matching tests under `tmp-ui-shell/src/test/java/com/tmp/ui/shell/screen/roleadmin/`.

### Forbidden

- re-implementing the "role in use" delete guard in the UI layer (the UI only surfaces the `RoleInUseException` message from the Application Service — the guard itself lives exclusively in `tmp-security`).

### Implementation requirements

- `RoleAdministrationViewModel`: observable list of `RoleSummary`; create/update/delete role; a permission-assignment sub-view (checklist of all known `PermissionSummary`s — from `AuditQueryService`? no — from a new read method exposed via `RoleAdministrationService`/`AuditQueryService`'s neighbours; **use** `RoleAdministrationService`'s permission-catalogue listing, which this task adds as a small extension: `List<PermissionSummary> listAllPermissionDefinitions()` on `com.tmp.security.api.RoleAdministrationService`, backed by `PermissionDefinitionRepository.findAll()` mapped to DTO — small, justified public-API extension since the UI genuinely needs the full catalogue, not just a role's current set); a user-role assignment sub-view (assign/revoke by user id/login lookup).
- `RoleAdministrationController`: roles table + permission checklist + user-assignment controls, delete button disabled/error-surfaced per the in-use guard.

### Public contracts that may change

- `com.tmp.security.api.RoleAdministrationService` gains `listAllPermissionDefinitions()` — additive, backward-compatible.

### Acceptance criteria

- [ ] roles table lists roles with their permission count;
- [ ] granting/revoking a permission on a role updates the checklist and persists correctly;
- [ ] assigning/revoking a role to/from a user works via login lookup;
- [ ] deleting an in-use role surfaces `RoleInUseException`'s message, does not remove the row from the table.

### Required tests

- `RoleAdministrationViewModelTest`: CRUD/permission/assignment delegation correctness, in-use-delete error surfacing.
- `RoleAdministrationControllerFxTest`: table/checklist/assignment wiring against a fake ViewModel.

### Verification commands

```bash
mvn -q -pl :tmp-security test -Dtest=RoleAdministrationApplicationServiceTest
mvn -q -pl :tmp-ui-shell test -Dtest=RoleAdministrationViewModelTest,RoleAdministrationControllerFxTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A working, minimal Role Administration Screen, completing the required minimal user/role admin UI (Stage 4 task §17).

---

## STAGE4-037 — Security Audit Screen

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-033, STAGE4-028
**Module:** tmp-ui-shell

### Goal

Реализовать read-only экран просмотра журнала аудита с пагинацией и фильтрацией.

### Required documents

- Stage 4 task §12/§15/§18 (read-only audit UI with pagination/filtering).

### Required code context

- `AuditQueryService`/`AuditEventSummary` (`com.tmp.security.api`, STAGE4-028); navigation/view id `"security.view.audit"` (STAGE4-016).

### Allowed code scope

- `tmp-ui-shell/src/main/resources/com/tmp/ui/shell/screen/audit/SecurityAuditScreen.fxml` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/audit/SecurityAuditController.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/audit/SecurityAuditViewModel.java` (new);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/UiShellAutoConfiguration.java` (extend: register screen bound to `"security.view.audit"`);
- matching tests under `tmp-ui-shell/src/test/java/com/tmp/ui/shell/screen/audit/`.

### Forbidden

- any create/update/delete control on this screen (strictly read-only, matching "Audit API read-only").

### Implementation requirements

- `SecurityAuditViewModel`: observable page of `AuditEventSummary`, filter fields (date range, actor, operation), next/previous page navigation, delegates entirely to `AuditQueryService`.
- `SecurityAuditController`: a read-only `TableView<AuditEventSummary>` + filter controls + pager.

### Public contracts that may change

- none.

### Acceptance criteria

- [ ] table displays audit events with correct columns (timestamp, actor, operation, target, description, result);
- [ ] filters narrow the displayed page correctly;
- [ ] pagination controls move between pages without duplication/gaps;
- [ ] no mutating control exists anywhere on this screen.

### Required tests

- `SecurityAuditViewModelTest`: filter/pagination delegation correctness.
- `SecurityAuditControllerFxTest`: table/filter/pager wiring against a fake ViewModel.

### Verification commands

```bash
mvn -q -pl :tmp-ui-shell test -Dtest=SecurityAuditViewModelTest,SecurityAuditControllerFxTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A working, read-only Security Audit Screen, completing all five mandatory Stage 4 screens.

---

## STAGE4-038 — Bootstrap integration finalization (login-gated startup, logout-to-login, shutdown session cleanup)

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-032, STAGE4-033, STAGE4-034, STAGE4-035, STAGE4-036, STAGE4-037
**Module:** tmp-bootstrap-app (+ tmp-ui-shell wiring touch-ups)

### Goal

Завершить сквозной поток запуска: главное окно не открывается до успешного login; logout возвращает Login Screen; shutdown очищает сессию.

### Required documents

- Stage 4 task §16 (bootstrap admin выполняется после persistence/permissions — already true via STAGE4-029's ordering; главное окно не открывается до успешного login; shutdown очищает session; logout возвращает Login Screen).

### Required code context

- `DesktopBootstrap`/`JavaFxShellLauncher`/`JavaFxShellApplication` (current state after STAGE4-032/033 edits); `SessionContext`/`AuthenticationService.logout()` (`com.tmp.security.api`).

### Allowed code scope

- `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/DesktopBootstrap.java` (finalize: remove the old `formatCapabilityStatus`/`formatDocumentPanel`/`EmptyMainShell`-based call, wire the `UiShellEntryPoint` fully);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/JavaFxShellApplication.java` (finalize: `start(Stage)` shows Login Screen first, swaps to Main Window on successful login via a callback from `LoginViewModel`, and back to Login Screen on logout; `stop()` calls `sessionContext`'s clear path in addition to the existing `onStopCallback`);
- `tmp-ui-shell/src/main/java/com/tmp/ui/shell/JavaFxShellLauncher.java` (finalize: static field for the `UiShellEntryPoint`);
- matching tests under `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/` and `tmp-ui-shell/src/test/java/com/tmp/ui/shell/`.

### Forbidden

- opening the Main Window scene before `AuthenticationService.isAuthenticated()` is `true`;
- leaving any old direct call to `platformCore.status()`/`documentEngine.search(...)` string-formatting in `DesktopBootstrap` now that the Main Window renders its own live navigation (superseded by STAGE4-033) — remove dead code, do not leave it commented out (per governance §6, no commented-out code).

### Implementation requirements

- `DesktopBootstrap.main()`: builds the Spring context (unchanged), looks up the `UiShellEntryPoint` bean, calls `JavaFxShellLauncher.launch(springContext::close, uiShellEntryPoint)`.
- `JavaFxShellApplication.start(Stage)`: loads the Login screen via `navigationService.load("login")`; the `LoginViewModel`'s successful-login callback swaps the stage's scene root to `navigationService.load("main-window")`; the Main Window's logout action swaps back to `navigationService.load("login")`.
- `stop()`: calls `authenticationService.logout()` if a session is still open (idempotent per STAGE4-021, safe no-op otherwise), then the existing `onStopCallback` (closes the Spring context).

### Public contracts that may change

- none.

### Acceptance criteria

- [ ] starting the app shows the Login Screen, not the Main Window;
- [ ] a failed login keeps the Login Screen visible with the error message;
- [ ] a successful login shows the Main Window with permission-filtered navigation;
- [ ] logout returns to the Login Screen and `SessionContext.current()` is empty immediately after;
- [ ] closing the application window (JavaFX `stop()`) clears any still-open session before the Spring context closes.

### Required tests

- `DesktopBootstrapWiringTest` (extends the existing bean-lookup smoke test style, e.g. alongside `CapabilityEngineBeanLookupTest`): confirms the `UiShellEntryPoint`/`NavigationService`/`AuthenticationService` beans are all resolvable from the real Spring context.
- `JavaFxShellApplicationFlowTest` (`tmp-ui-shell`): login-fail-stays, login-success-swaps-to-main, logout-swaps-to-login, stop()-clears-session — using fakes for the Navigation/Authentication services.

### Verification commands

```bash
mvn -q -pl :tmp-bootstrap-app test -Dtest=DesktopBootstrapWiringTest
mvn -q -pl :tmp-ui-shell test -Dtest=JavaFxShellApplicationFlowTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

A fully login-gated desktop application flow, ready for the architecture tests (STAGE4-039) and final gate (STAGE4-040).

---

## STAGE4-039 — Stage 4 architecture tests

**Status:** DONE
**Stage:** 4
**Depends on:** STAGE4-038
**Module:** tmp-architecture-tests

### Goal

Добавить `Stage4SecurityArchitectureTest`, покрывающий все правила Stage 4 task §19.

### Required documents

- Stage 4 task §19 (полный список обязательных правил);
- `Stage3CapabilityEngineArchitectureTest`/`Stage2DocumentEngineArchitectureTest` as the ArchUnit style precedent.

### Required code context

- `tmp-architecture-tests/pom.xml` (add `tmp-security` dependency); existing `StageNArchitectureTest` classes for style/imports precedent.

### Allowed code scope

- `tmp-architecture-tests/pom.xml` (add `com.tmp:tmp-security` dependency);
- `tmp-architecture-tests/src/test/java/com/tmp/architecture/Stage4SecurityArchitectureTest.java` (new).

### Forbidden

- weakening any existing `Stage0`/`Stage1`/`Stage2`/`Stage3ArchitectureTest` rule to make the new test pass;
- adding any rule not derivable from Stage 4 task §19.

### Implementation requirements

ArchUnit rules, one method per bullet of Stage 4 task §19:
- `tmp-security` (`com.tmp.security..`) does not depend on any business-module package (none exist yet, but assert no dependency on `com.tmp.document..`/`com.tmp.ui..`/`com.tmp.bootstrap..`, matching the "only core.api/capability.api" rule);
- `com.tmp.security..` (excluding `com.tmp.security.api..`) depends only on `com.tmp.core.api..`, `com.tmp.capability.api..`, `java..`, `org.springframework..`, `org.springframework.security.crypto..`, JDBC/`javax.sql`/`org.postgresql..`, and its own module packages;
- external packages (`com.tmp.ui..`, `com.tmp.bootstrap..`, `com.tmp.architecture..`) that reference `com.tmp.security..` reference only `com.tmp.security.api..`;
- `com.tmp.security.domain..` does not depend on `org.springframework..`, `jakarta.persistence..`, `org.hibernate..`, `javafx..`;
- `com.tmp.ui.shell..` Controller classes (`*Controller`) do not depend on `org.springframework.context..`/are not annotated with any Spring stereotype;
- `com.tmp.ui.shell..` Controller classes do not depend on any `*Repository`/`com.tmp.security.persistence..`/`com.tmp.security.application..` package;
- no class in `com.tmp.security.api..` has a field or method whose type name matches `PasswordHash`/`char\[\]` in a password-shaped position (reuse/extend the reflection check from STAGE4-028's `SecurityApiSurfaceNoCredentialLeakTest`, promoted here as an ArchUnit `ArchCondition` for durability across future additions);
- no `groupId` in the reactor is `org.springframework.security` other than `spring-security-crypto` (no `spring-security-web`/`spring-security-config`/`spring-security-oauth2-*`/`spring-security-ldap`), and no dependency named containing `jwt`/`oauth`/`ldap`/`saml` exists anywhere in the reactor's effective dependencies (checked via a Maven-dependency-tree-based test or a `pom.xml` text-scan test, whichever fits the existing `Stage0ArchitectureBaselineTest` convention — inspect that file first to match its exact checking mechanism, since ArchUnit itself cannot inspect Maven dependencies);
- no `com.tmp.order..`/`com.tmp.warehouse..`/`com.tmp.production..`/`com.tmp.cutting..`/`com.tmp.analytics..` package exists anywhere in the reactor (Stage 5+ absence check).

### Public contracts that may change

- none (test-only task).

### Acceptance criteria

- [ ] all rules above pass against the current reactor state;
- [ ] each rule is a separate `@ArchTest`/`@Test` method with a clear failure message (matching existing Stage tests' granularity).

### Required tests

- `Stage4SecurityArchitectureTest` (the task's whole deliverable).

### Verification commands

```bash
mvn -q -pl :tmp-architecture-tests test -Dtest=Stage4SecurityArchitectureTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Automated, durable enforcement of every Stage 4 architectural boundary.

---

## STAGE4-040 — Final Stage 4 verification gate

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-039, STAGE4-030, STAGE4-041, STAGE4-042, STAGE4-043, STAGE4-044, STAGE4-045, STAGE4-046, STAGE4-047, STAGE4-048, STAGE4-049, STAGE4-050, STAGE4-051, STAGE4-052, STAGE4-053  
**Module:** cross-stage  
**Closed by:** STAGE4-054 (2026-07-24)

### Goal

Полная верификация Stage 4 (`mvn clean verify`, package profile, PostgreSQL Testcontainers, jpackage app-image, manual `TMP.exe` smoke test) и закрытие Stage 4 на 100%.

### Required documents

- Stage 4 task §22 (полный чек-лист Final Stage Gate); `STAGE-4-SECURITY.md` (exit criteria).

### Required code context

- none beyond the already-implemented module set; this task performs verification only, no new production code (a genuine defect found here re-opens the specific earlier task that owns the affected area, per governance rules — this task itself does not silently patch around a failure).

### Allowed code scope

- none (verification-only task; `STATUS.md`/`WORK-QUEUE.md`/`IMPLEMENTATION-LOG.md`/`VERIFICATION-LOG.md` updates only).

### Forbidden

- skipping any check in Stage 4 task §22's checklist;
- marking DONE while any check fails.

### Implementation requirements

- Run `mvn clean verify` (full reactor); `mvn clean verify -Ppackage`; confirm PostgreSQL Testcontainers ITs across `tmp-security` (and unaffected earlier modules) pass; produce the jpackage app-image; launch `dist/jpackage/TMP/TMP.exe`; manually confirm: Spring Context starts; PostgreSQL/Flyway apply through `V4`; Security component starts after Capability Engine; bootstrap admin created exactly once; Login Screen displays; wrong password rejected with the generic message; correct password opens the Main Window; navigation matches permissions; a direct call to a secured operation enforces authorization regardless of UI state; logout clears the session and returns to Login Screen; closing the app clears the session; no password/hash appears in `logback` output.

### Public contracts that may change

- none.

### Acceptance criteria

- [x] every item in Stage 4 task §22 passes (automated subset via STAGE4-053; manual packaged GUI checklist confirmed by user 2026-07-24; formal Stage close recorded in STAGE4-054).

### Required tests

- none new (this task exercises the full existing suite plus the manual smoke checklist).

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
dist/jpackage/TMP/TMP.exe
```

### Documentation updates

- Formal Stage 4 close status fields and residual backlog are applied by `STAGE4-054` (supersedes the original “Last completed task: STAGE4-040” wording).

### Expected result

Stage 4 fully DONE; explicit stop before Stage 5 per governance §9 ("завершён текущий Stage и следующий Stage ещё не прошёл Start Gate").

---

# Stage 4 — Security (BLK-016 corrective tasks)

## STAGE4-041 — Authentication transaction and session consistency

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-039, BLK-016  
**Module:** `tmp-security`

### Goal

Разделить DB-транзакцию аутентификации и in-memory открытие session так, чтобы failed-login audit коммитился отдельно, session открывалась только после успешного commit success-audit, и при любом login failure session отсутствовала.

### Required documents

- Security Specification (authentication, audit, session);
- BLK-016 blocker 1.

### Required code context

- `AuthenticationApplicationService`, `SessionContext`, `SecurityAuditRepository`, `SecurityAutoConfiguration`.

### Allowed code scope

- `tmp-security/src/main/java/com/tmp/security/application/AuthenticationApplicationService.java`;
- `tmp-security/src/main/java/com/tmp/security/SecurityAutoConfiguration.java` (TransactionTemplate wiring if needed);
- matching unit + PostgreSQL IT tests under `tmp-security/src/test`.

### Forbidden

- revealing login existence in exceptions/messages;
- logging/auditing plaintext passwords or hashes;
- opening session before successful audit commit.

### Implementation requirements

- Remove `@Transactional` from `login()` as a single enclosing transaction for both failure path and session open.
- Use a completed `REQUIRES_NEW` (or equivalent) transaction for `LOGIN_FAILURE` audit before throwing.
- Persist `LOGIN_SUCCESS` audit in a DB transaction that commits before `sessionContext.open`.
- On audit failure: do not open session; propagate failure.
- On any authentication failure: session must be absent.

### Acceptance criteria

- [ ] Failed-login audit survives rollback of the authentication attempt / exception path.
- [ ] Session is opened only after successful success-audit commit.
- [ ] Audit failure on success path leaves no session.
- [ ] PostgreSQL Testcontainers IT covers failure-audit durability and success session timing.

### Required tests

- Unit tests for failure/success session invariants.
- `AuthenticationPostgresIntegrationIT` (or equivalent) with Testcontainers.

### Verification commands

```bash
mvn -pl :tmp-security -am test -Dtest=AuthenticationApplicationServiceTest
mvn -pl :tmp-security -am verify -Dit.test=AuthenticationPostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Login audit/session consistency matches Security Specification and BLK-016 blocker 1.

---

## STAGE4-042 — Login timing side-channel mitigation

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-041  
**Module:** `tmp-security`

### Goal

Устранить timing side-channel: для неизвестного login всегда выполнять BCrypt verification против постоянного dummy hash; одинаковое сообщение для unknown/wrong/deleted без раскрытия существования login.

### Required documents

- Security Specification (authentication failure messaging);
- BLK-016 blocker 2.

### Required code context

- `AuthenticationApplicationService`, `PasswordHasher`, `AuthenticationFailedException`.

### Allowed code scope

- `tmp-security` authentication application + tests.

### Forbidden

- placing login existence in exception messages;
- skipping `PasswordHasher.matches` for unknown login.

### Implementation requirements

- Constant technical dummy BCrypt `PasswordHash`.
- Always call `matches` for unknown/deleted/wrong paths.
- Keep generic `AuthenticationFailedException` message.

### Acceptance criteria

- [ ] Unknown login invokes `PasswordHasher.matches`.
- [ ] unknown/wrong/deleted share the same message.
- [ ] Unit test verifies matches invocation for unknown login.

### Required tests

- Extended `AuthenticationApplicationServiceTest` (mock/spy hasher).

### Verification commands

```bash
mvn -pl :tmp-security -am test -Dtest=AuthenticationApplicationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

No login-existence timing oracle via skipped BCrypt.

---

## STAGE4-043 — Atomic bootstrap administrator and unique role name

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-042  
**Module:** `tmp-security`

### Goal

Сделать bootstrap administrator атомарным под PostgreSQL transaction-scoped lock с повторной проверкой `existsAny`, без поглощения исключений, допускающих partial commit; добавить case-insensitive unique index на role name.

### Required documents

- Security Specification (bootstrap administrator);
- BLK-016 blocker 3 + additional item 1.

### Required code context

- `BootstrapAdministratorApplicationService`, Flyway migrations, JDBC role repository.

### Allowed code scope

- `tmp-security` bootstrap service + new Flyway migration (do not edit V4) + tests.

### Forbidden

- swallowing exceptions that can leave partial commits;
- creating role without protecting concurrent bootstrap.

### Implementation requirements

- `pg_advisory_xact_lock` (or approved equivalent) inside the bootstrap transaction.
- Re-check `existsAny()` after lock.
- Atomic role + user + assignment + success audit.
- `CREATE UNIQUE INDEX ... ON security.roles (lower(name))` in new migration.
- Concurrent PostgreSQL IT: exactly one user, one Security Administrator role, one assignment, one success audit.

### Acceptance criteria

- [ ] Concurrent bootstrap yields exactly one admin user/role/assignment/success audit.
- [ ] Unique case-insensitive role name enforced by DB.
- [ ] No swallowed `DuplicateLoginException` path that leaves orphan roles.

### Required tests

- Concurrent bootstrap PostgreSQL IT.

### Verification commands

```bash
mvn -pl :tmp-security -am verify -Dit.test=BootstrapAdministratorPostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Bootstrap is race-safe and atomic.

---

## STAGE4-044 — Remove bootstrap secret defaults from repository YAML

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-043  
**Module:** `tmp-bootstrap-app`

### Goal

Удалить default admin password / bootstrap credentials из `application-dev.yml`; credentials только через `TMP_SECURITY_BOOTSTRAP_*`; fail-fast на пустой DB без конфигурации; password не в log/exception.

### Required documents

- BLK-016 blocker 4.

### Required code context

- `application-dev.yml`, `SecurityBootstrapProperties`, `MissingBootstrapConfigurationException`, `BootstrapAdministratorApplicationService`.

### Allowed code scope

- bootstrap YAML profiles; fail-fast path (no password leakage).

### Forbidden

- default passwords in repository;
- printing password in logs/exceptions.

### Implementation requirements

- Remove `${...:default}` bootstrap password/login/display-name defaults from `application-dev.yml`.
- Empty DB + missing env → startup fails with `MissingBootstrapConfigurationException` without password value.
- Package profile continues to require env vars.

### Acceptance criteria

- [ ] No default bootstrap password in tracked YAML.
- [ ] Fail-fast without config on empty DB.
- [ ] Exception/log text does not contain password.

### Required tests

- Existing bootstrap missing-config unit/IT coverage updated as needed.

### Verification commands

```bash
mvn -pl :tmp-security,:tmp-bootstrap-app -am test -Dtest=BootstrapAdministratorApplicationServiceTest
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

No bootstrap secrets in repository defaults.

---

## STAGE4-045 — Permission ownership migration and synchronization

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-044  
**Module:** `tmp-security`

### Goal

Добавить `owner_capability_id` (NOT NULL + index) новой Flyway migration без изменения V4; расширить domain/repository; sync сохраняет owner, детектит конфликты PermissionId между Capability, деактивирует definitions не-ACTIVE Capability и orphan definitions, не удаляя role assignments/overrides.

### Required documents

- Security Specification (permission definitions / sync);
- BLK-016 blocker 5.

### Required code context

- `PermissionDefinition`, JDBC repository, `PermissionSynchronizationApplicationService`, V4 schema.

### Allowed code scope

- new `V5__...sql`; domain/persistence/sync + PostgreSQL tests.

### Forbidden

- modifying V4;
- deleting role assignments or individual overrides during sync.

### Implementation requirements

- Migration adds `owner_capability_id VARCHAR NOT NULL` + index (backfill existing rows with a deterministic owner if any, or delete+re-sync path only if empty in tests).
- Sync: save owner; conflict when same PermissionId claimed by different Capability; deactivate inactive-capability and catalogue-missing definitions.

### Acceptance criteria

- [ ] Schema has NOT NULL owner + index.
- [ ] Sync ownership/conflict/deactivation behaviours covered by PostgreSQL tests.
- [ ] Role assignments and overrides remain.

### Required tests

- Permission sync PostgreSQL IT.

### Verification commands

```bash
mvn -pl :tmp-security -am verify -Dit.test=PermissionSynchronizationPostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Permission definitions are capability-owned and sync-safe.

---

## STAGE4-046 — Deleted user cannot keep an active secured session

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-045  
**Module:** `tmp-security`

### Goal

Удалённый пользователь не может выполнять secured operations; Authorization учитывает актуальный `UserStatus`; удаление текущего пользователя очищает session; concurrency test login/authorization vs logical delete.

### Required documents

- Security Specification (user lifecycle, authorization);
- BLK-016 blocker 6.

### Required code context

- `AuthorizationApplicationService`, `UserAdministrationApplicationService`, `SessionContext`, `UserRepository`.

### Allowed code scope

- authorization/user-admin application services + tests.

### Forbidden

- allowing deleted users to pass `hasPermission` / `requirePermission`.

### Implementation requirements

- Authorization loads current user status; DELETED → deny.
- `deleteUser` clears session when deleting the authenticated user.
- Concurrency PostgreSQL IT: login/authorization vs logical delete.

### Acceptance criteria

- [ ] Deleted user session cannot authorize.
- [ ] Self-delete clears session.
- [ ] Concurrency IT passes.

### Required tests

- Unit + PostgreSQL concurrency IT.

### Verification commands

```bash
mvn -pl :tmp-security -am verify -Dit.test=DeletedUserSessionPostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Logical delete immediately stops secured access for that user.

---

## STAGE4-047 — Idempotent document contribution registration on restart

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-046  
**Module:** `tmp-capability-engine`

### Goal

Повторный запуск packaged app против той же PostgreSQL DB не падает на `Document type already registered` для sample/technical document contributions: registration must treat DB-persisted types as restart-safe while still rejecting in-process duplicate processor registration.

### Required documents

- BLK-016 evidence item 7; Stage 3 Capability registration semantics.

### Required code context

- `CapabilityRegistrationService.registerDocumentContributions`;
- `DefaultDocumentEngine.registeredTypes` (DB-backed);
- `DefaultDocumentEngine.registerProcessor` (DB upsert + in-memory processor).

### Allowed code scope

- `tmp-capability-engine` registration service + focused tests; document engine only if a minimal public probe is required.

### Forbidden

- deleting persisted document types on every startup;
- silent cross-capability type hijacking in the same process.

### Implementation requirements

- Stop treating DB-listed types as fatal conflicts during capability registration.
- Keep in-memory duplicate processor registration as a hard failure.
- Add test covering re-register after persisted type exists.

### Acceptance criteria

- [ ] Second registration against persisted type succeeds for processor bind.
- [ ] Same-process duplicate processor still fails.
- [ ] Packaged `TMP.exe` second launch no longer fails on sample.technical.document.

### Required tests

- Focused unit/IT in capability-engine module.

### Verification commands

```bash
mvn -pl :tmp-capability-engine -am test -Dtest=CapabilityRegistrationServiceTest,CapabilityLifecycle*
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Packaged app restart is stable with persisted document types.

---

## STAGE4-048 — VERIFICATION-LOG remediation for Stage 4

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-047  
**Module:** docs

### Goal

Исправить `VERIFICATION-LOG.md`: Latest result отражает Stage 4; добавить отдельные записи STAGE4-019..023 либо честно зафиксировать пакетное выполнение; не закрывать задачи без focused verification notes.

### Required documents

- BLK-016 additional items 2–4.

### Required code context

- `docs/development-control/VERIFICATION-LOG.md`.

### Allowed code scope

- VERIFICATION-LOG.md (and STATUS/IMPLEMENTATION-LOG cross-links if needed).

### Forbidden

- fabricating PASSED results without evidence;
- marking STAGE4-040 DONE here.

### Implementation requirements

- Update Latest result to Stage 4 / BLK-016 corrective scope.
- Add STAGE4-019..023 entries or an explicit package-execution note with honesty about batch verification.

### Acceptance criteria

- [ ] Latest result is Stage 4 accurate.
- [ ] STAGE4-019..023 accounted for.

### Required tests

- none (documentation).

### Verification commands

```bash
# manual review of VERIFICATION-LOG.md
```

### Documentation updates

- VERIFICATION-LOG; STATUS.

### Expected result

Verification log is trustworthy for Stage 4 review.

---

# Stage 4 — Security (BLK-017 residual corrective tasks)

## STAGE4-049 — Legacy permission ownership claim on V4→V5 upgrade

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-048, BLK-017  
**Module:** `tmp-security`

### Goal

Безопасно одноразово принять `owner_capability_id = legacy.unassigned` при synchronization после V5 upgrade, без изменения V4/V5 SQL; сохранить role permissions и individual overrides; после claim обычные ownership conflicts снова отклоняются.

### Required documents

- BLK-017 defect 1;
- Security Specification (permission definitions / sync).

### Required code context

- `PermissionDefinition`, `PermissionSynchronizationApplicationService`, Flyway V4/V5 (read-only).

### Allowed code scope

- `tmp-security` domain/sync + PostgreSQL upgrade IT;
- docs control files.

### Forbidden

- modifying V4 or V5 migrations;
- deleting role assignments / overrides during claim;
- allowing two Capabilities to own the same PermissionId.

### Implementation requirements

- `PermissionDefinition.claimLegacyOwnership(String capabilityId)` — only when owner is `legacy.unassigned`.
- Sync claims legacy ownership for the contributing Capability, then continues reconciliation.
- PostgreSQL Testcontainers upgrade IT: Flyway target V4 → seed V4 permission(+role permission/+override) → V5 → synchronize → owner=`security-administration` → assignments preserved → idempotent re-sync → other Capability rejected.

### Acceptance criteria

- [x] Legacy owner is claimable once by contributing Capability.
- [x] Upgrade IT covers V4→V5→sync path.
- [x] Subsequent ownership conflicts still fail.
- [x] Role permissions and overrides survive.

### Required tests

- `PermissionDefinitionTest`, `PermissionSynchronizationApplicationServiceTest`, `PermissionOwnershipUpgradePostgresIntegrationIT`.

### Verification commands

```bash
mvn -pl :tmp-security -am test -Dtest=PermissionDefinitionTest,PermissionSynchronizationApplicationServiceTest
mvn -pl :tmp-security verify -Dit.test=PermissionOwnershipUpgradePostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Existing V4 DBs upgrade through V5 and Security startup without ownership conflict.

---

## STAGE4-050 — Logout clears session on audit failure

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-049  
**Module:** `tmp-security`

### Goal

Гарантировать закрытие session при logout независимо от результата logout audit; audit failure не оставляет пользователя authenticated и не скрывается.

### Required documents

- BLK-017 defect 2.

### Required code context

- `AuthenticationApplicationService.logout`, `SessionContext`, `ControllableSecurityAuditRepository`.

### Allowed code scope

- authentication application + unit/IT tests.

### Forbidden

- swallowing audit failures without propagation;
- leaving session open after logout attempt.

### Implementation requirements

- `try/finally` (or equivalent): always `sessionContext.close()` after logout attempt.
- Unit test + PostgreSQL IT with controllable audit: `isAuthenticated()==false`, `requirePermission` → `AccessDeniedException`.

### Acceptance criteria

- [x] Audit failure still clears session.
- [x] Audit failure still propagates.
- [x] Protected operations denied after such logout.

### Required tests

- `AuthenticationApplicationServiceTest`, `AuthenticationPostgresIntegrationIT`.

### Verification commands

```bash
mvn -pl :tmp-security -am test -Dtest=AuthenticationApplicationServiceTest
mvn -pl :tmp-security verify -Dit.test=AuthenticationPostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Logout always ends authentication even if audit write fails.

---

## STAGE4-051 — Close prior session before login attempt

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-050  
**Module:** `tmp-security`

### Goal

Зафиксировать desktop TMP контракт: перед новой попыткой login закрывать предыдущую session; failed login не оставляёт ни новую, ни старую session; successful login создаёт только новую.

### Required documents

- BLK-017 defect 3; STAGE4-041 session-absent-on-failure criterion.

### Required code context

- `AuthenticationApplicationService.login`, `SessionContext`.

### Allowed code scope

- authentication application + unit/IT tests.

### Forbidden

- preserving prior session after failed login;
- auditing/logging password or hash.

### Implementation requirements

- Close current session at the start of every `login` attempt.
- Tests: active session + wrong/unknown/deleted login → no session; active + other valid user → only new session; audit events без password/hash.

### Acceptance criteria

- [x] Failed login with prior session leaves no session.
- [x] Successful switch-user replaces session.
- [x] Audit descriptions avoid secrets.

### Required tests

- `AuthenticationApplicationServiceTest`, `AuthenticationPostgresIntegrationIT`.

### Verification commands

```bash
mvn -pl :tmp-security -am test -Dtest=AuthenticationApplicationServiceTest
mvn -pl :tmp-security verify -Dit.test=AuthenticationPostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Login failure never leaves a prior authenticated session.

---

## STAGE4-052 — Login vs user-delete race: status re-check before session open

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-051  
**Module:** `tmp-security`

### Goal

Не открывать session для DELETED user, даже если password check прошёл по ранее прочитанному ACTIVE snapshot; зафиксировать политику re-check `UserStatus` непосредственно перед `sessionContext.open`.

### Required documents

- BLK-017 defect 4.

### Required code context

- `AuthenticationApplicationService`, `UserRepository`, `SessionContext`.

### Allowed code scope

- authentication application + deterministic concurrency PostgreSQL IT.

### Forbidden

- opening a usable session for a logically deleted user.

### Implementation requirements

- Re-load/re-check user ACTIVE status before success path opens session (and after credential acceptance).
- Deterministic IT: login pauses at status re-check; concurrent logical delete; login fails; no session; protected ops denied.

### Acceptance criteria

- [x] DELETED user does not receive a usable session.
- [x] Concurrency IT is deterministic.
- [x] Policy documented in service javadoc.

### Required tests

- `AuthenticationApplicationServiceTest`, `LoginDeleteRacePostgresIntegrationIT`.

### Verification commands

```bash
mvn -pl :tmp-security -am test -Dtest=AuthenticationApplicationServiceTest
mvn -pl :tmp-security verify -Dit.test=LoginDeleteRacePostgresIntegrationIT
```

### Documentation updates

- WORK-QUEUE; STATUS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Login/delete race cannot authenticate a deleted user.

---

## STAGE4-053 — Automated verification after BLK-017 fixes

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-049, STAGE4-050, STAGE4-051, STAGE4-052  
**Module:** cross-stage

### Goal

Выполнить полный automated gate после residual fixes: `mvn clean verify`, `mvn clean verify -Ppackage`, detached `TMP.exe` smoke (включая upgrade path evidence via IT). Не закрывать Stage 4 и не стартовать Stage 5. Финальный gate остаётся STAGE4-040 после ручного GUI подтверждения пользователя.

### Required documents

- BLK-017; Stage 4 exit criteria (automated subset).

### Required code context

- none beyond already-implemented fixes; verification + docs only.

### Allowed code scope

- docs control files; no production code unless verification finds a genuine defect owned by STAGE4-049…052.

### Forbidden

- marking STAGE4-040 DONE here;
- starting Stage 5;
- Git operations.

### Implementation requirements

- Run `mvn clean verify` and report result.
- Run `mvn clean verify -Ppackage` and report result.
- Launch `TMP.exe` detached (do not wait GUI foreground).
- Keep STAGE4-040 open for user manual packaged GUI checklist.

### Acceptance criteria

- [x] `mvn clean verify` PASSED.
- [x] `mvn clean verify -Ppackage` PASSED.
- [x] Detached `TMP.exe` launch attempted/reported.
- [x] Control docs updated; Stage 4 not closed; Stage 5 not started.

### Required tests

- full reactor suite (existing).

### Verification commands

```bash
mvn clean verify
mvn clean verify -Ppackage
dist/jpackage/TMP/TMP.exe
```

### Documentation updates

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG.

### Expected result

Automated residual gate green; STAGE4-040 waiting on user GUI confirmation.

---

## STAGE4-054 — Final Stage 4 close after manual packaged GUI confirmation

**Status:** DONE  
**Stage:** 4  
**Depends on:** STAGE4-040, STAGE4-053  
**Module:** cross-stage

### Goal

Зафиксировать подтверждение ручной packaged GUI-проверки пользователя, закрыть финальный verification gate Stage 4 (включая STAGE4-040) и установить Stage 4 в DONE 100% / `STAGE_COMPLETE`, без перехода к Stage 5 и без исправления некритичного UI-дефекта кодировки пагинации.

### Required documents

- Stage 4 task §22; `STAGE-4-SECURITY.md` (exit criteria); `templates/STAGE-CLOSE-TEMPLATE.md`; user confirmation of manual packaged GUI smoke (2026-07-24).

### Required code context

- none (documentation / stage-close only). Packaged app under test: `dist/jpackage/TMP/TMP.exe`.

### Allowed code scope

- development-control documentation only: `STATUS.md`, `WORK-QUEUE.md`, `IMPLEMENTATION-LOG.md`, `VERIFICATION-LOG.md`, `BLOCKERS.md`.

### Forbidden

- production-code changes (except if strictly required for documentation correctness — not needed here);
- fixing Security Audit pagination encoding in this task (tracked as `BACKLOG-001`);
- starting Stage 5 / Stage 5 Start Gate;
- any Git commands (commit, branch, tag, push).

### Implementation requirements

- Record user-confirmed manual packaged GUI checklist against `TMP.exe` + Docker `tmp-stage4-pg` / DB `tmp_gui_stage4` / user `tmp`.
- Close STAGE4-040 as the Stage 4 final verification gate.
- Register non-blocking `BACKLOG-001` for Security Audit pagination encoding (do not fix here).
- Update control docs to Stage 4 DONE 100% / `STAGE_COMPLETE`; Current Task None; stop before Stage 5.

### Public contracts that may change

- none.

### Acceptance criteria

- [x] Manual packaged GUI smoke confirmed PASSED by user (clean DB start, login/wrong password/neutral message, main window, Users/Roles/Audit screens, logout/relogin, restart without bootstrap env, single admin + Security Administrator, no secrets in logs, clean process exit).
- [x] STAGE4-040 closed (DONE).
- [x] Final Stage 4 verification gate closed.
- [x] `BACKLOG-001` registered; pagination encoding not fixed in this close.
- [x] STATUS/WORK-QUEUE/IMPLEMENTATION-LOG/VERIFICATION-LOG/BLOCKERS updated.
- [x] Project status `STAGE_COMPLETE`; Stage 4 DONE 100%; Stage 5 not started; no Git operations.

### Required tests

- none new (relies on STAGE4-053 automated gate + user manual GUI confirmation).

### Verification commands

```text
Manual (user-confirmed): dist/jpackage/TMP/TMP.exe
Environment: Docker tmp-stage4-pg; PostgreSQL DB tmp_gui_stage4; user tmp
Prior automated: mvn clean verify; mvn clean verify -Ppackage (STAGE4-053)
```

### Documentation updates

- STATUS.md → `Project status: STAGE_COMPLETE`, `Current Stage: Stage 4 — Security`, `Current Task: None`, `Last completed task: STAGE4-054`, `Active blocker: None`, `Stage 4: DONE 100%`;
- WORK-QUEUE (this task + STAGE4-040 DONE + BACKLOG-001);
- IMPLEMENTATION-LOG; VERIFICATION-LOG; BLOCKERS.

### Expected result

Stage 4 fully closed; non-blocking pagination encoding backlog only; explicit stop before Stage 5.

---

# Backlog (non-blocking / post-Stage)

## BACKLOG-001 — Fix Security Audit pagination text encoding

**Status:** PLANNED  
**Stage:** Backlog (post–Stage 4; does not block Stage 4 close)  
**Depends on:** STAGE4-054  
**Module:** `tmp-ui-shell` (Security Audit Screen)

### Goal

Исправить неправильную кодировку текста пагинации в нижней части экрана Security Audit (некритичный UI-дефект, обнаруженный при ручном packaged GUI smoke 2026-07-24).

### Required documents

- Stage 4 Security Audit UI (`STAGE4-037`); user manual GUI report 2026-07-24.

### Required code context

- Security Audit Screen FXML/Controller/ViewModel pagination labels in `tmp-ui-shell`.

### Allowed code scope

- `tmp-ui-shell` Security Audit pagination UI resources/strings (and tests if present); no Stage 4 re-open unless a separate task explicitly schedules it.

### Forbidden

- treating this defect as a Stage 4 exit-criteria failure;
- bundling the fix into STAGE4-054 / Stage 4 close without a dedicated task.

### Implementation requirements

- Identify source of mojibake/wrong encoding in pagination footer text (resource bundle / FXML / string formatting / charset).
- Fix so Russian (and other non-ASCII) pagination text renders correctly in packaged `TMP.exe` on Windows.
- Add or extend a focused UI/unit check if practical.

### Acceptance criteria

- [ ] Security Audit pagination footer displays correctly (no mojibake) in packaged GUI on Windows.
- [ ] No regression of audit filtering/pagination behaviour.
- [ ] Verified manually on packaged `TMP.exe` or equivalent UI test evidence.

### Required tests

- focused ViewModel/UI test if feasible; otherwise manual packaged GUI confirmation of pagination text.

### Verification commands

```text
Manual: dist/jpackage/TMP/TMP.exe → Security Audit → inspect pagination footer encoding
```

### Documentation updates

- WORK-QUEUE; STATUS (if in progress); IMPLEMENTATION-LOG; VERIFICATION-LOG when executed.

### Expected result

Correct pagination text encoding on Security Audit Screen; backlog item closable independently of Stage 4.

---

# Stage 5 — Order Management

> **Обязательно к прочтению перед любой задачей Stage 5:** `docs/development-control/stages/STAGE-5-ORDER-MANAGEMENT.md` (полный Manifest) и `Order-Management-Specification.md` v1.2. Правила контекста — `CONTEXT-MAP.md` → «Stage 5 — Order Management Context». Границы: Order Management владеет коммерческими данными заказа/позиции/редакции/спецификации и typed payload своих документов (по `DocumentId`, ADR-028); производственное состояние принадлежит Production (не хранится в Stage 5). Изменения — только через Document Engine; проведение атомарно и идемпотентно. Внешним Capability доступны только Public Query API (только active Revision) и Domain Events. Одновременно только одна задача в статусе READY. Git-операции запрещены (выполняет пользователь).

## STAGE5-000 — Stage 5 Start Gate and Specification Reconciliation

**Status:** DONE
**Stage:** 5
**Depends on:** STAGE4-054
**Module:** documentation only

### Goal

Устранить первичные противоречия документации Order Management, обновить Specification до v1.1, Stage Manifest и Context Map, пройти первичный documentation gate и сформировать очередь Stage 5 — без изменения Java-кода.

### Acceptance result

- [x] спецификация обновлена (v1.1); владение производственными статусами вынесено в Production;
- [x] документные операции формализованы; transition matrices определены;
- [x] Stage Manifest и Context Map обновлены; первичная очередь Stage 5 сформирована;
- [x] Java-код не изменялся.

### Expected result

Первичный Start Gate пройден. Последующая ревизия выявила документационные дефекты — см. `STAGE5-000-FIX`.

---

## STAGE5-000-FIX — Stage 5 Documentation Gate Corrections

**Status:** COMPLETED
**Stage:** 5
**Depends on:** STAGE5-000
**Module:** documentation only

- **Goal:** Исправить документационные дефекты после STAGE5-000, повторно пройти Documentation Gate и вернуть `STAGE5-001` в READY. Только документация и планирование.
- **Scope:** capability-owned typed document payload (ADR-028); подтверждение транзакционной границы Document Engine; уточнение Constitution (п.28) и ADR-003/ADR-004; разделение active/draft Revision и документ `ORDER_ITEM_REVISION_UPDATE`; безопасные правила отмены Stage 5; расширение Public Query API (поиск/пагинация); document lifecycle policy и idempotency; Specification → v1.2; Stage Manifest; Context Map; полная пересборка очереди Stage 5.
- **Out of scope:** Java-код, Maven-модули, `pom.xml`, SQL, FXML/CSS, тесты, старт `STAGE5-001`, Git.
- **Required documents:** Constitution; ADR; Order-Management-Specification; Production-Specification; Document Engine Specification + публичные API; Stage 5 Manifest; control docs.
- **Required code context (verification only):** `com.tmp.document.api..` (`DocumentProcessor`, `DocumentOperationContext`, `DocumentMetadata`, `Create/UpdateDocumentCommand`); `DefaultDocumentEngine`, `DocumentOperationContextImpl`, `TransactionAfterCommitEventPublisher`; `com.tmp.core.api` Event API.
- **Files allowed to change:** Constitution, ADR file, Order-Management-Specification, STAGE-5 Manifest, CONTEXT-MAP, WORK-QUEUE, STATUS, BLOCKERS, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Acceptance criteria:** все gates §18 пройдены; Specification v1.2; ADR-028 добавлен; Constitution п.28 и ADR-003/004 согласованы; очередь пересобрана; только `STAGE5-001` READY; Java-код не изменялся.
- **Verification commands:** `Documentation cross-reference only (no Maven build; no code/build changes).`
- **Documentation updates:** все перечисленные control и architecture документы.
- **Stop conditions:** отсутствие `DocumentId` в operation context; невозможность связать payload с документом или определить транзакционную границу; конфликт lifecycle Document Engine с политикой; остаточные противоречия ADR. (Не сработали — контракт подтверждён.)

---

## STAGE5-000-FIX2 — Final Documentation Corrections

**Status:** COMPLETED
**Stage:** 5
**Depends on:** STAGE5-000-FIX
**Module:** documentation only

- **Goal:** Устранить оставшиеся замечания подготовки Stage 5: публичный after-commit контракт `TransactionalEventPublisher`; физическая модель typed payload; корректная семантика idempotency (`void onPost`); транзакционный контракт в Document Engine Specification; синхронизация версий и номеров задач. Только документация.
- **Scope:** Document-Engine-Specification → v1.1; Order-Management-Specification §11.5/§12/§14/§16/§19; Stage Manifest; CONTEXT-MAP (v1.2, публичный publisher); полная пересборка очереди Stage 5 с prerequisite `TransactionalEventPublisher` до первого Document Processor; синхронизация номеров (`STAGE5-001..050`, GUI smoke `STAGE5-050`).
- **Out of scope:** Java-код, модули, `pom.xml`, миграции, тесты, старт `STAGE5-001`, Git.
- **Required documents:** Document-Engine-Specification; Order-Management-Specification; Stage 5 Manifest; CONTEXT-MAP; control docs; публичные контракты и минимальная реализация Document Engine (только для проверки транзакций).
- **Required code context (verification only):** `com.tmp.document.api..`; `DefaultDocumentEngine`, `TransactionAfterCommitEventPublisher` (только для подтверждения фактического поведения транзакций).
- **Files allowed to change:** Document-Engine-Specification, Order-Management-Specification, STAGE-5 Manifest, CONTEXT-MAP, WORK-QUEUE, STATUS, BLOCKERS, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Acceptance criteria:** Documentation Gate §9 пройден; after-commit механизм публичный; Order Management не использует внутренние классы Document Engine; физическое хранение typed payload определено; JSON payload не используется; idempotency соответствует `void onPost`; повторный публичный post отклоняется lifecycle validation; Document Engine Specification фиксирует транзакционный контракт; очередь и версии/номера синхронизированы; только `STAGE5-001` READY; Java-код не изменялся.
- **Verification commands:** `Documentation cross-reference only (no Maven build; no code/build changes).`
- **Documentation updates:** все перечисленные документы.
- **Stop conditions:** after-commit не может быть публичным; невозможно определить физическое хранение payload; idempotency не сводится к `void onPost`; транзакционный контракт не фиксируется. (Не сработали.)

---

## STAGE5-001 — Bootstrap `tmp-order-management` module

**Status:** DONE
**Stage:** 5
**Depends on:** STAGE5-000-FIX2
**Module:** `tmp-order-management` (new)

- **Goal:** Создать Maven-модуль `tmp-order-management`, подключить к reactor, задать package skeleton и разрешённые зависимости. Без агрегатов, таблиц, документов, UI.
- **Scope:** новый модуль; пакеты `com.tmp.order.api|domain|application|persistence|capability`; зависимости только `com.tmp.core.api`, `com.tmp.capability.api`, `com.tmp.document.api`, `com.tmp.security.api`.
- **Out of scope:** любая доменная/persistence/UI логика; изменение других модулей кроме reactor pom.
- **Required documents:** Manifest §3/§13; Database Spec (Schema per Module).
- **Required code context:** root `pom.xml`; существующие module pom как образец.
- **Files allowed to change:** root `pom.xml`, `tmp-order-management/pom.xml`, пустые package-info.
- **Acceptance criteria:** модуль в reactor; `mvn -q -pl tmp-order-management -am validate` зелёный; запрещённые зависимости отсутствуют.
- **Verification commands:** `mvn -q -pl tmp-order-management -am validate`
- **Documentation updates:** WORK-QUEUE, STATUS, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** невозможно подключить модуль без изменения чужой реализации.

---

## STAGE5-002 — Architecture boundaries and dependency rules

**Status:** DONE
**Stage:** 5
**Depends on:** STAGE5-001

- **Goal:** Зафиксировать архитектурные правила модуля (границы пакетов, запрет mutating API наружу, запрет production-данных, запрет импорта внутренних классов Document Engine) как ArchUnit-правила скелетом.
- **Scope:** правила: `api` не зависит от `persistence`; наружу только Query API; отсутствие импорта внутренних пакетов других Capability и внутренних классов Document Engine; отсутствие JavaFX.
- **Out of scope:** доменная логика; реальные агрегаты.
- **Required documents:** Manifest §3/§16; ADR-003/004/019/028.
- **Required code context:** `tmp-architecture-tests` конвенции; `com.tmp.*.api`.
- **Files allowed to change:** `tmp-architecture-tests` (новые правила для order), `tmp-order-management` package-info.
- **Acceptance criteria:** architecture tests компилируются и проходят на пустом модуле; правило «no internal Document Engine imports» присутствует.
- **Verification commands:** `mvn -q -pl tmp-architecture-tests -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** правило требует доступа к внутренней реализации другой Capability.

---

## STAGE5-003 — Identifiers and common value objects

**Status:** DONE
**Stage:** 5
**Depends on:** STAGE5-002

- **Goal:** Определить идентификаторы и базовые value objects (`OrderId`, `OrderItemId`, `RevisionNumber`, статусы, `PayloadSchemaVersion`, `PayloadRevision`).
- **Scope:** типобезопасные идентификаторы и enums в `com.tmp.order.api`/`domain`.
- **Out of scope:** агрегаты, persistence.
- **Required documents:** Spec §5/§8/§9/§11.2.
- **Required code context:** собственный домен.
- **Files allowed to change:** `tmp-order-management/.../api`, `.../domain`.
- **Acceptance criteria:** unit-тесты валидации идентификаторов проходят.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** требуется идентификатор чужой Capability как владелец.

---

## STAGE5-004 — Domain aggregate: Customer Order

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-003

- **Goal:** Реализовать агрегат Customer Order со статусами `DRAFT/APPROVED/CANCELLED` и инвариантами §8.
- **Scope:** доменные объекты, инварианты, переходы (без persistence/документов).
- **Out of scope:** persistence, документы, события, UI.
- **Required documents:** Spec §5.1/§8; ADR-017.
- **Required code context:** собственный домен.
- **Files allowed to change:** `tmp-order-management/.../domain`.
- **Acceptance criteria:** unit-тесты инвариантов и переходов (в т.ч. запрет `APPROVED→CANCELLED`).
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** статус без полного transition rule.

---

## STAGE5-005 — Domain aggregate: Order Item

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-004

- **Goal:** Реализовать агрегат Order Item со статусами `DRAFT/ACTIVE/CANCELLED`, коммерческими полями и указателями `activeRevisionNumber`/`draftRevisionNumber`.
- **Scope:** доменные инварианты §9 (в т.ч. запрет `ACTIVE→CANCELLED`).
- **Out of scope:** Revision-детали (STAGE5-006), persistence, документы.
- **Required documents:** Spec §5.2/§9; ADR-017.
- **Required code context:** собственный домен.
- **Files allowed to change:** `tmp-order-management/.../domain`.
- **Acceptance criteria:** unit-тесты инвариантов позиции и переходов.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** статус без полного transition rule.

---

## STAGE5-006 — Active/draft Revision model

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-005

- **Goal:** Реализовать `OrderItemRevision` с разделением active/draft: ≤ 1 draft; active immutable; создание N+1 не меняет active; утверждение атомарно переключает active и снимает draft.
- **Scope:** доменная логика редакций §6/§9.3.
- **Out of scope:** Specification-детали (STAGE5-007), документы, persistence.
- **Required documents:** Spec §5.3/§6/§9.3; ADR-018.
- **Required code context:** собственный домен.
- **Files allowed to change:** `tmp-order-management/.../domain`.
- **Acceptance criteria:** unit-тесты: одна draft; active не заменяется до утверждения; предыдущие revision immutable; утверждение атомарно.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** невозможно гарантировать единственность draft в домене.

---

## STAGE5-007 — Immutable Item Specification

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-006

- **Goal:** Реализовать Item Specification (состав/нормы) в границе Revision; после утверждения Immutable (ADR-018).
- **Scope:** доменная модель спецификации и её инвариант неизменяемости.
- **Out of scope:** persistence, документы, UI.
- **Required documents:** Spec §5.4/§7; ADR-018.
- **Required code context:** собственный домен.
- **Files allowed to change:** `tmp-order-management/.../domain`.
- **Acceptance criteria:** unit-тесты: изменение утверждённой спецификации запрещено; изменение только новой Revision.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** нарушается инвариант immutability.

---

## STAGE5-008 — Repository ports

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-007

- **Goal:** Определить domain-facing repository ports для Order/Item/Revision/Specification (интерфейсы, optimistic locking контракт).
- **Scope:** только порты (интерфейсы), без adapters.
- **Out of scope:** JDBC-адаптеры, миграции.
- **Required documents:** Spec §19; Database Spec (Optimistic Locking).
- **Required code context:** собственный домен/`repository`.
- **Files allowed to change:** `tmp-order-management/.../domain/repository`.
- **Acceptance criteria:** порты компилируются; контракт версии/конкурентности выражен.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** порт требует чужого хранилища.

---

## STAGE5-009 — Public Query API contracts and DTO

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-008

- **Goal:** Определить Public Query API (`getOrder`, `getOrderItems`, `getOrderItem`, `getOrderItemRevisions`, `getOrderItemRevision`, `getActiveOrderItemRevision`, `getItemSpecification`) и Query DTO.
- **Scope:** интерфейсы и DTO в `com.tmp.order.api`; DTO только данные Order Management; различие active/draft; внешне только active.
- **Out of scope:** реализация, пагинация (STAGE5-010), mutating операции.
- **Required documents:** Spec §15.1/§15.1.3; ADR-003.
- **Required code context:** `com.tmp.order.api`.
- **Files allowed to change:** `tmp-order-management/.../api`.
- **Acceptance criteria:** DTO не содержат Production/Stock/Cutting; не раскрывают entities; draft недоступен внешне.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** DTO вынуждены содержать чужие данные.

---

## STAGE5-010 — Paginated search contracts

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-009

- **Goal:** Определить `searchOrders(criteria, pageRequest)`, критерии поиска, page request и sort whitelist.
- **Scope:** search criteria (order number/status/customer/created from/to); pagination (default 50, max 100, zero-based); sort по умолчанию `createdAt DESC, orderId DESC`; whitelist sort fields.
- **Out of scope:** реализация репозитория, UI.
- **Required documents:** Spec §15.1.1/§15.1.2.
- **Required code context:** `com.tmp.order.api`.
- **Files allowed to change:** `tmp-order-management/.../api`.
- **Acceptance criteria:** только поля из модели; max page size enforced; недопустимый sort отклоняется.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** требуется фильтр по несуществующему полю.

---
## STAGE5-011 — Typed payload models: Order documents

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-010

- **Goal:** Определить typed payload модели документов заказа (`OrderCreatePayload`, `OrderUpdatePayload`, `OrderApprovePayload`, `OrderCancelPayload`) с полями identity §11.2.
- **Scope:** типизированные Java-модели; поля identity; связь по `DocumentId`; без JSON.
- **Out of scope:** persistence, processors, generic JSON.
- **Required documents:** Spec §11/§11.2; ADR-028.
- **Required code context:** `com.tmp.document.api` (`DocumentMetadata`/`DocumentId`).
- **Files allowed to change:** `tmp-order-management/.../application` (payload).
- **Acceptance criteria:** модели типизированы и versioned; отсутствует generic JSON.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** payload нельзя связать с `DocumentId`.

---

## STAGE5-012 — Typed payload models: Item documents

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-011

- **Goal:** Определить typed payload модели документов позиции (`OrderItemCreatePayload`, `OrderItemUpdatePayload`, `OrderItemCancelPayload`).
- **Scope:** типизированные модели с identity §11.2; `ORDER_ITEM_UPDATE` — только коммерческие поля (не Revision/Specification).
- **Out of scope:** persistence, processors.
- **Required documents:** Spec §11/§13; ADR-028.
- **Required code context:** `com.tmp.document.api`.
- **Files allowed to change:** `tmp-order-management/.../application` (payload).
- **Acceptance criteria:** `OrderItemUpdatePayload` не содержит spec/revision полей.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** payload смешивает коммерческие и revision данные.

---

## STAGE5-013 — Typed payload models: Revision documents

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-012

- **Goal:** Определить typed payload модели документов редакции (`OrderItemRevisionCreatePayload`, `OrderItemRevisionUpdatePayload`, `OrderItemRevisionApprovePayload`) и строки спецификации (payload line).
- **Scope:** модели редакции/спецификации с identity §11.2; коллекция строк как отдельная типизированная модель; `ORDER_ITEM_REVISION_UPDATE` изменяет только draft revision.
- **Out of scope:** persistence, processors.
- **Required documents:** Spec §6/§11/§13; ADR-028.
- **Required code context:** `com.tmp.document.api`.
- **Files allowed to change:** `tmp-order-management/.../application` (payload).
- **Acceptance criteria:** update-payload адресует только draft revision; строки спецификации — типизированная коллекция.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** payload позволяет менять утверждённую revision.

---

## STAGE5-014 — Payload application use cases (draft edit + optimistic lock)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-013

- **Goal:** Реализовать внутренние use cases создания/изменения draft payload с optimistic locking (`PayloadRevision`) и immutability после проведения.
- **Scope:** application use cases (только внутренние); редактирование только пока документ Draft.
- **Out of scope:** persistence-адаптер (STAGE5-020), processors.
- **Required documents:** Spec §11.3/§11.4.
- **Required code context:** собственный application; payload port (интерфейс использования).
- **Files allowed to change:** `tmp-order-management/.../application`.
- **Acceptance criteria:** unit-тесты: конфликт `PayloadRevision` отклоняется; правка после проведения запрещена.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** невозможно выразить optimistic lock.

---

## STAGE5-015 — Payload persistence port

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-014

- **Goal:** Определить persistence port для typed payload (load/store by `DocumentId`, версия схемы, `PayloadRevision`, каскадное удаление Draft).
- **Scope:** только порт (интерфейс).
- **Out of scope:** JDBC adapter, миграции.
- **Required documents:** Spec §11.5/§19.
- **Required code context:** `com.tmp.document.api` (`DocumentId`).
- **Files allowed to change:** `tmp-order-management/.../application` (port) или `.../persistence` (интерфейс порта).
- **Acceptance criteria:** порт компилируется; ключ — `DocumentId`; операции purge для Draft определены.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** payload нельзя ключевать по `DocumentId`.

---

## STAGE5-016 — Payload physical schema (Flyway typed tables)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-015

- **Goal:** Создать Flyway-миграцию физической модели payload (Spec §11.5): `order_document_payload` + typed-таблицы (`order_create_payload`, `order_update_payload`, `order_status_payload`, `order_item_create_payload`, `order_item_update_payload`, `order_item_status_payload`, `order_item_revision_create_payload`, `order_item_revision_update_payload`, `order_item_revision_approve_payload`) + `order_item_revision_payload_line`.
- **Scope:** только SQL-миграция payload (следующая свободная версия ≥ V6); FK на `order_document_payload(document_id)`; `payload_revision` для optimistic lock; каскадное удаление; typed-колонки без JSON.
- **Out of scope:** adapter, агрегатные/processing таблицы.
- **Required documents:** Spec §11.5/§19; Database Spec; Flyway (highest = V5).
- **Required code context:** `tmp-infra-db` конвенции.
- **Files allowed to change:** `src/main/resources/db/migration/Vx__order_payload_schema.sql`.
- **Acceptance criteria:** миграция применяется; FK и `payload_revision` присутствуют; нет JSON-колонок; нет generic payload в Platform Core.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** схема вынуждает JSON/сериализацию.

---

## STAGE5-017 — Public TransactionalEventPublisher contract and adapter (prerequisite)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-016

- **Goal:** Реализовать публичный контракт `TransactionalEventPublisher { void publishAfterCommit(DomainEvent event); }` и Spring transaction synchronization adapter в публичном API платформы/Document Engine, чтобы Capability публиковали события после commit без импорта внутренних классов Document Engine. Prerequisite до первого Document Processor.
- **Scope:** публичный интерфейс; adapter на основе transaction synchronization; тесты publish-only-after-commit и no-publish-after-rollback.
- **Out of scope:** Order Management processors; изменение бизнес-логики Document Engine.
- **Required documents:** Document Engine Specification (v1.1); Spec §12; Manifest §11.
- **Required code context:** `com.tmp.document.api`/`com.tmp.core.api` (`DomainEvent`, Event API); существующий after-commit механизм как reference (без экспонирования внутренних классов).
- **Files allowed to change:** публичный контракт и adapter (платформа/Document Engine public API) + их тесты.
- **Acceptance criteria:** контракт публичный; событие доставляется только после commit; при rollback не публикуется; Capability может зависеть только от публичного интерфейса.
- **Verification commands:** `mvn -q -pl tmp-document-engine -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** невозможно вынести after-commit в публичный контракт — остановить очередь, открыть blocker.

---

## STAGE5-018 — Processing record and idempotency model

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-017

- **Goal:** Определить модель processing record и idempotency guard (`DocumentId`, `DocumentTypeCode`, `Operation`, `ProcessingStatus`, `PayloadRevision`, `ProcessedAt`, `ResultReference`), уникальность `DocumentId + Operation`; семантика `void onPost` (already processed без повторного изменения/события, без возврата результата).
- **Scope:** доменная/application модель + порт + guard-логика.
- **Out of scope:** SQL-миграция (STAGE5-019), adapter (STAGE5-020).
- **Required documents:** Spec §14.1/§16; ADR-028.
- **Required code context:** `com.tmp.document.api` (`DocumentId`).
- **Files allowed to change:** `tmp-order-management/.../application` / `.../domain`.
- **Acceptance criteria:** unit-тесты: при существующей processing record повторная обработка не меняет агрегат, не публикует событие, не создаёт запись; `onPost` возвращает `void`; результат не возвращается наружу.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** невозможно определить idempotency guard без возврата результата.

---

## STAGE5-019 — Processing record schema (Flyway)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-018

- **Goal:** Создать Flyway-миграцию таблицы processing record с уникальным ограничением `document_id + operation`.
- **Scope:** только SQL-миграция (следующая свободная версия).
- **Out of scope:** adapter, агрегатные таблицы.
- **Required documents:** Spec §16/§19; Database Spec; Flyway.
- **Required code context:** `tmp-infra-db` конвенции.
- **Files allowed to change:** `src/main/resources/db/migration/Vx__order_processing_record.sql`.
- **Acceptance criteria:** миграция применяется; unique constraint присутствует.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** невозможно обеспечить уникальность `document_id + operation`.

---

## STAGE5-020 — Payload and processing-record persistence adapters

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-019

- **Goal:** Реализовать JDBC-адаптеры payload persistence port (STAGE5-015) и processing-record port (STAGE5-018): typed-таблицы §11.5, ключ `DocumentId`, optimistic lock `payload_revision`, каскадное удаление Draft, уникальность `DocumentId + Operation`.
- **Scope:** только адаптеры payload/processing (без JSON/сериализации).
- **Out of scope:** миграции (STAGE5-016/019), UI, processors.
- **Required documents:** Spec §11.5/§16/§19.
- **Required code context:** `tmp-infra-db` конвенции; собственные порты; `com.tmp.document.api`.
- **Files allowed to change:** `tmp-order-management/.../persistence`.
- **Acceptance criteria:** адаптеры реализуют optimistic lock, каскадное удаление Draft, идемпотентную запись processing record; без JSON.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** невозможно обеспечить уникальность/лок на уровне адаптера.

---
## STAGE5-021 — Business document type registration model

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-020

- **Goal:** Определить каталог document type codes Order Management и их дескрипторы (тип ↔ payload ↔ schema version ↔ required capability) без реализации processors.
- **Scope:** реестр типов документов §13; связь с payload и capability.
- **Out of scope:** processors, lifecycle-логика.
- **Required documents:** Spec §13; ADR-004/028.
- **Required code context:** `com.tmp.document.api` (registration контракт).
- **Files allowed to change:** `tmp-order-management/.../application` / `.../capability`.
- **Acceptance criteria:** все 10 типов описаны; каждому сопоставлены payload type и capability.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** тип без payload/capability.

---

## STAGE5-022 — Document lifecycle policy base

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-021

- **Goal:** Реализовать общий базовый lifecycle-контракт processors: `void onPost` (idempotency guard, load payload by `DocumentId`, проверка schema version + optimistic lock + предусловий, единственное бизнес-изменение, запись processing record, публикация события через публичный `TransactionalEventPublisher`); `onUnpost` = reject (NOT SUPPORTED); `onClose` = no business change; `onDelete` = draft only + удаление payload.
- **Scope:** абстрактный базовый processor/шаблон lifecycle §14 (без конкретных типов); использование публичного `TransactionalEventPublisher` (без внутренних классов Document Engine).
- **Out of scope:** конкретные document processors (STAGE5-023+).
- **Required documents:** Spec §14; ADR-028; Document Engine Spec v1.1.
- **Required code context:** `com.tmp.document.api` (`DocumentProcessor`, `DocumentOperationContext`, публичный `TransactionalEventPublisher`).
- **Files allowed to change:** `tmp-order-management/.../application`.
- **Acceptance criteria:** unit-тесты: `onUnpost` бросает; `onDelete` требует draft; `onClose` не меняет бизнес-состояние; `onPost` возвращает `void` и использует публичный publisher.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** lifecycle Document Engine противоречит политике.

---

## STAGE5-023 — Document processor: ORDER_CREATE

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-022

- **Goal:** Реализовать application command `createOrder`, processor `ORDER_CREATE` (`void onPost`: guard, load payload, validate, create order `DRAFT`, processing record, публикация `OrderCreated` через публичный publisher), регистрацию типа.
- **Scope:** один document type (create order).
- **Out of scope:** другие типы; persistence-адаптеры агрегатов (STAGE5-033).
- **Required documents:** Spec §8/§13/§14/§16/§17.
- **Required code context:** `com.tmp.document.api` (публичный publisher), собственный домен/application.
- **Files allowed to change:** `tmp-order-management/.../application`, `.../capability`.
- **Acceptance criteria:** unit-тесты: создаёт заказ `DRAFT`; idempotency guard; событие после commit; `onPost` void.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** невозможно загрузить payload по `DocumentId`.

---

## STAGE5-024 — Document processor: ORDER_UPDATE

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-023

- **Goal:** Реализовать `updateOrder` и processor `ORDER_UPDATE` (только коммерческие поля, заказ `DRAFT`), событие `OrderUpdated`.
- **Scope:** один document type (update order).
- **Out of scope:** другие типы; изменение утверждённого заказа.
- **Required documents:** Spec §8/§13/§14.
- **Required code context:** `com.tmp.document.api` (публичный publisher), собственный домен/application.
- **Files allowed to change:** `tmp-order-management/.../application`, `.../capability`.
- **Acceptance criteria:** unit-тесты: правка только `DRAFT`; idempotency guard; `onPost` void.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** payload затрагивает нерелевантные данные.

---

## STAGE5-025 — Document processor: ORDER_APPROVE

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-024

- **Goal:** Реализовать `approveOrder` и processor `ORDER_APPROVE` (≥1 active позиция), событие `OrderApproved`.
- **Scope:** один document type (approve order).
- **Out of scope:** другие типы.
- **Required documents:** Spec §8/§13/§14.
- **Required code context:** `com.tmp.document.api` (публичный publisher), собственный домен/application.
- **Files allowed to change:** `tmp-order-management/.../application`, `.../capability`.
- **Acceptance criteria:** unit-тесты: утверждение без active позиций отклоняется; idempotency guard.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** предусловие невозможно проверить.

---

## STAGE5-026 — Document processor: ORDER_CANCEL (draft only)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-025

- **Goal:** Реализовать `cancelOrder` и processor `ORDER_CANCEL` только для `DRAFT` заказа, событие `OrderCancelled`.
- **Scope:** один document type; запрет `APPROVED→CANCELLED`.
- **Out of scope:** компенсационная отмена утверждённого заказа (future scope).
- **Required documents:** Spec §8/§13/§22/§23.
- **Required code context:** `com.tmp.document.api` (публичный publisher), собственный домен/application.
- **Files allowed to change:** `tmp-order-management/.../application`, `.../capability`.
- **Acceptance criteria:** unit-тесты: отмена `APPROVED` отклоняется; отмена `DRAFT` работает.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** политика отмены неоднозначна.

---

## STAGE5-027 — Document processor: ORDER_ITEM_CREATE

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-026

- **Goal:** Реализовать `createOrderItem` и processor `ORDER_ITEM_CREATE` (позиция `DRAFT` + Revision 1 `DRAFT`, родительский заказ `DRAFT`), события `OrderItemCreated`, `OrderItemRevisionCreated`.
- **Scope:** один document type; запрет добавления позиции в `APPROVED`/`CANCELLED` заказ.
- **Out of scope:** редактирование revision (STAGE5-030).
- **Required documents:** Spec §9/§13/§14/§17.
- **Required code context:** `com.tmp.document.api` (публичный publisher), собственный домен/application.
- **Files allowed to change:** `tmp-order-management/.../application`, `.../capability`.
- **Acceptance criteria:** unit-тесты: добавление в `APPROVED` заказ отклоняется; создаётся Revision 1 draft.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** нарушение состава утверждённого заказа.

---

## STAGE5-028 — Document processor: ORDER_ITEM_UPDATE (commercial fields only)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-027

- **Goal:** Реализовать `updateOrderItem` и processor `ORDER_ITEM_UPDATE` только для коммерческих полей позиции (не Revision/Specification), событие `OrderItemUpdated`.
- **Scope:** один document type; запрет скрытого изменения revision.
- **Out of scope:** изменение spec/revision (STAGE5-030).
- **Required documents:** Spec §5.2/§6.3/§13.
- **Required code context:** `com.tmp.document.api` (публичный publisher), собственный домен/application.
- **Files allowed to change:** `tmp-order-management/.../application`, `.../capability`.
- **Acceptance criteria:** unit-тесты: попытка изменить revision/spec через этот документ отклоняется.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** payload позволяет менять revision.

---
## STAGE5-029 — Document processor: ORDER_ITEM_REVISION_CREATE

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-028

- **Goal:** Реализовать `createOrderItemRevision` и processor `ORDER_ITEM_REVISION_CREATE` (Revision N+1 `DRAFT` для active позиции; не меняет active; требует отсутствия draft), событие `OrderItemRevisionCreated`.
- **Scope:** один document type; ≤ 1 draft.
- **Out of scope:** редактирование draft (STAGE5-030), утверждение (STAGE5-031).
- **Required documents:** Spec §6.2/§9.3/§13.
- **Required code context:** `com.tmp.document.api` (публичный publisher), собственный домен/application.
- **Files allowed to change:** `tmp-order-management/.../application`, `.../capability`.
- **Acceptance criteria:** unit-тесты: вторая draft отклоняется; active не меняется.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** невозможно гарантировать единственность draft.

---

## STAGE5-030 — Document processor: ORDER_ITEM_REVISION_UPDATE

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-029

- **Goal:** Реализовать `updateOrderItemRevision` и processor `ORDER_ITEM_REVISION_UPDATE`, изменяющий только текущую Draft Revision (spec/количество, строки спецификации), событие `OrderItemRevisionUpdated`.
- **Scope:** один document type; только draft revision.
- **Out of scope:** утверждение; изменение утверждённой revision.
- **Required documents:** Spec §6.3/§9.3/§13.
- **Required code context:** `com.tmp.document.api` (публичный publisher), собственный домен/application.
- **Files allowed to change:** `tmp-order-management/.../application`, `.../capability`.
- **Acceptance criteria:** unit-тесты: правка утверждённой revision отклоняется; правка draft работает; idempotency guard.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** отсутствует draft для изменения.

---

## STAGE5-031 — Document processor: ORDER_ITEM_REVISION_APPROVE

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-030

- **Goal:** Реализовать `approveOrderItemRevision` и processor `ORDER_ITEM_REVISION_APPROVE`: проверка draft + полноты spec; draft → immutable; атомарное назначение новой `activeRevision`; снятие `draftRevision`; сохранение предыдущей; событие `OrderItemRevisionApproved`; позиция → `ACTIVE`.
- **Scope:** один document type; атомарное переключение active.
- **Out of scope:** внешние потребители события (Production — не в Stage 5).
- **Required documents:** Spec §6.4/§9.3/§17.
- **Required code context:** `com.tmp.document.api` (публичный publisher), собственный домен/application.
- **Files allowed to change:** `tmp-order-management/.../application`, `.../capability`.
- **Acceptance criteria:** unit-тесты: утверждение переключает active атомарно; предыдущая revision immutable; невалидная spec отклоняется.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** переключение active не атомарно.

---

## STAGE5-032 — Document processor: ORDER_ITEM_CANCEL (draft only)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-031

- **Goal:** Реализовать `cancelOrderItem` и processor `ORDER_ITEM_CANCEL` только для `DRAFT` позиции, событие `OrderItemCancelled`.
- **Scope:** один document type; запрет `ACTIVE→CANCELLED`.
- **Out of scope:** компенсационная отмена active позиции (future scope).
- **Required documents:** Spec §9/§22/§23.
- **Required code context:** `com.tmp.document.api` (публичный publisher), собственный домен/application.
- **Files allowed to change:** `tmp-order-management/.../application`, `.../capability`.
- **Acceptance criteria:** unit-тесты: отмена active отклоняется; отмена draft работает.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** политика отмены неоднозначна.

---

## STAGE5-033 — Aggregate persistence adapters

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-032

- **Goal:** Реализовать JDBC-адаптеры repository ports для Order/Item/Revision/Specification с optimistic locking.
- **Scope:** только адаптеры (реализация портов STAGE5-008).
- **Out of scope:** SQL-миграции (STAGE5-034), UI.
- **Required documents:** Spec §19; Database Spec (Optimistic Locking, Транзакции).
- **Required code context:** `tmp-infra-db`/`JdbcTemplate` конвенции; собственные порты.
- **Files allowed to change:** `tmp-order-management/.../persistence`.
- **Acceptance criteria:** адаптеры компилируются; реализуют контракт версии; без JPA.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** адаптер требует чужой схемы.

---

## STAGE5-034 — Aggregate Flyway migration (order_management schema)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-033

- **Goal:** Создать Flyway-миграцию агрегатных таблиц (`orders`, `order_items`, `order_item_revisions`, `item_specifications`, `item_specification_lines`) в схеме `order_management`.
- **Scope:** только SQL-миграция агрегатов (следующая свободная версия).
- **Out of scope:** payload/processing схемы (уже созданы), adapters.
- **Required documents:** Spec §19; Database Spec; Flyway.
- **Required code context:** `tmp-infra-db` конвенции.
- **Files allowed to change:** `src/main/resources/db/migration/Vx__order_management_schema.sql`.
- **Acceptance criteria:** миграция применяется на чистой БД; не хранит production/warehouse/cutting данных.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** схема вынуждает хранить чужие данные.

---

## STAGE5-035 — Security capabilities registration

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-034

- **Goal:** Зарегистрировать capabilities Order Management (`order.order.*`, `order.item.*`, `order.revision.create/edit`, `order.specification.view`) через Capability Engine/Security.
- **Scope:** дескрипторы разрешений и команд; 3-сегментный `PermissionId`.
- **Out of scope:** внутренняя реализация Security; UI.
- **Required documents:** Spec §18; Security `PermissionId` формат.
- **Required code context:** `com.tmp.capability.api`, `com.tmp.security.api`.
- **Files allowed to change:** `tmp-order-management/.../capability`.
- **Acceptance criteria:** все capability коды валидны (3 сегмента); соответствуют документам §13.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** код не соответствует формату `PermissionId`.

---

## STAGE5-036 — UI navigation contribution

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-035

- **Goal:** Добавить навигацию Order Management в `tmp-ui-shell` (пункт меню/раздел), управляемую capability.
- **Scope:** только навигация; без экранов данных.
- **Out of scope:** списки/редакторы (STAGE5-037+).
- **Required documents:** UI/UX Spec (Навигация); Manifest §14.
- **Required code context:** `tmp-ui-shell` навигация; `com.tmp.order.api`.
- **Files allowed to change:** `tmp-ui-shell` (навигация), при необходимости `com.tmp.order.capability` (NavigationContribution).
- **Acceptance criteria:** пункт появляется при наличии capability; скрыт без прав.
- **Verification commands:** `mvn -q -pl tmp-ui-shell -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** навигация требует прямых мутаций из UI.

---

## STAGE5-037 — UI: Order list (paginated Query API)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-036

- **Goal:** Реализовать экран списка заказов через `searchOrders`/пагинацию (только Query API), с фильтрами §15.1.1 и сортировкой по умолчанию.
- **Scope:** список, фильтры, пагинация; read-only.
- **Out of scope:** редактирование (STAGE5-038+), прямые мутации.
- **Required documents:** UI/UX Spec (Экраны); Spec §15.1.
- **Required code context:** `com.tmp.order.api` (Query/DTO); `tmp-ui-shell`.
- **Files allowed to change:** `tmp-ui-shell` (FXML/Controller/ViewModel списка).
- **Acceptance criteria:** список работает только через Query API; page size ≤ 100; сортировка стабильна.
- **Verification commands:** `mvn -q -pl tmp-ui-shell -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** список требует mutating API или чужих данных.

---

## STAGE5-038 — UI: Order editor (document-driven)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-037

- **Goal:** Реализовать редактор заказа: создать платформенный документ, сохранить typed draft payload, запросить проведение (`ORDER_CREATE`/`ORDER_UPDATE`/`ORDER_APPROVE`/`ORDER_CANCEL`).
- **Scope:** экран заказа + document flow через внутренние use cases.
- **Out of scope:** позиции/редакции (STAGE5-039).
- **Required documents:** UI/UX Spec; Spec §11.4/§14; Manifest §14.
- **Required code context:** `com.tmp.order.api` (Query), `com.tmp.document.api`; `tmp-ui-shell`.
- **Files allowed to change:** `tmp-ui-shell` (FXML/Controller/ViewModel заказа).
- **Acceptance criteria:** UI создаёт документ, сохраняет draft payload, инициирует проведение; нет прямых мутаций агрегата.
- **Verification commands:** `mvn -q -pl tmp-ui-shell -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** UI вынужден менять агрегат напрямую.

---

## STAGE5-039 — UI: Item and Revision editor

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-038

- **Goal:** Реализовать редактор позиций и редакций: `ORDER_ITEM_*` и `ORDER_ITEM_REVISION_*` через document flow; различать active/draft revision; draft доступен только во внутреннем UI use case.
- **Scope:** экраны позиции/редакции + document flow.
- **Out of scope:** спецификация (STAGE5-040).
- **Required documents:** UI/UX Spec; Spec §6/§9/§11.4.
- **Required code context:** `com.tmp.order.api`, `com.tmp.document.api`; `tmp-ui-shell`.
- **Files allowed to change:** `tmp-ui-shell` (FXML/Controller/ViewModel позиции/редакции).
- **Acceptance criteria:** UI показывает active и draft раздельно; создание/правка draft через документы; утверждение переключает active.
- **Verification commands:** `mvn -q -pl tmp-ui-shell -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** draft revision раскрывается как внешняя спецификация.

---

## STAGE5-040 — UI: Specification editor (immutable after approve)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-039

- **Goal:** Реализовать редактор спецификации draft revision; после утверждения — read-only.
- **Scope:** экран спецификации через document flow (`ORDER_ITEM_REVISION_UPDATE`).
- **Out of scope:** прочие экраны.
- **Required documents:** UI/UX Spec; Spec §7/§11.4.
- **Required code context:** `com.tmp.order.api`, `com.tmp.document.api`; `tmp-ui-shell`.
- **Files allowed to change:** `tmp-ui-shell` (FXML/Controller/ViewModel спецификации).
- **Acceptance criteria:** утверждённая спецификация только для чтения; правка только draft.
- **Verification commands:** `mvn -q -pl tmp-ui-shell -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** UI позволяет менять утверждённую спецификацию.

---

## STAGE5-041 — UI: Error handling and user messages

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-040

- **Goal:** Реализовать единообразную обработку ошибок домена/проведения (optimistic lock, запрещённые переходы, отклонённый unpost, отклонённый повторный post) и сообщения пользователю.
- **Scope:** отображение ошибок use cases/документов в UI.
- **Out of scope:** новые экраны данных.
- **Required documents:** UI/UX Spec (Сообщения пользователю); Spec §14/§16.
- **Required code context:** `tmp-ui-shell`; `com.tmp.order.api`.
- **Files allowed to change:** `tmp-ui-shell` (обработчики/сообщения).
- **Acceptance criteria:** ошибки отображаются понятно; UI не «проглатывает» отказ проведения.
- **Verification commands:** `mvn -q -pl tmp-ui-shell -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** отказ проведения невозможно донести до пользователя.

---
## STAGE5-042 — Unit tests consolidation

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-041

- **Goal:** Обеспечить покрытие домена/application unit-тестами (агрегаты, active/draft revision, immutability, payload optimistic lock, lifecycle policy, idempotency guard, `void onPost`).
- **Scope:** только unit-тесты модуля.
- **Out of scope:** integration/DB тесты.
- **Required documents:** Manifest §15.
- **Required code context:** собственный модуль.
- **Files allowed to change:** `tmp-order-management/src/test` (unit).
- **Acceptance criteria:** unit-набор зелёный; ключевые инварианты покрыты.
- **Verification commands:** `mvn -q -pl tmp-order-management -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** инвариант невозможно протестировать без чужой реализации.

---

## STAGE5-043 — Persistence integration tests (PostgreSQL Testcontainers)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-042

- **Goal:** Проверить схему/ограничения/optimistic lock/immutability revision/payload typed-таблицы/каскадное удаление Draft на реальном PostgreSQL.
- **Scope:** IT persistence (агрегаты + payload typed tables + processing record).
- **Out of scope:** document lifecycle/idempotency/rollback (отдельные задачи).
- **Required documents:** Manifest §15; Spec §11.5; Database Spec.
- **Required code context:** Testcontainers инфраструктура; собственные адаптеры.
- **Files allowed to change:** `tmp-order-management/src/test` (IT).
- **Acceptance criteria:** IT зелёные на PostgreSQL; unique/lock/каскад проверены; нет JSON-колонок.
- **Verification commands:** `mvn -q -pl tmp-order-management -am verify`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** схема не соответствует модели.

---

## STAGE5-044 — Document lifecycle integration tests

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-043

- **Goal:** Проверить полный документный поток (create → draft payload → post → aggregate change → event via public publisher) и политики `unpost` (rejected), `close` (no business change), `delete` (draft only, payload removed).
- **Scope:** IT lifecycle через Document Engine + Order Management processors + публичный `TransactionalEventPublisher`.
- **Out of scope:** idempotency/rollback (отдельно).
- **Required documents:** Spec §14; Manifest §15; Document Engine Spec v1.1.
- **Required code context:** `com.tmp.document.api` (публичный publisher); собственные processors.
- **Files allowed to change:** `tmp-order-management/src/test` (IT).
- **Acceptance criteria:** unpost проведённого отклонён; delete draft удаляет payload (все typed-таблицы); close не меняет бизнес-состояние; событие после commit.
- **Verification commands:** `mvn -q -pl tmp-order-management -am verify`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** lifecycle расходится с политикой §14.

---

## STAGE5-045 — Idempotency tests

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-044

- **Goal:** Проверить семантику idempotency: публичный повторный `DocumentEngine.postDocument(documentId)` для проведённого документа отклоняется lifecycle validation; idempotency guard внутри processor при существующей processing record завершается как already processed без повторного изменения агрегата, без повторного события, без новой processing record; `onPost` возвращает `void`.
- **Scope:** IT идемпотентности (public reject + internal guard).
- **Out of scope:** rollback (STAGE5-046).
- **Required documents:** Spec §14.1/§16; Manifest §15.
- **Required code context:** processing record adapter; processors; `com.tmp.document.api`.
- **Files allowed to change:** `tmp-order-management/src/test` (IT).
- **Acceptance criteria:** повторный публичный post отклонён lifecycle validation; guard не дублирует изменение/событие/запись; результат наружу не возвращается.
- **Verification commands:** `mvn -q -pl tmp-order-management -am verify`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** повторная обработка дублирует бизнес-изменение.

---

## STAGE5-046 — Transaction rollback tests

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-045

- **Goal:** Проверить атомарность: сбой в `onPost` откатывает изменение агрегата, processing record, metadata документа и lifecycle journal; документ не переходит в `POSTED`; событие не публикуется (публичный `TransactionalEventPublisher` не доставляет при rollback).
- **Scope:** IT rollback в транзакционной границе Document Engine.
- **Out of scope:** прочее.
- **Required documents:** Spec §12; Manifest §11/§15; Document Engine Spec v1.1.
- **Required code context:** `com.tmp.document.api` (публичный publisher); processors.
- **Files allowed to change:** `tmp-order-management/src/test` (IT).
- **Acceptance criteria:** при откате нет частичных изменений и события.
- **Verification commands:** `mvn -q -pl tmp-order-management -am verify`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** обнаружена неатомарность — открыть blocker Platform/Document Engine.

---

## STAGE5-047 — Architecture tests (boundaries and ownership)

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-046

- **Goal:** Финализировать architecture tests: границы пакетов; отсутствие production-owned данных; отсутствие внешнего mutating API; payload не в Platform Core (без JSON); зависимости только на разрешённые публичные API; запрет импорта внутренних классов Document Engine; использование публичного `TransactionalEventPublisher`.
- **Scope:** правила ArchUnit для Order Management.
- **Out of scope:** функциональные тесты.
- **Required documents:** Manifest §16; ADR-003/004/019/028; Document Engine Spec v1.1.
- **Required code context:** `tmp-architecture-tests`; `com.tmp.*.api`.
- **Files allowed to change:** `tmp-architecture-tests`.
- **Acceptance criteria:** все архитектурные правила проходят; нарушение «no internal Document Engine imports» ловится.
- **Verification commands:** `mvn -q -pl tmp-architecture-tests -am test`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** нарушена граница владения/зависимостей.

---

## STAGE5-048 — Full reactor verification

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-047

- **Goal:** Полная сборка и проверка реактора.
- **Scope:** `mvn clean verify` по всему проекту.
- **Out of scope:** packaging (STAGE5-049).
- **Required documents:** Manifest §19; RUN-DEVELOPMENT.
- **Required code context:** весь реактор.
- **Files allowed to change:** только исправления, выявленные сборкой (в рамках Stage 5).
- **Acceptance criteria:** `mvn clean verify` зелёный.
- **Verification commands:** `mvn -q clean verify`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** сборка падает по причинам вне Stage 5.

---

## STAGE5-049 — Packaged application verification

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-048

- **Goal:** Собрать упакованное приложение и проверить запуск.
- **Scope:** package profile; проверка артефакта.
- **Out of scope:** ручной GUI smoke (STAGE5-050).
- **Required documents:** Manifest §19; RUN-DEVELOPMENT.
- **Required code context:** `tmp-bootstrap-app`, packaging.
- **Files allowed to change:** packaging-конфигурация при необходимости.
- **Acceptance criteria:** `mvn -Ppackage` зелёный; артефакт создан.
- **Verification commands:** `mvn -q -Ppackage clean verify`
- **Documentation updates:** WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** упаковка не собирается.

---

## STAGE5-050 — Manual packaged GUI smoke and Stage 5 close

**Status:** PLANNED
**Stage:** 5
**Depends on:** STAGE5-049

- **Goal:** Пользовательский ручной GUI smoke по чек-листу exit criteria (Manifest §20); закрытие Stage 5; остановка перед Stage 6.
- **Scope:** ручная проверка; фиксация результата в control docs.
- **Out of scope:** старт Stage 6; любые Git-операции (выполняет пользователь).
- **Required documents:** Manifest §20; RUN-DEVELOPMENT.
- **Required code context:** упакованное приложение.
- **Files allowed to change:** STATUS, WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Acceptance criteria:** пользователь подтвердил GUI smoke; Stage 5 DONE 100%; остановка перед Stage 6.
- **Verification commands:** `Manual: packaged app (user-confirmed checklist)`
- **Documentation updates:** STATUS, WORK-QUEUE, IMPLEMENTATION-LOG, VERIFICATION-LOG.
- **Stop conditions:** пользователь не подтвердил smoke.
