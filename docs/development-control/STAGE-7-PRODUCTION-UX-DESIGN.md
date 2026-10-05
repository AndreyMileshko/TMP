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
| Future actions on LEVEL 1 | Material Request: **[Запросить материалы]** + **[Черновики материалов]** (Phase 5); Release: **[Выпустить]** (Phase 7) |
| Permissions | Open: `production.order.view`; selection needs no mutation rights; Material Request create: `production.transfer.create`; Release: `production.release.create` |
| Transitional DETAIL | Replaced in Phase 4 by Order Card (§0.2) |
| Removed from LEVEL 1 | UUID input, Spec/Cutting columns, logical transfer, «Подтвердить получение» |
| Phase 2 shim | **DELETED in Phase 4** — no remaining `src/main` callers of single-order prepare |
| Adapter | `selectedItemsForMaterialRequirement()` → `MaterialRequirementSourceItemRefView` list; Phase 5 prepare/submit wired |

**Следующая фаза:** Phase 7 — Release UX.

---

## 0.3 IMPLEMENTATION PHASE 5 — Cross-Order Material Requirement UX (2026-10-02)

**Статус:** IMPLEMENTED (LEVEL 1 tree → Material Request wizard). Phase 6 Materials Readiness implemented (§0.4).

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
| Submit | `submitMaterialRequirement` → Warehouse Demand accept + best-effort Transfer; success even when lines WAIT (unmatched/ambiguous/zero stock); Demand ≠ Transfer |
| Shortage / coverage conflict | DRAFT остаётся; human messages |
| DRAFT persistence | Close не удаляет; reopen через **[Черновики материалов]** + `listMaterialRequirementDrafts` / `getMaterialRequirement` |
| Multiple DRAFTs | Список по дате/времени + N позиций / M заказов (без UUID) |
| After Submit | Toast; clear selection submitted items; remain on LEVEL 1 tree |
| Out of scope | Release UX, readiness (→ Phase 6), receipt, reservation, Cutting, History redesign |

**Следующая фаза:** Phase 8 — Stage 7 consolidation / cleanup.

---

## 0.5 IMPLEMENTATION PHASE 7 — Release UX (2026-10-02)

**Статус:** IMPLEMENTED (LEVEL 1 tree selection → Release wizard).

| Элемент | Реализация |
|---|---|
| Entry point | LEVEL 1 Tree: **[Выпустить]** (`production.release.create`; hidden without permission; disabled when selection empty) |
| Selection | Same `ProductionTreeSelectionModel` — no second selection model; Launch remains whole-order only |
| Invalid items | Not silently skipped: RELEASED / CANCELLED / NOT_ACCEPTED / active=0 → validation with order + position |
| Quantity Mode | Read from order via `getOrderQuantityModes`; **no mode selector** in Release dialog |
| STANDARD | releaseQuantity = full `activeProductionQuantity` (read-only) |
| FLEXIBLE | default = active; editable 1..active |
| Mixed orders | One wizard; items grouped by OrderId; modes may differ per order |
| Mode/quantity race | Before leaving STEP 1: authoritative reload; mode change / quantity change messages; no reinterpret of stale FLEXIBLE input |
| Readiness preflight | `getMaterialReadinessForRelease(orderId, itemReleaseQuantities)` per order; NOT Material Requirement; snapshot only; no reservation |
| NOT_READY | «Недостаточно материалов для выпуска.» + [Подробнее]; cannot continue |
| NO_PRODUCTION_WAREHOUSE | «Не назначен производственный склад.»; cannot continue |
| Plan / fact | Existing `prepareRelease` → `ReleasePreviewView.defaultActuals`; plan read-only; fact editable; cell codes via Warehouse `listStorageCells`; no new allocation algorithm; deviation allowed |
| Multi-order | Group by OrderId; per-order Release command/TX; OrderId ascending; **not** cross-order atomic |
| Partial failure | Stop after first authoritative confirm failure; UI shows A success / B failed / C not processed; no UI rollback of POSTED Release |
| Confirm | Existing `releaseProducts` (lock → revalidate → plan → stock precheck → consume → persist → item states) |
| After success | Refresh tree; clear successfully released selection; remain LEVEL 1 |
| Reservation | **Not introduced** |
| Permissions | Release action: `production.release.create`; readiness read: `production.order.view` |
| Out of scope | Order Card competing Release selection; legacy backend deletion; Cutting; Warehouse reservation redesign |

**Следующая фаза:** Phase 8 — Stage 7 consolidation / cleanup.

---

## 0.6 IMPLEMENTATION PHASE 8 — Final Consolidation (2026-10-02)

**Статус:** IMPLEMENTED (Cancellation + History Order Card UX; legacy audit; Stage 7 acceptance).

| Элемент | Реализация |
|---|---|
| Completeness | LEVEL 1 tree + Order Card + Launch + Quantity Mode + MR + Readiness + Release + Cancellation + History |
| Cancellation entry | Order Card **[Действия ▾]** → «Отменить производство…» (not primary) |
| Cancellation permission | `production.cancellation.create`; visible only when Order View = `IN_PRODUCTION` |
| Cancellation confirm | Human dialog: unfinished cancelled / released preserved / no Warehouse return; optional reason (API supports `Optional<String>`) |
| Cancellation backend | Existing `cancelOrderProduction` / `CancelOrderProductionService` (order lock; whole-order; no Warehouse) |
| History block | Order Card compact «ИСТОРИЯ»: latest operation or empty state |
| History details | Dialog via `listProductionHistory`; Russian type labels; no raw English summary / UUID / entryId |
| Actor | `actorRef` shown when human-readable (login); UUID → «—»; no new Security display-name API |
| MR history gap | Submit MR still does **not** write history — **KNOWN NON-BLOCKING GAP** (DEP-9 retained) |
| Material Transfer Template | **RETAINED** — public `listLogicalTransfers` / receipt path + ArchUnit rules + historical DB; not used by new MR flow |
| Production receipt backend | **RETAINED** — `confirmMaterialReceipt` public API + ArchUnit; no Production UI action (Warehouse owns physical receipt) |
| Legacy DB | **RETAINED** — no DROP / no migration edits |
| Permissions | All 7 audited; `production.receipt.confirm` / `production.materials.check` retained as security contract (legacy-capable) |
| Reservation | Unchanged — snapshot readiness + authoritative Release confirm |
| Cutting | Deferred (`STAGE7-008A`) |
| Docs | This file Phase 8 + `STAGE-7-PRODUCTION-COMPLETION.md` |

**Stage 7 Production:** ready for closure after Phase 8 acceptance (tests / package / startup).

---

## 0.4 IMPLEMENTATION PHASE 6 — Material Readiness Read Model (2026-10-02)

**Статус:** IMPLEMENTED (Production-owned readiness read model + Order Card materials block). Phase 7 Release UX implemented (§0.5).

| Элемент | Реализация |
|---|---|
| Required source | Same as Release: `ReleaseMaterialPlanBuilder` / `PartialReleaseMaterialPlanCalculator` (§15.1.1) — **not** Material Requirement quantity |
| Available source | Warehouse `AVAILABLE` on production warehouse only (`WarehouseAvailabilityQueryPort.availableQuantities` → `getStockByWarehouse`, AVAILABLE cells aggregated) |
| Overall | READY iff every line `available >= required`; else NOT_READY; missing warehouse → `NO_PRODUCTION_WAREHOUSE`; NOT_ACCEPTED/MANUFACTURED/CANCELLED → `NOT_APPLICABLE` |
| Public API | `getOrderRemainingMaterialReadiness(orderId)`; `getMaterialReadinessForRelease(orderId, itemReleases)` |
| Permission | `production.order.view` only (read-only; no `materials.check`, no `release.create`) |
| Side effects | None: no MATERIALS_CHECKED history, no reservation, no Warehouse mutation |
| Persistence | None (volatile Warehouse snapshot) |
| Order Card | Materials block: summary + [Подробнее] dialog (Требуется / Доступно / Не хватает / Ед.); no cells/docs/UUID; no «Получено» |
| Reservation | **Not introduced.** Snapshot only; Release confirm remains authoritative |
| Out of scope | Release UX, fulfillment tracking, Warehouse receipt, MR redesign |

**Следующая фаза:** Phase 7 — Release UX (tree selection → Release; STANDARD/FLEXIBLE quantities; readiness preflight; confirm under locks).

---

## 0.2 IMPLEMENTATION PHASE 4 — Order Production Card + Quantity Mode UX (2026-10-02)

**Статус:** IMPLEMENTED (LEVEL 2 Order Card). Phase 5 Material Request UX implemented (§0.3). Phase 6 Materials block implemented (§0.4).

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

**Следующая фаза:** Phase 7 — Release UX (см. §0.4).

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

Связь строк: `ItemProductionStateView.sourceOrderItemId` ↔ `OrderItemDto.orderItemId` (`OrderQueryService.getOrderItems` via `ProductionOrderItemsLoader` — all pages).

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

### 7.3 Material Requirement flow (IMPLEMENTED Phase 5 — cross-order)

> Historical single-order assumption removed. See §0.3 for the authoritative Phase 5 implementation.

1. LEVEL 1 Tree selection (cross-order) → **[Запросить материалы]** → STEP 1 product quantities (STANDARD/FLEXIBLE via `getOrderQuantityModes` + coverage).
2. `prepareMaterialRequirement(List<MaterialRequirementProductSelectionView>)` creates a persisted DRAFT (not ViewModel-only memory).
3. STEP 2: edit line quantities (`changeMaterialRequirementQuantity` + optimistic version) → **[Отправить на склад]** → `submitMaterialRequirement`.
4. Reopen DRAFTs via **[Черновики материалов]** (`listMaterialRequirementDrafts` / `getMaterialRequirement`).
5. Selection of positions is retained (checkbox tree); repeated prepare creates a new DRAFT (allowed).

Ошибки submit → понятные сообщения (см. Phase 5 UI mapper). Терминология «шаблон» / «логическое перемещение» в UX отсутствует.

---

## 8. MATERIAL STATUS MODEL (presentation, не persisted)

| Presentation state | Смысл | Источник | Доступность источника |
|---|---|---|---|
| **НЕ ЗАПРОШЕНЫ** | Потребность не создана или черновик не отправлен | наличие/статус MR | **DEP-1 RESOLVED (partial):** DRAFT reopen via `listMaterialRequirementDrafts` / `getMaterialRequirement`; per-order MR list for card S1–S3 still optional |
| **ЗАПРОШЕНЫ** | MR `SUBMITTED`, склад ещё не отправил | MR `SUBMITTED` + generated Transfer Documents | **DEP-1 + DEP-2** — document ids after submit still not a durable public per-order query |
| **ОЖИДАЮТСЯ** | Отправлено, в пути, не принято | документы `POSTED` + `AWAITING_RECEIPT` | **DEP-2** (out of Phase 6 scope) |
| **ЧАСТИЧНО ПОЛУЧЕНЫ** | Часть получена | Transfer fulfillment | **DEP-3** (out of Phase 6 scope) |
| **ГОТОВЫ** | Необходимые материалы AVAILABLE на производственном складе | Release plan vs production-warehouse AVAILABLE | **DEP-8 RESOLVED (Phase 6):** `getOrderRemainingMaterialReadiness` / `getMaterialReadinessForRelease` |

Правило приоритета: состояние **ГОТОВЫ** определяется **фактическим наличием на производственном складе**, а не статусом MR (`SUBMITTED ≠ READY`). Phase 6 Order Card показывает Требуется / Доступно / Не хватает — без «Получено».

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

- **Карточка (S3/S4):** готовность заказа к выпуску остатка через `getOrderRemainingMaterialReadiness` (Phase 6 / DEP-8). Required = Release plan for active remainder; Available = production-warehouse AVAILABLE. UI only displays the result.
- **Мастер выпуска (§11 / Phase 7):** `getMaterialReadinessForRelease` for entered quantities; [Выпустить] gating.
- **Confirm:** R4 остаётся последней линией защиты. Phase 6 snapshot **не** заменяет confirm validation.
- Кнопка «Проверить наличие» удалена; Order Card readiness path is read-only (no MATERIALS_CHECKED).

### 9.4 Тексты причины

| Ситуация | Текст |
|---|---|
| READY | «✓ Материалов достаточно для выпуска оставшихся изделий.» + [Подробнее] |
| NOT_READY | «Недостаточно материалов…» + «Не хватает: N позиций» + [Подробнее] |
| Нет производственного склада | «Не назначен производственный склад.» |
| NOT_ACCEPTED | «Материалы будут доступны после принятия заказа в производство.» |
| MANUFACTURED | «Все изделия выпущены.» |
| CANCELLED | «Производство заказа отменено.» |

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

**Implemented (Phase 7):** Tree is the Release entry point. Quantity Mode is informational (STANDARD = full active remainder, read-only; FLEXIBLE editable). Mixed orders supported. Preflight is snapshot readiness; confirm is authoritative per-order Release. Plan/fact kept; cells shown by code; no reservation; multi-order batch uses per-order transactions with honest partial-success semantics.

### 11.1 Шаг 1 — «Выпуск изделий: количество»

```text
ВЫПУСК ИЗДЕЛИЙ

Заказ №4183 — Стандартный
Поз. 1   Окно
Заказано: 10
Изготовлено: 6
Осталось: 4
К выпуску: 4

Заказ №4184 — Гибкий
Поз. 3   Дверь
Заказано: 8
Изготовлено: 2
Осталось: 6
К выпуску: [4]

                                   [Далее]  [Отмена]
```

- STANDARD: «К выпуску» = «Осталось» (`activeProductionQuantity`), **not editable**.
- FLEXIBLE: default = «Осталось»; editable in range 1..Осталось.
- Quantity Mode is **not** chosen in the dialog — only read from the order.
- After quantities — readiness via `getMaterialReadinessForRelease` for selected release quantities. При нехватке — cannot continue + «Недостаточно материалов для выпуска» + [Подробнее].
- Mode/quantity race before Next: reload authoritative data; do not reinterpret stale FLEXIBLE input.

### 11.2 Шаг 2 — «Фактический расход материалов» (существующий plan/fact)

| Материал | План | Факт | Ячейки производственного склада |
|---|---:|---:|---|
| 101.208 Профиль белый (м) | 20.000 | [20.000] | П-01: 20.000 |

- Значения по умолчанию — `ReleasePreviewView.defaultActuals`.
- Ячейки показываются по `StorageCellView.code`, не UUID; добавление/удаление распределения gated `production.release.create` (устраняет audit mismatch #2).
- Это единственное место, где Production оперирует ячейками — потому что Release/Consumption требует allocations (ADR-035/036); сам остаток ячеек остаётся Warehouse.

### 11.3 Подтверждение и результат

- Final summary then [Выпустить]; button blocked during operation (double-submit guard).
- Multi-order: OrderId ascending; each order own TX; stop after first authoritative failure; UI shows partial success honestly (not all-or-nothing).
- Успех: «Выпущено: N изделия.» / «Изделия выпущены. Заказ изготовлен полностью» при MANUFACTURED. `documentId` / UUID не показываются.
- Ошибки: over-release / конкурентный выпуск → «Количество к выпуску изменилось…»; stock race → human shortage message; отменённое производство → «Производство заказа отменено. Выпуск невозможен.»
- After success: refresh tree; clear successfully released items from selection; remain on LEVEL 1.

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
| **DEP-1** | MR reopen / drafts | **RESOLVED (Phase 5):** `getMaterialRequirement`, `listMaterialRequirementDrafts`, product coverage APIs. Per-order MR status strip for card S1–S3 still optional / not Phase 6 | Production |
| **DEP-2** | ЗАПРОШЕНЫ / ОЖИДАЮТСЯ | Ссылки на сгенерированные Transfer Documents не доступны через публичный API после Submit | Production |
| **DEP-3** | «Получено / Осталось» по строке MR | Out of Phase 6 scope; Card uses AVAILABLE/shortage terminology instead | Warehouse (+ Production) |
| **DEP-4** | Колонка «Позиция» | `externalPositionNumber` fallback «Позиция N» implemented in tree/card | Order Management / UI |
| **DEP-5** | «Размер / характеристики» изделия | В `OrderItemDto` / `ProductionSpecificationDto` нет полей размеров/характеристик | Order Management |
| **DEP-6** | Колонка «Материалы» в списке | Нет batch-источника состояния материалов по набору заказов | Production |
| **DEP-7** | Колонка «Позиций» / all item pages | **RESOLVED:** `ProductionOrderItemsLoader` loads all pages (`MAX_PAGE_SIZE`); tree/card no longer truncate to first page | Production / UI |
| **DEP-8** | Release gate readiness | **RESOLVED (Phase 6):** `getOrderRemainingMaterialReadiness` / `getMaterialReadinessForRelease`; snapshot AVAILABLE only; no new reservation | Production |
| **DEP-9** | История «Материалы запрошены» | Submit MR не пишет `production_history` | Production |
| **DEP-10** | Понятная ошибка нехватки при confirm | `ReleaseProductsException` из `precheckStock` содержит UUID в тексте; нужен типизированный признак причины для маппинга в UI-сообщение | Production |

---

## 17. OPEN QUESTIONS — Phase 8 status

1. **OQ-1:** «Получено» per MR line (fulfillment tracking) — **OUT OF SCOPE / DEFERRED** for Stage 7. v1 shows Available / Required / Shortage (Phase 6).
2. **OQ-2:** Partial release readiness basis — **RESOLVED (Phase 7):** readiness preflight uses selected release quantities via `getMaterialReadinessForRelease`; Release confirm remains authoritative.
3. **OQ-3:** Reservation — **RESOLVED FOR STAGE 7:** no new reservation; AVAILABLE snapshot + authoritative Release confirm.
4. **OQ-4:** MR selection / DRAFT — **RESOLVED (Phase 5).**
5. **OQ-5:** Warehouse fulfillment public query — **DEFERRED / OUT OF SCOPE** Stage 7.
6. **OQ-6:** MATERIALS_CHECKED side effect on card — **RESOLVED (Phase 6):** readiness is read-only.
7. **OQ-7:** Actor display name — **DEFERRED:** show human-readable `actorRef` (login) when present; UUID → «—»; no new Security display-name subsystem in Phase 8.
8. **OQ-8:** Item pagination — **RESOLVED:** `ProductionOrderItemsLoader` loads all pages.
9. **OQ-9:** lineQuantity semantics — **RESOLVED (Phase 2/5 vs Release plan).**
10. **DEP-9:** MR Submit history event — **KNOWN NON-BLOCKING GAP** (business flow works; no new history architecture in Phase 8).

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

## POST-ACCEPTANCE PHASE A

Manual-acceptance UI/UX corrections after Stage 7 Production closure. No Material Requirement
backend root-cause fix, no readiness identity investigation, no cancellation actor/reason fix,
no «Вернуть в производство», no Order reimport, no Stage 8.

### Implemented corrections

| Area | Correction |
|------|------------|
| Checkbox leaf | First click `unchecked → checked`; leaf never indeterminate |
| Checkbox parent | Tri-state display only; click: unchecked/indeterminate → select all selectable; checked → clear |
| Non-actionable selection | CANCELLED / RELEASED / `activeProductionQuantity <= 0` → checkbox disabled; parent disabled when no selectable children; invalid selection pruned on authoritative refresh |
| Order Card tabs | Header + actions outside tabs; default **Обзор** (Mode / Materials / History); **Позиции (N)** holds positions table only |
| Order number in tables | LEVEL 1 tree + Material Request / Release quantity tables show bare number (`25096174`); card/dialog messages may keep `Заказ №…` |
| Empty states | Production tables/dialogs use Russian placeholders (`Нет данных`); no `No content in table` |
| Period filter | Added **Весь период** (open/nullable created-at bounds via `OrderWorklistCriteria`) and **Произвольный период…** with С/По DatePickers; validation `from <= to` |
| Period persistence | `ui.production.workbench.v1` / `period` via `UserUiPreferenceService`; custom from/to persisted; quick search not persisted; default 30 days |
| Position search | Matches displayed label (`Поз. 2` / `поз. 2` / `Поз 2`) in addition to raw fields |
| Expanded retention | Expanded order IDs retained across LEVEL 1 → Order Card → LEVEL 1 |
| Card return refresh | `backToTree()` performs authoritative tree reload; filters/selection/expanded retained where still valid |
| Material Request qty | FLEXIBLE local validation `1..requestable` before prepare; Next disabled while invalid; dialog stays open |
| Release error dialog | Long validation messages wrap with sufficient dialog size; no technical IDs |

### Deferred (Phase B / later)

- Valid Material Request submit failure (STANDARD/FLEXIBLE)
- Material Readiness empty deficit / «Материал не найден в справочнике склада»
- Cancellation history actor/reason
- Restore Production
- Order reimport / snapshots

---

## POST-ACCEPTANCE PHASE B2

Material identity + MR / readiness UI corrections after Phase B1 audit. No Material Catalog,
no Color Catalog, no zero-stock SUBMIT redesign, no reservation, no Restore Production, no Order
reimport.

### Implemented corrections

| Area | Correction |
|------|------------|
| UoM identity matching | Comparison-time canonicalization via Warehouse `UnitOfMeasure.equalForKey` exposed as public `UnitOfMeasureKeys.equalForKey` (single Warehouse rule). OM `"шт"` / `" ШТ "` matches Warehouse `"шт."`. Display UoM strings unchanged; no OM/Warehouse UoM migration. |
| Matching location | `DefaultWarehouseReferenceQueryApi.findMaterialReferencesByIdentity` + `MaterialReferenceResolver` via `com.tmp.warehouse.api.UnitOfMeasureKeys`. No new catalog subsystem. |
| Size behavior | Unchanged: size not part of Production identity match. Multiple Warehouse refs with same article+color+canonical UoM → AMBIGUOUS (no arbitrary pick). |
| MR PREPARE | Matching ref + AVAILABLE=0 still creates DRAFT; PREPARE does not query stock. |
| MR unresolved / ambiguous UI | `MaterialRequirementNotReadyException` → business messages (`MATERIALS_UNRESOLVED` / `MATERIALS_AMBIGUOUS`), not technical failure; optional article/color/UoM context. |
| Release readiness UNRESOLVED | Shows identity mismatch wording; **not** «Недостаточно материалов». Details button hidden when no shortage lines. |
| Release readiness SHORTAGE | Unchanged: «Недостаточно материалов для выпуска.» + Подробнее with shortage rows. |
| Order Card | `MATERIAL_REFERENCE_UNRESOLVED` wording aligned; no «справочник материалов/цветов». |
| Zero-stock SUBMIT | **Unchanged** (deferred architectural decision). Existing `MATERIAL_SHORTAGE` presentation kept; DRAFT retained on failed submit. |

### Deferred (still)

- Zero-stock Warehouse demand / SUBMIT semantics (Phase B3+)
- Restore Production
- Order reimport
- Cancellation history actor/reason

---

## 20. CODE CHANGES

Phase A UI/presentation corrections (see POST-ACCEPTANCE PHASE A).
Phase B2 material identity + MR/readiness presentation (see POST-ACCEPTANCE PHASE B2).

## 21. DATABASE

NONE (preferences use existing `UserUiPreferenceService` storage; Phase B2 — no Flyway)

## 22. PACKAGE

After targeted tests — `mvn -pl :tmp-bootstrap-app pre-integration-test -Ppackage -DskipTests`

## 23. STARTUP

Manual retest package

## 24. GIT

No commit. No push.

---

*End of Stage 7 Production UX Design.*
