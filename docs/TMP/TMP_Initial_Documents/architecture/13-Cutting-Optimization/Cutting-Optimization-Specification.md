# Cutting Optimization Specification

**Document ID:** TMP-SPEC-013  
**Status:** Accepted  
**Version:** 1.1

---

# 1. Назначение

Cutting Optimization — Capability TOP Manufacturing Platform (TMP) для создания, хранения, утверждения и сопровождения карт раскроя длинномерных материалов.

Capability не выполняет производственные операции и не управляет оборудованием.

Cutting Optimization отвечает за определение рекомендуемого способа использования исходного длинномерного материала.

**Stage 8 status:** NOT STARTED. Настоящий документ задаёт целевой контракт; реализация не начата.

---

# 2. Ответственность

Cutting Optimization отвечает за:

- создание карт раскроя;
- автоматическую и/или ручную оптимизацию;
- ручную корректировку до утверждения;
- хранение карт;
- утверждение карт;
- экспорт и печать;
- собственный lifecycle карты (без управления со стороны Production);
- предоставление утверждённых карт через Public Query API / Domain Events.

Не отвечает за:

- производственные операции и MES;
- управление оборудованием и сменами;
- складские остатки и резервы;
- изменение Specification;
- управление заказами;
- lifecycle Production.

---

# 3. Основные принципы

1. Cutting Plan — самостоятельный бизнес-документ Capability.
2. Cutting Plan не является частью Specification.
3. Specification никогда не изменяется Cutting Optimization.
4. После утверждения карта **immutable**.
5. **Модели Cutting Plan Revision нет.** Другой расчёт = **новый Cutting Plan** (ADR-034).
6. Одна карта строится для одного материала (артикул + цвет) и явного набора Order Item.
7. Карта необязательна; Production работает без неё по Specification (ADR-024).
8. Карта носит **рекомендательный** характер для Production.
9. Карта может включать позиции одного или нескольких заказов.
10. Связь с заказами/позициями — явная через `Order ID` / `Order Item ID`.
11. Production не создаёт, не редактирует, не пересчитывает карту и не управляет её lifecycle.
12. Межмодульное взаимодействие — Constitution принцип 28.

---

# 4. Бизнес-документ Cutting Plan

```text
Cutting Plan
```

Каждый Cutting Plan — отдельный самостоятельный документ с собственным `Cutting Plan ID`.

Состав (логический):

- материал (артикул + цвет + единица);
- включённые позиции: Order ID, Order Item ID, количество изделий;
- технологические данные раскроя (исходные хлысты, раскладка и т.п. — внутренние объекты Capability);
- статус карты;
- audit metadata.

Внутренние объекты (`Source Bar`, `Cut Piece`, allocation details) принадлежат только Cutting Optimization и не являются частью Production/Warehouse persistence.

---

# 5. Охват заказов и позиций

Карта может включать:

- одну позицию одного заказа;
- несколько позиций одного заказа;
- позиции нескольких разных заказов с совместимым материалом.

Пример:

```text
Cutting Plan CP-1001
Материал: VEKA 103.211 White

Заказ 101 / Позиция 1 — 40 изделий
Заказ 104 / Позиция 2 — 35 изделий
Заказ 108 / Позиция 5 — 25 изделий
```

Связь всегда однозначна через Order ID / Order Item ID. Ссылка на Order Item Revision в модели Cutting Plan **не требуется** для Production-facing контракта; при необходимости внутренней трассировки OM Revision остаётся ответственностью Order Management / будущего Stage 8 detail design, но не создаёт Revision Cutting Plan.

---

# 6. Lifecycle Cutting Plan

Минимальная модель (владелец — Cutting Optimization):

```text
DRAFT
  → APPROVED
  → (опционально ARCHIVE / SUPERSEDED_BY_NEW_PLAN)
```

Правила:

- редактирование допускается только в `DRAFT`;
- после `APPROVED` карта immutable;
- если нужен другой расчёт — создаётся **новый** Cutting Plan;
- статусы `IN_USE` / `COMPLETED`, управляемые событиями Production, **не являются** обязательной частью контракта v1.0;
- Production может хранить ссылку на `Cutting Plan ID`, но не переводит статусы карты.

Детализация дополнительных статусов Stage 8 — ответственность Cutting Optimization и не блокирует Stage 7.

---

# 7. Интеграция с Production

Production:

- может ссылаться на существующий утверждённый Cutting Plan ID;
- использует карту как **рекомендуемый/default** источник плановой потребности длинномерного материала;
- может работать полностью без карты;
- не управляет lifecycle карты;
- не получает и не хранит Cutting Plan Revision.

При отсутствии карты Production использует Specification.

План/факт складского расхода и корректировка перемещений описываются Production Specification v2.0 / ADR-035 и не изменяют Cutting Plan.

---

# 8. Интеграция с Warehouse

Warehouse:

- проверяет наличие и выполняет складские операции;
- не хранит Source Bar / Cut Piece;
- не пересчитывает раскрой.

Cutting Optimization не изменяет Stock Position.

---

# 9. Интеграция с Order Management

Cutting Optimization читает ACTIVE Order / Order Item / current immutable Specification через Public Query API Order Management.

Не изменяет заказы, позиции и спецификации.

---

# 10. Public Query API (целевой)

```text
getCuttingPlan(cuttingPlanId)
findCuttingPlansByOrder(orderId)
findCuttingPlansByOrderItem(orderItemId)
getApprovedCuttingPlanSummary(cuttingPlanId)  // плановая потребность для длинномера
```

Mutating операции — внутренний Application API / документы Cutting Optimization, не межмодульный Public mutating API.

---

# 11. Domain Events (целевой минимум)

| Event | Назначение |
|-------|------------|
| `CuttingPlanApproved` | карта доступна как рекомендация |
| `CuttingPlanArchived` | карта снята с использования (если введён статус) |

События `ProductionLaunchStarted` / `ProductionLaunchCompleted` **не** используются как обязательный драйвер lifecycle карты.

---

# 12. Security (целевой минимум)

| Permission | Назначение |
|------------|------------|
| `cutting.plan.view` | просмотр карт |
| `cutting.plan.create` | создание |
| `cutting.plan.edit` | редактирование DRAFT |
| `cutting.plan.approve` | утверждение |
| `cutting.plan.export` | экспорт/печать |

---

# 13. Invariants

1. Cutting Plan не изменяет Specification.
2. Нет Cutting Plan Revision.
3. Новый расчёт = новый Cutting Plan.
4. Production не изменяет содержимое и статусы карты.
5. Связь с заказами — через Order ID / Order Item ID.
6. Карта необязательна для Production.
7. Warehouse не зависит от внутренних объектов раскроя.

---

# 14. Version limitations

В целевую v1.0 Capability входят карты раскроя, оптимизация, утверждение, экспорт/печать, query API.

Не входят: MES, управление пилами, автоматический забор данных с оборудования, обязательная жёсткая связка lifecycle с Production.

---

# 15. История документа

| Версия | Изменение |
|--------|-----------|
| 1.0 | Исходная модель с Cutting Plan Revision и lifecycle IN_USE/COMPLETED по событиям Production. |
| 1.1 | Согласование с ADR-034: без Revision; новый расчёт = новый документ; multi-order coverage; рекомендательный характер; Production не управляет lifecycle; Stage 8 NOT STARTED. |

---

# Итог

Cutting Optimization владеет Cutting Plan как независимым рекомендательным технологическим документом без Revision-модели и без обязательного управления со стороны Production.
