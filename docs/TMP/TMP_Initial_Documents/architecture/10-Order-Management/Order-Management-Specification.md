# Order Management Specification

**Document ID:** TMP-SPEC-010
**Status:** Accepted
**Version:** 1.2

---

# 1. Назначение

Order Management — функциональная область TOP Manufacturing Platform (TMP), отвечающая за коммерческий жизненный цикл заказов клиентов, позиций заказов, редакций позиций и неизменяемых спецификаций изделий.

Order Management является единственным владельцем заказов клиентов, позиций заказов, редакций позиций и их спецификаций, а также владельцем строго типизированного бизнес-содержимого (payload) своих документов.

Главным объектом платформы является **позиция заказа (Order Item)**. Источником данных для Production, Warehouse, Cutting, Procurement является позиция заказа в конкретной **утверждённой (active) Revision** (`Order Item ID` + `Revision`).

Order Management **не владеет** производственным состоянием — оно принадлежит Production.

---

# 2. Цели Order Management

* единое хранение заказов клиентов;
* управление коммерческим жизненным циклом заказа, позиции и редакции позиции;
* хранение коммерческой информации;
* формирование неизменяемой спецификации изделия, привязанной к конкретной Revision;
* владение типизированным payload своих бизнес-документов;
* предоставление данных другим Capability через Public Query API и Domain Events;
* управление изменениями только через бизнес-документы Document Engine;
* идемпотентная обработка проведения документов;
* прослеживаемость собственных изменений.

Order Management **не** хранит и **не** вычисляет производственное состояние.

---

# 3. Границы владения

Order Management является единственным владельцем и хранителем:

* Customer Order, коммерческих данных;
* Order Item, признака активности позиции;
* Order Item Revision, номера редакции, признаков active/draft;
* Item Specification (состав изделия, нормы);
* количества изделий в Revision (ordered quantity);
* связей между редакциями;
* типизированного payload своих документов (по `DocumentId`);
* processing record проведённых документов;
* истории собственных изменений.

Order Management **не** владеет и **не** хранит (принадлежит другим Capability):

* Production State, `Production Status`, launched/active/released quantity, производственные партии и документы Production — **Production**;
* Stock Position, резервы, складские движения и документы — **Warehouse**;
* Cutting Plan, Source Bar, Cut Piece и внутренние данные раскроя — **Cutting Optimization**;
* аналитические проекции — **Analytics**;
* платформенные lifecycle и metadata документа (`DocumentId`, тип, заголовок, автор, статус, операции `post`/`unpost`/`close`/`delete`) — **Document Engine**.

| Данные | Владелец |
|---|---|
| Customer Order, Order Item, Revision, Specification, ordered quantity, typed payload, processing record | Order Management |
| Production Status, launched/active/released quantity, производственные партии/документы | Production |
| Stock Position, резервы, складские движения | Warehouse |
| Cutting Plan и его внутренние данные | Cutting Optimization |
| Document lifecycle и metadata | Document Engine |

> **Architecture Rule (ADR-019, ADR-028)**
> Order Management не хранит копий данных других Capability; предметный payload документов принадлежит Order Management и не хранится в Platform Core как generic JSON.

---

# 4. Основные архитектурные принципы

1. Главным объектом платформы является позиция заказа (ADR-017).
2. Другие Capability работают с конкретной утверждённой (active) Revision позиции.
3. Спецификация active Revision — единственный источник состава изделия; после утверждения Immutable (ADR-018).
4. Изменение изделия выполняется новой Revision.
5. Любое изменение агрегатов выполняется только через бизнес-документ Document Engine (ADR-004, Constitution принцип 28).
6. Изменяющие операции не являются внешним Public API; другие Capability используют только Public Query API и Domain Events (ADR-003).
7. Document Engine владеет lifecycle/metadata документа; Order Management владеет типизированным payload, связанным по `DocumentId` (ADR-028).
8. Проведение документа идемпотентно; повторный `onPost` не повторяет бизнес-изменение.
9. Проведённые документы и утверждённое содержимое неизменяемы (ADR-021).
10. Вычисляемые состояния не хранятся (ADR-020).
11. Владелец хранения данных совпадает с владельцем их изменения.
12. Order Management не хранит производственное состояние.

---

# 5. Агрегаты

## 5.1 Customer Order (Aggregate Root)

* **Root:** `CustomerOrder`; **id:** `OrderId` (стабильный).
* **VO/поля:** `OrderNumber` (уникальный), `CustomerRef`/`CustomerName`, `ContractRef`, `SiteRef`, `Direction`, `Currency`, `OrderStatus`, `createdAt`/`updatedAt`, optimistic-lock version.
* **Инварианты:** номер уникален; коммерческие поля изменяются только в `DRAFT`; ссылается на позиции по `OrderItemId`.
* **Изменения:** только через документы `ORDER_*`.
* **Транзакция:** собственная граница.

### Атрибуты Customer Order

| Атрибут | Назначение |
| --- | --- |
| ID (`OrderId`) | Уникальный идентификатор |
| Номер | Уникальный номер заказа |
| Заказчик | Клиент (`CustomerRef` / `CustomerName`) |
| Договор | Основание выполнения |
| Объект | Строительный объект |
| Дата | Дата оформления (`createdAt`) |
| Ответственный | Менеджер |
| Направление | Частное / дилер / корпоративный |
| Валюта | Валюта расчётов |
| Статус | Коммерческий статус (см. §8) |

Не содержит производственных статусов и количеств.

## 5.2 Order Item (Aggregate Root)

* **Root:** `OrderItem` (главный объект); **id:** `OrderItemId` (стабильный на всех редакциях).
* **VO/поля:** набор `OrderItemRevision`, `activeRevisionNumber` (nullable), `draftRevisionNumber` (nullable), `OrderItemStatus`, `ProductCode`, коммерческие поля позиции.
* **Инварианты:**
  * принадлежит одному заказу;
  * не более одной Draft Revision одновременно;
  * `activeRevisionNumber` указывает на последнюю утверждённую Revision;
  * номера редакций монотонно возрастают, не переиспользуются;
  * не хранит производственных статусов/количеств.
* **Изменения:** только через документы позиции/редакции.
* **Транзакция:** агрегат Order Item включает свои Revision и их Specification; создание/обновление/утверждение редакции и заморозка спецификации атомарны в границе Order Item.

### Атрибуты Order Item

| Атрибут | Назначение |
| --- | --- |
| ID (`OrderItemId`) | Стабильный идентификатор позиции |
| Order | Родительский заказ (`OrderId`) |
| Код изделия | Код изделия |
| Наименование | Наименование |
| Active Revision | Номер утверждённой active Revision (nullable) |
| Draft Revision | Номер текущей draft Revision (nullable) |
| Статус | Коммерческий статус позиции (см. §9) |

Коммерческие поля позиции (наименование, код изделия, комментарии), не входящие в Revision/Specification, изменяются документом `ORDER_ITEM_UPDATE`.

## 5.3 Order Item Revision (сущность в границе Order Item)

* **id:** `OrderItemId` + `RevisionNumber`.
* **VO:** `RevisionNumber`, `RevisionStatus` (`DRAFT` | `APPROVED`), `OrderedQuantity`, ссылка на предыдущую Revision, ссылка на `ItemSpecification`.
* **Инварианты:** номер уникален в позиции; ровно одна Specification на Revision; после `APPROVED` — Immutable; предыдущие Revision не изменяются и не удаляются.

## 5.4 Item Specification (значимый объект в границе Revision)

* **id:** привязан к `OrderItemId` + `RevisionNumber`.
* **Состав:** материалы, количество, единицы измерения, нормы расхода, производственные параметры.
* **Не содержит:** складских остатков, партий, резервов, производственных количеств.
* **Инвариант:** после утверждения Revision Immutable (ADR-018).

---

# 6. Revision model: active и draft

## 6.1 Разделение

Для позиции различаются:

* **`activeRevision`** — последняя утверждённая Revision:
  * доступна другим Capability (Production, Warehouse, Cutting);
  * Immutable;
  * сохраняется и доступна по точному `RevisionNumber`;
  * является единственной Revision, возвращаемой во внешний Public Query API как производственная спецификация.
* **`draftRevision`** — необязательная Revision в разработке:
  * не заменяет `activeRevision` до утверждения;
  * недоступна другим Capability как производственная спецификация;
  * редактируется до утверждения (документ `ORDER_ITEM_REVISION_UPDATE`);
  * после утверждения становится новой `activeRevision`;
  * предыдущая `activeRevision` остаётся Immutable и доступной по истории.

Одновременно у позиции существует не более одной Draft Revision.

## 6.2 Создание новой Revision (для active позиции)

Документ `ORDER_ITEM_REVISION_CREATE`:

* создаёт `Revision N+1` в статусе `DRAFT`;
* не изменяет действующую `activeRevision`;
* не изменяет коммерческий статус позиции;
* допускает копирование данных предыдущей Revision как начальное значение;
* не изменяет предыдущую Revision;
* требует отсутствия уже существующей Draft Revision.

## 6.3 Редактирование Draft Revision

Документ `ORDER_ITEM_REVISION_UPDATE` изменяет только текущую Draft Revision (спецификация, количество). Использование `ORDER_ITEM_UPDATE` для изменения Revision/Specification запрещено — `ORDER_ITEM_UPDATE` работает только с коммерческими полями позиции.

## 6.4 Утверждение Revision

Документ `ORDER_ITEM_REVISION_APPROVE` атомарно:

1. проверяет наличие Draft Revision;
2. проверяет полноту и корректность Item Specification;
3. делает Draft Revision Immutable;
4. назначает её новой `activeRevision`;
5. удаляет ссылку `draftRevision`;
6. сохраняет предыдущую active Revision без изменений;
7. публикует `OrderItemRevisionApproved` (после commit).

Другие Capability всегда ссылаются на `OrderItemId` + `RevisionNumber`. Смена `activeRevision` не делает предыдущую Revision недействительной для уже начатых внешних процессов, которые работают по конкретному номеру Revision.

---

# 7. Неизменяемость спецификации

После утверждения Revision её Item Specification становится **Immutable** (нельзя изменить/добавить/удалить материал, изменить количество или нормы). Изменение изделия — только новой Revision или новой позицией. Предыдущие Revision и спецификации не изменяются и не удаляются (ADR-018, ADR-021).

---

# 8. Коммерческий жизненный цикл Customer Order

## 8.1 Статусы (Stage 5)

| Статус | Назначение |
| --- | --- |
| `DRAFT` | Черновик заказа |
| `APPROVED` | Заказ утверждён |
| `CANCELLED` | Заказ отменён (только из `DRAFT`) |

Статусы `IN_PROGRESS` и `COMPLETED` **исключены** из Stage 5 (нет самостоятельного коммерческого процесса; производны от производственного состояния, владелец — Production). Перенесены в будущую интеграцию Order↔Production (см. §18). Customer Order не связывается автоматически с `Production Status`.

## 8.2 Transition matrix — Customer Order (Stage 5)

| From | To | Business document | Required capability | Preconditions | Forbidden conditions | Domain event |
| --- | --- | --- | --- | --- | --- | --- |
| (none) | `DRAFT` | `ORDER_CREATE` | `order.order.create` | уникальный номер | дублирующий номер | `OrderCreated` |
| `DRAFT` | `DRAFT` | `ORDER_UPDATE` | `order.order.edit` | заказ в `DRAFT` | изменение утверждённого/отменённого заказа | `OrderUpdated` |
| `DRAFT` | `APPROVED` | `ORDER_APPROVE` | `order.order.approve` | ≥ 1 активная позиция | утверждение без активных позиций | `OrderApproved` |
| `DRAFT` | `CANCELLED` | `ORDER_CANCEL` | `order.order.cancel` | заказ в `DRAFT` | отмена утверждённого заказа (запрещено в Stage 5) | `OrderCancelled` |

Переход `APPROVED → CANCELLED` **запрещён в Stage 5** и перенесён в будущую интеграционную задачу (компенсационные документы + проверка внешних Capability).

---

# 9. Коммерческий жизненный цикл Order Item

## 9.1 Статусы (Stage 5)

| Статус | Назначение |
| --- | --- |
| `DRAFT` | Позиция формируется; активная работа с Draft Revision |
| `ACTIVE` | Есть ≥ 1 утверждённая Revision; доступна другим Capability |
| `CANCELLED` | Позиция отменена (только из `DRAFT`) |

Производственные статусы (`NOT_STARTED`, `READY_FOR_PRODUCTION`, `IN_PRODUCTION`, `PARTIALLY_RELEASED`, `RELEASED`) **не являются** статусами Order Item — это `Production Status` во владении Production.

## 9.2 Transition matrix — Order Item (Stage 5)

| From | To | Business document | Required capability | Preconditions | Forbidden conditions | Domain event |
| --- | --- | --- | --- | --- | --- | --- |
| (none) | `DRAFT` | `ORDER_ITEM_CREATE` | `order.item.create` | родительский заказ в `DRAFT` | добавление позиции в `APPROVED`/`CANCELLED` заказ | `OrderItemCreated`, `OrderItemRevisionCreated` |
| `DRAFT` | `DRAFT` | `ORDER_ITEM_UPDATE` (коммерческие поля) / `ORDER_ITEM_REVISION_UPDATE` (draft spec) | `order.item.edit` / `order.revision.edit` | позиция `DRAFT`; для spec — есть Draft Revision | изменение состава через `ORDER_ITEM_UPDATE` | `OrderItemUpdated` / `OrderItemRevisionUpdated` |
| `DRAFT` | `ACTIVE` | `ORDER_ITEM_REVISION_APPROVE` | `order.item.approve` | Draft Revision валидна | утверждение без спецификации | `OrderItemRevisionApproved` |
| `ACTIVE` | `ACTIVE` | `ORDER_ITEM_REVISION_CREATE` → `ORDER_ITEM_REVISION_UPDATE`* → `ORDER_ITEM_REVISION_APPROVE` | `order.revision.create`, `order.revision.edit`, `order.item.approve` | текущая Revision `APPROVED`, нет открытой Draft | новая Revision при существующей Draft | `OrderItemRevisionCreated`, `OrderItemRevisionUpdated`*, `OrderItemRevisionApproved` |
| `DRAFT` | `CANCELLED` | `ORDER_ITEM_CANCEL` | `order.item.cancel` | позиция `DRAFT` | отмена active позиции (запрещено в Stage 5) | `OrderItemCancelled` |

\* `ORDER_ITEM_REVISION_UPDATE` необязателен, применяется при необходимости правок draft.

Переход `ACTIVE → CANCELLED` **запрещён в Stage 5** (перенесён в будущую интеграцию).

## 9.3 Transition matrix — Order Item Revision (Stage 5)

| From | To | Business document | Required capability | Preconditions | Forbidden conditions | Domain event |
| --- | --- | --- | --- | --- | --- | --- |
| (none) | `DRAFT` | `ORDER_ITEM_CREATE` (Rev 1) / `ORDER_ITEM_REVISION_CREATE` (Rev N+1) | `order.item.create` / `order.revision.create` | для N+1: предыдущая `APPROVED`, нет Draft | вторая Draft у позиции | `OrderItemRevisionCreated` |
| `DRAFT` | `DRAFT` | `ORDER_ITEM_REVISION_UPDATE` | `order.revision.edit` | Revision в `DRAFT` | правка утверждённой Revision | `OrderItemRevisionUpdated` |
| `DRAFT` | `APPROVED` | `ORDER_ITEM_REVISION_APPROVE` | `order.item.approve` | спецификация полна и валидна | утверждение невалидной спецификации | `OrderItemRevisionApproved` |

После `APPROVED` спецификация Immutable; актуальность вычисляется по `activeRevisionNumber`.

## 9.4 Разделение жизненных циклов

```text
Customer Order commercial lifecycle → §8 (Order Management)
Order Item commercial lifecycle     → §9.2 (Order Management)
Order Item Revision lifecycle       → §9.3 (Order Management)
Production lifecycle                → Production Specification (Production)
```

Production lifecycle не входит в Stage 5.

---

# 10. Производственное состояние (внешнее)

Производственное состояние принадлежит Production и связывается им с `Order Item ID` + `Revision`. Order Management не хранит `Production Status`, производственные количества и партии; при необходимости читает состояние через Production Public API. Завершённость производства заказа вычисляется владельцем состояния и не хранится Order Management (ADR-020).

---

# 11. Типизированный payload бизнес-документа (ADR-028)

## 11.1 Владение и хранение

* Document Engine владеет lifecycle и metadata документа (`DocumentId`, тип, заголовок, статус, `post`/`unpost`/`close`/`delete`, вызов `DocumentProcessor`).
* Order Management владеет строго типизированным business payload своих документов.
* Payload:
  * **не** хранится в `DocumentMetadata`;
  * **не** является generic JSON внутри Platform Core;
  * **не** изменяет публичную модель Document Engine;
  * хранится Order Management отдельно (собственный persistence port и adapter);
  * связывается с платформенным документом через `DocumentId`;
  * имеет строго типизированную Java-модель на тип документа;
  * редактируется только внутренними UI/Application use cases Order Management;
  * недоступен другим Capability для прямого чтения или изменения.

## 11.2 Payload identity

Каждый payload содержит или определяет:

```text
DocumentId          — главный идентификатор payload
DocumentTypeCode
PayloadSchemaVersion
PayloadRevision      — optimistic lock черновика
CreatedAt
UpdatedAt
```

Не создаётся отдельный бизнес-документ Order Management, не связанный с платформенным `DocumentId`.

## 11.3 Изменяемость payload

Payload изменяем только пока платформенный документ в редактируемом Draft-состоянии. После начала успешного проведения payload становится Immutable: его нельзя заменить или исправить; исправление — новым документом; историческое содержимое сохраняется. Optimistic locking черновика — через `PayloadRevision`.

## 11.4 Поток

```text
Order Management UI
  -> Document Engine: create platform document
  -> Document Engine returns DocumentId
  -> Order Management stores typed draft payload by DocumentId
  -> User edits typed draft payload (ORDER_*_UPDATE use cases)
  -> User requests document posting
  -> Document Engine starts posting operation (transactional)
  -> Document Engine calls Order Management DocumentProcessor.onPost(context)
  -> DocumentProcessor reads DocumentId from context.document().id()
  -> loads typed payload from Order Management repository
  -> validates payload schema version, optimistic lock, preconditions
  -> creates internal application command
  -> changes aggregate
  -> persists aggregate
  -> writes processing record (idempotency)
  -> publishes Domain Event after commit
  -> marks processing result
```

---

# 12. Транзакционная модель (подтверждено по фактическому контракту Document Engine)

Проверка контракта Document Engine (`tmp-document-engine`):

* `DocumentOperationContext.document()` возвращает `DocumentMetadata` с `DocumentId` (`id()`), доступным во всех hook'ах, включая `onPost`.
* `DefaultDocumentEngine` аннотирован `@Transactional`; `postDocument(...)` вызывает `DocumentProcessor.onPost(context)` **внутри** той же транзакции, что и изменение статуса документа (`DRAFT → POSTED`) и запись lifecycle-журнала.
* Domain Events публикуются только после commit (`TransactionAfterCommitEventPublisher.afterCommit`); при откате событие не публикуется (подтверждено тестами `eventNotEmittedOnRollback`, `rollbackAfterProcessorValidationFailureDoesNotEmitEvent`).

Следствие для Order Management:

* изменение агрегата, запись processing record и изменение результата проведения выполняются в `onPost` и участвуют в **той же** транзакционной границе Document Engine (Spring propagation `REQUIRED`);
* при исключении в `onPost` вся транзакция откатывается атомарно (документ не переходит в `POSTED`, агрегат не изменяется, событие не публикуется);
* Domain Events Order Management публикуются после commit через Platform Core `EventBus` тем же after-commit механизмом.

Атомарность требуемого набора (processor → aggregate → processing record → posting result; event после commit) **гарантирована** существующим контрактом Document Engine. Отдельная prerequisite-задача Platform/Document Engine **не требуется**. Для страховки в очередь Stage 5 включена задача верификации транзакционной границы (архитектурный/интеграционный тест) до реализации Document Processors.

---

# 13. Бизнес-документы

## 13.1 Каталог (Stage 5)

| Document type code | Business name | Application command | Affected aggregate | Required capability | Result | Domain event |
| --- | --- | --- | --- | --- | --- | --- |
| `ORDER_CREATE` | Создание заказа | `createOrder` | Customer Order | `order.order.create` | заказ `DRAFT` | `OrderCreated` |
| `ORDER_UPDATE` | Изменение заказа (коммерческие поля) | `updateOrder` | Customer Order | `order.order.edit` | обновлён заказ | `OrderUpdated` |
| `ORDER_APPROVE` | Утверждение заказа | `approveOrder` | Customer Order | `order.order.approve` | заказ `APPROVED` | `OrderApproved` |
| `ORDER_CANCEL` | Отмена заказа (только из `DRAFT`) | `cancelOrder` | Customer Order | `order.order.cancel` | заказ `CANCELLED` | `OrderCancelled` |
| `ORDER_ITEM_CREATE` | Создание позиции | `createOrderItem` | Order Item | `order.item.create` | позиция `DRAFT` + Revision 1 `DRAFT` | `OrderItemCreated`, `OrderItemRevisionCreated` |
| `ORDER_ITEM_UPDATE` | Изменение коммерческих полей позиции | `updateOrderItem` | Order Item | `order.item.edit` | обновлены коммерческие поля | `OrderItemUpdated` |
| `ORDER_ITEM_REVISION_UPDATE` | Изменение Draft Revision (spec/количество) | `updateOrderItemRevision` | Order Item | `order.revision.edit` | обновлена Draft Revision | `OrderItemRevisionUpdated` |
| `ORDER_ITEM_REVISION_APPROVE` | Утверждение редакции | `approveOrderItemRevision` | Order Item | `order.item.approve` | Revision `APPROVED` (Immutable), позиция `ACTIVE`, active Revision обновлён | `OrderItemRevisionApproved` |
| `ORDER_ITEM_CANCEL` | Отмена позиции (только из `DRAFT`) | `cancelOrderItem` | Order Item | `order.item.cancel` | позиция `CANCELLED` | `OrderItemCancelled` |
| `ORDER_ITEM_REVISION_CREATE` | Создание новой Draft Revision | `createOrderItemRevision` | Order Item | `order.revision.create` | новая Revision `DRAFT` | `OrderItemRevisionCreated` |

## 13.2 Policy по каждому типу документа

| Document type | payload type | payload schema version | post policy | unpost policy | close policy | delete policy | idempotency key |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `ORDER_CREATE` | `OrderCreatePayload` | v1 | single change → create order | NOT SUPPORTED | no business change | draft only, removes payload | `DocumentId + POST` |
| `ORDER_UPDATE` | `OrderUpdatePayload` | v1 | single change → update commercial fields | NOT SUPPORTED | no business change | draft only, removes payload | `DocumentId + POST` |
| `ORDER_APPROVE` | `OrderApprovePayload` | v1 | single change → approve order | NOT SUPPORTED | no business change | draft only, removes payload | `DocumentId + POST` |
| `ORDER_CANCEL` | `OrderCancelPayload` | v1 | single change → cancel draft order | NOT SUPPORTED | no business change | draft only, removes payload | `DocumentId + POST` |
| `ORDER_ITEM_CREATE` | `OrderItemCreatePayload` | v1 | create item + Revision 1 draft | NOT SUPPORTED | no business change | draft only, removes payload | `DocumentId + POST` |
| `ORDER_ITEM_UPDATE` | `OrderItemUpdatePayload` | v1 | update commercial item fields | NOT SUPPORTED | no business change | draft only, removes payload | `DocumentId + POST` |
| `ORDER_ITEM_REVISION_UPDATE` | `OrderItemRevisionUpdatePayload` | v1 | update draft revision spec/qty | NOT SUPPORTED | no business change | draft only, removes payload | `DocumentId + POST` |
| `ORDER_ITEM_REVISION_APPROVE` | `OrderItemRevisionApprovePayload` | v1 | approve draft revision → active | NOT SUPPORTED | no business change | draft only, removes payload | `DocumentId + POST` |
| `ORDER_ITEM_CANCEL` | `OrderItemCancelPayload` | v1 | cancel draft item | NOT SUPPORTED | no business change | draft only, removes payload | `DocumentId + POST` |
| `ORDER_ITEM_REVISION_CREATE` | `OrderItemRevisionCreatePayload` | v1 | create new draft revision | NOT SUPPORTED | no business change | draft only, removes payload | `DocumentId + POST` |

Документы без конкретного бизнес-смысла не добавляются.

---

# 14. Document lifecycle policy (общая)

## 14.1 `onPost`

* выполняет единственное бизнес-изменение;
* загружает typed payload по `DocumentId`;
* проверяет `PayloadSchemaVersion`;
* проверяет optimistic lock (`PayloadRevision`);
* валидирует предусловия и состояние документа;
* идемпотентен: повторный `onPost` не создаёт повторное изменение;
* записывает processing result (см. §16).

## 14.2 `onUnpost`

Для проведённых бизнес-документов Order Management: **UNPOST IS NOT SUPPORTED**. `onUnpost` отклоняет операцию (бросает исключение), поэтому распроведение не отменяет бизнес-изменение. Исправление — отдельными компенсационными документами (future scope).

## 14.3 `onDelete`

Разрешено только для непроведённого Draft-документа:

* бизнес-агрегаты не изменяются;
* capability-owned draft payload удаляется;
* processing record отсутствует;
* Domain Event не публикуется.

Удаление проведённого документа запрещено (Document Engine требует `DRAFT` для delete).

## 14.4 `onClose`

* не изменяет бизнес-агрегаты;
* не является компенсацией;
* не удаляет payload;
* сохраняет историю;
* только завершает platform lifecycle документа (если разрешено Document Engine).

---

# 15. Public API

## 15.1 Public Query API (для других Capability и UI, read-only)

```text
searchOrders(criteria, pageRequest)
getOrder(orderId)
getOrderItems(orderId, pageRequest)
getOrderItem(orderItemId)
getOrderItemRevisions(orderItemId, pageRequest)
getOrderItemRevision(orderItemId, revisionNumber)
getActiveOrderItemRevision(orderItemId)
getItemSpecification(orderItemId, revisionNumber)
```

### 15.1.1 Order search criteria

Минимальные фильтры (только существующие в модели поля):

* order number;
* order status;
* customer reference / customer name;
* created from;
* created to.

### 15.1.2 Пагинация

```text
default page size: 50
maximum page size: 100
page index: zero-based
default sort: createdAt DESC, orderId DESC
```

Разрешены только явно перечисленные sort fields (`createdAt`, `orderId`, `orderNumber`, `status`).

### 15.1.3 DTO

Query DTO:

* содержат только данные Order Management;
* не содержат `Production Status`, Stock Position, Cutting Plan;
* не раскрывают persistence entities;
* различают active и draft Revision;
* **не** возвращают draft Revision другим Capability. Draft Revision доступна только внутреннему UI use case Order Management. Для межмодульного Public Query API доступна только утверждённая `activeRevision`.

## 15.2 Внутренний Application API (только Document Processors Order Management)

```text
createOrder(...)            updateOrder(...)            approveOrder(...)         cancelOrder(...)
createOrderItem(...)        updateOrderItem(...)        cancelOrderItem(...)
createOrderItemRevision(...) updateOrderItemRevision(...) approveOrderItemRevision(...)
```

Не предоставляются другим Capability; не заменяют документы; вызываются только Document Processor'ами внутри проведения (ADR-003, ADR-004, ADR-028).

## 15.3 Public API не предоставляет

Операций запуска производства, готовности, выпуска, карт раскроя, складских документов, резервирования, изменения производственных статусов/остатков — принадлежат другим Capability.

---

# 16. Idempotency и processing record

Для каждого проведённого документа существует processing record:

```text
DocumentId
DocumentTypeCode
Operation           (POST)
ProcessingStatus
PayloadRevision
ProcessedAt
ResultReference
```

Уникальное ограничение: `DocumentId + Operation`.

Повторный `onPost`:

* не выполняет бизнес-операцию повторно;
* возвращает ранее сохранённый результат либо подтверждает уже выполненную обработку.

Idempotency применяется в той же согласованной транзакционной границе, что и изменение агрегата (§12).

---

# 17. Domain Events

Публикуются только после commit (ADR-021). `OrderItemRevisionApproved` потребляется Production (Production Spec §16). События Production/Warehouse/Cutting Order Management не публикует.

| Event type | Source operation | Moment | Minimal payload | Consumers | Idempotency id |
| --- | --- | --- | --- | --- | --- |
| `OrderCreated` | `createOrder` | после commit | eventId, orderId, actor, correlationId | внутренние | `orderId`+eventId |
| `OrderUpdated` | `updateOrder` | после commit | eventId, orderId, actor | внутренние | `orderId`+eventId |
| `OrderApproved` | `approveOrder` | после commit | eventId, orderId, actor | внутренние | `orderId`+eventId |
| `OrderCancelled` | `cancelOrder` | после commit | eventId, orderId, actor | внутренние | `orderId`+eventId |
| `OrderItemCreated` | `createOrderItem` | после commit | eventId, orderId, orderItemId, actor | внутренние | `orderItemId`+eventId |
| `OrderItemUpdated` | `updateOrderItem` | после commit | eventId, orderItemId, actor | внутренние | `orderItemId`+eventId |
| `OrderItemRevisionCreated` | `createOrderItem` (Rev1)/`createOrderItemRevision` | после commit | eventId, orderItemId, revision, actor | внутренние | `orderItemId`+revision+eventId |
| `OrderItemRevisionUpdated` | `updateOrderItemRevision` | после commit | eventId, orderItemId, revision, actor | внутренние | `orderItemId`+revision+eventId |
| `OrderItemRevisionApproved` | `approveOrderItemRevision` | после commit | eventId, orderId, orderItemId, revision, actor, correlationId | **Production**, Warehouse, Cutting | `orderItemId`+revision+eventId |
| `OrderItemCancelled` | `cancelOrderItem` | после commit | eventId, orderId, orderItemId, actor | внутренние | `orderItemId`+eventId |

Отдельные события `ItemSpecificationCreated/Approved` не публикуются: спецификация создаётся и замораживается вместе с Revision (представлено `OrderItemRevisionCreated`/`OrderItemRevisionApproved`).

---

# 18. Security

Security выполняет проверку разрешений; Order Management определяет требуемую capability. Коды — 3 сегмента `<area>.<resource>.<action>` (формат Security `PermissionId`).

| Capability code | Действие |
| --- | --- |
| `order.order.view` | Просмотр заказов |
| `order.order.create` | Создание заказа |
| `order.order.edit` | Изменение заказа |
| `order.order.approve` | Утверждение заказа |
| `order.order.cancel` | Отмена заказа (draft) |
| `order.item.view` | Просмотр позиций и редакций |
| `order.item.create` | Создание позиции |
| `order.item.edit` | Изменение коммерческих полей позиции |
| `order.item.approve` | Утверждение редакции позиции |
| `order.item.cancel` | Отмена позиции (draft) |
| `order.revision.create` | Создание новой Draft Revision |
| `order.revision.edit` | Изменение Draft Revision |
| `order.specification.view` | Просмотр спецификации |

---

# 19. Persistence scope

Схема `order_management`:

* `orders`;
* `order_items` (active/draft revision pointers, коммерческий статус, признак активности);
* `order_item_revisions` (`order_item_id`, `revision_number`, `revision_status`, `ordered_quantity`, ссылка на предыдущую);
* `item_specifications`, `item_specification_lines` (по `order_item_id` + `revision_number`);
* типизированный payload документов (per-type typed persistence), ключ `DocumentId`, поля `document_type_code`, `payload_schema_version`, `payload_revision`, `created_at`, `updated_at`, immutability-after-post;
* processing record (`document_id`, `document_type_code`, `operation`, `processing_status`, `payload_revision`, `processed_at`, `result_reference`), уникальность `document_id + operation`.

Optimistic locking, общие технические поля, schema-per-module — по Database Specification.

## 19.1 Явно запрещено хранить

`Production Status`, launched/active/released quantity, производственные партии/документы, Warehouse Stock Position, резервы, складские движения, Cutting Plan internals, generic domain JSON в Platform Core.

---

# 20. Интеграция с будущими Capability

Order Management предоставляет стабильные `Order Item ID`, `Revision`, Query DTO и Domain Events. Другие Capability не изменяют данные Order Management.

* **Production:** Query (`getOrderItemRevision`, `getActiveOrderItemRevision`, `getItemSpecification`); событие `OrderItemRevisionApproved`; связь по `Order Item ID + Revision`; не изменяет Order Management.
* **Warehouse:** Query по active Revision; не изменяет позицию/спецификацию.
* **Cutting Optimization:** Query по active Revision; Cutting Plan вне Order Management.
* **Procurement/Analytics:** read-only использование; проекции/решения принадлежат им.

Для межмодульного Public Query API доступна только active Revision.

---

# 21. Инварианты

1. Заказ/позиция/редакция/спецификация/typed payload принадлежат Order Management.
2. Главный объект — позиция в конкретной active Revision.
3. Спецификация active Revision — единственный источник состава; после утверждения Immutable.
4. Order Management не хранит производственного состояния.
5. Владелец хранения совпадает с владельцем изменения.
6. Изменения — только через бизнес-документы; изменяющие операции не являются внешним Public API.
7. Payload типизирован, версионирован, связан по `DocumentId`, Immutable после проведения.
8. Проведение идемпотентно (processing record `DocumentId + Operation`).
9. Атомарность проведения обеспечивается транзакционной границей Document Engine.
10. `Order Item ID` стабилен; конкретная редакция — `Order Item ID + Revision`.
11. Одновременно не более одной Draft Revision; active Revision не заменяется draft до утверждения.
12. Предыдущие Revision и спецификации не изменяются.
13. В Stage 5 нельзя отменить утверждённый заказ или активную позицию, изменять состав утверждённого заказа.
14. События публикуются только после commit; события Production/Warehouse Order Management не публикует.

---

# 22. Forbidden scope (Stage 5)

* хранение `Production Status`, производственных количеств, партий;
* хранение Stock Position, резервов, Cutting Plan internals;
* generic JSON payload в Platform Core;
* прямые mutating-вызовы между Capability;
* отмена утверждённого заказа (`APPROVED → CANCELLED`);
* отмена активной позиции (`ACTIVE → CANCELLED`);
* добавление/удаление позиций в/из `APPROVED` заказа;
* изменение утверждённой спецификации;
* статусы заказа `IN_PROGRESS`/`COMPLETED`;
* compile-time зависимости от Production/Warehouse/Cutting/Analytics.

---

# 23. Future integration processes

Перенесены за пределы Stage 5 (будущая интеграция Order↔Production и коммерческие процессы):

* отмена утверждённого заказа (компенсационные документы + проверка внешних Capability);
* отмена активной позиции;
* изменение состава утверждённого заказа (добавление/удаление позиций);
* коммерческие статусы `IN_PROGRESS`/`COMPLETED` (при наличии собственного процесса);
* компенсационные документы Order Management.

Изменение характеристик активной позиции выполняется новой Revision (в рамках Stage 5).

---

# 24. Architecture Rules

- **AR-001** Главный объект — позиция заказа (ADR-017).
- **AR-002** Заказ — контейнер коммерческой информации.
- **AR-003** Спецификация принадлежит Order Management, привязана к Revision.
- **AR-004** После утверждения Revision спецификация Immutable (ADR-018).
- **AR-005** Production State (статусы и количества) хранит/меняет только Production (ADR-019).
- **AR-006** Любое изменение агрегата — бизнес-документом (ADR-004, Constitution п.28).
- **AR-007** Изменяющие операции — не внешний Public API; межмодульно только Query API и Domain Events (ADR-003).
- **AR-008** Document Engine владеет lifecycle/metadata; Order Management — typed payload по `DocumentId` (ADR-028).
- **AR-009** Проведение атомарно и идемпотентно.
- **AR-010** История и проведённые документы неизменяемы (ADR-021).
- **AR-011** active Revision отделена от draft; ≤ 1 draft; внешне доступна только active.
- **AR-012** В Stage 5 не отменяются утверждённые объекты и не меняется состав утверждённого заказа.

---

# 25. Связанные документы

* TMP Constitution (v1.2)
* TMP Architecture Decisions (ADR-003, ADR-004, ADR-017, ADR-018, ADR-019, ADR-020, ADR-021, ADR-022, **ADR-028**)
* Document Engine Specification
* Platform Core Specification (Event API)
* Capability Engine Specification
* Security Specification
* Production Specification (v1.1)
* Warehouse / Cutting Optimization / Procurement Specifications

---

# 26. История документа

| Версия | Изменение |
| --- | --- |
| 1.0 | Базовая архитектура Order Management: позиция как главный объект, Immutable Specification, Public API, Domain Events, Capability, инварианты, редакции. |
| 1.1 | Разделено владение Order Management и Production; удалено хранение производственных статусов/количеств; разделены жизненные циклы; формализована модель Revision; изменяющие операции привязаны к Document Engine; Public API разделён на Query API и внутренний Application API; добавлены transition matrices; коды capability приведены к 3-сегментному формату; `IN_PROGRESS`/`COMPLETED` перенесены; определён persistence scope с запретами. |
| 1.2 | Введён capability-owned typed business document payload (ADR-028), связанный по `DocumentId`, versioned, с optimistic locking (`PayloadRevision`) и immutability после проведения; уточнён document lifecycle (`post`/`unpost`=NOT SUPPORTED/`close`/`delete`) и idempotency (processing record `DocumentId + Operation`); подтверждена транзакционная граница Document Engine (processor внутри транзакции проведения, события после commit); модель Revision разделена на active/draft, добавлен документ `ORDER_ITEM_REVISION_UPDATE`, `ORDER_ITEM_UPDATE` ограничен коммерческими полями; безопасный коммерческий lifecycle Stage 5 (запрет `APPROVED→CANCELLED`, `ACTIVE→CANCELLED`, изменения состава утверждённого заказа); расширен Public Query API (`searchOrders`, списки items/revisions, пагинация 50/100 zero-based, стабильная сортировка); уточнены Constitution/ADR-003/ADR-004; определены future integration processes. |
