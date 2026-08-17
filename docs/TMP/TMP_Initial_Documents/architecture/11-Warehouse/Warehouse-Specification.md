# Warehouse Specification

**Document ID:** TMP-SPEC-011  
**Status:** Accepted  
**Version:** 1.5

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

---

# 2. Границы ответственности

## 2.1 Warehouse отвечает за

- регистрацию и контроль текущих складских остатков;
- внутренние и межскладские перемещения;
- операции поступления, списания, корректировки, инвентаризации;
- информационное резервирование;
- проверку доступности материалов;
- ведение истории изменений (`Warehouse Movement`).

## 2.2 Warehouse не отвечает за

- заказы клиентов;
- позиции заказов;
- редакции;
- спецификации изделий;
- расчет производственной потребности;
- определение бизнес-причины складской операции;
- закупки, поставщиков, цены.

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

Production:

- получает ACTIVE Order / Order Items / current immutable Specification из Order Management;
- рассчитывает плановую потребность (Specification и опционально Cutting Plan как рекомендация);
- формирует редактируемый шаблон перемещения;
- инициирует создание Warehouse-owned Transfer через Warehouse Application/Document commands;
- может инициировать подтверждение получения (receive) из UI Production;
- при выпуске инициирует Warehouse-owned Consumption фактического количества (не через Public Query API).

Warehouse:

- остаётся владельцем Transfer / Consumption documents и Stock Position;
- выполняет send (основной склад → IN_TRANSIT);
- выполняет receive (IN_TRANSIT → склад производства);
- проверяет наличие и выполняет Consumption;
- не рассчитывает производственную потребность;
- не изменяет производственное состояние Production.

Production не создаёт вторую складскую модель и не пишет в таблицы Warehouse напрямую.

Атомарность связки Production Release + Warehouse Consumption — требование Production Spec §21 / ADR-035 (Start Gate Stage 7).

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
