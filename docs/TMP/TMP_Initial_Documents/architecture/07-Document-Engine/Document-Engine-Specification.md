# Document Engine Specification

**Document ID:** TMP-SPEC-007 **Status:** Accepted **Version:** 1.2

------------------------------------------------------------------------

# Назначение

Document Engine является инфраструктурным компонентом платформы,
управляющим жизненным циклом документов.

Document Engine не содержит предметной бизнес-логики.

------------------------------------------------------------------------

# Ответственность

Document Engine отвечает за:

-   создание документов;
-   изменение статуса документов;
-   проведение документа;
-   отмену проведения;
-   удаление документа (при допустимости);
-   вызов зарегистрированного Document Processor;
-   ведение журнала жизненного цикла документа.

------------------------------------------------------------------------

# Что не входит в Document Engine

Document Engine не знает:

-   складской учет;
-   производство;
-   снабжение;
-   аналитику;
-   бизнес-правила документов.

------------------------------------------------------------------------

# Архитектура

``` text
Document
    │
    ▼
Document Engine
    │
    ▼
Document Processor
    │
    ▼
Business Module
```

------------------------------------------------------------------------

# Компоненты

## Document

Хранит данные документа и его состояние.

Не содержит бизнес-логики.

## Document Engine

Управляет жизненным циклом документа и вызывает соответствующий Document
Processor.

## Document Processor

Реализует бизнес-логику конкретного типа документа.

Для каждого типа документа допускается только один зарегистрированный
Document Processor.

------------------------------------------------------------------------

# Жизненный цикл

-   Создание
-   Редактирование
-   Проведение
-   Отмена проведения
-   Закрытие (при необходимости)

------------------------------------------------------------------------

# Правила

1.  Документ не изменяет бизнес-объекты самостоятельно.
2.  Все изменения выполняются через Document Processor.
3.  Один тип документа --- один Document Processor.
4.  Document Engine не зависит от бизнес-модулей.
5.  Business Module предоставляет Document Processor.

------------------------------------------------------------------------

# Транзакционный контракт

Транзакционное поведение lifecycle-операций является частью публичного контракта Document Engine.

1.  `DocumentOperationContext` предоставляет `DocumentId` (`context.document().id()`), доступный во всех hook-ах Document Processor, включая `onPost`.
2.  Document Engine владеет metadata и lifecycle документа; Capability владеет typed business payload и загружает его в Document Processor по `DocumentId`.
3.  `DocumentProcessor` вызывается **внутри** транзакции соответствующей lifecycle-операции (create/post/unpost/close/delete).
4.  Ошибка Document Processor откатывает в одной транзакционной границе: изменения Capability, metadata документа и записи lifecycle journal. Частичное применение исключено.
5.  Изменения аудита lifecycle (journal) и изменения статуса документа согласованы с работой processor в той же транзакции.
6.  События, которые должны публиковаться после успешного commit, публикуются через публичный after-commit механизм (см. ниже), а не напрямую внутри транзакции.

------------------------------------------------------------------------

# Multi-document / ambient transaction contract

Публичный lifecycle contract Document Engine использует **REQUIRED** semantics.

1. Lifecycle operation без внешней совместимой транзакции открывает собственную транзакцию.
2. Lifecycle operation внутри существующей совместимой транзакции присоединяется к ней и не открывает независимую транзакцию.
3. REQUIRED является обязательным semantics публичного lifecycle contract (create/post/unpost/close/delete). Lifecycle operations не используют REQUIRES_NEW для проведения документа или выполнения processor.
4. Document Processor выполняется внутри той же транзакции, к которой присоединилась (или которую открыла) lifecycle operation.
5. Несколько document lifecycle operations могут быть атомарно скоординированы внешним application use case одной общей ACID-транзакцией, если участники используют один совместимый transaction manager / database transaction boundary.
6. Document Engine не знает бизнес-смысла orchestration и не предоставляет business-specific orchestrator.
7. Rollback внешней транзакции откатывает все присоединившиеся изменения: document metadata, lifecycle journal и capability-owned изменения, выполненные processor-ами внутри этой транзакции.
8. After-commit publication привязана к финальному outer commit: события публикуются только после успешного commit внешней транзакции и не публикуются после rollback.

Данный контракт не гарантирует атомарность разных баз данных, внешних сервисов, message brokers или удалённых HTTP services. Distributed transaction / 2PC / Saga не входят в Document Engine.

См. ADR-036.

------------------------------------------------------------------------

# Публичный after-commit механизм

Публикация доменных событий после успешного commit выполняется через публичный контракт платформы:

```java
public interface TransactionalEventPublisher {
    void publishAfterCommit(DomainEvent event);
}
```

Правила:

1.  Контракт `TransactionalEventPublisher` является публичным API платформы/Document Engine и доступен бизнес-модулям (Capability).
2.  Реализация основана на Spring transaction synchronization: событие доставляется только после успешного commit; при rollback событие не публикуется.
3.  Capability публикуют свои доменные события только через этот публичный контракт и **не импортируют** внутренние классы Document Engine.
4.  Доставка after-commit является best-effort относительно уже зафиксированной операции: сбой подписчика после commit не откатывает уже проведённую lifecycle-операцию.

------------------------------------------------------------------------

# История документа

| Версия | Изменение |
| --- | --- |
| 1.0 | Базовая спецификация Document Engine: назначение, ответственность, компоненты, жизненный цикл, правила. |
| 1.1 | Зафиксирован транзакционный контракт lifecycle-операций (`DocumentId` в operation context; вызов Document Processor внутри транзакции; атомарный откат изменений Capability, metadata и lifecycle journal при ошибке processor; загрузка capability-owned payload по `DocumentId`; владение metadata/lifecycle против typed business payload) и публичный after-commit контракт `TransactionalEventPublisher`. |
| 1.2 | Зафиксирован multi-document / ambient transaction contract: REQUIRED joins существующую совместимую TX; несколько lifecycle operations могут быть атомарно скоординированы внешним application use case; Document Engine не знает смысла orchestration; rollback внешней TX откатывает все присоединившиеся document/capability changes; after-commit привязан к outer commit (ADR-036). |

------------------------------------------------------------------------

# Связанные документы

-   TMP Constitution
-   TMP Architecture Overview
-   Platform Core Specification
-   TMP Architecture Decisions
-   ADR-036
