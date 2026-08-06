# Stage 6 Manifest — Warehouse

**Stage:** 6 — Warehouse  
**Primary specification:** `docs/TMP/TMP_Initial_Documents/architecture/11-Warehouse/Warehouse-Specification.md` (v1.2)  
**ADR:** `TMP-Architecture-Decisions.md` (v1.10; **ADR-032** Material Responsibility — No Separate Material Master)  
**Status:** **NOT STARTED** (Start Gate documentation complete 2026-08-06; **code not started**)

---

## 1. Stage 6 Start Gate — Material Handling Decision (2026-08-06)

### Принятое решение (финальное)

- В TMP **отсутствует отдельный Material Master**.
- Order Management определяет материалы **в контексте Item Specification** (ACTIVE Revision).
- Warehouse хранит только **складское состояние** материалов; не изменяет спецификацию.
- **Material Mapping** — пользовательское сопоставление внешних наименований (СуперОкна) с нормализованными значениями TMP; отсутствие mapping не блокирует импорт; Warehouse не выполняет mapping.

### Схема взаимодействия

```text
Order Management
        |
        v
Specification (ACTIVE)
        |
        v
Production  (расчёт потребности; Material Mapping)
        |
        v
Warehouse   (остатки, доступность, резервирование)
```

---

## 2. Source documents

Warehouse Specification (v1.2), Order Management Specification (v1.8, §28), ADR-032, Order public contracts, Document Engine API, Database Specification.

---

## 3. Stage 6 readiness (Order side — PASS)

Warehouse получает из ACTIVE Specification (через Production) **без отдельного каталога материалов**:

| Поле | Источник |
|------|----------|
| `materialCode` | ACTIVE Specification line |
| `materialName` | ACTIVE Specification line |
| `color` | ACTIVE Specification line |
| `unitOfMeasure` | ACTIVE Specification line |
| `lengthMm` | ACTIVE Specification line |
| `lineQuantity` | ACTIVE Specification line (норма на 1 изделие) |

Warehouse **не требует** Material Master.

---

## 4. Planning domains

1. warehouse and location model;
2. **MaterialReference** identity contracts (из Specification, не Material Master);
3. balances;
4. receipt;
5. movement;
6. reservation rules;
7. issue/consumption rules;
8. immutable correction operations;
9. production warehouse rules;
10. persistence and migrations;
11. document types and posting handlers;
12. UI;
13. cross-module integration tests (Production → Warehouse).

> Material Mapping — отдельная planning domain (не Warehouse); scope уточняется при декомпозиции Stage 6+.

---

## 5. Architectural boundaries

**Warehouse owns:**

- склады, ячейки хранения;
- Stock Position, Stock State;
- Batch (складская часть);
- Warehouse Operation, Warehouse Movement;
- резервирование;
- складские документы.

**Warehouse does NOT own:**

- Item Specification / материалы как master-данные;
- Material Master / каталог материалов;
- Material Mapping;
- расчёт производственной потребности.

---

## 6. Exit criteria

Warehouse owns and changes only warehouse state, with auditable document-driven operations and specified stock rules. Material identity — MaterialReference from Specification; no Material Master.

---

## 7. Start conditions

- Stage 5 = DONE ✓
- ADR-032 accepted ✓
- Warehouse Specification v1.2 aligned ✓
- **Explicit user decision to begin Stage 6 implementation** (code) — pending
