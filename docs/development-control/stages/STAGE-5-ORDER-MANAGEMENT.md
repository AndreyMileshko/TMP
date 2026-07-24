# Stage 5 Manifest — Order Management

**Stage:** 5 — Order Management
**Primary specification:** `docs/TMP/TMP_Initial_Documents/architecture/10-Order-Management/Order-Management-Specification.md` (v1.2)
**Status:** Start Gate PASSED (STAGE5-000 → STAGE5-000-FIX); implementation not started.

---

## 1. Цель Stage 5

Реализовать Capability Order Management как единственного владельца коммерческого жизненного цикла заказов, позиций, редакций и неизменяемых спецификаций, полностью управляемого через документную модель Document Engine с capability-owned typed payload, с Public Query API (поиск/пагинация) и Domain Events для будущих Capability, без хранения производственного, складского или раскроечного состояния.

---

## 2. Входные условия

- Stage 0–4 завершены (DONE 100%); нет открытых блокеров Stage 0–4.
- Order Management Specification обновлена до v1.2; Constitution v1.2; ADR v1.3 (ADR-028 добавлен; ADR-003/004 уточнены); documentation gate STAGE5-000-FIX пройден.
- Подтверждённая транзакционная граница Document Engine (processor внутри транзакции проведения; события после commit) — prerequisite Platform/Document Engine НЕ требуется.
- Flyway highest version = `V5`; Order Management начинает с `V6`.
- Reactor: `tmp-platform-core`, `tmp-infra-db`, `tmp-document-engine`, `tmp-capability-engine`, `tmp-security`, `tmp-ui-shell`, `tmp-bootstrap-app`, `tmp-architecture-tests`.

---

## 3. Архитектурные границы

- Новый модуль: `tmp-order-management`.
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

---

## 5. Capability-owned document payload

- Document Engine владеет lifecycle/metadata; Order Management владеет typed payload по `DocumentId` (ADR-028).
- Payload: typed Java-модель на тип документа; поля `DocumentId`, `DocumentTypeCode`, `PayloadSchemaVersion`, `PayloadRevision`, `CreatedAt`, `UpdatedAt`.
- Собственный persistence port и adapter; optimistic locking черновика через `PayloadRevision`.
- Editable только в Draft-состоянии документа; Immutable после проведения.
- Не generic JSON в Platform Core; недоступен другим Capability напрямую.
- Processing record: `DocumentId + Operation` unique; идемпотентность проведения.

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

- `onPost`: единственное бизнес-изменение; загрузка payload по `DocumentId`; проверка schema version + optimistic lock + предусловий; идемпотентность; запись processing record.
- `onUnpost`: NOT SUPPORTED (отклоняется).
- `onDelete`: только Draft; удаляет payload; без изменения агрегатов, без события.
- `onClose`: без изменения бизнес-состояния; сохраняет payload/историю.

---

## 11. Транзакционная граница (verified)

Подтверждено по фактическому контракту Document Engine: `DefaultDocumentEngine` `@Transactional`; `onPost` вызывается внутри транзакции проведения; события после commit (`TransactionAfterCommitEventPublisher`); `DocumentId` доступен через `context.document().id()`. Изменение агрегата + processing record + результат проведения атомарны в этой границе. Включена задача верификации транзакционной границы (тест) до реализации Document Processors.

---

## 12. Persistence scope

Схема `order_management`: `orders`, `order_items`, `order_item_revisions`, `item_specifications`, `item_specification_lines`, typed document payload persistence (ключ `DocumentId`), processing record (`document_id + operation` unique). Optimistic locking; schema-per-module.

Запрещено хранить: Production Status, launched/active/released quantity, партии/документы Production, Stock Position, резервы, складские движения, Cutting Plan internals, generic JSON в Platform Core.

---

## 13. Flyway scope

Единый `classpath:db/migration`. Order Management добавляет `V6__order_management_schema.sql` (+ `V7+` при необходимости, напр. payload/processing). `V1..V5` не изменяются.

---

## 14. UI scope

В `tmp-ui-shell`: навигация, Order list (через paginated Query API), Order editor, Item + Revision editor, Specification editor (read-only после утверждения), обработка ошибок. UI создаёт платформенный документ, сохраняет typed draft payload, запрашивает проведение; прямых мутаций агрегатов нет.

---

## 15. Testing scope

- Unit: domain (агрегаты, инварианты, active/draft revision, immutability), application (commands/processors), payload use cases.
- Integration (PostgreSQL Testcontainers): schema/constraints, optimistic lock, revision immutability, payload persistence, document lifecycle (post/unpost-rejected/close/delete), idempotency (повторный post), transaction rollback (сбой в onPost откатывает всё, событие не публикуется), end-to-end документный поток, Query API поиск/пагинация.
- Architecture tests: границы пакетов; отсутствие production-owned данных; отсутствие внешнего mutating API; payload не в Platform Core; зависимость только от разрешённых публичных API; транзакционная граница.
- Full reactor `mvn clean verify`; package profile; manual packaged GUI smoke (пользователь).

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
- прямые mutating-вызовы между Capability.

---

## 17. Порядок реализации

Определён в `WORK-QUEUE.md` (`STAGE5-001..STAGE5-045`): module bootstrap → architecture boundaries → identifiers/VO → domain aggregates → active/draft revision → immutable specification → repository ports → Query API contracts → paginated search contracts → typed payload models → payload application use cases → payload persistence port → payload DB schema → processing record & idempotency model → document type registration → document lifecycle policies → transaction boundary verification → Document Processors (по группам) → application commands → Domain Events → aggregate persistence → Flyway → Security capabilities → UI (навигация/список/редакторы/спецификация/ошибки) → unit tests → persistence ITs → document lifecycle ITs → idempotency tests → transaction rollback tests → architecture tests → full reactor → packaged verify → manual GUI smoke.

Одновременно только одна задача Stage 5 в статусе `READY`. Первой READY-задачей является `STAGE5-001`.

---

## 18. Правила контекста

`CONTEXT-MAP.md` → «Stage 5 — Order Management Context» (в т.ч. группы: document payload model / payload persistence / processor lifecycle / processing idempotency / revision draft workflow / query search and pagination / transaction boundary verification). Запрещено загружать полную реализацию Production/Warehouse/Cutting/Analytics.

---

## 19. Verification gates

- Per-task focused tests; integration gate (Testcontainers); document lifecycle gate; idempotency gate; transaction rollback gate; architecture gate; full `mvn clean verify` + `-Ppackage`; manual GUI smoke (`STAGE5-045`).

---

## 20. Exit criteria

Stage 5 завершён только когда:

- UI может создать платформенный документ и сохранить typed draft payload;
- processor получает `DocumentId` и загружает payload;
- payload immutable после проведения; repeat posting идемпотентен;
- unpost проведённого документа отклоняется; delete draft-документа удаляет payload;
- active Revision не заменяется Draft Revision до утверждения; предыдущие Revision immutable;
- Query API предоставляет данные (Order/Item/Revision/Specification) с поиском и пагинацией; order list работает только через Query API;
- Stage 5 не отменяет approved order или active item и не меняет состав approved order;
- производственное состояние не хранится;
- capabilities зарегистрированы; UI использует application/document flow;
- миграции и persistence соответствуют модели; unit/integration/architecture tests проходят;
- `mvn clean verify` и `-Ppackage` зелёные; packaged GUI smoke подтверждён пользователем;
- явная остановка перед Stage 6.
