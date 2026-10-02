# Stage 7 Production UX Design — Target Production Workbench v1

**Тип:** UX design document (НЕ implementation task)  
**Дата:** 2026-10-01  
**Вход:** `STAGE-7-PRODUCTION-AUDIT.md`, Production Specification v2.6, ADR-033 / ADR-036 / ADR-037, публичные API Order Management / Warehouse / Production / Document Engine  
**Ограничение:** не изменялись production code, FXML, CSS, Java, database, Flyway, API, ADR, Production Specification. Единственный артефакт — этот файл.

---

## 0. RESULT

**FINDINGS** — целевой UX спроектирован полностью. Часть целевых данных (материалы «Получено/Ожидается» по строкам, чтение потребности после повторного открытия заказа, сводка материалов в списке заказов) **не может быть получена из текущих публичных API** и зафиксирована как IMPLEMENTATION DEPENDENCY (§16). Новые API не придумывались.

---

## 0.1 IMPLEMENTATION PHASE 3 — Production Workbench Tree (2026-10-02)

**Статус:** IMPLEMENTED (LEVEL 1 tree + selection model). Phase 4 Order Card implemented (§0.2). Phase 5 Material Request UX implemented (§0.3).

| Элемент | Реализация |
|---|---|
| Primary LEVEL 1 | `TreeTableView` Order → Order Items (`ProductionWorkbenchScreen.fxml`) |
| Control | `TreeTableView` + checkbox column (business selection ≠ row focus) |
| Order node | № заказа, заказчик, Production state, qty / изготовлено / осталось из `OrderProductionListFacts` (без Quantity Mode / UUID / Spec / Cutting) |
| Item node | `externalPositionNumber` → «Поз. …»; fallback «Позиция N»; изделие; qty; state; изготовлено; осталось |
| Selection SoT | `ProductionTreeSelectionModel` / `selectedOrderItemRefs` (`sourceOrderId` + `sourceOrderItemId`) |
| Parent checkbox | UNCHECKED / CHECKED / INDETERMINATE; select/deselect all children |
| Cross-order | Да; deterministic order by OrderId then ItemId |
| Filter/search | State filter (`ProductionTreeStatusFilter`); search by order number, customer, position, product name/code; hidden selection retained |
| Refresh | Retains selection + expanded Order ids when still authoritative |
| Pagination | `ProductionOrderItemsLoader` loads all pages (`MAX_PAGE_SIZE`); no first-page truncation |
| Future actions on LEVEL 1 | Material Request: **[Запросить материалы]** + **[Черновики материалов]** (Phase 5); Release not shown |
| Permissions | Open: `production.order.view`; selection needs no mutation rights; Material Request create: `production.transfer.create` |
| Transitional DETAIL | Replaced in Phase 4 by Order Card (§0.2) |
| Removed from LEVEL 1 | UUID input, Spec/Cutting columns, logical transfer, «Подтвердить получение» |
| Phase 2 shim | **DELETED in Phase 4** — no remaining `src/main` callers of single-order prepare |
| Adapter | `selectedItemsForMaterialRequirement()` → `MaterialRequirementSourceItemRefView` list; Phase 5 prepare/submit wired |

**Следующая фаза:** Phase 6 — Materials Read Model / readiness.

---

## 0.3 IMPLEMENTATION PHASE 5 — Cross-Order Material Requirement UX (2026-10-02)

**Статус:** IMPLEMENTED (LEVEL 1 tree → Material Request wizard). Phase 6+ не начаты.

| Элемент | Реализация |
|---|---|
| Entry point | LEVEL 1 Tree: **[Запросить материалы]** (`production.transfer.create` only; hidden without permission) |
| Selection | `ProductionTreeSelectionModel` / `selectedItemsForMaterialRequirement()` — без второй selection model |
| Invalid items | Не silent skip: validation message со списком позиций (requestable=0 / CANCELLED / RELEASED) |
| Batch preload | `getMaterialRequirementProductCoverage` + **`getOrderQuantityModes(List)`** (без N+1) |
| STEP 1 | Product quantities: STANDARD read-only = requestable; FLEXIBLE editable 1..requestable (default = requestable) |
| Mixed modes | Один dialog / один `prepareMaterialRequirement` / один DRAFT |
| Mode/coverage race | Prepare reject → human message + reload STEP 1 |
| STEP 2 DRAFT | Source summary + material lines; edit quantity via `changeMaterialRequirementQuantity` + expectedVersion |
| Product coverage | Material edit не меняет `requestedProductQuantity` source items |
| Submit | `submitMaterialRequirement` → Warehouse demand; success без UUID документов |
| Shortage / coverage conflict | DRAFT остаётся; human messages |
| DRAFT persistence | Close не удаляет; reopen через **[Черновики материалов]** + `listMaterialRequirementDrafts` / `getMaterialRequirement` |
| Multiple DRAFTs | Список по дате/времени + N позиций / M заказов (без UUID) |
| After Submit | Toast; clear selection submitted items; remain on LEVEL 1 tree |
| Out of scope | Release UX, readiness, receipt, reservation, Cutting, History redesign |

**Следующая фаза:** Phase 6 — Production Materials Read Model / Warehouse fulfillment visibility / readiness gate foundation.

---

## 0.2 IMPLEMENTATION PHASE 4 — Order Production Card + Quantity Mode UX (2026-10-02)

**Статус:** IMPLEMENTED (LEVEL 2 Order Card). Phase 5 Material Request UX implemented (§0.3).

| Элемент | Реализация |
|---|---|
| Navigation | Double-click Order in LEVEL 1 tree → LEVEL 2 Order Card; back «← К производству» |
| Header | Заказ №…, заказчик, объект (`siteRef` или «—»), состояние, прогресс «N из M» |
| Positions | Read-only table: Позиция / Изделие / Кол-во / Состояние / Изготовлено / Осталось; all pages via `ProductionOrderItemsLoader` |
| Quantity Mode location | **Only on Order Card** (radio Стандартный / Гибкий + hint). LEVEL 1 tree never shows mode |
| Permission | View mode: `production.order.view`; change mode: `production.order.accept` only (`release.create` / `transfer.create` do not grant edit) |
| Default | STANDARD (version 0) when no DB row |
| State-independent | Mode editor available in every production state when accept permission present |
| Save | Explicit [Сохранить] when dirty; no-op when unchanged (no backend call) |
| Optimistic locking | `changeOrderQuantityMode(orderId, mode, expectedVersion)`; conflict → human message + authoritative reload |
| Accept | Whole-order «Принять в производство» + confirmation; success refreshes card; concurrent launch → human message |
| Navigation state | Back retains search, status filter, selected items, expanded orders |
| Cleanup | Removed transitional MR / Release / materials / receipt / history panels and dead UI helpers |
| Legacy MR shim | **DELETED** — `prepareMaterialRequirement(orderId, itemIds)` API/service overload removed (no `src/main` callers) |
| Backend preserved | Quantity Mode, cross-order MR list prepare, Release / Partial Release unchanged |

**Следующая фаза:** Phase 6 — Production Materials Read Model / readiness (см. §0.3).

---

## 1. BASELINE

| Параметр | Значение |
|---|---|
| HEAD | `31ed5da0aaba4d81998e836b9d44851222d37471` (совпадает с ожидаемым) |
| Working tree | `?? docs/development-control/STAGE-7-PRODUCTION-AUDIT.md` (untracked audit, сохранён) + этот файл |
| Ветка | не переключалась; reset/clean/checkout/restore/commit/push не выполнялись |

---

## 2. AGREED DECISIONS (приняты, не пересматриваются)

| # | Решение | Следствие для UX |
|---|---|---|
| D1 | Физическая приёмка материалов — только Warehouse (Склад → Задачи → Приёмка) | «Подтвердить получение» и выбор «Логическое перемещение» удаляются из Production UX. Production только показывает результат |
| D2 | Release разрешён только при наличии необходимых материалов на производственном складе | Кнопка «Выпустить» disabled с понятной причиной; MR Submit ≠ «материалы получены» |
| D3 | Whole-order Launch | Одна кнопка «Принять в производство» на уровне заказа; никаких item-level checkbox/quantity для запуска |
| D4 | Human-readable identity | Никаких UUID / Specification ID / documentId в рабочих колонках и сообщениях |
| D5 | Cutting скрыт до STAGE7-008A | Нет колонки/контролов Cutting в основном UX |
| D6 | Material summary + detail | Главный экран — сводка; таблица — только в окне «Подробнее» |
| D7 | Режим количества заказа STANDARD / FLEXIBLE (см. §2.1) | Показывается в карточке заказа; в главном дереве/списке не показывается |

### 2.1 Production Quantity Mode (финализировано, Stage 7 Implementation Phase 1)

| Правило | Решение |
|---|---|
| Владелец | Production-настройка конкретного `OrderId` (`production.order_quantity_modes`, V48). Не Production Order, не Revision, не документ, не состояние заказа |
| Значения | `STANDARD` — действие берёт всё допустимое оставшееся количество позиции; `FLEXIBLE` — пользователь вводит количество ≤ допустимого остатка |
| По умолчанию | `STANDARD`, в том числе для заказа без сохранённой записи (version 0) |
| Независимость от состояния | Меняется в любом состоянии: не принят, в производстве, частично выпущен, выпущен, отменён. State gate отсутствует |
| Право на изменение | `production.order.accept` (backend проверяет). `production.order.view` — только чтение. `production.release.create` / `production.transfer.create` право изменения не дают |
| Область действия | Одна настройка управляет и запросом материалов, и выпуском изделий. Диалоги операций режим не спрашивают — только читают текущий |
| История операций | Смена режима не пересчитывает прошлые выпуски, частичные выпуски, DRAFT/SUBMITTED потребности. Новый режим действует только для следующих действий |
| Где виден | Карточка заказа (LEVEL 2). Не показывается в главном дереве/списке (LEVEL 1) |
| Конкурентность | Optimistic `version`; устаревшая версия → `OrderQuantityModeOptimisticLockException` без перезаписи |
| API | `ProductionApplicationApi.getOrderQuantityMode(orderId)` / `changeOrderQuantityMode(orderId, mode, expectedVersion)` → `OrderQuantityModeView(orderId, quantityMode, version)` |

**Зависимость Phase 2 (финализировано):** Material Requirement — кросс-заказная модель покрытия «позиция заказа → количество изделий».

| Правило | Решение |
|---|---|
| Выбор в дереве | Можно выбирать Order Items из разных Orders |
| Один документ потребности | Одна Material Requirement покрывает выбранные позиции нескольких заказов |
| Quantity Mode | Влияет на product quantity при prepare: STANDARD — всё requestable; FLEXIBLE — явное количество ≤ requestable |
| `lineQuantity` / норма | Количество материала в спецификации — **на одно изделие** (per product) |
| Материал | `material quantity = norm × product quantity` (агрегация по позициям с provenance/contributions) |
| Submitted coverage | Submitted product coverage блокирует повторный спрос на те же изделия |
| DRAFT | DRAFT **не** резервирует coverage |
| Submit | Revalidate coverage под row-lock заказов (OrderId ascending); конфликт → `MaterialRequirementCoverageConflictException` |
| Ручное редактирование | Количество материала в строке остаётся редактируемым; product quantity в source items не меняется |
| Смена режима | Mode change **не** переинтерпретирует уже созданный DRAFT |

Не путать product coverage с quantity строки материала; поля `calculatedQuantity` / `requestedQuantity` / `recommendedQuantity` в строке не вводятся.

---

## 3. UX PRINCIPLE

Пользователь видит три ответа, а не набор технических операций:

1. **Что происходит?** — состояние производства заказа одной фразой.
2. **Что нужно сделать?** — одна основная кнопка следующего шага (или «действий не требуется»).
3. **Можно ли продолжать?** — готовность материалов: да / нет + причина.

Production **не дублирует Warehouse**: остатки, ячейки, перемещения, приёмка, задачи — только в Warehouse. Production отвечает за производственное состояние, потребность, готовность материалов, запуск и выпуск.

---

## 4. TARGET INFORMATION ARCHITECTURE

```text
Производство (навигация, permission production.order.view)
│
├── LEVEL 1  «Производство» — список производственных заказов
│             фильтр состояния, поиск по номеру/заказчику, признак «Нужно действие»
│
└── LEVEL 2  «Заказ №…» — производственная карточка заказа
              ├── Шапка: заказ, заказчик, объект, общее состояние, [Действия ▾]
              ├── Блок «Следующий шаг»: одна основная кнопка / информационное состояние
              ├── ПОЗИЦИИ (таблица изделий)
              ├── МАТЕРИАЛЫ (сводка) ──► окно «Материалы — подробнее»
              ├── ИСТОРИЯ (последняя операция) ──► окно «История производства»
              │
              ├── Диалог «Принять в производство» (подтверждение)
              ├── Окно «Потребность в материалах» (DRAFT: редактирование → Отправить на склад)
              ├── Мастер «Выпуск изделий» (шаг 1: количество; шаг 2: фактический расход)
              └── Диалог «Отменить производство» (из меню Действия)
```

Переход LEVEL 1 → LEVEL 2: двойной клик / Enter / кнопка «Открыть». Возврат — «← К списку». Поле ручного ввода UUID заказа удаляется; открытие по номеру заказа остаётся как поиск в списке.

---

## 5. MAIN PRODUCTION SCREEN (LEVEL 1)

### 5.1 Колонки

| Колонка | Источник (существующий API) | Статус |
|---|---|---|
| Заказ (`№4183`) | `OrderWorklistQuery.listWorklistRows` → `OrderWorklistRowDto.orderNumber` | AVAILABLE |
| Заказчик | `OrderWorklistRowDto.customerName` (`—` если null) | AVAILABLE |
| Состояние | `ProductionQueryApi.getOrderProductionListFacts(orderIds)` → `status` (+ `cancellationPosted`) | AVAILABLE |
| Изготовлено (`7 / 17`) | `OrderProductionListFacts.releasedQuantity / orderedQuantity` | AVAILABLE (для NOT_ACCEPTED — `—`) |
| Позиций | `OrderProductionView.itemCount` через `getOrderProductionView(orderId)` — только по одному заказу | **DEP-7** (нет batch-поля) |
| Материалы | нет batch-источника | **DEP-6** — в v1 колонка скрывается, если зависимость не закрыта |
| Нужно действие (маркер ●) | вычисляется presentation-слоем из Состояния (+ Материалы, когда DEP-6 закрыт) | COMPUTED |

### 5.2 Состояние заказа (presentation labels)

| `OrderProductionViewStatus` | Подпись | Нужно действие |
|---|---|---|
| `NOT_ACCEPTED` при `OrderStatus.ACTIVE` | Не принят | ● Принять в производство |
| `IN_PRODUCTION` | В производстве | ● если материалы не запрошены / готовы к выпуску (после DEP-6), иначе — |
| `MANUFACTURED` | Изготовлен | — |
| `CANCELLED` | Производство отменено | — |

Дублирующая строка «детализация статуса» (audit UX #10) удаляется.

### 5.3 Набор строк и фильтр

- Источник строк: `OrderWorklistQuery` (период обязателен, лимит `MAX_ROWS`) + batch production facts. Фильтрация по производственному состоянию — в composition/read слое (как в существующем Orders list).
- Фильтр по умолчанию: «В работе» = `NOT_ACCEPTED` (только ACTIVE заказы) ∪ `IN_PRODUCTION`. Дополнительно: «Изготовлены», «Отменены», «Все».
- Список не содержит материалов по строкам, позиций, UUID.

---

## 6. ORDER PRODUCTION CARD (LEVEL 2)

### 6.1 Шапка

| Поле | Источник |
|---|---|
| Заказ №… | `OrderQueryService.getOrder` → `OrderDto.orderNumber` |
| Заказчик | `OrderDto.customerName` |
| Объект | `OrderDto.siteRef` (`—` если пусто) |
| Менеджер (опционально) | `OrderDto.responsibleManager` |
| Состояние производства | `ProductionQueryApi.getOrderProductionView` → presentation label (§5.2) |
| Прогресс | Σ released / Σ ordered из `getItemProductionStatesByOrderId` |
| [Действия ▾] | меню заказа: «Обновить», «Отменить производство» (если допустимо) |

### 6.2 ПОЗИЦИИ

| Колонка | Источник | Примечание |
|---|---|---|
| Позиция | `OrderItemDto.externalPositionNumber` | Если пусто → `—` и **DEP-4** «нужен человекочитаемый идентификатор» |
| Изделие | `OrderItemDto.name` (+ `productCode` второй строкой/tooltip) | |
| Размер / характеристики | — | **DEP-5**: в `OrderItemDto` нет полей размеров/характеристик; `comments` не является структурированной характеристикой |
| Количество | `ItemProductionStateView.orderedQuantity`; до Launch — `ProductionSpecificationDto.orderedQuantity` из `getOrderForProduction` | |
| Состояние | `ItemProductionStateView.status` → «В производстве» / «Частично выпущено» / «Выпущено» / «Отменено»; нет состояния → «Не принято» | |
| Изготовлено | `releasedQuantity` | |
| Осталось | `activeProductionQuantity` | для `CANCELLED` — `0`, подпись «отменено» |

Связь строк: `ItemProductionStateView.sourceOrderItemId` ↔ `OrderItemDto.orderItemId` (`OrderQueryService.getOrderItems`, сейчас `firstPage()` — см. OQ-8).

**Удаляется:** Specification ID, UUID позиции (сейчас «номер / UUID»), Cutting UUID/count, Revision, checkbox-выбор строк на основном экране (выбор для выпуска переносится в мастер выпуска §11).

### 6.3 Блок «Следующий шаг» (Production Action Model)

Одна основная кнопка вычисляется из существующих данных. Новые backend-состояния не создаются.

| # | Presentation state | Условие (из существующих данных) | Основное действие | Текст |
|---|---|---|---|---|
| S0 | Не принят | `NOT_ACCEPTED` | **[Принять в производство]** (`production.order.accept`) | «Заказ ещё не принят в производство» |
| S0-x | Нельзя принять | `NOT_ACCEPTED` ∧ (`OrderStatus ≠ ACTIVE` ∨ `activeItemCount = 0` ∨ `missingSpecificationItemIds ≠ ∅`) | кнопка disabled | «Заказ не активен» / «В заказе нет активных позиций» / «У позиций без утверждённой спецификации: Поз. 3, Поз. 5» |
| S1 | Материалы не запрошены | `IN_PRODUCTION` ∧ потребность отсутствует | **[Запросить материалы]** (`production.transfer.create`) | «Материалы ещё не запрошены» |
| S2 | Запрос не отправлен | `IN_PRODUCTION` ∧ потребность `DRAFT` | **[Продолжить запрос]** → окно потребности | «Запрос материалов подготовлен, но не отправлен на склад» |
| S3 | Ожидаем материалы | `IN_PRODUCTION` ∧ потребность `SUBMITTED` ∧ материалы не готовы | нет основной кнопки; [Выпустить] disabled | «Ожидаем поступление материалов на производственный склад» |
| S4 | Можно выпускать | `IN_PRODUCTION` ∧ материалы готовы (§9) | **[Выпустить]** (`production.release.create`) | «Материалы готовы. Можно выпускать изделия» |
| S5 | Изготовлен | `MANUFACTURED` | — | «Все изделия выпущены» |
| S6 | Отменён | `CANCELLED` | — | «Производство заказа отменено. Выпущено до отмены: N шт.» |

Правила:

- Отмена никогда не является основной кнопкой; только в меню [Действия ▾] (§12).
- «Проверить наличие» не является отдельной кнопкой рабочего потока (см. §9.3).
- Кнопки, недоступные по permission, не показываются; кнопки, недоступные по состоянию, показываются disabled только для текущего шага, с причиной.
- Состояния S1/S2/S3 зависят от чтения потребности по заказу — **DEP-1**. Состояние S4 — от вычисления готовности — **DEP-8**.

---

## 7. MATERIAL UX

### 7.1 Сводка на карточке

```text
МАТЕРИАЛЫ
  Состояние:  Ожидаются
  Материалов: 12   Готовы: 9   Ожидаются: 3
  [Подробнее]
```

**Design finding (MAT-UNIT):** пример из задания «Требуется 125 / Получено 95» суммирует количества разных единиц (м, шт, кг) — такая сумма не имеет смысла. Сводка считает **строки материалов** (позиции потребности) по состоянию, а количества показываются только в детализации по каждой строке со своей единицей. Суммарное количество допустимо только если у всех строк одна единица измерения.

Варианты сводки:

| Presentation state | Сводка |
|---|---|
| НЕ ЗАПРОШЕНЫ | «Материалы ещё не запрошены» + [Запросить материалы] |
| НЕ ЗАПРОШЕНЫ (черновик) | «Запрос подготовлен, не отправлен» + [Продолжить запрос] |
| ЗАПРОШЕНЫ | «Материалы запрошены. Склад готовит отправку» |
| ОЖИДАЮТСЯ | «Ожидаем поступление материалов» · Материалов N · Готовы 0 |
| ЧАСТИЧНО ПОЛУЧЕНЫ | «Получены частично» · Материалов N · Готовы K · Ожидаются N−K |
| ГОТОВЫ | «✓ Материалы готовы» · Материалов N · Готовы N |

### 7.2 Окно «Материалы — подробнее»

Небольшое модальное окно / боковая панель, только чтение.

| Артикул | Наименование | Цвет | Размер | Требуется | Получено | Осталось | Ед. |
|---|---|---|---|---:|---:|---:|---|
| 101.208 | Профиль | Белый | 6000 | 50 | 50 | 0 | м |
| 101.305 | Профиль | Антрацит | 6000 | 40 | 25 | 15 | м |

| Колонка | Источник | Статус |
|---|---|---|
| Артикул, Наименование, Цвет, Ед. | `MaterialRequirementLineView.materialCode / materialName / color / unitOfMeasure` | AVAILABLE (только в момент prepare/submit — см. DEP-1) |
| Размер | `WarehouseApi.StockView.size` / material reference display по `materialReferenceId` | AVAILABLE через Warehouse reference API (в MR line поля нет) |
| Требуется | `MaterialRequirementLineView.quantity` | AVAILABLE (DEP-1) |
| Получено | нет публичного источника полученного количества по строке потребности | **DEP-3** |
| Осталось | Требуется − Получено | зависит от DEP-3 |
| *(вариант v1)* Доступно на произв. складе | `CurrentMaterialAvailabilityQueryService` → `MaterialAvailabilityLineView.productionWarehouseAvailable`, либо `WarehouseQueryApi.getStockByMaterialReferenceId` (AVAILABLE ∧ production warehouse) | AVAILABLE — см. §9 |

Если DEP-3 не закрыт к реализации, v1 показывает колонку «Доступно на производственном складе» вместо «Получено/Осталось» (решение — OQ-1). Колонки «Основной склад», ячейки, склад-источник, документы перемещения **не показываются** (это Warehouse).

Состояние строки: «Готов» / «Не хватает N ед.» — без технических статусов `MATERIAL_UNRESOLVED/AMBIGUOUS`; для них — «Материал не найден в справочнике склада» / «Материал определён неоднозначно — обратитесь к складу».

### 7.3 Material Requirement flow (основа сохраняется: DRAFT → edit → Submit → Warehouse Transfer Documents)

1. **[Запросить материалы]** → `prepareMaterialRequirement(orderId, selectedOrderItemIds)`. В целевом UX выбор позиций не показывается: передаются все позиции заказа с `activeProductionQuantity > 0` (согласовано с whole-order моделью). Подтверждение этого выбора — OQ-4.
2. Открывается окно **«Потребность в материалах»** (статус «Черновик»): таблица Артикул / Наименование / Цвет / Ед. / Количество. Количество редактируется inline только в `DRAFT` (`changeMaterialRequirementQuantity`, optimistic version). Отдельная кнопка «Применить количество» заменяется сохранением при подтверждении ячейки; контрол gated `production.transfer.create` (устраняет audit mismatch #1).
3. **[Отправить на склад]** → `submitMaterialRequirement`. Результат: «Материалы запрошены. Склад подготовит перемещение». Номера/UUID Transfer Documents не показываются.
4. После Submit окно только для чтения. Добавление/удаление строк не проектируется (текущий API поддерживает только изменение количества).

Ошибки submit → понятные сообщения:

| Техническая причина | Сообщение |
|---|---|
| `DemandSourceUnavailableException` (нет AVAILABLE источника) | «Материал 101.305 отсутствует на складах. Запрос не отправлен — уточните количество или обратитесь к складу» |
| Нет производственного склада (`destinationWarehouse` пуст) | «Не назначен производственный склад. Обратитесь к администратору склада» |
| Optimistic conflict | «Потребность изменена другим пользователем. Данные обновлены» |

Терминология «шаблон», «логическое перемещение», «Material Transfer Template» в UX отсутствует.

---

## 8. MATERIAL STATUS MODEL (presentation, не persisted)

| Presentation state | Смысл | Источник | Доступность источника |
|---|---|---|---|
| **НЕ ЗАПРОШЕНЫ** | Потребность не создана или черновик не отправлен | наличие/статус MR по заказу | **DEP-1** — нет публичного запроса MR по заказу; сейчас MR хранится только в памяти ViewModel |
| **ЗАПРОШЕНЫ** | MR `SUBMITTED`, склад ещё не отправил | MR `SUBMITTED` + сгенерированные документы в `DocumentStatus.DRAFT` (`WarehouseQueryApi.getTransferDocument(id).documentStatus`) | **DEP-1 + DEP-2** — id сгенерированных документов есть только в `SubmitMaterialRequirementResultView` в момент submit |
| **ОЖИДАЮТСЯ** | Отправлено, в пути, не принято | документы `POSTED` + `settlementState = AWAITING_RECEIPT` | **DEP-2** (+ continuation-документы `continuationOfDocumentId` — DEP-3) |
| **ЧАСТИЧНО ПОЛУЧЕНЫ** | Часть получена | часть документов `SETTLED`/`CLOSED` или частичная приёмка / открытый continuation; либо (вариант по складу) часть строк покрыта AVAILABLE на произв. складе | per-line received — **DEP-3**; вариант по складу — AVAILABLE (§9) |
| **ГОТОВЫ** | Необходимые материалы доступны на производственном складе | AVAILABLE на production warehouse ≥ требуемого (§9) | AVAILABLE через существующие источники; Production-owned read model — **DEP-8** |

Правило приоритета: состояние **ГОТОВЫ** определяется **фактическим наличием на производственном складе** (D2), а не статусом документов. Состояния ЗАПРОШЕНЫ/ОЖИДАЮТСЯ/ЧАСТИЧНО — информационные, описывают ход снабжения.

---

## 9. MATERIAL READINESS (Release Gate)

### 9.1 Что считается «готово»

Release разрешён, если для каждой строки материала, необходимой для выбранного выпуска, AVAILABLE на производственном складе ≥ плановому расходу этого выпуска. MR Submit готовностью не считается.

### 9.2 Существующие источники для проверки

| # | Источник | Что даёт | Ограничения |
|---|---|---|---|
| R1 | `ProductionApplicationApi.prepareRelease(orderId, itemReleases)` → `ReleasePreviewView.plannedMaterialLines` (`materialReferenceId`, `plannedQuantity`) | Требуемый расход **именно для этого выпуска** (§15.1.1, включая частичный) | unlocked preview; под lock пересчитывается при confirm |
| R2 | `WarehouseQueryApi.findProductionWarehouse()` / `ProductionApplicationApi.destinationWarehouse()` | id производственного склада | пусто = склад не назначен (валидное состояние) |
| R3 | `WarehouseQueryApi.getStockByMaterialReferenceId` (фильтр `AVAILABLE` ∧ `warehouseId = production`) или `checkAvailability(materialReferenceId, warehouseId, qty)` | Фактический AVAILABLE на производственном складе | Остаток **не закреплён за заказом** (OQ-3) |
| R4 | `ReleaseProductsService.precheckStock` (существующий backend) | Проверка AVAILABLE по выбранным ячейкам при confirm | Срабатывает только при confirm; сейчас выдаёт техническое исключение с UUID |
| R5 | `CurrentMaterialAvailabilityQueryService` / `checkMaterialAvailability` + `getMaterialAvailabilityResult` | `productionWarehouseAvailable`, `deficit` по строкам спецификации заказа | базис — спецификация всего заказа, не остаток к выпуску и не отредактированная MR; `check` пишет `MATERIALS_CHECKED` в историю при каждом вызове; требует `production.materials.check` |

### 9.3 Целевое поведение

- **Карточка (S3/S4):** готовность заказа к выпуску остатка. Источник данных — R1 (для всего `activeProductionQuantity`) + R2 + R3, либо R5. Вычисление готовности — бизнес-правило, поэтому оно должно жить в Production application layer, а не в UI. Публичного Production read-метода «готовность к выпуску» нет → **DEP-8**. UI не должен самостоятельно комбинировать Warehouse API для вывода правила допуска.
- **Мастер выпуска (§11):** после ввода «К выпуску» — R1 для введённых количеств; строки, где AVAILABLE < план, подсвечиваются; [Выпустить] disabled.
- **Confirm:** R4 остаётся последней линией защиты (concurrency: остаток мог быть израсходован между preview и confirm). Ошибка R4 маппится в сообщение: «Недостаточно материала 101.305 на производственном складе (доступно 25 м, требуется 40 м). Выпуск не выполнен».
- Отдельная кнопка «Проверить наличие» удаляется из рабочего потока; если выбран R5, проверка вызывается неявно при открытии карточки/«Обновить» — решение по побочной записи истории — OQ-6.

### 9.4 Тексты причины

| Ситуация | Текст под disabled [Выпустить] |
|---|---|
| Материалы не запрошены | «Материалы ещё не запрошены» |
| Не хватает материалов | «Недостаточно материалов для выпуска» + [Подробнее] |
| Нет производственного склада | «Не назначен производственный склад» |
| Нет прав | кнопка скрыта |

---

## 10. LAUNCH UX

- Кнопка **[Принять в производство]** только на уровне заказа (S0). Нет checkbox позиций, нет поля количества, нет «запустить позицию».
- Подтверждение:
  ```text
  Принять заказ №4183 в производство?
  Будут запущены все активные позиции (5) в полном заказанном количестве.
  [Принять]  [Отмена]
  ```
- Недоступность (S0-x): причины из `OrderForProductionDto` (`status`, `activeItemCount`, `missingSpecificationItemIds` → человекочитаемые позиции через `getOrderItems`).
- Успех: «Заказ №4183 принят в производство». Карточка переходит в S1.
- Конфликт (`ProductionLaunchConflictException`, повторный/параллельный запуск): «Заказ уже принят в производство. Данные обновлены» + refresh.
- Revision не показывается и не выбирается.

---

## 11. RELEASE UX

### 11.1 Шаг 1 — «Выпуск изделий: количество»

```text
ВЫПУСК ИЗДЕЛИЙ — Заказ №4183

| Позиция | Изделие        | Заказано | Изготовлено | Осталось | К выпуску |
|---------|----------------|---------:|------------:|---------:|----------:|
| 1       | Окно ПВХ 2-ств |       10 |           6 |        4 |      [4]  |
| 2       | Дверь балкон.  |        5 |           5 |        0 |       —   |

Материалы для этого выпуска:  ✓ готовы   [Подробнее]

                                   [Далее]  [Отмена]
```

- «К выпуску» по умолчанию = «Осталось» (`activeProductionQuantity`), редактируемо в диапазоне 0..Осталось; полный и частичный выпуск — одним и тем же полем.
- Строки с «Осталось = 0» не редактируются.
- После изменения количеств — preview R1 + проверка готовности (§9.3). При нехватке — [Далее] disabled + «Недостаточно материалов для выпуска».

### 11.2 Шаг 2 — «Фактический расход материалов» (существующий plan/fact)

| Материал | План | Факт | Ячейки производственного склада |
|---|---:|---:|---|
| 101.208 Профиль белый (м) | 20.000 | [20.000] | П-01: 20.000 |

- Значения по умолчанию — `ReleasePreviewView.defaultActuals`.
- Ячейки показываются по `StorageCellView.code`, не UUID; добавление/удаление распределения gated `production.release.create` (устраняет audit mismatch #2).
- Это единственное место, где Production оперирует ячейками — потому что Release/Consumption требует allocations (ADR-035/036); сам остаток ячеек остаётся Warehouse.

### 11.3 Подтверждение и результат

- [Выпустить] блокируется на время операции (устраняет audit mismatch #3).
- Успех: «Выпущено: 4 шт.» (сумма `releaseQuantity`), либо «Изделия выпущены. Заказ изготовлен полностью» при переходе в `MANUFACTURED`. `documentId` / UUID не показываются.
- Ошибки: over-release / конкурентный выпуск → «Количество к выпуску изменилось — другой пользователь уже выпустил часть изделий. Данные обновлены»; precheck R4 → §9.3; отменённое производство → «Производство заказа отменено, выпуск невозможен».

---

## 12. CANCELLATION UX

- Расположение: [Действия ▾] → **«Отменить производство»**. Никогда рядом с позициями и не как основная кнопка.
- Доступно: `OrderProductionViewStatus = IN_PRODUCTION` ∧ `production.cancellation.create`. Иначе пункт меню скрыт (MANUFACTURED/CANCELLED/NOT_ACCEPTED).
- Подтверждение:
  ```text
  Отменить производство заказа №4183?
  • Незавершённые изделия будут отменены (осталось: 10 шт.).
  • Уже выпущенные изделия сохраняются (выпущено: 7 шт.).
  • Материалы на производственном складе автоматически не возвращаются.
  Причина (необязательно): [______________]
  [Отменить производство]  [Не отменять]
  ```
- Результат: «Производство заказа №4183 отменено». Карточка → S6.
- Семантика backend не меняется: whole-order, `production.cancellation`, FOR UPDATE lock, released preserved, без Warehouse return.

---

## 13. HISTORY UX

- На карточке — компактный блок: «Последняя операция: 01.10.2026 14:05 — Выпуск изделий — Иванов И.» + [Подробнее].
- Окно «История производства»: Дата/время | Операция | Пользователь | Описание.

| Поле | Источник |
|---|---|
| Дата/время | `ProductionHistoryEntryView.occurredAt` (локальное время) |
| Операция | `historyType` → русская подпись (ниже) |
| Пользователь | `actorRef` → отображаемое имя (сейчас строка actor; маппинг в ФИО — OQ-7) |
| Описание | формируется из `historyType` + человекочитаемых данных позиции (через `sourceOrderItemId` → `OrderItemDto`); **raw `summary` не выводится** (сейчас технический английский текст, напр. «Material transfer created») |

| `historyType` | Подпись |
|---|---|
| `ORDER_ACCEPTED` | Заказ принят в производство |
| `MATERIALS_CHECKED` | Проверено наличие материалов |
| `MATERIAL_TRANSFER_CREATED` | Создано перемещение материалов (историческое) |
| `MATERIAL_RECEIPT_CONFIRMED` | Подтверждено получение материалов (историческое) |
| `PRODUCTS_RELEASED` | Выпуск изделий |
| `PLAN_FACT_DEVIATION` | Отклонение факта расхода от плана |
| `PRODUCTION_CANCELLED` | Производство отменено |

- `sourceDocumentId`, `businessReferenceId`, `entryId` не показываются.
- Новый history mechanism не создаётся. Факт: Submit потребности **не пишет** запись в `production_history` → событие «Материалы запрошены» в истории отсутствует (**DEP-9**, не блокирует UX).

---

## 14. EMPTY / LOADING / ERROR STATES

| Контекст | Состояние | Текст |
|---|---|---|
| Список | пусто | «Нет заказов в производстве» |
| Список | пусто по фильтру | «Нет заказов, подходящих под фильтр» |
| Любой | загрузка | «Загрузка…» (контролы действий disabled) |
| Карточка / Материалы | нет потребности | «Материалы ещё не запрошены» |
| Карточка / Материалы | ожидание | «Ожидаем поступление материалов» |
| Позиции | нет позиций (NOT_ACCEPTED, нет ACTIVE) | «В заказе нет активных позиций» |
| История | пусто | «Операций по производству пока не было» |
| Ошибка доступа | `AccessDenied` | «Недостаточно прав для этого действия» |
| Ошибка сети/БД | непредвиденная | «Не удалось выполнить операцию. Повторите попытку или обратитесь к администратору» |
| Конфликт | concurrency | «Данные изменены другим пользователем. Информация обновлена» |

Запрещено показывать: UUID, stack trace, имена классов исключений, английские enum-статусы Warehouse (audit UX #5).

---

## 15. KEEP / REWORK / REPLACE

### KEEP

| Элемент | Основание |
|---|---|
| Production item state (item-owned, `production_item_states`) | ADR-033, Spec §5 |
| Whole-order Launch | D3, Spec §10 |
| Release | ADR-035/036 |
| Partial Release (§15.1.1) | Spec |
| Cancellation (whole-order) | Spec §16 |
| Production documents (launch/release/cancellation, POSTED immutable) | Spec §9 |
| Material Requirement (DRAFT → Submit → Warehouse Transfer Documents) | ADR-037 §E/§F, Spec §13.1 |
| Warehouse integration через public API | Constitution |
| History (append-only) | Spec §22 |
| Security (7 permissions) | Spec §20 |
| Concurrency: Release lock, Cancel lock, MR optimistic locking | ADR-036, audit §9 |

### REWORK

| Элемент | Что меняется |
|---|---|
| Workbench IA | Одна плотная страница → LEVEL 1 список + LEVEL 2 карточка + диалоги |
| Material readiness UX | Сводка + «Подробнее»; presentation state §8 |
| Release gate UX | disabled + понятная причина; мастер из двух шагов |
| History presentation | Блок «Последняя операция» + окно; русские подписи по типу |
| Action model | 6 равноправных кнопок → «состояние → следующий шаг» |
| Secondary controls gating | apply qty / allocations / confirm loading по permission и состоянию |

### REPLACE

| Было | Стало |
|---|---|
| Template как активный UX (logical transfer combo) | удалён из UX; legacy retention decision deferred |
| Production receipt UX («Подтвердить получение») | Warehouse → Задачи → Приёмка; Production показывает результат |
| Specification ID presentation | Позиция / Изделие / Количество |
| Технические UUID (позиция, заказ, документ, cutting, release result) | человекочитаемые поля или скрыто |
| Cutting column / controls | скрыто до STAGE7-008A |
| Кнопка «Проверить наличие» как шаг потока | неявная проверка готовности (§9) |

---

## 16. IMPLEMENTATION DEPENDENCIES

Новые API не придумываются; ниже — факты недостаточности текущих контрактов.

| ID | Нужно для | Факт текущего состояния | Владелец |
|---|---|---|---|
| **DEP-1** | Состояния S1/S2/S3, сводка и детализация материалов после повторного открытия заказа | Нет публичного чтения Material Requirement по заказу: ни `ProductionQueryApi`, ни `ProductionApplicationApi` не имеют get/list MR; Workbench держит `currentRequirement` только в памяти | Production |
| **DEP-2** | ЗАПРОШЕНЫ / ОЖИДАЮТСЯ | Ссылки на сгенерированные Transfer Documents (`material_requirement_generated_documents`) не доступны через публичный API после Submit | Production |
| **DEP-3** | «Получено / Осталось» по строке MR | `TransferDocumentView.lines` содержат только запрошенное количество; принятое количество по строке, связь строки MR ↔ строка документа ↔ continuation-документы не доступны публично | Warehouse (+ Production для связи) |
| **DEP-4** | Колонка «Позиция» | `OrderItemDto.externalPositionNumber` может быть пустым; иного человекочитаемого идентификатора позиции нет — **нужен человекочитаемый идентификатор** (OM не меняется в рамках UX) | Order Management |
| **DEP-5** | «Размер / характеристики» изделия | В `OrderItemDto` / `ProductionSpecificationDto` нет полей размеров/характеристик | Order Management |
| **DEP-6** | Колонка «Материалы» в списке | Нет batch-источника состояния материалов по набору заказов | Production |
| **DEP-7** | Колонка «Позиций» в списке | `OrderProductionListFacts` не содержит количество позиций; `itemCount` есть только в per-order `OrderProductionView` | Production |
| **DEP-8** | Release gate как бизнес-правило | Источники есть (R1–R5), но Production-owned read-метода «готовность материалов к выпуску» нет; комбинировать правило в UI запрещено (no business logic in UI) | Production |
| **DEP-9** | История «Материалы запрошены» | Submit MR не пишет `production_history` | Production |
| **DEP-10** | Понятная ошибка нехватки при confirm | `ReleaseProductsException` из `precheckStock` содержит UUID в тексте; нужен типизированный признак причины для маппинга в UI-сообщение | Production |

---

## 17. OPEN QUESTIONS (не решаются из существующих API)

1. **OQ-1:** Как точно вычислять «Получено» для каждой строки MR — по принятому количеству Transfer Documents (вкл. continuation) или по AVAILABLE на производственном складе? До ответа v1 может показывать только «Доступно на производственном складе».
2. **OQ-2:** Как определять readiness для partial release — по плановому расходу конкретного выпуска (R1) или по всему остатку заказа? Можно ли выпускать часть при нехватке для остатка?
3. **OQ-3:** AVAILABLE на производственном складе не закреплён за заказом. Если два заказа используют один материал, «готово» для одного может означать нехватку для другого. Нужно ли закрепление (reservation `PRODUCTION_DEMAND` существует как тип в Warehouse API) — или достаточно общего остатка?
4. **OQ-4:** «Запросить материалы» — всегда по всем позициям заказа с `activeProductionQuantity > 0`, или сохранить выбор позиций? Допустим ли повторный запрос (сейчас каждый prepare создаёт новый DRAFT)?
5. **OQ-5:** Нужен ли отдельный Warehouse public query для material requirement fulfillment (DEP-3), или достаточно Production read-model поверх существующих Warehouse queries?
6. **OQ-6:** Если для готовности используется R5, допустима ли запись `MATERIALS_CHECKED` в историю при каждом открытии карточки, или нужен read-only вариант?
7. **OQ-7:** Как отображать `actorRef` как ФИО пользователя (какой Security public query использовать)?
8. **OQ-8:** Workbench читает позиции через `getOrderItems(..., firstPage())` — для заказов с числом позиций больше размера страницы нужна полная загрузка; подтвердить ожидаемый максимум позиций в заказе.
9. **OQ-9:** Количество MR рассчитывается как сумма `lineQuantity` спецификаций (не × количество изделий, audit §12). Подтвердить, что это ожидаемый базис для «Требуется» в UX.

---

## 18. WIREFRAMES

### 18.1 LEVEL 1 — Производство

```text
================================================================================
ПРОИЗВОДСТВО
================================================================================
Период: [01.09.2026 – 01.10.2026]  Состояние: [В работе ▾]  Поиск: [_________]

  | Заказ  | Заказчик        | Позиций | Состояние      | Материалы  | Изготовлено |
  |--------|-----------------|--------:|----------------|------------|------------:|
● | №4184  | ООО «Стройдом»  |       3 | Не принят      | —          |       —     |
● | №4183  | ООО «Альфа»     |       5 | В производстве | Готовы     |     7 / 17  |
  | №4179  | ИП Петров       |       2 | В производстве | Ожидаются  |     0 / 6   |
  | №4170  | ООО «Север»     |       4 | Изготовлен     | —          |    12 / 12  |

● — нужно действие                                         [Открыть]  [Обновить]
--------------------------------------------------------------------------------
(Позиций / Материалы — при закрытии DEP-7 / DEP-6; иначе колонки скрыты)
```

### 18.2 LEVEL 2 — Карточка, материалы ожидаются

```text
================================================================================
← К списку                     ЗАКАЗ №4179                         [Действия ▾]
================================================================================
Заказчик: ИП Петров            Объект: ул. Лесная, 12
Состояние: В производстве      Изготовлено: 0 из 6

┌ СЛЕДУЮЩИЙ ШАГ ───────────────────────────────────────────────────────────────┐
│ Ожидаем поступление материалов на производственный склад                     │
│ [Выпустить]  (недоступно: Недостаточно материалов для выпуска)               │
└──────────────────────────────────────────────────────────────────────────────┘

ПОЗИЦИИ
| Позиция | Изделие            | Кол-во | Состояние      | Изготовлено | Осталось |
|---------|--------------------|-------:|----------------|------------:|---------:|
| 1       | Окно ПВХ 2-ств.    |      4 | В производстве |           0 |        4 |
| 2       | Дверь балконная    |      2 | В производстве |           0 |        2 |

МАТЕРИАЛЫ
Состояние: Получены частично
Материалов: 12    Готовы: 9    Ожидаются: 3                          [Подробнее]

ИСТОРИЯ
Последняя операция: 30.09.2026 10:12 — Заказ принят в производство — Иванов И.
                                                                     [Подробнее]
```

### 18.3 LEVEL 2 — Материалы готовы

```text
ЗАКАЗ №4183                                                        [Действия ▾]
Заказчик: ООО «Альфа»          Объект: ЖК «Парк»
Состояние: В производстве      Изготовлено: 7 из 17

┌ СЛЕДУЮЩИЙ ШАГ ───────────────────────────────────────────────────────────────┐
│ Материалы готовы. Можно выпускать изделия                    [Выпустить]     │
└──────────────────────────────────────────────────────────────────────────────┘

МАТЕРИАЛЫ
✓ Материалы готовы
Материалов: 14    Готовы: 14    Ожидаются: 0                         [Подробнее]
```

### 18.4 LEVEL 2 — Не принят

```text
ЗАКАЗ №4184                                                        [Действия ▾]
Заказчик: ООО «Стройдом»       Объект: —
Состояние: Не принят

┌ СЛЕДУЮЩИЙ ШАГ ───────────────────────────────────────────────────────────────┐
│ Заказ ещё не принят в производство            [Принять в производство]       │
└──────────────────────────────────────────────────────────────────────────────┘

ПОЗИЦИИ
| Позиция | Изделие         | Кол-во | Состояние  | Изготовлено | Осталось |
| 1       | Окно ПВХ        |      6 | Не принято |           — |        — |

МАТЕРИАЛЫ
Материалы будут доступны после принятия заказа в производство
```

### 18.5 LEVEL 2 — Материалы не запрошены

```text
┌ СЛЕДУЮЩИЙ ШАГ ───────────────────────────────────────────────────────────────┐
│ Материалы ещё не запрошены                         [Запросить материалы]     │
└──────────────────────────────────────────────────────────────────────────────┘
МАТЕРИАЛЫ
Материалы ещё не запрошены
```

### 18.6 Окно «Потребность в материалах» (черновик)

```text
================================================================================
ПОТРЕБНОСТЬ В МАТЕРИАЛАХ — Заказ №4183                         Черновик
================================================================================
| Артикул | Наименование | Цвет     | Ед. | Количество |
|---------|--------------|----------|-----|-----------:|
| 101.208 | Профиль      | Белый    | м   |   [50.000] |
| 101.305 | Профиль      | Антрацит | м   |   [40.000] |

Склад сам выберет, откуда переместить материалы на производственный склад.
                                       [Отправить на склад]  [Закрыть]
```

### 18.7 Окно «Материалы — подробнее»

```text
================================================================================
МАТЕРИАЛЫ — Заказ №4179                              Состояние: Получены частично
================================================================================
| Артикул | Наименование | Цвет     | Размер | Требуется | Получено | Осталось | Ед. |
|---------|--------------|----------|--------|----------:|---------:|---------:|-----|
| 101.208 | Профиль      | Белый    | 6000   |        50 |       50 |        0 | м   |
| 101.305 | Профиль      | Антрацит | 6000   |        40 |       25 |       15 | м   |
(«Получено/Осталось» — после DEP-3; иначе колонка «Доступно на произв. складе»)
                                                                      [Закрыть]
```

### 18.8 Мастер выпуска — см. §11.1 / §11.2. Результат:

```text
✓ Выпущено: 4 шт.
```

### 18.9 Меню [Действия ▾]

```text
[Действия ▾]
  Обновить
  ─────────────
  Отменить производство…     (только В производстве + право отмены)
```

### 18.10 Окно «История производства»

```text
| Дата/время       | Операция                      | Пользователь | Описание                   |
|------------------|-------------------------------|--------------|----------------------------|
| 01.10.2026 14:05 | Выпуск изделий                | Иванов И.    | Поз. 1 Окно ПВХ — 4 шт.    |
| 30.09.2026 10:12 | Заказ принят в производство   | Иванов И.    | 5 позиций                  |
```

---

## 19. ARCHITECTURAL CONSTRAINTS

1. UI → только `ProductionApplicationApi` / `ProductionQueryApi` + публичные query других capability для отображения (OM `OrderQueryService`, `OrderWorklistQuery`; Warehouse reference/display). Без repository/таблиц.
2. Правило готовности к выпуску (D2) — бизнес-логика Production application layer; UI только отображает результат (DEP-8).
3. Production не пишет `StockPosition` / `WarehouseMovement`; приёмка — Warehouse (D1, ADR-037 §K).
4. Новые persisted Production statuses не создаются; все состояния §6.3 и §8 — presentation, вычисляемые.
5. Whole-order Launch / Cancel сохраняются (D3); UX не подразумевает item-level Launch.
6. Concurrency не меняется: Release FOR UPDATE, Cancel FOR UPDATE, MR optimistic version + submit FOR UPDATE. **QA gap FINDING-C1:** нет multi-thread PostgreSQL IT для параллельного Launch — в рамках UX design тест не пишется.
7. Revision не является объектом Production UX (ADR-033): нет Revision Number, selector, column.
8. Cutting скрыт до STAGE7-008A; backend Cutting не меняется.
9. Legacy Material Transfer Template (V27/V28, `listLogicalTransfers`, `confirmMaterialReceipt`, unwired confirm service) — исторический, вне нового UX; удаление backend/database не выполняется. **Legacy retention decision deferred.**
10. Документационные расхождения audit FINDING-D1 (Spec §13.1 vs §14.2) и FINDING-D2 (Manifest v2.4 vs Spec v2.6) остаются открытыми; этот документ их не исправляет.

---

## 20. CODE CHANGES

NONE

## 21. DATABASE

NONE

## 22. PACKAGE

NOT RUN

## 23. STARTUP

NOT RUN

## 24. GIT

No commit. No push.

---

*End of Stage 7 Production UX Design.*
