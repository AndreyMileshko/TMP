# Production Specification

**Document ID:** TMP-SPEC-012  
**Status:** Accepted  
**Version:** 2.1

---

# 1. Назначение

Production — Capability TOP Manufacturing Platform (TMP), отвечающая за производственное состояние позиций заказа, принятие заказа в производство, обеспечение материалами через Warehouse и фиксацию выпуска изделий.

Production работает с **позицией заказа (Order Item)** как с владельцем производственного состояния и не создаёт отдельную бизнес-сущность Production Order.

Пользовательский сценарий строится вокруг **заказа целиком**: принятие, отмена и общий статус отображаются на уровне заказа, а хранимое состояние остаётся item-owned (ADR-017, ADR-033).

Production не описывает внутреннее выполнение технологических операций и не является MES-системой.

---

# 2. Область ответственности

Production отвечает за:

- хранение производственного статуса и количеств по Order Item;
- вычисляемое представление производственного статуса заказа (Order Production View);
- принятие всего ACTIVE Order в производство;
- отмену производства заказа целиком;
- проверку наличия материалов (пользовательская команда / расчёт);
- формирование редактируемого шаблона перемещения материалов на склад производства;
- инициирование создания Warehouse-owned Transfer через штатный контракт Warehouse;
- подтверждение физического получения материалов в UI Production с фактическим изменением склада только через Warehouse;
- выпуск изделий с фиксацией план/факт расхода материалов;
- фиксацию Production Specification Reference (`Specification ID`) при принятии в производство;
- набор ссылок MaterialReference → Cutting Plan ID (0..N на Order Item);
- Public Query API производственного состояния;
- Domain Events о завершённых изменениях собственного состояния;
- Security permissions бизнес-действий Production;
- аудит значимых пользовательских действий.

Production не отвечает за:

- создание и изменение заказов, позиций и спецификаций;
- владение Order Item как коммерческим объектом;
- Order Item Revision (внутренняя модель Order Management);
- создание, изменение и пересчёт Cutting Plan;
- Material Master и Material Mapping;
- складские остатки, движения и проведение складских документов (владелец — Warehouse);
- MES: маршруты, этапы, операции, участки, рабочие центры, оборудование, смены, брак, переделка, OEE, APS, календарное планирование;
- склад готовой продукции, отгрузку, монтаж, сервис;
- производственную аналитику и KPI.

> **Architecture Rule**  
> Production изменяет только собственное производственное состояние и собственные документы и не изменяет данные других Capability напрямую.

---

# 3. Основные принципы

1. Главной единицей хранимого производственного состояния остаётся Order Item (ADR-017).
2. Пользователь работает с заказом целиком; принятие и отмена — по всему заказу (ADR-033).
3. Отдельный Production Order не создаётся.
4. Общий производственный статус заказа вычисляется и не хранится как отдельный источник истины (ADR-020).
5. Downstream-контракт Production использует `ACTIVE Order` → Order Items → current immutable Specification + stable `Specification ID`; **без** зависимости от Order Item Revision в состоянии и документах Production (ADR-033).
6. Все значимые изменения производственного состояния выполняются через бизнес-документы Production.
7. Проверка материалов — пользовательская команда/расчёт, не бизнес-документ и не prerequisite принятия в производство.
8. Specification — нормативная основа состава изделия; Production не хранит копию содержимого Specification, но после Launch хранит стабильный `Specification ID` (Production Specification Reference).
9. Cutting Plan необязателен и носит рекомендательный характер (ADR-024, ADR-034).
10. При отсутствии Cutting Plan потребность полностью определяется по Specification.
11. Для длинномерных материалов связанный Cutting Plan — рекомендуемый/default источник плановой потребности; мастер может скорректировать шаблон перемещения и фактический расход (ADR-034, ADR-035).
12. Production хранит связи `MaterialReference → Cutting Plan ID` (0..N на Order Item; не одно поле на всю позицию; не Revision Cutting Plan).
13. Production не управляет lifecycle Cutting Plan.
14. Warehouse — единственный владелец Stock Position, Warehouse Movement и Warehouse Operation.
15. Выпуск проводится только вместе с успешным фактическим Warehouse Consumption со склада производства (атомарная orchestration — §21).
16. План и факт расхода могут различаться; Specification и Cutting Plan из-за факта не изменяются (ADR-035).
17. Частичный выпуск количеств/позиций во времени разрешён; частичный запуск отдельных позиций как основной сценарий не используется.
18. Проведённые документы Production immutable; исправление — новым компенсирующим документом.
19. Аналитика не входит в Public Query API Production.
20. Stage 7 v1.0 независим от реализации Stage 8 Cutting Optimization.

---

# 4. Владение данными

Production является владельцем:

- производственного статуса Order Item;
- количеств launched / in-production / released по Order Item;
- документов Production и результатов обработки их строк;
- Production Specification Reference (`Specification ID`) по каждому принятому Order Item;
- набора связей MaterialReference → Cutting Plan ID;
- plan/fact строк расхода материалов в Production Release;
- настроек Production;
- истории изменения собственного производственного состояния.

Production не владеет:

- Customer Order / Order Item / Specification;
- Order Item Revision;
- Cutting Plan и его содержимым;
- складами, ячейками, Stock Position, Warehouse Movement, Warehouse Operation;
- Material Master / Material Mapping;
- аналитическими проекциями.

Доступ к данным других Capability — только через их Public Query API, Domain Events и document-driven операции владельца данных (Constitution принцип 28).

---

# 5. Основной объект и идентификация

## 5.1 Хранимое состояние

Основной объект Production — **производственное состояние Order Item**.

Идентификация состояния:

```text
Order ID
Order Item ID
Specification ID   // Production Specification Reference (opaque)
```

Production **не** хранит и **не** требует `Order Item Revision` / revision number в своём состоянии, документах или Public Query API.

Владелец Order Item, внутренней Revision и содержимого Specification — Order Management.

### Production Specification Reference

При команде **Принять в производство** для каждой позиции Production фиксирует стабильный opaque `Specification ID`, полученный из Order Management Public Query API вместе с current immutable Specification.

Правила:

1. `Specification ID` однозначно адресует конкретную immutable Specification в OM.
2. Production **не копирует** содержимое Specification.
3. После Launch все операции Production по этой позиции (проверка материалов, transfer template, release, plan/fact, audit) используют **зафиксированный** `Specification ID`, а не «текущую» Specification заказа.
4. Если позже в OM появится новая current Specification, уже запущенное Production **не** переключается на неё автоматически.
5. Это не возвращает Order Item Revision в Production contract.

## 5.2 Хранимые атрибуты Order Item Production State

| Атрибут | Описание |
|----------|----------|
| Order ID | Идентификатор заказа |
| Order Item ID | Идентификатор позиции |
| Specification ID | Production Specification Reference (зафиксирован при Launch) |
| Production Status | Текущий производственный статус позиции |
| Ordered Quantity | Количество изделий позиции на момент принятия |
| Launched Quantity | Количество, принятое в производство по позиции |
| Active Production Quantity | Количество в активном производстве |
| Released Quantity | Накопленный выпуск |
| Cutting Plan Links | Набор `MaterialReference → CuttingPlanId` (0..N) |
| Last Material Check At | Время последней проверки материалов |
| Last Status Changed At | Дата и время последнего изменения статуса |

### 5.2.1 Cutting Plan Links (cardinality)

```text
Order Item
  → 0..N  (MaterialReference → CuttingPlanId)

1 Cutting Plan
  → N Order Item
```

Одно поле `Optional Cutting Plan ID` на всю позицию **запрещено**, если у позиции несколько длинномерных материалов.

Связь many-to-many ограничена material identity: для каждого длинномерного материала — не более одной активной ссылки на Cutting Plan в Production state / Launch document.

## 5.3 Order Production View (вычисляемый)

Пользовательский общий статус заказа вычисляется из item states и **не хранится**.

| Order Production View | Правило |
|------------------------|---------|
| Доступен Production | ACTIVE Order ещё не принят в производство |
| В ПРОИЗВОДСТВЕ | Есть хотя бы одна позиция `IN_PRODUCTION` или `PARTIALLY_RELEASED`, и нет полной отмены |
| ИЗГОТОВЛЕН | Все позиции `RELEASED` |
| ОТМЕНЁН | Производство заказа отменено целиком (незавершённые позиции `CANCELLED`; выпущенные остаются `RELEASED`) |

---

# 6. Появление заказа в Production

Order Management создаёт или получает заказ. После достижения штатного downstream-состояния **ACTIVE Order** заказ автоматически доступен Production.

Новый искусственный статус Order Management ради Production **не вводится**.

Production получает:

```text
ACTIVE Order
→ Order Items
→ current immutable Specification
→ Specification ID (stable opaque)
```

После Launch Production продолжает читать содержимое **только** по зафиксированному `Specification ID`.

---

# 7. Производственный lifecycle (пользовательский)

```text
Заказ доступен Production
        ↓
Принять в производство
        ↓
В ПРОИЗВОДСТВЕ
        ↓
проверка материалов / перемещения / изготовление
        ↓
выпуск изделий
        ↓
ИЗГОТОВЛЕН
```

Альтернатива:

```text
В ПРОИЗВОДСТВЕ
        ↓
Отменить производство заказа
        ↓
ОТМЕНЁН
```

Заказ остаётся «В производстве», пока не изготовлены все позиции (или пока производство не отменено).

MES lifecycle не вводится.

---

# 8. Производственные статусы позиции

| Код | UI (пример) | Назначение |
|------|-------------|------------|
| `NOT_STARTED` | Не начато | Производство не начиналось |
| `IN_PRODUCTION` | В производстве | Позиция принята в производство |
| `PARTIALLY_RELEASED` | Частично изготовлено | Выпущена часть количества |
| `RELEASED` | Изготовлено | Выпущено всё количество |
| `CANCELLED` | Отменено | Производство позиции отменено |

`READY_FOR_PRODUCTION` **не** является обязательным persisted status. Проверка материалов не переводит позицию в отдельный статус готовности и не блокирует принятие в производство.

## 8.1 Переходы

```text
NOT_STARTED
      │  Production Launch (весь заказ)
      ▼
IN_PRODUCTION
      │
      ├───────────────┐
      ▼               │
PARTIALLY_RELEASED    │  Production Release
      │               │
      ▼               │
RELEASED              │

NOT_STARTED / IN_PRODUCTION / PARTIALLY_RELEASED
      │  Production Cancellation (весь заказ)
      ▼
CANCELLED

RELEASED не отменяется задним числом при отмене заказа.
```

---

# 9. Документы Production

Document-driven модель сохраняется. Пользователь не обязан вручную работать с техническими Document ID / lifecycle Document Engine.

Типы документов:

| Документ | Назначение |
|----------|------------|
| **Production Launch** | Принятие **всего** заказа в производство |
| **Production Cancellation** | Отмена производства заказа целиком |
| **Production Release** | Фактический выпуск и plan/fact материалов |

Каждому типу соответствует Document Processor. После проведения документ immutable. Исправление — только новым документом.

Отдельный пользовательский сценарий «отмена запуска строки» не вводится: используется единый **Production Cancellation** по заказу.

---

# 10. Production Launch — принять в производство

Команда UI: **Принять в производство**.

Документ содержит строки **всех** производимых Order Item заказа.

Частичный запуск отдельных позиций как основной workflow **не используется**.

## 10.1 Состав строки

- Order ID;
- Order Item ID;
- количество изделий (полное количество позиции на момент принятия);
- `Specification ID` (фиксируется при проведении);
- набор `MaterialReference → CuttingPlanId` (0..N);
- статус обработки строки.

## 10.2 Проверки перед проведением

- заказ `ACTIVE` в Order Management;
- заказ ещё не принят в производство (позиции в `NOT_STARTED`);
- корректность количеств;
- при указании Cutting Plan links — существование карт у Cutting Optimization (если Stage 8 доступен); отсутствие карт не блокирует запуск;
- фиксация Specification ID для каждой строки.

Проверка материалов **не** является prerequisite.

## 10.3 После проведения

- позиции → `IN_PRODUCTION`;
- сохраняется Production Specification Reference (`Specification ID`) на позицию;
- сохраняются Cutting Plan Links (если заданы);
- увеличивается `Launched Quantity` / `Active Production Quantity`;
- публикуется Domain Event `OrderAcceptedIntoProduction` (после commit);
- Order Production View → «В ПРОИЗВОДСТВЕ».

---

# 11. Проверка наличия материалов

Команда UI: **Проверить наличие материалов**.

Доступна для заказа «В производстве». Не создаёт бизнес-документ.

Production:

1. формирует плановую потребность (§12);
2. обращается к Public Query API Warehouse;
3. показывает результат пользователю.

Пользователь видит как минимум:

- материал;
- требуемое количество;
- количество на основном складе;
- количество на складе производства;
- доступное количество;
- дефицит;
- источник плановой потребности: `Specification` или `Cutting Plan`.

Production не хранит и не рассчитывает складские остатки самостоятельно.

---

# 12. Расчёт потребности материалов

Нормативная основа — **зафиксированная** Specification по `Specification ID` (после Launch), а не автоматически сменившаяся current Specification OM.

| Материал | Правило |
|----------|---------|
| Обычные материалы | потребность → Specification |
| Длинномерные при наличии связанного Cutting Plan | по умолчанию данные Cutting Plan (рекомендация) |
| Длинномерные без Cutting Plan | Specification |

Cutting Plan для длинномера — **рекомендуемый источник планирования**, не безусловный источник фактического складского движения.

Пользователь может скорректировать формируемое перемещение и фактический расход. Specification и Cutting Plan при этом не изменяются.

Один и тот же материал в одном расчёте не учитывается одновременно по двум источникам.

Production не использует внутренние объекты Cutting Optimization (`Source Bar`, `Cut Piece` и т.п.) — только итоговую плановую потребность карты (если карта доступна).

---

# 13. Создание перемещения материалов

Команда UI: **Создать перемещение материалов**.

Старая модель пассивной `generateMaterialTransferRecommendation()` как внешнего Public API **заменена**.

## 13.1 Шаблон

Production формирует **предзаполненный редактируемый шаблон**:

- материалы;
- рекомендуемые количества;
- источник расчёта (`Specification` / `Cutting Plan`);
- данные Cutting Plan для длинномеров, если карта используется;
- привязка к Order ID / Order Item ID по необходимости аудита.

Мастер:

1. проверяет шаблон;
2. при необходимости корректирует количества;
3. подтверждает **Создать перемещение**.

## 13.2 Владение

После подтверждения создаётся **настоящий Warehouse-owned business document** (Transfer) через штатный контракт Warehouse.

Production:

- не становится владельцем складского документа;
- не изменяет Stock Position;
- не проводит складской документ самостоятельно как владелец данных.

Создание и проведение складского документа выполняет Warehouse (document-driven модель Stage 6).

Для прав на фактическое создание Transfer по возможности используются существующие Warehouse permissions (`warehouse.transfer.create`), без дублирующего technical permission на каждый внутренний шаг.

---

# 14. Передача на склад производства

Используется существующая двухэтапная модель Warehouse:

```text
основной склад
→ отправка (Transfer send)
→ IN_TRANSIT
→ получение (Transfer receive)
→ склад производства
```

Пользовательский сценарий:

```text
Production: Создать перемещение
        ↓
Warehouse: обрабатывает и физически выдаёт материал
        ↓
Production: физически получает материал
        ↓
Подтвердить получение (UI Production)
        ↓
Warehouse: фиксирует получение (receive)
        ↓
материал на складе производства
```

Команда **Подтвердить получение** может находиться в UI Production, но фактическое изменение складского состояния выполняет только Warehouse.

Вторая конкурирующая складская модель внутри Production **запрещена**.

---

# 15. Production Release — выпуск изделий

Команда UI: **Выпустить изделия**.

Выпуск возможен только для заказа «В производстве».

Перед выпуском Production:

1. определяет нормативный (плановый) расход;
2. показывает мастеру плановое списание;
3. позволяет указать **фактический** расход;
4. запрашивает Warehouse на списание фактического количества **со склада производства**.

## 15.1 Plan / Fact (обязательно)

Specification и Cutting Plan — нормативная/плановая информация.

Фактический расход может быть:

```text
план > факт
план = факт
план < факт
```

Production Release хранит по каждой строке материала:

- плановое количество;
- фактически списанное количество;
- отклонение;
- при необходимости комментарий/причину.

Ни Specification, ни Cutting Plan из-за отклонения не изменяются.

Warehouse списывает **подтверждённое фактическое** количество.

## 15.2 Недостаток материала

Если на складе производства недостаточно материала для фактического списания:

```text
Production Release НЕ проводится
```

Сообщение пользователю содержит: материал, требуется, имеется, дефицит.

Доступные действия:

1. создать дополнительное перемещение;
2. скорректировать фактический расход, если он действительно отличается от плана.

Запрещено:

- изменять Specification для обхода ошибки;
- создавать отрицательные складские остатки.

## 15.3 После успешного проведения

- уменьшается `Active Production Quantity`;
- увеличивается `Released Quantity`;
- статус позиции → `PARTIALLY_RELEASED` или `RELEASED`;
- при полном выпуске всех позиций Order Production View → «ИЗГОТОВЛЕН»;
- публикуется `ProductionReleased` (после commit).

Повторный Release разрешён, если позиции/количества выпускаются в разное время.

Production **не** изменяет lifecycle Order Management.

---

# 16. Production Cancellation — отмена производства заказа

Команда UI: **Отменить производство заказа**.

Отмена выполняется **по заказу целиком**. Пользователь не обязан вручную отменять строки/позиции.

Правила:

- незавершённые позиции → `CANCELLED`;
- уже выпущенные изделия (`RELEASED`) не отменяются задним числом;
- проведённые Production/Warehouse documents не изменяются;
- история сохраняется;
- материалы на складе производства автоматически не удаляются и не списываются;
- автоматический возврат на основной склад без отдельного пользовательского складского решения **не выполняется**.

Внутри система выполняет необходимые компенсирующие Production actions через проведение **Production Cancellation**.

---

# 17. Cutting Plan integration

Cutting Plan — самостоятельный документ Cutting Optimization (ADR-034). Подробная функциональность Stage 8 сохранена в Cutting Optimization Specification.

Правила для Production:

1. рекомендательный характер;
2. не обязателен для работы Production;
3. без карты — работа полностью по зафиксированной Specification;
4. Production не создаёт / не редактирует / не пересчитывает карту;
5. хранит **набор** связей `MaterialReference → CuttingPlanId` (0..N на Order Item);
6. один Cutting Plan может охватывать несколько Order / Order Item;
7. одна Order Item может ссылаться на несколько Cutting Plan (разные длинномерные материалы);
8. связь трассируема через Order ID / Order Item ID / MaterialReference;
9. **нет** модели Cutting Plan Revision;
10. Production **не** управляет lifecycle карты.

Stage 8 может определить собственные статусы Cutting Plan независимо.

---

# 18. Public Query API и Application API

Согласовано с Constitution принцип 28 и ADR-003 / ADR-004.

## 18.1 Public Query API (межмодульный, read-only)

```text
getOrderProductionView(orderId)
getItemProductionState(orderItemId)
getMaterialAvailabilityResult(orderId)   // последний/текущий результат проверки (если сохранён как projection)
listProductionHistory(orderId)
```

Mutating methods **не** входят во внешний межмодульный Public API.

## 18.2 Внутренний Application API (use cases / Document Processors)

```text
acceptOrderIntoProduction(orderId)          // → Production Launch
checkMaterialAvailability(orderId)          // команда/расчёт
prepareMaterialTransferTemplate(orderId)
confirmMaterialTransferCreate(template)     // → Warehouse Application/Document: Transfer
confirmMaterialReceipt(transferRef)         // → Warehouse Application/Document: receive
releaseProducts(request)                    // → Production Release (+ Warehouse-owned Consumption)
cancelOrderProduction(orderId)              // → Production Cancellation
```

Эти операции доступны UI Production / внутренним сервисам и выполняются через бизнес-документы владельцев данных. Они **не** являются Public mutating API для других Capability.

---

# 19. Domain Events

Production публикует только завершённые бизнес-события после commit.

| Event | Когда |
|-------|--------|
| `OrderAcceptedIntoProduction` | после Production Launch |
| `ProductionReleased` | после Production Release |
| `OrderProductionCancelled` | после Production Cancellation |

События `ProductionLaunchStarted` / `ProductionLaunchCompleted` как обязательный механизм управления lifecycle Cutting Plan **не используются** в Production v2.1.

## 19.1 Подписки

Production может подписаться на:

- события доступности ACTIVE Order (при необходимости синхронизации списков);
- `WarehouseDocumentPosted` / связанные события Warehouse (информационно);
- при наличии Stage 8 — события о доступных Cutting Plan (информационно).

Production **не** обязан подписываться на `Order Item Revision Approved` для поддержания собственного состояния: связь состояния — по `Order Item ID` + зафиксированный `Specification ID`; содержимое Specification читается через OM Query **по Specification ID**, а не по «текущей» Specification.

Ни один внешний Capability не изменяет документы Production напрямую.

---

# 20. Security

Permissions уровня бизнес-действий:

| Permission | Назначение |
|------------|------------|
| `production.view` | Просмотр Production / истории |
| `production.accept` | Принять заказ в производство |
| `production.check_materials` | Проверить наличие материалов |
| `production.create_transfer` | Инициировать создание перемещения из Production (фактический Warehouse document — с учётом Warehouse permissions) |
| `production.confirm_receipt` | Подтвердить получение (инициирует Warehouse receive) |
| `production.release` | Выпустить изделия |
| `production.cancel` | Отменить производство заказа |

Технические permissions на внутренние шаги одной пользовательской операции не плодятся.

Для Warehouse-owned операций предпочтительно переиспользовать:

- `warehouse.transfer.create`;
- `warehouse.consumption.create`;
- соответствующие receive/send permissions Stage 6.

---

# 21. Атомарность Release ↔ Consumption

Document-driven invariants:

- Production business document проводится целиком или не проводится;
- запрещены состояния «материал списан, но Release не зафиксирован» и «Release проведён, но Consumption не выполнен».

### Требуемая orchestration-модель

Проведение Production Release и связанного Warehouse Consumption должно выполняться в **одной согласованной транзакционной границе**, в которой:

1. создаётся/проводится Warehouse-owned Consumption (фактические количества, склад производства);
2. проводится Production Release с plan/fact;
3. при любой ошибке откатываются оба изменения;
4. Domain Events публикуются только after-commit.

Взаимодействие остаётся document-driven: Production не пишет в таблицы Warehouse; Warehouse остаётся владельцем Stock Position.

### Start Gate

Публичный контракт Document Engine фиксирует атомарность **одной** lifecycle-операции документа и его processor. Явной платформенной гарантии атомарного проведения **двух** документов разных Capability в одной TX в текущей DE Spec **нет**.

Поэтому до начала реализации Stage 7 требуется Start Gate решение:

- подтвердить, что orchestration через один outer transaction + `DocumentEngine.post` с `Propagation.REQUIRED` обеспечивает атомарность обоих документов; **или**
- принять отдельный ADR / расширение Document Engine для multi-document orchestration.

До закрытия этого пункта реализация mutating Stage 7 **не начинается**. См. `BLOCKERS.md` → `BLK-STAGE7-RELEASE-CONSUMPTION-ATOMICITY`.

---

# 22. Audit

Автоматически аудитируются:

- принятие заказа в производство;
- проверка материалов;
- создание складского перемещения из Production;
- подтверждение получения;
- выпуск;
- отклонения plan/fact;
- отмена производства.

Дополнительных действий пользователя ради аудита не требуется.

Не аудитируются промежуточные вычисления и внутренние сервисные шаги без бизнес-значения.

---

# 23. UI / рабочее место Production

Одно основное рабочее место. Основной объект UI — **Заказ**.

Показывать:

- номер заказа;
- клиента;
- позиции/изделия;
- общий производственный статус (Order Production View);
- состояние материалов;
- наличие связанных Cutting Plan (по материалам);
- краткую историю действий.

Команды:

```text
Принять в производство
Проверить наличие материалов
Создать перемещение материалов
Подтвердить получение
Выпустить изделия
Отменить производство заказа
```

В будущем (Stage 8):

```text
Создать / открыть карту раскроя
```

Не перегружать пользователя: Document ID, Domain Events, Processing Records, техническим lifecycle DE, производственными партиями как отдельной сущностью UI, маршрутами и MES-операциями. Документы создаются системой внутри.

---

# 24. Invariants

1. Production не изменяет данные Order Management.
2. Production не изменяет Specification.
3. Production state идентифицируется `Order ID` + `Order Item ID` + `Specification ID` без Order Item Revision.
4. Order-level production status не хранится как источник истины.
5. Production не изменяет Cutting Plan и не управляет его lifecycle.
6. При отсутствии Cutting Plan потребность — по зафиксированной Specification; Cutting Plan links — 0..N по MaterialReference.
7. Проведённые документы Production immutable.
8. Active production quantity и released quantity не отрицательны; released не превышает ordered.
9. Warehouse никогда не изменяет производственное состояние Production.
10. Списание при выпуске — только со склада производства и только фактическим количеством.
11. Отрицательные складские остатки запрещены.
12. Изменения собственного состояния — только через собственные документы / разрешённую orchestration с документами владельцев данных.

---

# 25. Architecture Rules

## Rule 1

Production — единственный владелец собственного производственного состояния.

## Rule 2

Межмодульное взаимодействие: изменения — через бизнес-документы владельца; чтение — Public Query API; уведомления — Domain Events.

## Rule 3

Warehouse — единственный владелец складских остатков, движений и складских документов.

## Rule 4

Cutting Optimization — единственный владелец Cutting Plan. Production может хранить только набор `MaterialReference → Cutting Plan ID`.

## Rule 5

Order Management — владелец Order / Order Item / Revision / Specification. Production-facing read contract скрывает Revision как обязательную часть состояния Production.

## Rule 6

Нет MES в Stage 7 v1.0.

## Rule 7

Нет отдельного Production Order и нет Material Master.

---

# 26. Version 1.0 Limitations

Входят:

- whole-order accept / cancel;
- item-owned state + computed order view;
- material availability check;
- editable transfer template → Warehouse Transfer;
- receive confirmation via Warehouse;
- Production Release + plan/fact;
- optional Cutting Plan reference (без зависимости от реализации Stage 8);
- Security + Audit + simple workbench UI;
- integration with Order Management и Warehouse.

Не входят:

- MES и всё перечисленное в §2;
- обязательная интеграция runtime Stage 8;
- склад готовой продукции;
- автоматический возврат материалов при отмене;
- частичный запуск отдельных позиций как основной UX;
- управление lifecycle Cutting Plan из Production.

---

# 27. История документа

| Версия | Изменение |
|--------|-----------|
| 1.1 | Предыдущая модель: item/revision-centric launch, READY_FOR_PRODUCTION, партии, Revision Cutting Plan, lifecycle-события Cutting, mutating Public API. |
| 2.0 | Новая согласованная модель Stage 7: order-level UX + item-owned state; без Order Item Revision в Production contract; Cutting Plan как рекомендация без Revision; editable transfer + plan/fact; Public Query vs Application API; Start Gate атомарности Release/Consumption (ADR-033…035). |
| 2.1 | Corrective pass: Production Specification Reference (`Specification ID`); Cutting Plan links 0..N by MaterialReference; restored detailed Cutting Spec alignment; Warehouse Query vs Document commands. |

---

# Итог

Production управляет производственным состоянием позиций и простым order-level workflow пользователя, взаимодействуя с Order Management и Warehouse только через утверждённые контракты и не являясь MES.
