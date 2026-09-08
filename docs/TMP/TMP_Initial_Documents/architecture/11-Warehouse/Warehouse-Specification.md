# Warehouse Specification

**Document ID:** TMP-SPEC-011  
**Status:** Accepted  
**Version:** 1.8

---

# 1. Назначение

Warehouse — функциональная область TOP Manufacturing Platform (TMP), отвечающая только за складское состояние материалов.

Warehouse является единственным владельцем:

- `Warehouse`;
- `Storage Cell`;
- `Stock Position`;
- `Warehouse Operation`;
- `Warehouse Movement`.

Warehouse не рассчитывает производственную потребность и не определяет состав изделия.

Warehouse **не** заменяется operational UX/document layer Stage 3.5: новый workflow работает **над** существующим inventory foundation (ADR-037).

---

# 2. Границы ответственности

## 2.1 Warehouse отвечает за

- регистрацию и контроль текущих складских остатков;
- внутренние и межскладские перемещения;
- операции поступления, списания, корректировки, инвентаризации;
- информационное резервирование;
- проверку доступности материалов;
- ведение истории изменений (`Warehouse Movement`);
- User↔Warehouse responsibility (many-to-many) и operational transfer document/workflow (ADR-037);
- automatic source warehouse routing по AVAILABLE для production requirements;
- Warehouse task / operational inbox projection (не generic Messenger).

## 2.2 Warehouse не отвечает за

- заказы клиентов;
- позиции заказов;
- редакции;
- спецификации изделий;
- расчет производственной потребности;
- определение бизнес-причины складской операции;
- закупки, поставщиков, цены;
- material→warehouse mapping в справочнике материалов;
- матрицу разрешённых направлений warehouse→warehouse.

---

# 3. Взаимодействие с Order Management

Order Management отвечает за:

- `Order`;
- `Order Item`;
- `Revision`;
- `Specification`.

Specification является источником информации о необходимых материалах.

Пример Specification Line (минимальный состав для Warehouse):

- `materialCode`;
- `materialName`;
- `color`;
- `unitOfMeasure`;
- `length`;
- `quantity`.

Warehouse использует эти данные для выполнения складских операций и не изменяет данные спецификации.

---

# 4. Материалы и Material Mapping

Материалы определяются через данные спецификации Order Management.

Для нормализации входящих данных используется `Material Mapping`.

`Material Mapping` используется для сопоставления различных представлений одного материала.

Пример:

```text
Вход из расчётной программы: "Профиль VEKA 103.211 Белый"
TMP:                         "VEKA 103.211 WHITE"
```

Правила:

- настройка сопоставления выполняется пользователем;
- Warehouse не отвечает за сопоставление;
- сопоставление не является складской операцией;
- отсутствие сопоставления не блокирует импорт.

**Запрещено** вводить постоянную конфигурацию:

- Material → Main Warehouse;
- Material → Supply Warehouse;
- Material Group → Warehouse;
- любой иной material→warehouse mapping в справочнике.

Один материал физически может одновременно находиться на нескольких складах, в нескольких ячейках, в `IN_TRANSIT` — это нормально (ADR-037).

---

# 5. Складская модель хранения

Базовая модель:

```text
Warehouse
   ↓
Storage Cell
   ↓
Stock Position
```

`Stock Position` — текущее состояние остатка.

`Warehouse Movement` — неизменяемая история изменений.

---

# 6. Stock Position

## 6.1 Определение

`Stock Position` определяется комбинацией:

`Warehouse + Storage Cell + Material + Stock State + Quantity`

`Stock Position` не хранит историю изменений.

## 6.2 Правила

- создаётся и изменяется только через `Warehouse Operation`;
- прямое изменение `Stock Position` запрещено;
- отрицательное количество запрещено;
- каждое изменение состояния или количества фиксируется `Warehouse Movement`.

---

# 7. Stock State

В версии 1.0 используется упрощённый набор состояний:

- `AVAILABLE`;
- `IN_TRANSIT`;
- `BLOCKED`.

Состояние `SCRAPPED` допускается только для внутренних операций списания/утилизации, если это требуется.

Состояние `RESERVED` не используется.

---

# 8. Резервирование

Резервирование в версии 1.0 — информационная связь, а не изменение складского состояния.

Reservation:

- не изменяет `Stock Position`;
- не изменяет количество;
- не создаёт `Warehouse Movement`;
- не создаёт `Stock State = RESERVED`.

Хранится как связь:

`Material + Order / Production Demand + Quantity`

Отдельный процесс снятия резерва в версии 1.0 не реализуется.

---

# 9. Warehouse Movement

`Warehouse Movement`:

- неизменяем;
- не удаляется;
- создаётся при изменении складского состояния и/или количества.

Используется для:

- истории;
- аудита;
- восстановления последовательности операций.

---

# 10. Warehouse Operation

Все изменения склада выполняются только через `Warehouse Operation`.

`Warehouse Operation`:

- проверяет возможность операции;
- выполняет изменение;
- создаёт `Warehouse Movement`;
- обновляет `Stock Position`.

Прямое изменение `Stock Position` запрещено.

---

# 11. Операции версии 1.0

В версии 1.0 поддерживаются:

- `Receipt`;
- `Move`;
- `Transfer`;
- `Reservation` (информационная);
- `Consumption`;
- `Adjustment`;
- `Inventory`.

Сложные процессы версии >1.0 в этот документ не входят.

---

# 12. Поступление (Receipt)

`Receipt` увеличивает остаток.

Warehouse отвечает за:

- регистрацию факта поступления;
- создание движения;
- изменение `Stock Position`.

Warehouse не отвечает за:

- поставщика;
- закупку;
- цену.

---

# 13. Перемещения

## 13.1 Внутреннее перемещение

```text
Storage Cell A
      ↓
Storage Cell B
```

Количество не изменяется.

## 13.2 Межскладское перемещение

```text
AVAILABLE
    ↓
IN_TRANSIT
    ↓
AVAILABLE
```

Два этапа:

1. Отправка;
2. Получение.

Количество Transfer должно быть строго больше нуля. `StockQuantity` по-прежнему допускает ноль как значение остатка; ноль запрещён именно для операции Transfer.

Один успешно завершённый `TRANSFER_SEND` имеет 0..1 успешный `TRANSFER_RECEIVE` (exactly-once receive). Повторное получение того же send не создаёт Movement и не изменяет Stock Position.

Логический статус Transfer для Query API:

- `DRAFT` — заявка создана, stock не изменён;
- `SENT` — send выполнен (`AVAILABLE` → `IN_TRANSIT`);
- `RECEIVED` — receive выполнен (`IN_TRANSIT` → destination `AVAILABLE`).

Дополнительные operational statuses (reject / continuation NEW и т.п.) уточняются на implementation step; минимум ADR-037 обязателен.

### 13.2.1 Execution layer vs operational document layer

Warehouse **сохраняет** line-operation execution:

- одна `Warehouse Operation` — одна строка материала;
- `Warehouse Movement` — immutable fact;
- Stock Position изменяется только через Operation.

Над этим ядром появляется **Warehouse-owned** operational Transfer document/workflow (ADR-037):

```text
Transfer Document
  → lines
  → Warehouse Operations
  → immutable Movements
  → Stock Positions
```

Пользовательское перемещение может содержать несколько материалов. Группировка multi-line — ответственность Warehouse operational layer, **не** второй inventory engine и **не** Production-owned stock model.

### 13.2.2 Source / destination cells

После automatic выбора source warehouse TMP предлагает кладовщику source StorageCell allocations (подсказка); кладовщик может изменить allocations до фактической передачи.

Отправитель выбирает destination **warehouse**; destination **cells** выбирает получатель при приёмке.

### 13.2.3 Partial receive, shortfall continuation, reject

- accepted quantity не может превышать sent quantity;
- partial receive поддерживается архитектурно (точная state machine — DEFERRED);
- shortfall → новое связанное continuation transfer (тот же source/destination, NEW/draft, parent/origin); stock уменьшается только при реальной последующей передаче; исходный проведённый факт не переписывается;
- модель `SPLIT` / replacement document (бывший ADR-014) **не** используется;
- reject требует обязательной причины; основное действие отправителя — «Вернуть материалы на склад» (не reverse-transfer UX); default return cells = исходные source cells с возможностью override.

### 13.2.4 Directions

Матрица разрешённых направлений warehouse→warehouse **не** вводится. При RBAC + responsibility за source допустим transfer на любой существующий destination warehouse.

---

# 14. Списание (Consumption)

`Consumption` уменьшает остаток.

Production определяет:

- что списывать;
- сколько списывать (**фактическое** количество из Production Release plan/fact).

Warehouse выполняет операцию и фиксирует результат в `Warehouse Movement`.

Списание материалов для выпуска изделий допускается только со склада производства.

Отрицательные остатки запрещены. При недостатке материала операция Consumption не проводится.

---

# 15. Интеграция с Production

## 15.1 TARGET Stage 3.5 contract (ADR-037)

Production:

- формирует Material Requirement (Order / selected items / Specifications → aggregated quantities);
- до Submit мастер может редактировать итоговое требование (без dual calculated/requested model и без recommendation formula в target);
- после Submit передаёт **финальное** требование в Warehouse;
- не выбирает source warehouse;
- не владеет Stock Position / Warehouse Movement / Warehouse Operation.

Warehouse:

- остаётся владельцем Transfer / Consumption documents, Stock Position и operational transfer workflow;
- выполняет **automatic source warehouse routing** по AVAILABLE (исключая destination);
- при необходимости создаёт несколько internal Warehouse tasks / transfer documents из одного требования;
- предлагает source cell allocations; receive с destination cells выполняет получатель;
- выполняет send (`AVAILABLE` → `IN_TRANSIT`) и receive (`IN_TRANSIT` → destination `AVAILABLE`);
- проверяет наличие и выполняет Consumption;
- не рассчитывает производственную потребность;
- не изменяет производственное состояние Production.

## 15.2 CURRENT IMPLEMENTATION (Stage 7 — until Stage 3.5 refactor)

Текущий runtime (не удалять и не менять в 3.5.0):

- `ProductionWarehouseScope(mainWarehouseId, productionWarehouseId)`;
- `MaterialTransferRecommendationCalculator`: recommended = min(max(required − productionAvailable, 0), mainAvailable);
- Production-owned Material Transfer Template с recommended/requested quantities;
- confirm → N Warehouse line Transfer drafts; grouping refs в Production;
- receive confirmation может инициироваться из Production UI.

Это **CURRENT IMPLEMENTATION**, подлежащая refactor на последующих Stage 3.5 implementation steps. Документация target contract (§15.1) **не** утверждает, что код уже соответствует ADR-037.

Production не создаёт вторую складскую модель и не пишет в таблицы Warehouse напрямую.

Атомарность связки Production Release + Warehouse Consumption — ADR-035 (бизнес-граница) и ADR-036 (механизм: общая локальная ACID-транзакция).

---

# 15A. User ↔ Warehouse responsibility и operational inbox

User ↔ Warehouse — many-to-many responsibility (ADR-037).

- effective authorization = RBAC permission + warehouse responsibility;
- отдельного material scope нет;
- все ответственные видят задачи склада;
- «Взять в работу» — informational ownership, не exclusive lock;
- operational messaging = tasks + statuses + responsibility + notifications (не generic Messenger);
- target primary UX: Мои склады → Задачи / Остатки / История (default = Задачи).

Администрирование responsibility relation — future Security/Warehouse implementation step.

---

# 15B. Automatic source warehouse routing

Для строки требования Material M, quantity Q, destination warehouse D алгоритм deterministic (ADR-037 §D):

1. исключить D;
2. склады с AVAILABLE(M) ≥ Q → выбрать max AVAILABLE(M);
3. иначе → max положительный AVAILABLE(M);
4. tie-breaker — стабильный warehouse identity/code.

Пользователь не выбирает source warehouse. Persistent material→warehouse mapping запрещён.

---

# 16. Интеграция с Cutting Optimization

Cutting Optimization:

- рассчитывает рекомендуемую плановую потребность длинномерного материала.

Warehouse:

- проверяет наличие;
- выдаёт материал по своим операциям.

Warehouse не использует:

- `Source Bar`;
- `Cut Piece`;
- внутренние allocation-структуры раскроя;
- алгоритмы раскроя.

---

# 17. Межмодульные и application-контракты Warehouse

Разделение соответствует Constitution принцип 28, ADR-003 и ADR-004.

## 17.1 Warehouse Public Query API (read-only)

Только чтение. Не изменяет Stock Position и не проводит документы.

```text
getStock(material, warehouse, cell)
checkAvailability(request)
getMaterialAvailability(request)
getTransferStatus(documentId / transferRef)
getReservationLink(request)   // информационное чтение, если требуется
```

## 17.2 Warehouse Application / Document Commands (mutating, Warehouse-owned)

Изменяющие операции **не** являются Public Query API и **не** являются mutating Public API чужого Capability.

Они выполняются как:

- Warehouse Application Use Cases;
- Warehouse Document Commands / Document Engine lifecycle;
- Warehouse-owned business documents / operations.

Примеры:

```text
createTransferDraft(request)     // в т.ч. по инициации из Production UI template
postTransferSend(documentId)
postTransferReceive(documentId)  // может инициироваться из Production UI «Подтвердить получение»
createConsumptionDraft(request)  // фактические количества из Production Release orchestration
postConsumption(documentId)
createReservationLink(request)   // informational write, всё ещё Warehouse-owned
executeWarehouseOperation(...)   // обобщённый путь Stage 6, если используется
```

Production UI может **инициировать** пользовательский workflow, но:

- владельцем Transfer / Consumption остаётся Warehouse;
- Stock Position изменяет только Warehouse;
- Production не описывает эти команды как свой mutating Public API.

# 18. Security

Warehouse использует существующую Security Capability.

Capability permissions (идентификаторы `PermissionId`):

**Операции и просмотр остатков**

- `warehouse.stock.view` — просмотр складов, ячеек, остатков
- `warehouse.receipt.create` — Receipt
- `warehouse.move.create` — Move
- `warehouse.transfer.create` — Transfer
- `warehouse.reservation.create` — информационное Reservation Link
- `warehouse.consumption.create` — Consumption
- `warehouse.adjustment.create` — Adjustment
- `warehouse.inventory.create` — Inventory

**Управление складской структурой**

- `warehouse.warehouse.view`
- `warehouse.warehouse.create`
- `warehouse.warehouse.update`
- `warehouse.warehouse.delete`
- `warehouse.storage-cell.view`
- `warehouse.storage-cell.create`
- `warehouse.storage-cell.update`
- `warehouse.storage-cell.delete`

Примечание: код ячейки хранения — `storage-cell` (дефис), не `storage_cell`; формат `PermissionId` не допускает underscore.

---

# 19. Ограничения версии 1.0

В версию 1.0 не входят:

- `Batch`;
- партии;
- `Supplier Batch`;
- `FIFO`;
- `FEFO`;
- стратегии выбора партии;
- WMS;
- штрихкодирование;
- склад готовой продукции;
- закупки.

---

# 20. Invariants

1. Warehouse отвечает только за складское состояние материалов.
2. Единственный механизм изменения склада — `Warehouse Operation`.
3. Прямое изменение `StockPosition` запрещено.
4. `WarehouseMovement` неизменяем и не удаляется.
5. Warehouse не владеет заказами, позициями, редакциями и спецификациями.
6. Warehouse не рассчитывает производственную потребность.
7. Reservation — информационная связь без изменения stock состояния.
8. Stage 6 implementation code в рамках этого документа не стартует.
9. Transfer quantity > 0.
10. Completed `TRANSFER_SEND` may be received at most once per send fact (exactly-once на конкретный send); partial/continuation оформляются новыми связанными transfers (ADR-037).
11. Operational Transfer layer не создаёт второй inventory engine.
12. Material→warehouse mapping в справочнике запрещён.
13. Source warehouse для production requirement выбирается автоматически по AVAILABLE.
14. Accepted quantity на receive не превышает sent quantity.
15. User↔Warehouse responsibility — many-to-many; RBAC + responsibility.

---

# 21. Rule Set

## Rule 1

Order Management владеет `Order`, `Order Item`, `Revision`, `Specification`.

## Rule 2

Specification — единственный источник состава требуемых материалов для Warehouse входа.

## Rule 3

Warehouse выполняет только складскую часть операции и не изменяет спецификацию.

## Rule 4

Все взаимодействия между Capability выполняются через бизнес-документы владельца данных, Public Query API и Domain Events (Constitution принцип 28).

---

# 22. История документа

| Версия | Изменение |
|--------|-----------|
| 1.0 | Базовая спецификация Warehouse. |
| 1.1 | Интеграция с Production и Cutting Optimization. |
| 1.2 | Stage 6 Start Gate: Warehouse только складское состояние; без Material Master. |
| 1.3 | Упрощение модели Stage 6 Start Gate: исключены Batch/FIFO/FEFO/Supplier Batch; Reservation как информационная связь; упрощённые Stock State; минимальный API; подтверждена граница Warehouse-only state. |
| 1.3.1 | Stage 6 Final Closure: §18 — canonical `PermissionId` codes (16 permissions incl. structure management). |
| 1.4 | Stage 7 docs alignment (ADR-035): Production инициирует Transfer template/receive confirmation и actual Consumption; Warehouse остаётся владельцем документов и Stock Position; без возврата Batch/FIFO/FEFO. |
| 1.5 | Corrective pass: §17 разделён на Public Query API и Warehouse Application/Document Commands (Constitution принцип 28). |
| 1.6 | §15: атомарность Release + Consumption ссылается на ADR-036 (механизм) при сохранении ADR-035 (бизнес-граница). |
| 1.7 | §13.2 / §20: Transfer quantity > 0; exactly-once receive на completed TRANSFER_SEND; логический статус DRAFT/SENT/RECEIVED; Warehouse остаётся line-operation, группировка — Production. |
| 1.8 | Stage 3.5.0 / ADR-037: User↔Warehouse responsibility; no material→warehouse mapping; automatic source routing; Warehouse-owned multi-line Transfer document over Operation layer; source cell suggestion; destination cell on receive; shortfall continuation; reject/return; partial receive; CURRENT vs TARGET Production integration; supersede ADR-013/014; qualify ADR-035. |
