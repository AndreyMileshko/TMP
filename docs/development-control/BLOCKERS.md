# TMP Development Blockers

## `BLK-001` — `Shell execution environment unavailable`

**Status:** RESOLVED  
**Task:** `STAGE0-001`  
**Detected:** 2026-07-17

### Reason

Команды в Shell не возвращают exit status, из-за чего невозможно достоверно выполнить обязательную verification-команду задачи.

### Evidence

- Document/file/check: `mvn -q -DskipTests validate` -> "The shell command returned no exit status, so its result is unknown".
- Document/file/check: `dir` -> "The shell command returned no exit status, so its result is unknown".

### Options

1. Перезапустить/восстановить терминальное окружение Cursor и продолжить с текущего состояния файлов.
2. Разрешить временную ручную верификацию вне Shell tool (с локальным подтверждением пользователя).

### Recommendation

Вариант 1: восстановить Shell execution в среде Cursor и продолжить автоматический цикл, сохранив строгую трассируемость verification.

### Required user decision

Можете перезапустить/восстановить терминальную среду Cursor и подтвердить, что можно повторно запустить `mvn -q -DskipTests validate`?

---

# Blocker Template

## `<BLOCKER-ID>` — `<title>`

**Status:** OPEN | RESOLVED  
**Task:** `<TASK-ID>`  
**Detected:** YYYY-MM-DD

### Reason

...

### Evidence

- Document/file/check: ...

### Options

1. ...
2. ...

### Recommendation

...

### Required user decision

Один конкретный вопрос.

### Resolution

Терминальная среда восстановлена; проверка выполнена локальным portable Maven (`.tools/apache-maven-3.9.9/bin/mvn.cmd`), `validate` прошёл успешно.

## `BLK-002` — `JDK 21 compiler not available in environment`

**Status:** RESOLVED  
**Task:** `STAGE0-004`  
**Detected:** 2026-07-17

### Reason

Для Stage 0 и текущего parent build baseline требуется Java 21, но доступен только JRE 21 и JDK 17. Компиляция тестов невозможна в требуемом окружении.

### Evidence

- Document/file/check: `docs/TMP/TMP-Discussion-Checklist.md` указывает Java 21 LTS как целевую версию.
- Document/file/check: `.tools/apache-maven-3.9.9/bin/mvn.cmd -q test` -> "No compiler is provided in this environment".
- Document/file/check: запуск с `JAVA_HOME` на JDK 17 -> fail enforcer rule: Java version not in `[21,)`.

### Options

1. Установить JDK 21 и повторить `STAGE0-004` verification.
2. Ослабить Java baseline до 17 (потребует изменения утвержденных документов/архитектурного решения).

### Recommendation

Вариант 1: установить JDK 21 и продолжить Stage 0 без отклонений от утвержденных требований.

### Required user decision

Можете установить/подключить JDK 21 в среде (или указать путь к уже установленному JDK 21), чтобы я продолжил `STAGE0-004`?

### Resolution

Установлен portable JDK 21 в `.tools/jdk-21.0.11+10`; `mvn test` выполнен успешно с `JAVA_HOME` на JDK 21.

## `BLK-003` — `Docker environment unavailable for Testcontainers`

**Status:** RESOLVED  
**Task:** `STAGE0-009`  
**Detected:** 2026-07-17

### Reason

Интеграционные тесты Testcontainers не могут быть выполнены: Docker environment не найден в системе.

### Evidence

- Document/file/check: `mvn -q -pl :tmp-infra-db verify` -> `Could not find a valid Docker environment`.
- Document/file/check: Testcontainers log reports `docker-machine executable was not found on PATH`.

### Options

1. Установить и запустить Docker Desktop (или другой Docker engine), затем повторить `STAGE0-009` verification.
2. Временно пропустить containerized integration tests (нарушает acceptance criteria Stage 0).

### Recommendation

Вариант 1: установить/запустить Docker и продолжить автопроход без изменения спецификации.

### Required user decision

Можете установить и запустить Docker, чтобы я завершил `STAGE0-009` и продолжил Stage 0?

### Resolution

Docker Desktop установлен и запущен; WSL2 kernel обновлён; Testcontainers обновлён до 1.21.4 с `api.version=1.44` для совместимости с Docker Engine 29. Verification `mvn -q -pl :tmp-infra-db verify` PASSED.

## `BLK-004` — `TmpBootstrapApplication excludes database auto-configuration`

**Status:** RESOLVED  
**Task:** `STAGE0-012` (re-opened)  
**Detected:** 2026-07-20

### Reason

Stage 0 acceptance review выявил, что `TmpBootstrapApplication` исключает `DataSourceAutoConfiguration` и `FlywayAutoConfiguration`. Из-за этого packaged `TMP.exe` не подключается к PostgreSQL и не применяет Flyway, несмотря на успешные тесты модуля `tmp-infra-db`.

### Evidence

- Document/file/check: `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/TmpBootstrapApplication.java` содержит `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})`.
- Document/file/check: acceptance review — `TMP.exe` стартует без DB/Flyway wiring.
- Document/file/check: `tmp-infra-db` integration tests проходят на отдельном `InfraDbTestApplication`, не на реальном bootstrap entry point.

### Options

1. Удалить exclusions, настроить профили dev/test/package и добавить integration test на `TmpBootstrapApplication` с PostgreSQL Testcontainers.
2. Оставить exclusions и полагаться только на ручную конфигурацию (нарушает acceptance criteria Stage 0).

### Recommendation

Вариант 1: восстановить штатный Spring Boot wiring DataSource/Flyway в bootstrap и закрыть Stage 0 только после PostgreSQL/Flyway integration test на реальном entry point.

### Required user decision

Не требуется — исправление выполняется в рамках текущего Stage 0 rework.

### Resolution

Удалены exclusions из `TmpBootstrapApplication`; настроены профили `dev`/`test`/`package` (package использует `TMP_DB_URL`, `TMP_DB_USERNAME`, `TMP_DB_PASSWORD`); добавлен `TmpBootstrapPostgresIntegrationIT` на реальном entry point; усилены `SpringContextSmokeTest` и `PackagingSmokeIT`. Verification: `mvn clean verify` и `mvn clean verify -Ppackage` PASSED.

---

## `BLK-005` — `Unstable PlatformEvent and DomainEvent metadata`

**Status:** RESOLVED  
**Task:** `STAGE1-014`  
**Detected:** 2026-07-20

### Reason

`PlatformEvent.eventId()`, `PlatformEvent.occurredAt()`, `DomainEvent.eventId()` и `DomainEvent.occurredAt()` генерировали новые значения при каждом вызове через default methods, что нарушало стабильность event contract.

### Evidence

- Document/file/check: acceptance review Stage 1 — repeated calls to `eventId()` / `occurredAt()` returned different values.
- Document/file/check: `PlatformStartedEvent` / `PlatformStoppingEvent` наследовали нестабильное поведение default methods.

### Options

1. Ввести immutable base classes с metadata на construction time и убрать генерацию из default methods.
2. Оставить lazy generation в getters (нарушает acceptance criteria).

### Recommendation

Вариант 1: единый стабильный event contract через `AbstractPlatformEvent` / `AbstractDomainEvent`.

### Required user decision

Не требуется — исправление выполняется в рамках Stage 1 rework.

### Resolution

Удалены default generators из `PlatformEvent` и `DomainEvent`; добавлены `AbstractPlatformEvent` и `AbstractDomainEvent` с immutable `eventId` / `occurredAt`; обновлены `PlatformStartedEvent` и `PlatformStoppingEvent`; расширены тесты `SynchronousEventBusTest` (metadata stability, unsubscribe, supertype subscription, multiple handlers, handler failure policy). Verification PASSED.

---

## `BLK-006` — `Lifecycle failure consistency in DefaultLifecycleManager`

**Status:** RESOLVED  
**Task:** `STAGE1-014`  
**Detected:** 2026-07-20

### Reason

`DefaultLifecycleManager` некорректно обрабатывал startup/shutdown failures: не было rollback ранее запущенных компонентов, platform state после stop failure мог сообщать успешное завершение, повторные `startAll`/`stopAll` не защищены.

### Evidence

- Document/file/check: acceptance review Stage 1 — initialize/start failure не переводил platform в `FAILED` с rollback.
- Document/file/check: stop failure мог оставить platform в `STOPPED` вместо `FAILED`.

### Options

1. Переписать lifecycle transitions с явным rollback и защитой от повторных вызовов.
2. Оставить best-effort lifecycle (нарушает acceptance criteria).

### Recommendation

Вариант 1: строгие transitions, rollback в обратном порядке, исходное исключение с suppressed rollback errors.

### Required user decision

Не требуется — исправление выполняется в рамках Stage 1 rework.

### Resolution

Переписан `DefaultLifecycleManager`: допустимые transitions зафиксированы; при initialize/start failure component → `FAILED`, platform → `FAILED`, rollback в обратном порядке с сохранением исходного исключения; при stop failure platform → `FAILED`; повторные `startAll`/`stopAll` отклоняются; добавлен `platformState()` в API; comprehensive tests в `DefaultLifecycleManagerTest`. Verification PASSED.

---

## `BLK-007` — `Split component registration across PlatformRegistry and LifecycleManager`

**Status:** RESOLVED  
**Task:** `STAGE1-014`  
**Detected:** 2026-07-20

### Reason

Регистрация компонента требовала двух независимых вызовов (`PlatformRegistry.register()` и `LifecycleManager.registerComponent()`), что допускало partial state и нарушало atomic registration contract.

### Evidence

- Document/file/check: acceptance review Stage 1 — split registration API.
- Document/file/check: `PlatformCoreIntegrationIT` использовал два register-вызова.

### Options

1. Единый публичный `PlatformCore.registerComponent()` с atomic rollback при failure.
2. Документировать необходимость двух вызовов (нарушает acceptance criteria).

### Recommendation

Вариант 1: один публичный registration path; registries read-only снаружи.

### Required user decision

Не требуется — исправление выполняется в рамках Stage 1 rework.

### Resolution

Добавлен `PlatformCore.registerComponent(PlatformComponent)` как единственный публичный entry point; `PlatformRegistry.register()` и `LifecycleManager.registerComponent()` удалены из public API; internal `registerInternal`/`unregisterInternal` на implementations; обновлён `PlatformCoreIntegrationIT`; добавлен `DefaultPlatformCoreRegistrationTest`. Verification PASSED.

---

## `BLK-008` — `Stage 1 acceptance review remaining defects`

**Status:** RESOLVED  
**Task:** `STAGE1-015`  
**Detected:** 2026-07-21

### Reason

Повторная проверка Stage 1 выявила оставшиеся дефекты после частичного закрытия BLK-007:

1. Регистрация компонентов разрешена после начала lifecycle.
2. Публичные platform events (`PlatformStartedEvent`, `PlatformStoppingEvent`) находились во внутреннем пакете `com.tmp.core.event`.
3. Исключение обработчика `PlatformStoppingEvent` предотвращало вызов `lifecycleManager.stopAll()`.
4. ArchUnit требовал ручного перечисления каждого внутреннего пакета `com.tmp.core`.

### Evidence

- Document/file/check: acceptance review — `registerComponent()` не проверял platform state.
- Document/file/check: `PlatformCoreIntegrationIT` импортировал `com.tmp.core.event.PlatformStartedEvent`.
- Document/file/check: `PlatformCoreAutoConfiguration.onContextClosed` вызывал `stopAll()` только после `publish()` без `finally`.
- Document/file/check: `Stage1PlatformCoreArchitectureTest` перечислял internal packages вручную.

### Options

1. Закрыть все четыре дефекта в рамках STAGE1-015 без перехода к Stage 2.
2. Отложить исправления до Stage 2 (нарушает acceptance criteria Stage 1).

### Recommendation

Вариант 1: завершить Stage 1 с полным compliance по registration guard, public event packages, shutdown resilience и generic ArchUnit rule.

### Required user decision

Не требуется — исправление выполняется в рамках Stage 1 rework.

### Resolution

`registerComponent()` разрешает регистрацию только в `REGISTERED` и `STOPPED`; platform events перенесены в `com.tmp.core.api.event.platform`; shutdown listener использует `try/finally` для гарантированного `stopAll()`; ArchUnit rule обобщён на `com.tmp.core..` minus `com.tmp.core.api..`; добавлены тесты registration guard и `PlatformCoreLifecycleListenerTest`. Verification PASSED.

---

## `BLK-009` — `Registration/lifecycle race condition (split synchronization)`

**Status:** RESOLVED  
**Task:** `STAGE1-016`  
**Detected:** 2026-07-21

### Reason

`DefaultPlatformCore.registerComponent()` синхронизировался через отдельный `registrationLock`, а `DefaultLifecycleManager.startAll()`/`stopAll()` — через монитор lifecycle manager. Проверка `platformState` и lifecycle-регистрация не были атомарны относительно перехода `REGISTERED` → `INITIALIZING`, что допускало partial state и `ConcurrentModificationException`.

### Evidence

- Document/file/check: STAGE1-015 re-review — race между `registerComponent()` и `startAll()`.
- Document/file/check: `DefaultPlatformCore` использовал `registrationLock`, не связанный с lifecycle monitor.

### Options

1. Объединить synchronization boundary в `DefaultLifecycleManager` для registration, state reads и lifecycle transitions.
2. Оставить split locks (нарушает acceptance criteria).

### Recommendation

Вариант 1: единый monitor lifecycle manager; `registerComponentWithRegistry()`; snapshot iteration в `startAll()`.

### Required user decision

Не требуется — исправление выполняется в рамках Stage 1 rework.

### Resolution

Удалён `registrationLock`; атомарная регистрация перенесена в `DefaultLifecycleManager.registerComponentWithRegistry()` под тем же monitor, что `startAll()`/`stopAll()`; все read/write lifecycle state синхронизированы; `startAll()` итерирует snapshot `List.copyOf(components.values())`; добавлены deterministic concurrency test (200 iterations) и restart-after-STOPPED test. Verification PASSED.

---

## Stage 2 note

Stage 2 (`STAGE2-001..021`) завершён. Acceptance review blockers BLK-010..012 устранены в STAGE2-017..021.

---

## `BLK-010` — `Duplicate DocumentEngine Spring beans`

**Status:** RESOLVED  
**Task:** `STAGE2-017`  
**Detected:** 2026-07-21

### Reason

`DocumentEngineAutoConfiguration` создаёт два Spring bean типа `DocumentEngine`: `DefaultDocumentEngine documentEngine` и `DocumentEngine documentEngineFacade`. `DesktopBootstrap.getBean(DocumentEngine.class)` получает несколько кандидатов.

### Evidence

- Document/file/check: `DocumentEngineAutoConfiguration.java` — два `@Bean` метода возвращают `DocumentEngine`.
- Document/file/check: `DesktopBootstrap.java` — `springContext.getBean(DocumentEngine.class)`.

### Options

1. Оставить один `@Bean` типа `DocumentEngine` и удалить facade bean.
2. Добавить `@Primary` на один из beans (не устраняет дублирование).

### Recommendation

Вариант 1: единственный bean `DocumentEngine`; тесты bean lookup и DesktopBootstrap smoke test.

### Required user decision

Не требуется — исправление выполняется в рамках Stage 2 rework.

### Resolution

Удалён `documentEngineFacade`; единственный `@Bean DocumentEngine documentEngine(...)` возвращает `DefaultDocumentEngine`. `DocumentEnginePlatformRegistrar` принимает `DocumentEngine` и регистрирует как `PlatformComponent`. Тесты: `DocumentEngineBeanLookupTest`, `DesktopBootstrapLookupSmokeTest`. Verification PASSED.

---

## `BLK-011` — `Non-atomic processor registration`

**Status:** RESOLVED  
**Task:** `STAGE2-018`  
**Detected:** 2026-07-21

### Reason

`registerProcessor()` изменяет in-memory `processorRegistry` до записи document type в БД. DB rollback не откатывает in-memory registry, допуская partial state.

### Evidence

- Document/file/check: acceptance review Stage 2 — registry mutate before DB write.
- Document/file/check: original `DefaultDocumentEngine.registerProcessor()` order.

### Options

1. Регистрировать document type в БД первым в рамках `@Transactional`; in-memory registry — после успешной DB-записи.
2. Добавить compensating unregister при DB failure (сложнее).

### Recommendation

Вариант 1: DB-first порядок в одной транзакции; тест с намеренной DB failure и повторной регистрацией.

### Required user decision

Не требуется — исправление выполняется в рамках Stage 2 rework.

### Resolution

`registerProcessor()` вызывает `documentStorage.registerDocumentType(...)` до `processorRegistry.register(...)`. При DB failure processor не попадает в registry; повторная регистрация после failure успешна. Тесты: `DefaultDocumentEngineRegistrationTest`. Verification PASSED.

---

## `BLK-012` — `Document events published before transaction commit`

**Status:** RESOLVED  
**Task:** `STAGE2-019`  
**Detected:** 2026-07-21

### Reason

`publishAfterCommit()` напрямую вызывает синхронный `EventBus.publish()` внутри транзакции. События доставляются до commit; при rollback события уже опубликованы.

### Evidence

- Document/file/check: acceptance review Stage 2 — direct EventBus publish inside transaction.
- Document/file/check: original `DefaultDocumentEngine.publishAfterCommit()`.

### Options

1. Использовать `TransactionSynchronizationManager.registerSynchronization` с `afterCommit` callback (monolith, без broker).
2. Внедрить message broker (запрещено спецификацией).

### Recommendation

Вариант 1: `TransactionAfterCommitEventPublisher` через Spring transaction synchronization.

### Required user decision

Не требуется — исправление выполняется в рамках Stage 2 rework.

### Resolution

Добавлен `TransactionAfterCommitEventPublisher` с `afterCommit` через Spring transaction synchronization (без message broker). События не публикуются при rollback; после commit публикуются один раз. Тесты: `DefaultDocumentEngineTransactionEventTest`. Verification PASSED.
