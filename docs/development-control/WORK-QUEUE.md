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
