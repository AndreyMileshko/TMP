# Stage 5 Manifest — Order Management

**Stage:** 5 — Order Management
**Primary specification:** `docs/TMP/TMP_Initial_Documents/architecture/10-Order-Management/Order-Management-Specification.md` (v1.1)
**Status:** Start Gate PASSED (STAGE5-000); implementation not started.

---

## 1. Цель Stage 5

Реализовать Capability Order Management как единственного владельца коммерческого жизненного цикла заказов клиентов, позиций заказов, редакций позиций и неизменяемых спецификаций изделий, полностью управляемого через документную модель Document Engine, с Query Public API и Domain Events для будущих Capability, без хранения производственного, складского или раскроечного состояния.

---

## 2. Входные условия

- Stage 0–4 завершены (DONE 100%); нет открытых блокеров Stage 0–4.
- Order Management Specification обновлена до v1.1 и прошла documentation gate (STAGE5-000).
- Доступны публичные контракты: Document Engine, Platform Core Event API, Capability Engine, Security, UI Shell.
- Flyway highest version = `V5`; Order Management начинает с `V6`.
- Reactor содержит: `tmp-platform-core`, `tmp-infra-db`, `tmp-document-engine`, `tmp-capability-engine`, `tmp-security`, `tmp-ui-shell`, `tmp-bootstrap-app`, `tmp-architecture-tests`.

---

## 3. Архитектурные границы

- Новый модуль: `tmp-order-management`.
- Public API package: `com.tmp.order.api` (Query DTO, идентификаторы, события — стабильные контракты).
- Внутренние пакеты: `com.tmp.order.domain` (+ `...domain.repository` ports), `com.tmp.order.application`, `com.tmp.order.persistence`, `com.tmp.order.capability`, `com.tmp.order` (auto-configuration + `PlatformComponent`).
- Разрешённые зависимости модуля: `com.tmp.core.api..`, `com.tmp.capability.api..`, `com.tmp.document.api..`, `com.tmp.security.api..` (только публичные API). JavaFX/UI — запрещены в `tmp-order-management`.
- UI-экраны Order Management реализуются в `tmp-ui-shell` (FXML + Controller + ViewModel), как в Stage 4.
- Персистентность: `spring-boot-starter-jdbc` + `JdbcTemplate`, ручные ports/adapters/row-mappers (прецедент Stage 2/4). Без JPA/Hibernate.
- Схема БД: `order_management` (schema-per-module).

---

## 4. Реализуемые агрегаты

- **Customer Order** (root `CustomerOrder`, id `OrderId`): коммерческий заказ; статусы `DRAFT`/`APPROVED`/`CANCELLED`.
- **Order Item** (root `OrderItem`, id `OrderItemId`): главный объект; статусы `DRAFT`/`ACTIVE`/`CANCELLED`; `currentRevisionNumber`.
- **Order Item Revision** (сущность в границе Order Item, id `OrderItemId + RevisionNumber`): статусы `DRAFT`/`APPROVED`.
- **Item Specification** (значимый объект в границе Revision): материалы, количества, нормы; Immutable после утверждения Revision.

Границы транзакций: Customer Order — собственная транзакция; Order Item + его Revisions + Specifications — единая граница агрегата Order Item.

---

## 5. Public API

### Query API (для других Capability, read-only)
`getOrder`, `getOrderItem`, `getOrderItemRevision`, `getCurrentOrderItemRevision`, `getItemSpecification`. DTO содержат только данные Order Management.

### Внутренний Application API (только Document Processors Order Management)
`createOrder`, `updateOrder`, `approveOrder`, `cancelOrder`, `createOrderItem`, `updateOrderItem`, `approveOrderItemRevision`, `cancelOrderItem`, `createOrderItemRevision`. Не доступны внешним Capability.

---

## 6. Бизнес-документы

`ORDER_CREATE`, `ORDER_UPDATE`, `ORDER_APPROVE`, `ORDER_CANCEL`, `ORDER_ITEM_CREATE`, `ORDER_ITEM_UPDATE`, `ORDER_ITEM_REVISION_APPROVE`, `ORDER_ITEM_CANCEL`, `ORDER_ITEM_REVISION_CREATE`.

Каждый документ имеет Document Processor, application command, affected aggregate, required capability, validation rules, result, Domain Event и idempotency rule (по `documentId`) — см. Specification §16.1.

---

## 7. Domain Events

`OrderCreated`, `OrderUpdated`, `OrderApproved`, `OrderCancelled`, `OrderItemCreated`, `OrderItemUpdated`, `OrderItemRevisionCreated`, `OrderItemRevisionApproved`, `OrderItemCancelled`.

Публикация только после commit. `OrderItemRevisionApproved` потребляется Production (Production Spec §16). События Production/Warehouse/Cutting Order Management не публикует.

---

## 8. Capabilities

Формат кодов — 3 сегмента `<area>.<resource>.<action>` (совместимо с Security `PermissionId`):
`order.order.view`, `order.order.create`, `order.order.edit`, `order.order.approve`, `order.order.cancel`, `order.item.view`, `order.item.create`, `order.item.edit`, `order.item.approve`, `order.item.cancel`, `order.revision.create`, `order.specification.view`.

Регистрируются через `OrderManagementCapability` (Capability Engine), permission gating навигации — как в Stage 4.

---

## 9. Persistence scope

Схема `order_management`: `orders`, `order_items`, `order_item_revisions`, `item_specifications`, `item_specification_lines`. Optimistic locking, общие технические поля, schema-per-module — по Database Specification.

---

## 10. Flyway scope

Единый глобальный `classpath:db/migration`. Order Management добавляет `V6__order_management_schema.sql` (и последующие `V7+` при необходимости). Существующие `V1..V5` не изменяются.

---

## 11. UI scope

В `tmp-ui-shell`: навигация Order Management, Order list, Order editor, Order Item + Revision editor, Specification view/editor, обработка ошибок (валидация, access denied, нейтральные сообщения). UI использует только application/document flow и Query API; прямых мутаций агрегатов нет.

---

## 12. Testing scope

- Unit: domain (агрегаты, инварианты, immutability), application (commands/processors).
- Integration (PostgreSQL Testcontainers): schema/constraints, optimistic lock, revision immutability, end-to-end document→command→aggregate→persistence→event, query API.
- Architecture tests (`tmp-architecture-tests`): package boundaries, отсутствие production-owned данных, отсутствие внешнего mutating API, зависимость только от разрешённых публичных API.
- Full reactor `mvn clean verify`; package profile `mvn clean verify -Ppackage`; manual packaged GUI smoke (пользователь).

---

## 13. Запрещённый scope (Out of scope)

Явно исключены из Stage 5:

- производственные статусы (`Production Status`);
- Production State и производственные количества (launched/active/released);
- запуск производства, отмена запуска, выпуск изделий;
- проверка обеспеченности материалами;
- резервирование и складские движения;
- Warehouse Stock Position и складские документы;
- Cutting Plan и его внутренние данные;
- аналитика;
- Procurement;
- коммерческие статусы заказа `IN_PROGRESS`/`COMPLETED` (перенесены в будущую интеграцию Order Management ↔ Production);
- прямая интеграция с внутренней реализацией будущих Capability (только через Public API/Domain Events);
- compile-time зависимости от Production/Warehouse/Cutting/Analytics.

---

## 14. Порядок реализации

Последовательность задач определена в `WORK-QUEUE.md` (`STAGE5-001..STAGE5-038`):
модуль и границы → architecture rules → идентификаторы/VO → домен (Order → Item → Revision → Specification) → immutability → repositories/ports → Query API → application commands → Domain Events → документы/процессоры → application services/транзакции/идемпотентность → capability/Security → schema/Flyway/adapters → persistence & query ITs → UI (навигация/списки/редакторы/спецификация/ошибки) → unit/integration/architecture tests → full reactor → packaged verify → manual GUI smoke (final gate).

Одновременно только одна задача Stage 5 в статусе `READY`. Первой READY-задачей является `STAGE5-001`.

---

## 15. Правила контекста

Определены в `CONTEXT-MAP.md`, раздел «Stage 5 — Order Management Context». Cursor загружает только релевантные разделы Specification v1.1, Stage Manifest и разрешённые публичные API. Запрещено загружать полную реализацию Production, Warehouse, Cutting Optimization и Analytics.

---

## 16. Verification gates

- Per-task: команды из `Verification commands` каждой задачи (focused module tests).
- Integration gate: PostgreSQL Testcontainers ITs зелёные.
- Architecture gate: Stage 5 architecture tests зелёные.
- Full gate: `mvn clean verify` + `mvn clean verify -Ppackage` зелёные.
- Manual gate: packaged GUI smoke подтверждён пользователем (`STAGE5-038`).

---

## 17. Exit criteria

Stage 5 завершён только когда:

- заказы создаются и изменяются через документы;
- позиции и Revision создаются и утверждаются через документы;
- утверждённая Specification неизменяема;
- Query API предоставляет данные по Order/Order Item/Revision/Specification;
- другие Capability не могут изменять Order Management напрямую;
- производственное состояние не хранится в Stage 5;
- capabilities зарегистрированы в Security/Capability Engine;
- UI использует application/document flow;
- миграции и persistence соответствуют модели;
- unit, integration и architecture tests проходят;
- `mvn clean verify` и `mvn clean verify -Ppackage` зелёные;
- packaged GUI smoke выполнен пользователем;
- явная остановка перед Stage 6 (Start Gate Stage 6 не пройден).
