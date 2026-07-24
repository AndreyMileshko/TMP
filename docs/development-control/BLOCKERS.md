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

Stage 2 (`STAGE2-001..026`) завершён. Re-review residual BLK-011 и BLK-013 устранены в STAGE2-022..026. Stop before Stage 3.

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

Удалён `documentEngineFacade`; единственный `@Bean DocumentEngine documentEngine(...)` возвращает `DefaultDocumentEngine`. Re-review 2026-07-22 подтвердил полное устранение.

---

## `BLK-011` — `Non-atomic processor registration (transaction final outcome)`

**Status:** RESOLVED  
**Task:** `STAGE2-022`  
**Detected:** 2026-07-21  
**Reopened:** 2026-07-22

### Reason

`registerProcessor()` пишет document type в БД, затем меняет in-memory registry, но commit транзакции происходит после возврата метода. При outer `rollbackOnly` или commit failure: тип отсутствует в БД, а processor остаётся в registry.

### Evidence

- Document/file/check: re-review Stage 2 — registry mutate before final commit outcome.
- Document/file/check: `DefaultDocumentEngine.registerProcessor()` без rollback compensation.

### Options

1. Compensating unregister через `TransactionSynchronization.afterCompletion` при non-COMMITTED.
2. Отложить mutate registry до `afterCommit` (ломает same-TX create после register).

### Recommendation

Вариант 1: immediate registry register + compensation on rollback; FK + `documentTypeExists` guard на create.

### Required user decision

Не требуется — исправление в STAGE2-022.

### Resolution

Добавлен `registerProcessorRollbackCompensation`: при `afterCompletion != COMMITTED` вызывается `processorRegistry.unregister`. Create проверяет `documentTypeExists`. FK `fk_documents_document_type` в `V3__documents_document_type_fk.sql`. Тесты: `DefaultDocumentEngineRegistrationTransactionTest`, `DocumentEnginePostgresIntegrationIT`. Verification PASSED.

---

## `BLK-012` — `Document events published before transaction commit`

**Status:** RESOLVED  
**Task:** `STAGE2-019`  
**Detected:** 2026-07-21

### Reason

`publishAfterCommit()` напрямую вызывает синхронный `EventBus.publish()` внутри транзакции. События доставляются до commit; при rollback события уже опубликованы.

### Evidence

- Document/file/check: acceptance review Stage 2 — direct EventBus publish inside transaction.

### Options

1. `TransactionSynchronization.afterCommit` (monolith).
2. Message broker (запрещено).

### Recommendation

Вариант 1.

### Required user decision

Не требуется.

### Resolution

`TransactionAfterCommitEventPublisher` публикует только после commit. Основная часть BLK-012 подтверждена re-review; residual handler-failure → BLK-013.

---

## `BLK-013` — `After-commit event handler failure policy undefined`

**Status:** RESOLVED  
**Task:** `STAGE2-023`  
**Detected:** 2026-07-22

### Reason

Исключение after-commit подписчика пробрасывалось вызывающему коду после успешного commit документа.

### Evidence

- Document/file/check: `TransactionAfterCommitEventPublisher.afterCommit` → `eventBus.publish` without catch.
- Document/file/check: Platform Core `SynchronousEventBus` propagates handler failures (контракт не менялся).

### Options

1. Document Engine: log + swallow после commit; документировать best-effort; не менять Platform Core.
2. Изменить Platform Core (отдельный blocker).

### Recommendation

Вариант 1.

### Required user decision

Не требуется.

### Resolution

`deliverSafely` логирует и поглощает delivery failures. Policy в javadoc: документная операция не откатывается; callers must not retry mutations. Platform Core unchanged. Тесты: `DefaultDocumentEngineTransactionEventTest`, `DocumentEnginePostgresIntegrationIT`. Verification PASSED.

---

## `BLK-014` — `Public APIs cannot reverse external Capability contributions`

**Status:** RESOLVED  
**Task:** `STAGE3-022`  
**Detected:** 2026-07-22

### Reason

Stage 3 acceptance review: `CapabilityRegistrationService` не мог полностью откатить внешние contributions при ошибке позднего шага; деактивация оставляла stale Platform Core capability metadata, public services, document processors и event subscriptions. Partial external state не является допустимым поведением.

### Evidence

- Document/file/check: acceptance review Stage 3 — registration rollback left Platform Core descriptor / Document Processor / service registrations.
- Document/file/check: `CapabilityRegistrationService` javadoc acknowledged residual limitation before rework.
- Document/file/check: tests previously tolerated partial external state.

### Options

1. **Минимальные owner-aware reversible public API** (Stage 1–2):
   - `ServiceRegistration` handle с `unregister()`; `ServiceRegistry.register()` возвращает handle.
   - `CapabilityRegistry.unregister(String capabilityId)`.
   - `DocumentProcessorRegistration` с `unregister()` и `deactivate()`; `DocumentEngine.registerProcessor()` возвращает handle.
   - Event subscriptions: `EventSubscription` handles, tracked by Capability Engine.
2. Reflection / internal map cleanup / Spring Context bypass (запрещено политикой Stage 3).

### Recommendation

Вариант 1: расширить принятые public API Stage 1–2 минимальными compensation handles; Capability Engine ведёт стек compensation и снимает event subscriptions при rollback / activation failure / stop / deactivation.

### Required user decision

Не требуется — API proposal принят; реализация в STAGE3-022.

### Resolution

Добавлены reversible public API:
- `com.tmp.core.api.ServiceRegistration` + `DefaultServiceRegistry` unregister by handle;
- `CapabilityRegistry.unregister` + `DefaultCapabilityRegistry`;
- `DocumentProcessorRegistration` + `deactivate()` / `unregister()` в Document Engine;
- `DocumentStoragePort.hasDocumentsForType()` / `unregisterDocumentType()`.

Capability Engine: `CapabilityExternalContributionRegistry`, `CapabilityEventSubscriptionRegistry`, `CapabilityTrackingEventBus`, atomic `CapabilityRegistrationService` с compensation stack, lifecycle failure handling и deactivation cleanup. Acceptance tests: `CapabilityDeactivationAcceptanceTest`, `CapabilityLifecycleFailureAcceptanceTest`, расширенные registration/lifecycle/PostgreSQL ITs. Verification: `mvn clean verify`, `mvn clean verify -Ppackage`, manual `TMP.exe`.

---

## `BLK-015` — `Lifecycle failures leave stale Capability contributions`

**Status:** RESOLVED  
**Task:** `STAGE3-024`  
**Detected:** 2026-07-23

### Reason

Повторная проверка Stage 3 после BLK-014: при `onInitialize` failure Capability переходит в `FAILED`, но external/internal contributions остаются доступными. `deactivate()` ставит `DEACTIVATED` до завершения cleanup. `handleActivationFailure` может потерять исходную ошибку при исключении cleanup. `unsubscribeAll` молча поглощает unsubscribe failures.

### Evidence

- Document/file/check: `CapabilityLifecycleManager.handleLifecycleFailure` — только `unsubscribeAll` + `markFailed`; catalogs / services / processors / platform metadata не снимались.
- Document/file/check: `deactivate()` вызывал `transition(...DEACTIVATED)` до cleanup.
- Document/file/check: `handleActivationFailure` вызывал cleanup без сохранения исходного activation failure при исключении cleanup.
- Document/file/check: `CapabilityEventSubscriptionRegistry.unsubscribeAll` — `catch (RuntimeException ignored)`.

### Options

1. Единый `cleanupFailedCapability` / `cleanupContributions`: unsubscribe + catalogs + services + processors + platform metadata; продолжать после ошибок; исходное lifecycle exception сохранять; cleanup failures — `suppressed`. `DEACTIVATED` только после успешной полной cleanup; иначе `FAILED`. `unsubscribeAll` агрегирует failures.
2. Точечные патчи без единого механизма (риск расхождения путей failure).

### Recommendation

Вариант 1.

### Required user decision

Не требуется — исправление в STAGE3-024.

### Resolution

`CapabilityLifecycleManager`: единый `cleanupContributions` / `cleanupFailedCapability` для initialize, activation, stop и deactivation failures; `DEACTIVATED` только после успешной полной cleanup. `CapabilityEventSubscriptionRegistry.unsubscribeAll` возвращает агрегированное failure. `CapabilityExternalContributionRegistry.deactivateAll` продолжает после ошибок и возвращает first+suppressed. Тесты: `CapabilityLifecycleCleanupAcceptanceTest`, PostgreSQL IT Order 5. Verification: `mvn clean verify`, `mvn clean verify -Ppackage`, manual `TMP.exe`.

---

## `BLK-016` — `Stage 4 Security acceptance defects`

**Status:** RESOLVED  
**Task:** `STAGE4-041`…`STAGE4-048` (corrective); blocks `STAGE4-040`  
**Detected:** 2026-07-23

### Reason

Stage 4 acceptance review: шесть блокирующих дефектов Security (transaction/session consistency при login, timing side-channel, non-atomic bootstrap admin, bootstrap secret в репозитории, отсутствие ownership у permission definitions, активная session удалённого пользователя), плюс необходимость case-insensitive unique role name, remediation VERIFICATION-LOG, и idempotent document-type re-registration при повторном запуске packaged app.

### Evidence

1. **Authentication transaction / session:** `AuthenticationApplicationService.login` — `@Transactional`; `LOGIN_FAILURE` audit + `AuthenticationFailedException` в одной транзакции → audit откатывается. `sessionContext.open` до audit/commit success → частичная session при audit/commit failure.
2. **Timing side-channel:** unknown login short-circuit без `PasswordHasher.matches`.
3. **Bootstrap:** role создаётся до user; `DuplicateLoginException` поглощается; concurrent loser может оставить лишнюю role; нет unique `lower(roles.name)`.
4. **Secret in repo:** `application-dev.yml` содержит default `dev-admin-password` через `${TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD:dev-admin-password}`.
5. **Permission ownership:** `security.permission_definitions` / `PermissionDefinition` без `owner_capability_id`; sync не детектит конфликты ownership и не деактивирует orphan/inactive-capability definitions.
6. **Deleted user session:** `AuthorizationApplicationService` не проверяет `UserStatus`; `deleteUser` не очищает session текущего пользователя.
7. **Packaged restart:** `CapabilityRegistrationService.registerDocumentContributions` трактует DB-persisted document types как конфликт → второй запуск `TMP.exe` падает.

### Options

1. Corrective tasks STAGE4-041…048 (рекомендуется): точечные исправления по owner-областям + PostgreSQL Testcontainers tests + VERIFICATION-LOG remediation; затем STAGE4-040.
2. Отложить Stage 4 и перейти к Stage 5 (запрещено governance / user instruction).

### Recommendation

Вариант 1.

### Required user decision

Не требуется — acceptance review уже задал исправления; агент выполняет STAGE4-041…048 без Git-операций и без Start Gate Stage 5.

### Resolution

Corrective tasks STAGE4-041…048 implemented and verified:

1. Authentication: separate `REQUIRES_NEW` audit transactions; session opens only after success-audit commit (`AuthenticationApplicationService`, `AuthenticationPostgresIntegrationIT`).
2. Timing side-channel: constant dummy BCrypt hash + always `PasswordHasher.matches` for unknown/deleted paths.
3. Bootstrap: `pg_advisory_xact_lock` + re-check `existsAny`; no swallowed duplicate path; concurrent IT; `uk_roles_name` on `lower(name)` in V5.
4. `application-dev.yml` bootstrap defaults removed; env-only credentials; fail-fast without password leakage.
5. V5 `owner_capability_id` NOT NULL + index; sync ownership/conflict/orphan/inactive deactivation without deleting assignments/overrides.
6. Authorization consults `UserStatus`; self-delete clears session; deleted-user ITs.
7. Capability document contribution registration restart-safe (DB-persisted types).
8. VERIFICATION-LOG Latest result + STAGE4-019..023 honest batch note.

Automated gate after fixes: `mvn clean verify`, `mvn clean verify -Ppackage`, `TMP.exe` first and second launch — PASSED. BLK-016 closed. Formal Stage 4 close completed later via STAGE4-054 (2026-07-24) after user GUI confirmation.

---

## `BLK-017` — `Stage 4 Security residual acceptance defects after BLK-016`

**Status:** RESOLVED  
**Task:** `STAGE4-049`…`STAGE4-053` (corrective); blocks `STAGE4-040`  
**Detected:** 2026-07-23

### Reason

Повторная проверка Stage 4 подтвердила устранение основной части BLK-016, но выявила четыре оставшихся acceptance defects:

1. **V5 legacy permission ownership:** V5 backfill ставит `owner_capability_id = 'legacy.unassigned'`; sync сравнивал owner строго с Capability ID и бросал `PermissionOwnershipConflictException` на upgrade существующей V4-БД.
2. **Logout + audit failure:** `logout` писал audit до `sessionContext.close()`; при audit failure session оставалась активной.
3. **Login with pre-existing session:** failed login при уже активной session сохранял старую session (нарушение STAGE4-041 «on any login failure session must be absent»).
4. **Login vs user delete race:** после успешной проверки пароля concurrent logical delete мог позволить открыть session для DELETED user.

### Evidence

- Document/file/check: `PermissionSynchronizationApplicationService` — strict owner equality without legacy claim.
- Document/file/check: Flyway `V5__permission_ownership_and_role_name_unique.sql` — `legacy.unassigned` backfill (V4/V5 не изменять).
- Document/file/check: `AuthenticationApplicationService.logout` — `close()` only after successful audit.
- Document/file/check: `AuthenticationApplicationService.login` — no prior-session close; no `UserStatus` re-check immediately before `sessionContext.open`.

### Options

1. Corrective tasks STAGE4-049…053 (рекомендуется): legacy ownership claim + logout try/finally + close-before-login + status re-check/race IT + automated verification; затем STAGE4-040 после ручного UI.
2. Отложить и перейти к Stage 5 (запрещено).

### Recommendation

Вариант 1.

### Required user decision

Ручной packaged GUI smoke (неверный пароль / вход / navigation / logout / Login Screen / повторный запуск / отсутствие credentials в logs) подтверждает пользователь перед закрытием STAGE4-040.

### Resolution

Corrective STAGE4-049…053 implemented and verified:

1. `PermissionDefinition.claimLegacyOwnership` + sync one-time legacy claim; V4/V5 SQL unchanged; `PermissionOwnershipUpgradePostgresIntegrationIT` + packaged `TMP.exe` on V4-seeded DB → owner=`security-administration`, assignments preserved.
2. `logout` closes session in `finally`; audit failure propagates; unit + PostgreSQL IT.
3. `login` closes prior session first; failed login leaves no session; switch-user opens only new session.
4. `UserStatus` re-checked before `sessionContext.open`; deterministic `LoginDeleteRacePostgresIntegrationIT`.

Automated gate: `mvn clean verify` PASSED; `mvn clean verify -Ppackage` PASSED; detached `TMP.exe` on V4→V5 DB PASSED. BLK-017 closed.

**2026-07-24 follow-up:** User confirmed full manual packaged GUI checklist PASS. STAGE4-040 closed by STAGE4-054; Stage 4 DONE 100% / `STAGE_COMPLETE`. Non-blocking residual UI defect (Security Audit pagination encoding) tracked as `BACKLOG-001` — not a blocker, not fixed in Stage 4 close. Active blocker: None. Stage 5 not started.
