# TMP Development Status

**Mode:** Autonomous Cursor Agent  
**Project status:** IN_PROGRESS  
**Current Stage:** Stage 5 — Order Management  
**Current Task:** STAGE5-001 (READY, not started)  
**Last completed task:** STAGE5-000-FIX2  
**Active blocker:** None  

---

# Stage Progress

| Stage | Name | Status | Progress |
|---:|---|---|---:|
| 0 | Development Foundation | DONE | 100% |
| 1 | Platform Core | DONE | 100% |
| 2 | Document Engine | DONE | 100% |
| 3 | Capability Engine | DONE | 100% |
| 4 | Security | DONE | 100% |
| 5 | Order Management | IN_PROGRESS (Documentation Gate re-PASSED) | 0% |
| 6–11 | (later) | PLANNED | 0% |

---

**Stage 0–4:** завершены (DONE 100%).

**Stage 5 Start Gate (STAGE5-000):** PASSED (v1.1). Последующая ревизия выявила документационные дефекты; для их исправления Stage 5 был временно заблокирован документационными противоречиями, `STAGE5-001` переведён в `PLANNED`, добавлена задача `STAGE5-000-FIX` (READY).

**Stage 5 Documentation Gate (STAGE5-000-FIX):** re-PASSED — документационная задача. Order Management Specification обновлена до v1.2; введён capability-owned typed document payload (ADR-028), связанный по `DocumentId`, versioned, с optimistic locking и immutability после проведения; подтверждена транзакционная граница Document Engine (processor внутри транзакции проведения; события после commit) — prerequisite Platform/Document Engine не требуется; уточнены Constitution (v1.2, принцип 28) и ADR-003/ADR-004; модель Revision разделена на active/draft, добавлен документ `ORDER_ITEM_REVISION_UPDATE`; введены безопасные правила отмены Stage 5 (запрет отмены approved order и active item, запрет изменения состава approved order); расширен Public Query API (`searchOrders`, списки items/revisions, пагинация 50/100 zero-based); формализованы document lifecycle policy и idempotency (processing record `DocumentId + Operation`); Stage Manifest и Context Map обновлены; очередь Stage 5 полностью пересобрана (`STAGE5-001..050`). Java-код не изменялся.

**Stage 5 Documentation Gate (STAGE5-000-FIX2):** Documentation Gate был повторно открыт для финальных исправлений (на время исправлений `STAGE5-001` переведён в `PLANNED`, добавлена `STAGE5-000-FIX2` как единственная `READY`) и повторно пройден. Итоги: Document Engine Specification → v1.1 (зафиксирован транзакционный контракт lifecycle-операций и публичный `TransactionalEventPublisher`); Order Management переведён на публичный after-commit контракт и **не импортирует** внутренние классы Document Engine; определена физическая модель typed payload (`order_document_payload` + typed-таблицы + `order_item_revision_payload_line`, без JSON/сериализации); исправлена семантика idempotency (`void onPost`; повторный публичный `postDocument` отклоняется lifecycle validation; guard внутри processor завершается как already processed без возврата результата); добавлена prerequisite-задача `STAGE5-017` (public `TransactionalEventPublisher` contract and adapter) до первого Document Processor; синхронизированы версии/номера (Doc Engine v1.1, OM Spec v1.2, CONTEXT-MAP v1.2, очередь `STAGE5-001..050`, GUI smoke `STAGE5-050`). Java-код не изменялся.

**Реализация Stage 5:** ещё не начата. Documentation Gate повторно пройден; Stage 5 готов к началу реализации. Следующая задача — `STAGE5-001`. Только `STAGE5-001` имеет статус `READY`; остальные — `PLANNED`.

**Открытых блокеров Stage 5:** нет.

**Некритичный residual (Stage 4, не блокирует):** `BACKLOG-001` — кодировка пагинации Security Audit Screen.

**Далее:** выполнять `STAGE5-001` по решению пользователя. Stage 6 не стартовать. Git-операции выполняет пользователь самостоятельно.
