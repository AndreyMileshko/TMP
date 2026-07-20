# TMP — Discussion Checklist

**Document ID:** TMP-CHK-001  
**Status:** Active  
**Version:** 2.0

---

# Назначение

Данный документ является единственным мастер-чек-листом подготовки проекта до начала production-разработки.

Он используется исключительно во время обсуждения архитектуры и подготовки проекта.

После начала реализации основными документами становятся Master Implementation Plan и Master Implementation Checklist.

---

# Правила работы

1. Обсуждение ведётся строго последовательно.
2. Новый раздел начинается только после завершения предыдущего.
3. Пункт отмечается выполненным только после окончательного согласования.
4. Любое изменение архитектуры может повторно открыть ранее закрытый пункт.
5. Cursor AI не использует данный документ во время реализации.
6. Все принятые решения обязательно отражаются в соответствующих спецификациях.
7. После утверждения документа повторное обсуждение допускается только при наличии архитектурной причины.

---

# Общий прогресс подготовки

| Раздел | Статус |
|---------|---------|
| Архитектура | ✅ |
| Технологический стек | ✅ |
| Общая структура проекта | ✅ |
| Platform Core | ✅ |
| Capability Engine | ✅ |
| Document Engine | ✅ |
| Security | ✅ |
| UI/UX | ✅ |
| Cursor AI Guide | ✅ |
| Cutting Optimization | ✅ |
| Development Guide | ✅ |
| Database Specification | ✅ |
| Code Quality Standards | ✅ |
| Development Foundation | ⏳ |
| Начало разработки | ⏳ |

---

# Блок 1. Архитектурные решения

## Общая архитектура

- [x] Определена архитектура системы.
- [x] Принят Modular Monolith.
- [x] Platform Core выделен отдельно.
- [x] Domain Capability выделены отдельно.
- [x] Используется единое Desktop Application.
- [x] HTTP между внутренними модулями запрещён.
- [x] Используются Java API и события.
- [x] Domain изолирован от инфраструктуры.
- [x] Архитектурные решения оформлены через ADR.

---

## Архитектурные документы

- [x] Architecture Overview
- [x] Architecture Decisions (ADR)
- [x] Platform Core
- [x] Capability Engine
- [x] Document Engine
- [x] Security Specification
- [x] UI/UX Specification

---

# Блок 2. Технологический стек

## Backend

- [x] Java 21 LTS
- [x] Maven
- [x] Spring Boot
- [x] PostgreSQL
- [x] Flyway
- [x] Testcontainers

## Desktop

- [x] JavaFX 21
- [x] FXML
- [x] CSS
- [x] JavaFX Bootstrap
- [x] Spring Bootstrap

## Deployment

- [x] jlink
- [x] jpackage
- [x] Встроенный JRE

---

# Блок 3. Структура проекта

## Репозиторий

- [x] Один Git Repository
- [x] Один Maven Reactor
- [x] Модульная структура

## Правила модулей

- [x] Domain независим
- [x] Domain не использует Spring
- [x] Domain не использует JavaFX
- [x] Domain не использует JPA
- [x] Infrastructure зависит от Domain
- [x] UI зависит только от Application API

---

## Структура модулей

- [ ] Утвердить окончательный Maven Reactor
- [ ] Утвердить зависимости между модулями
- [ ] Утвердить структуру пакетов
- [ ] Утвердить naming convention модулей

---

# Блок 4. Документация проекта

## Архитектурные документы

- [x] Constitution
- [x] Architecture Overview
- [x] Architecture Decisions
- [x] Platform Core
- [x] Capability Engine
- [x] Document Engine
- [x] Security Specification
- [x] UI/UX Specification

## Документы подготовки разработки

- [x] Cursor AI Guide
- [x] Development Guide
- [x] Database Specification
- [x] Code Quality Standards
- [x] Master Implementation Plan
- [ ] Master Implementation Checklist
# Блок 5. Правила работы Cursor AI

## Общие правила

- [x] Каждая задача должна быть небольшой и проверяемой.
- [x] Один чат Cursor = одна задача.
- [x] Контекст каждой задачи должен быть минимальным.
- [x] Cursor запрещено анализировать весь проект без разрешения.
- [x] Cursor запрещено самостоятельно изменять архитектуру.
- [x] Cursor запрещено изменять файлы вне Scope задачи.
- [x] Cursor запрещено реализовывать соседние этапы "заодно".
- [x] Cursor запрещено добавлять зависимости без согласования.

---

## Перед написанием кода

- [x] Выполняется Question Gate.
- [x] Все блокирующие вопросы должны быть заданы заранее.
- [x] До получения ответов код не пишется.
- [x] При необходимости план реализации корректируется до начала работы.

---

## После завершения задачи

- [x] Обновляется чек-лист этапа.
- [x] Обновляется журнал реализации.
- [x] Обновляется документация.
- [x] Выполняются проверки.
- [x] Только после этого задача считается завершённой.

---

# Блок 6. Security

## Архитектура

- [x] Security является платформенным модулем.
- [x] Security не содержит бизнес-логики.
- [x] Capability самостоятельно объявляют свои Permissions.
- [x] Проверка доступа централизована.

---

## Пользователи

- [x] Локальные пользователи PostgreSQL.
- [x] Самостоятельная регистрация запрещена.
- [x] Первый администратор создаётся вручную.
- [x] Пользователи управляются администраторами.

---

## Авторизация

- [x] Используются роли.
- [x] Используются Permissions.
- [x] Используются Capability.
- [x] Проверка доступа обязательна.

---

## Безопасность

- [x] Пароли хранятся только в виде хеша.
- [x] Используется BCrypt.
- [x] LDAP отсутствует.
- [x] Active Directory отсутствует.
- [x] OAuth отсутствует.
- [x] 2FA отсутствует.
- [x] Email Recovery отсутствует.

---

## Аудит

- [x] Логируются успешные входы.
- [x] Логируются неуспешные входы.
- [x] Логируются операции безопасности.

---

# Блок 7. JavaFX Desktop

## Общие требования

- [x] Используется JavaFX.
- [x] Используется FXML.
- [x] Используется CSS.
- [x] Bootstrap отделён от Spring Boot.

---

## Архитектура UI

- [x] FXML отвечает только за View.
- [x] Controller не содержит бизнес-логики.
- [x] Application Service вызывается через ViewModel.
- [x] Навигация централизована.

---

## Пользовательский интерфейс

- [x] Определена структура главного окна.
- [x] Определена модель навигации.
- [x] Определены технические экраны.
- [x] Определена обработка ошибок.
- [x] Определены уведомления.
- [x] Определены правила долгих операций.
- [x] Определён жизненный цикл экранов.

---

# Блок 8. Документы подготовки разработки

## Готовы

- [x] Architecture Overview
- [x] Architecture Decisions
- [x] Platform Core
- [x] Capability Engine
- [x] Document Engine
- [x] Security Specification
- [x] UI/UX Specification
- [x] Cursor AI Guide
- [x] Database Specification
- [x] Development Guide
- [x] Code Quality Standards
- [x] Master Implementation Plan

---

## Не начаты

- [ ] Master Implementation Checklist
- [ ] Development Foundation Plan
- [ ] Development Foundation Checklist
- [ ] Development Foundation Prompts
- [ ] Development Foundation Implementation Log
# Блок 9. Этапы реализации

## Этап 0. Development Foundation

- [ ] Создан Maven Reactor.
- [ ] Настроены все модули проекта.
- [ ] Настроен Spring Boot.
- [ ] Настроен JavaFX Bootstrap.
- [ ] Настроен Flyway.
- [ ] Настроен PostgreSQL.
- [ ] Настроен Testcontainers.
- [ ] Настроен jlink.
- [ ] Настроен jpackage.
- [ ] Первый запуск Desktop Application выполнен.

---

## Этап 1. Platform Core

- [ ] Реализован Platform Core.
- [ ] Реализована регистрация Capability.
- [ ] Реализована система событий.
- [ ] Реализованы сервисы ядра.
- [ ] Видимый результат в UI.

---

## Этап 2. Document Engine

- [ ] Реализован Document Engine.
- [ ] Реализовано хранение документов.
- [ ] Реализована регистрация типов документов.
- [ ] Видимый результат в UI.

---

## Этап 3. Capability Engine

- [ ] Реализован Capability Engine.
- [ ] Реализена регистрация модулей.
- [ ] Реализована загрузка Capability.
- [ ] Видимый результат в UI.

---

## Этап 4. Security

- [ ] Реализованы пользователи.
- [ ] Реализованы роли.
- [ ] Реализованы Permissions.
- [ ] Реализован Login.
- [ ] Реализован аудит.
- [ ] Видимый результат в UI.

---

## Этап 5. Order Management

- [ ] Реализован модуль.
- [ ] Видимый результат в UI.

---

## Этап 6. Warehouse

- [ ] Реализован модуль.
- [ ] Видимый результат в UI.

---

## Этап 7. Production

- [ ] Реализован модуль.
- [ ] Видимый результат в UI.

---

## Этап 8. Cutting Optimization

- [ ] Реализован модуль.
- [ ] Видимый результат в UI.

---

# Блок 10. Code Quality Standards

## Документ

- [x] Подготовлен Code Quality Standards.
- [x] Документ утверждён.

---

## Общие правила

- [x] Правила именования.
- [x] Правила структуры пакетов.
- [ ] Правила JavaDoc.
- [ ] Правила использования Optional.
- [ ] Правила immutability.
- [ ] Правила использования record.
- [ ] Правила использования sealed.
- [ ] Правила использования final.

---

## Domain

- [x] Aggregate Root.
- [x] Entity.
- [x] Value Object.
- [x] Domain Service.
- [x] Domain Event.
- [x] Domain Exception.
- [x] Инварианты.

---

## Application

- [x] Commands.
- [x] Queries.
- [x] Application Service.
- [x] DTO.
- [x] Transaction Boundary.

---

## Infrastructure

- [x] Repository.
- [x] Persistence Adapter.
- [x] Spring Configuration.
- [x] Flyway.
- [x] External Adapter.

---

## UI

- [x] Controller.
- [x] ViewModel.
- [x] FXML.
- [x] CSS.
- [ ] Validation.
- [x] Background Tasks.

---

## Тестирование

- [x] Unit Tests.
- [x] Integration Tests.
- [x] Architecture Tests.
- [x] JavaFX Tests.
- [ ] Regression Tests.

---

## Maven

- [x] dependencyManagement.
- [x] Plugins.
- [x] Проверка зависимостей.
- [ ] Проверка уязвимостей.

---

## Git

- [ ] Commit Message.
- [ ] Branch Strategy.
- [x] Commit Rules.

---

## Definition of Done

- [x] Архитектура соблюдена.
- [x] Код отформатирован.
- [x] Статический анализ успешен.
- [x] Все тесты успешны.
- [x] Maven Verify успешен.
- [x] Документация обновлена.
- [x] Чек-листы обновлены.
- [x] Журнал реализации обновлён.
# Блок 11. Готовность к началу разработки

## Development Foundation

- [ ] Утверждён Development Guide.
- [x] Утверждён Database Specification.
- [x] Утверждён Code Quality Standards.
- [x] Подготовлен Master Implementation Plan.
- [ ] Подготовлен Master Implementation Checklist.
- [ ] Подготовлены документы Development Foundation.
- [ ] Подготовлены чек-листы Development Foundation.
- [ ] Подготовлены стартовые промпты Cursor.
- [ ] Подготовлен Implementation Log.

---

## Техническая готовность

- [ ] Установлена Java 21.
- [ ] Установлен Maven.
- [ ] Установлен PostgreSQL.
- [ ] Установлен Git.
- [ ] Установлен Cursor AI.
- [ ] Проверена работа JavaFX.
- [ ] Проверена работа Flyway.
- [ ] Проверена работа Testcontainers.
- [ ] Проверена сборка Maven.
- [ ] Проверена упаковка jpackage.

---

## Организационная готовность

- [x] Все архитектурные документы утверждены.
- [x] Все спорные вопросы закрыты.
- [ ] Все ADR утверждены.
- [ ] Scope первого этапа окончательно зафиксирован.
- [ ] Определены критерии завершения первого этапа.
- [ ] Подготовлена инструкция открытия нового чата Cursor.
- [ ] Подготовлена инструкция восстановления контекста.

---

# Definition of Ready

Перед началом разработки должны одновременно выполняться все условия:

- [x] Все обязательные документы утверждены.
- [x] Все архитектурные решения зафиксированы.
- [x] Все открытые архитектурные вопросы закрыты.
- [x] Подготовлен Master Implementation Plan.
- [x] Подготовлен Master Implementation Checklist.
- [ ] Подготовлены документы Development Foundation.
- [ ] Подготовлены промпты Cursor.
- [ ] Определён Scope первого этапа.
- [ ] Команда готова к реализации.

---