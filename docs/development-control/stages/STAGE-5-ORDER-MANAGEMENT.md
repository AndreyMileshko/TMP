# Stage 5 Manifest — Order Management

**Stage:** 5 — Order Management  
**Primary specification:** `docs/TMP/TMP_Initial_Documents/architecture/10-Order-Management/Order-Management-Specification.md` (v1.3)  
**ADR document:** `docs/TMP/TMP_Initial_Documents/architecture/05-ADR/TMP-Architecture-Decisions.md` (v1.6; ADR-028, ADR-029, ADR-030)  
**Status:** Implementation IN_PROGRESS; STAGE5-050 DONE — PASS; STAGE5-051 DONE; STAGE5-052 DONE; STAGE5-052A DONE; STAGE5-053 DONE; STAGE5-054..057 NOT STARTED.

---

## 1. Цель Stage 5

Реализовать Capability Order Management как единственного владельца коммерческого жизненного цикла заказов, позиций, редакций и неизменяемых спецификаций, полностью управляемого через документную модель Document Engine с capability-owned typed payload, с Public Query API (поиск/пагинация) и Domain Events для будущих Capability, без хранения производственного, складского или раскроечного состояния.

Включает расширение Order Intake MVP (ручной ввод + STXT-импорт) после завершения core smoke `STAGE5-050`.

---

## 2. Входные условия

- Stage 0–4 завершены (DONE 100%); нет открытых блокеров Stage 0–4.
- Order Management Specification = **v1.3**; Constitution v1.2; ADR = **v1.6** (ADR-028, ADR-029, ADR-030).
- Подтверждённая транзакционная граница Document Engine (processor внутри транзакции проведения; события после commit).
- **Фактические миграции Order Management:** `V6`…`V11`. Текущая последняя = **V11** (`V11__order_item_incomplete_draft_contract.sql`).
- Reactor: `tmp-platform-core`, `tmp-infra-db`, `tmp-document-engine`, `tmp-capability-engine`, `tmp-security`, `tmp-ui-shell`, `tmp-bootstrap-app`, `tmp-architecture-tests`, `tmp-order-management`.

---

## 3. Архитектурные границы

- Модуль: `tmp-order-management`.
- Public API package: `com.tmp.order.api` (Query DTO, идентификаторы, события).
- Внутренние: `com.tmp.order.domain` (+ `...repository`), `com.tmp.order.application`, `com.tmp.order.persistence`, `com.tmp.order.capability`, `com.tmp.order` (auto-config + `PlatformComponent`).
- Разрешённые зависимости: `com.tmp.core.api..`, `com.tmp.capability.api..`, `com.tmp.document.api..`, `com.tmp.security.api..` (только публичные API). JavaFX запрещён в модуле.
- UI — в `tmp-ui-shell` (FXML/Controller/ViewModel).
- Персистентность: `spring-boot-starter-jdbc` + `JdbcTemplate`, ручные ports/adapters. Без JPA.
- Схема БД: `order_management`.

---

## 4. Реализуемые агрегаты

- **Customer Order** (`OrderId`): `DRAFT`/`APPROVED`/`CANCELLED`.
- **Order Item** (`OrderItemId`): `DRAFT`/`ACTIVE`/`CANCELLED`; `activeRevisionNumber`, `draftRevisionNumber`.
- **Order Item Revision** (`OrderItemId + RevisionNumber`): `DRAFT`/`APPROVED`; active/draft разделены.
- **Item Specification** (в границе Revision): Immutable после утверждения.

Границы транзакций: Customer Order — собственная; Order Item + Revisions + Specifications — единая граница агрегата.

**ADR-030:** `DRAFT` может временно не содержать обязательные коммерческие поля; `ORDER_APPROVE` требует их заполнения.

---

## 5. Capability-owned document payload

- Document Engine владеет lifecycle/metadata; Order Management владеет typed payload по `DocumentId` (ADR-028).
- Payload: typed Java-модель на тип документа; поля `DocumentId`, `DocumentTypeCode`, `PayloadSchemaVersion`, `PayloadRevision`, `CreatedAt`, `UpdatedAt`.
- Собственный persistence port и adapter; optimistic locking черновика через `PayloadRevision`.
- Editable только в Draft-состоянии документа; Immutable после проведения.
- Не generic JSON в Platform Core; недоступен другим Capability напрямую.
- Физическая модель (Spec §11.5): `order_document_payload` + typed-таблицы + `order_item_revision_payload_line`; связь через `document_id` (FK), optimistic lock `payload_revision`, каскадное удаление Draft, immutability после проведения; JSON/сериализация запрещены.
- Processing record: `DocumentId + Operation` unique; idempotency guard внутри processor.

---

## 6. Public API

### Query API (для других Capability и UI, read-only)
`searchOrders(criteria, pageRequest)`, `getOrder`, `getOrderItems(orderId, pageRequest)`, `getOrderItem`, `getOrderItemRevisions(orderItemId, pageRequest)`, `getOrderItemRevision(orderItemId, revisionNumber)`, `getActiveOrderItemRevision(orderItemId)`, `getItemSpecification(orderItemId, revisionNumber)`.

Пагинация: default 50, max 100, zero-based; sort по умолчанию `createdAt DESC, orderId DESC`; разрешённые sort fields `createdAt/orderId/orderNumber/status`. Внешне доступна только active Revision; draft Revision — только внутренний UI use case.

### Внутренний Application API (только Document Processors)
`createOrder`, `updateOrder`, `approveOrder`, `cancelOrder`, `createOrderItem`, `updateOrderItem`, `cancelOrderItem`, `createOrderItemRevision`, `updateOrderItemRevision`, `approveOrderItemRevision`. Не доступны внешним Capability.

---

## 7. Бизнес-документы

`ORDER_CREATE`, `ORDER_UPDATE`, `ORDER_APPROVE`, `ORDER_CANCEL`, `ORDER_ITEM_CREATE`, `ORDER_ITEM_UPDATE`, `ORDER_ITEM_REVISION_UPDATE`, `ORDER_ITEM_REVISION_APPROVE`, `ORDER_ITEM_CANCEL`, `ORDER_ITEM_REVISION_CREATE`.

Для каждого: typed payload, schema version, application command, affected aggregate, required capability, validation, result, Domain Event, idempotency key (`DocumentId + POST`) и lifecycle policy — Specification §13/§14. `ORDER_ITEM_UPDATE` — только коммерческие поля позиции; `ORDER_ITEM_REVISION_UPDATE` — только Draft Revision.

---

## 8. Domain Events

`OrderCreated`, `OrderUpdated`, `OrderApproved`, `OrderCancelled`, `OrderItemCreated`, `OrderItemUpdated`, `OrderItemRevisionCreated`, `OrderItemRevisionUpdated`, `OrderItemRevisionApproved`, `OrderItemCancelled`. Публикация после commit; `OrderItemRevisionApproved` потребляется Production.

---

## 9. Capabilities

`order.order.view/create/edit/approve/cancel`, `order.item.view/create/edit/approve/cancel`, `order.revision.create`, `order.revision.edit`, `order.specification.view` (3-сегментный формат `PermissionId`).

---

## 10. Document lifecycle policy

- `onPost` (сигнатура `void`): idempotency guard; загрузка payload по `DocumentId`; проверка schema version + optimistic lock + предусловий; единственное бизнес-изменение; запись processing record; публикация события через публичный `TransactionalEventPublisher`. При наличии processing record завершается как already processed без повторного изменения/события и без возврата результата.
- `onUnpost`: NOT SUPPORTED (отклоняется).
- `onDelete`: только Draft; удаляет payload; без изменения агрегатов, без события.
- `onClose`: без изменения бизнес-состояния; сохраняет payload/историю.
- Публичный повторный `DocumentEngine.postDocument(documentId)` проведённого документа отклоняется lifecycle validation.

---

## 11. Транзакционная граница (public contract)

Зафиксировано публичным контрактом Document Engine (Document Engine Specification v1.1): `DocumentProcessor` вызывается внутри транзакции lifecycle-операции; ошибка processor атомарно откатывает изменения Capability, metadata и lifecycle journal; `DocumentId` доступен через `context.document().id()`. Изменение агрегата + processing record атомарны в этой границе. События после успешного commit публикуются через публичный контракт `TransactionalEventPublisher.publishAfterCommit(DomainEvent)`; Order Management **не импортирует** внутренние классы Document Engine.

---

## 12. Persistence scope

Схема `order_management`: `orders`, `order_items`, `order_item_revisions`, `item_specifications`, `item_specification_lines`; typed payload — `order_document_payload` + typed-таблицы + `order_item_revision_payload_line` (ключ/FK `document_id`, без JSON); processing record (`document_id + operation` unique). Optimistic locking; schema-per-module.

Запрещено хранить: Production Status, launched/active/released quantity, партии/документы Production, Stock Position, резервы, складские движения, Cutting Plan internals, generic JSON в Platform Core.

---

## 13. Flyway scope

Единый `classpath:db/migration`. Фактически в Order Management: **V6** (payload), **V7** (processing record), **V8** (aggregates). Следующий свободный номер для Order Intake = **V9**. `V1..V5` и существующие `V6..V8` не изменяются на месте.

---

## 14. UI scope

В `tmp-ui-shell`: навигация, Order list (через paginated Query API), Order editor, Item + Revision editor, Specification editor (read-only после утверждения), обработка ошибок; **расширение Order Intake:** ручной ввод `externalPositionNumber` / количества изделий / `color` / `lengthMm` / `lineQuantity`; экран импорта STXT (выбор файла, preview, confirm/cancel). UI создаёт платформенный документ, сохраняет typed draft payload, запрашивает проведение; прямых мутаций агрегатов нет. Файловый адаптер не вызывается из Controller напрямую для persistence.

---

## 15. Testing scope

- Unit: domain (агрегаты, инварианты, active/draft revision, immutability), application (commands/processors), payload use cases; **Order Intake:** parser, import validation, source-neutral model.
- Integration (PostgreSQL Testcontainers): schema/constraints, optimistic lock, revision immutability, payload persistence, document lifecycle, idempotency, transaction rollback, Query API; **Order Intake:** atomic import, existing-order conflict, duplicate import metadata, rollback.
- Architecture tests: границы пакетов; отсутствие production-owned данных; отсутствие внешнего mutating API; payload не в Platform Core; **адаптер источника не пишет в persistence напрямую**.
- Full reactor `mvn clean verify`; package profile; manual packaged GUI smoke: `STAGE5-050` (core), затем `STAGE5-057` (Order Intake).

---

## 16. Запрещённый scope (Out of scope)

- Production Status, Production State, производственные количества/партии;
- запуск производства, отмена запуска, выпуск изделий, проверка обеспеченности, резервирование, складские движения, Warehouse Stock Position/документы;
- Cutting Plan и его внутренние данные; аналитика; Procurement;
- generic JSON payload в Platform Core;
- отмена утверждённого заказа (`APPROVED → CANCELLED`);
- отмена активной позиции (`ACTIVE → CANCELLED`);
- добавление/удаление позиций в/из `APPROVED` заказа;
- коммерческие статусы `IN_PROGRESS`/`COMPLETED`;
- compile-time зависимости от будущих Capability;
- прямые mutating-вызовы между Capability;
- **Firebird JDBC / прямое подключение к БД «СуперОкна» / фоновый обмен / plugin framework**;
- **merge/перезапись существующего заказа при импорте**;
- **orientation / placement в спецификации**;
- **фиктивные коммерческие значения (`UNKNOWN`/`N/A`/`IMPORT`/`—`)**.

---

## 17. Порядок реализации

### Core Order Management

`STAGE5-001..STAGE5-049` — реализованы.  
`STAGE5-050` — Manual Packaged GUI Smoke — Core Order Management: **DONE (PASS)**.

Успех `STAGE5-050` → `STAGE5-051` Order Intake contracts. `STAGE5-051` = **DONE**. Stage 5 остаётся **IN_PROGRESS**. Не закрывает Stage 5, не ставит 100%, не стартует Stage 6.

### Order Intake extension

Строгая последовательность. `STAGE5-051` = **DONE**. `STAGE5-052` = **DONE**. `STAGE5-052A` = **DONE**. `STAGE5-053` = **DONE**. Следующая: `STAGE5-054` = **NOT STARTED**.

| ID | Title |
| --- | --- |
| STAGE5-051 | Order Item and Specification Contracts |
| STAGE5-052 | Manual Entry UI |
| STAGE5-052A | Imported Draft Item Commercial Contract |
| STAGE5-053 | Import Core |
| STAGE5-054 | STXT File Adapter |
| STAGE5-055 | Import GUI |
| STAGE5-056 | Automated Verification |
| STAGE5-057 | Manual GUI Smoke (Order Intake) |

Порядок: 050 DONE → 051 → 052 → 052A → 053 → 054 → 055 → 056 → 057. Одновременно только одна задача `IN_PROGRESS`. Финальное закрытие Stage 5 — только после `STAGE5-057` и итоговой ручной проверки.

`TransactionalEventPublisher` уже реализован ранее.

---

## 18. Правила контекста

`CONTEXT-MAP.md` → «Stage 5 — Order Management Context» и группы Order Intake (`stage5-order-intake-*`). Запрещено загружать полную реализацию Production/Warehouse/Cutting/Analytics. Security — только при прямой необходимости permission checks.

---

## 19. Verification gates

- Per-task focused tests; integration gate (Testcontainers); document lifecycle gate; idempotency gate; transaction rollback gate; architecture gate; **Order Intake parser/import gates**; full `mvn clean verify` + `-Ppackage`; manual GUI smoke (`STAGE5-050` core, затем `STAGE5-057` intake).

---

## 20. Exit criteria

Stage 5 завершён только когда:

- UI может создать платформенный документ и сохранить typed draft payload;
- processor получает `DocumentId` и загружает payload (`onPost` возвращает `void`);
- payload immutable после проведения; публичный повторный `postDocument` отклоняется lifecycle validation; idempotency guard внутри processor предотвращает повторную/конкурентную обработку;
- публичный `TransactionalEventPublisher` реализован; события публикуются только после commit;
- unpost проведённого документа отклоняется; delete draft-документа удаляет payload;
- active Revision не заменяется Draft Revision до утверждения; предыдущие Revision immutable;
- Query API предоставляет данные с поиском и пагинацией;
- Stage 5 не отменяет approved order или active item и не меняет состав approved order;
- производственное состояние не хранится;
- capabilities зарегистрированы; UI использует application/document flow;
- миграции и persistence соответствуют модели; unit/integration/architecture tests проходят;
- `mvn clean verify` и `-Ppackage` зелёные;
- **core** packaged GUI smoke (`STAGE5-050`) подтверждён пользователем;
- **Order Intake MVP** (`STAGE5-051..057`): ручной ввод и файловый импорт создают одинаковую доменную структуру; preview до persistence; атомарный импорт; конфликт существующего заказа; без Firebird; incomplete DRAFT per ADR-030; итоговый smoke `STAGE5-057` подтверждён;
- явная остановка перед Stage 6.

`STAGE5-050` сам по себе **не** закрывает Stage 5.

## 21. Decisions for Order Intake

- **ADR-030 / бывший blocker `STAGE5-INTAKE-COMMERCIAL-DRAFT`:** RESOLVED — incomplete commercial data allowed only in DRAFT (order-level and item-level `productCode`/`name`); approval requires all mandatory commercial fields; placeholders prohibited. Item-level incompleteness gated by `STAGE5-052A` before Import Core.
