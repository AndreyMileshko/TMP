# TMP — Colors / SO7 Mapping Implementation Plan

**Plan ID:** TMP-COLORS-PLAN-001  
**Status:** Accepted (COL-005) — implementation not started  
**Version:** 1.0  
**Date:** 2026-11-08  
**Spec:** `TMP-Reference-Data-Colors-Specification.md` — TMP-SPEC-015 v1.0 Accepted  
**ADR:** ADR-039 — Reference Data Capability Architecture (Accepted)  
**Audit baseline:** `COL-000-Architecture-Audit.md` (HEAD `d29a4fcdc9299758fecfb4dbc3bcee9aaedfeaa1`)  
**Stage Manifest:** `docs/development-control/stages/STAGE-COL-REFERENCE-DATA-COLORS.md`  
**Initial dataset:** `TMP_Цвета_и_сопоставления_СО7_согласовано.xlsx` (860 + 245)

---

# 1. Цель

Реализовать Capability Reference Data с разделом «Справочники → Цвета»: каталог одиночных цветов, глобальные соответствия текстов СО7, общесистемный переключатель автосопоставления и единое окно разрешения цветов в импорте STXT — **без регресса** Order Management, Warehouse, Production, Document Engine и Security и строго по TMP-SPEC-015 / ADR-039.

---

# 2. Контрольные принципы

1. Одна задача = один измеримый результат; лимиты governance: преимущественно один Maven-модуль, ≤ 5 новых и ≤ 10 изменённых production-файлов. Исключения указаны в задаче с причиной.
2. Задачи выполняются строго в порядке зависимостей (§6); следующая задача — только после `DONE` предыдущей.
3. Не изменять Order Management, Warehouse, Production сверх явно разрешённого задачей; парсер STXT, `OrderImportValidator`, `DefaultOrderImportService`, транзакция confirm — не изменяются ни в одной задаче.
4. Переключатель `OFF` по умолчанию; путь импорта при `OFF` остаётся прежним.
5. Начальные данные — только из согласованного Excel (860 / 245); условные ID Excel не используются.
6. Никаких изменений существующих immutable спецификаций, документов, остатков.
7. Cursor **не** делает `git commit` / `git push`; фиксацию решает пользователь.
8. `tmp_pilot` не используется для непроверенных миграций и экспериментов; destructive-операции — только в COL-060A с резервной копией и отдельным разрешением.
9. Задача с ручной GUI-приёмкой не переводится в `DONE` агентом: после сборки и запуска остаётся `VERIFYING` до подтверждения пользователя.
10. Миграции V58 / V59 зарезервированы COL-005; если к началу COL-010 номер занят — blocker, не перенумерация существующих миграций.

---

# 3. Состояние входных гейтов

| Гейт | Результат |
|---|---|
| COL-000 read-only audit | DONE — READY WITH CONDITIONS |
| Решения Q-01 … Q-07, Q-05-bis | Утверждены product owner (2026-11-08) |
| ADR-039 | Accepted |
| TMP-SPEC-015 v1.0 | Accepted |
| Резерв V58 / V59 | Свободны на момент COL-005 (последняя миграция — V57) |
| Задачи в WORK-QUEUE и Stage Manifest | Зарегистрированы |
| Stage Start Gate (Colors track) | PASSED (COL-005); COL-010 = READY; запуск — по команде пользователя |

---

# 4. Общие правила для всех задач

## 4.1 Общие запреты

- Изменения вне Allowed scope задачи; будущие задачи; несвязанный рефакторинг.
- Зависимость `tmp-order-management`, `tmp-warehouse`, `tmp-production` от `tmp-reference-data`; зависимость `tmp-reference-data` от OM / Warehouse / Production / UI.
- Изменение парсера STXT, `OrderImportValidator`, `DefaultOrderImportService`, транзакции confirm, Public Query OM, контрактов Warehouse / Production, точного сравнения цвета.
- Изменение существующих Flyway-миграций; миграции кроме V58 / V59.
- Новые типы документов Document Engine / Domain Events для справочника; платформенный Settings framework; generic framework справочников.
- Новые Maven-зависимости кроме межмодульных зависимостей, перечисленных в задаче.
- TODO / FIXME / HACK, заглушки, закомментированный код, отключение проверок (Checkstyle / SpotBugs / ArchUnit).
- Операции с `tmp_pilot` (кроме COL-060A по разрешению); удаление данных.
- `git commit` / `git push`.

## 4.2 Базовые проверки каждой задачи

```powershell
mvn -pl :<module> -am verify -DskipITs        # unit + Checkstyle + SpotBugs затронутого модуля
mvn -pl :tmp-architecture-tests -am test      # архитектурные правила
git diff --check
```

Testcontainers-тесты (`*IT`) — там, где указано в задаче (`mvn -pl :<module> -am verify`). Полный `mvn clean verify` — только в COL-060B (либо по прямому указанию задачи).

## 4.3 Протокол ручной GUI-приёмки

Для задач с отметкой «GUI: да»:

1. Убедиться, что `dist\jpackage\TMP` не используется запущенным процессом пользователя; иначе — попросить пользователя закрыть приложение (не завершать процессы самостоятельно).
2. Собрать **обновлённый** пакет: `mvn -pl :tmp-bootstrap-app -am pre-integration-test -Ppackage -DskipTests`.
3. Запустить актуальный `TMP.exe` через `scripts/run-tmp-package.ps1` с явным `TMP_DB_URL` на **изолированную приёмочную БД** `tmp_col_acceptance` (контейнер `tmp-stage5-pg`, порт 55432). Создание этой пустой БД — только с явного согласия пользователя при первом GUI-шаге; `tmp_pilot` и `tmp_gui_stage5` не используются. Flyway пакетного профиля применяет миграции, включая V58 / V59, при старте.
4. **Оставить приложение открытым**, передать пользователю чек-лист сценариев задачи.
5. Задача остаётся `VERIFYING`; `DONE` — только после подтверждения пользователя. Commit не выполняется.

## 4.4 Обновление документов после каждой задачи

WORK-QUEUE (статус), STATUS, IMPLEMENTATION-LOG, VERIFICATION-LOG, таблица §10 этого плана и Stage Manifest. Спецификация / ADR — только через blocker и решение пользователя.

---

# 5. Задачи

Общий Required documents для всех задач: TMP-SPEC-015 (указанные разделы), ADR-039, этот план (раздел задачи), Stage Manifest Colors. Дополнительные документы указаны в задаче.

---

## COL-010 — Module and schema

| | |
|---|---|
| **Objective** | Создать модуль `tmp-reference-data` с минимальной Capability `reference`, схемой V58 и ArchUnit-границами |
| **Depends on** | COL-005 |
| **Module** | `tmp-reference-data` (+ reactor wiring, architecture tests) |
| **Required documents** | Spec §3, §16.1–§16.3; Database Specification §3–§5, §10, §11; Capability Engine Spec (descriptor) |
| **Required code context** | `pom.xml` (root), `tmp-warehouse/pom.xml` (образец), `tmp-warehouse/.../security/WarehouseCapability.java` (образец descriptor), `tmp-bootstrap-app/pom.xml`, `Stage6WarehouseArchitectureTest.java` (образец правил) |
| **GUI** | нет |
| **Database impact** | Новая миграция `V58__reference_colors_schema.sql` (схема `reference`, три таблицы §16.1–§16.3, без данных). Применяется только в Testcontainers |

**Allowed scope:**

- `pom.xml` (root: `<module>tmp-reference-data</module>`, dependencyManagement);
- `tmp-reference-data/pom.xml` (зависимости: `tmp-platform-core`, `tmp-capability-engine`, `tmp-security`, `tmp-infra-db`);
- `tmp-reference-data/src/main/java/com/tmp/reference/capability/ReferenceDataCapability.java` (descriptor без прав и навигации);
- `tmp-reference-data/src/main/java/com/tmp/reference/package-info.java`;
- `tmp-reference-data/src/main/resources/db/migration/V58__reference_colors_schema.sql`;
- `tmp-bootstrap-app/pom.xml` и регистрация Capability bean в существующей auto-configuration bootstrap (по образцу Warehouse);
- `tmp-architecture-tests/pom.xml`, новый `tmp-architecture-tests/src/test/java/com/tmp/architecture/ReferenceDataArchitectureTest.java`;
- тесты модуля `tmp-reference-data/src/test/**` (в т.ч. Flyway IT схемы);
- существующие тесты, проверяющие последнюю версию Flyway / список схем, — только обновление ожидаемой версии (найти `rg '"57"' -g "*.java"`).

**Forbidden:** доменные классы, сервисы, права, UI, seed-данные; изменения OM / Warehouse / Production.

**Implementation requirements:** таблицы, ограничения и индексы — точно по Spec §16.1–§16.3 (UUID, `code` / `code_key` `VARCHAR(31)`, `source_text` / `source_key` `VARCHAR(64)`, FK `ON DELETE RESTRICT`, переключатель `id SMALLINT CHECK (id = 1)`). ArchUnit: (1) внешние модули используют только `com.tmp.reference.api..`; (2) `com.tmp.reference..` не зависит от `com.tmp.order..`, `com.tmp.warehouse..`, `com.tmp.production..`, `com.tmp.ui..`, `com.tmp.bootstrap..`, `javafx..`; (3) `com.tmp.order..`, `com.tmp.warehouse..`, `com.tmp.production..` не зависят от `com.tmp.reference..`; (4) домен `com.tmp.reference.domain..` без Spring / JDBC / JavaFX. Исключение по размеру: атомарная инфраструктурная настройка нового модуля (несколько pom и регистрация), не проверяемая по частям.

**Acceptance criteria:**

- [ ] Reactor собирается с новым модулем; Capability `reference` регистрируется при старте Spring-контекста.
- [ ] V58 применяется на чистой PostgreSQL (Testcontainers): схема `reference`, три таблицы, все UNIQUE / FK / CHECK; данных нет.
- [ ] Повторный старт Flyway не изменяет схему.
- [ ] ArchUnit-правила (1)–(4) зелёные и падают на намеренном нарушении (negative-тест по образцу Stage 5).
- [ ] OM / Warehouse / Production production-код не изменён.

**Verification:**

```powershell
mvn -pl :tmp-reference-data -am verify
mvn -pl :tmp-architecture-tests -am test
mvn -pl :tmp-bootstrap-app -am verify -DskipITs
git diff --check
```

---

## COL-011 — Initial data

| | |
|---|---|
| **Objective** | Идемпотентное начальное заполнение 860 цветов, 245 соответствий и переключателя OFF |
| **Depends on** | COL-010 |
| **Module** | `tmp-reference-data` |
| **Required documents** | Spec §5.4, §6.2, §6.5, §16.4; Excel листы «Цвета TMP», «Сопоставления СО7» |
| **GUI** | нет |
| **Database impact** | Новая миграция `V59__reference_colors_initial_data.sql` (только INSERT в схему `reference`). Применяется только в Testcontainers |

**Allowed scope:**

- `tmp-reference-data/src/main/resources/db/migration/V59__reference_colors_initial_data.sql`;
- `tmp-reference-data/src/main/java/com/tmp/reference/domain/So7SourceKey.java` и `ColorCodeKey.java` (чистые функции нормализации §6.2 / §5.1 — нужны для теста согласованности литералов seed);
- `tmp-reference-data/src/test/**`;
- генерация SQL из Excel выполняется одноразовым локальным скриптом **вне репозитория** (не коммитится, не добавляется в production-код).

**Forbidden:** POI / новые зависимости; `upper()` / `lower()` PostgreSQL для ключей; условные ID Excel; соответствие `Без цвета → 00`; 393 / 725 аналитических строк.

**Acceptance criteria:**

- [ ] Чистая БД: 860 цветов (все `active`), 245 соответствий (все `active`), строка переключателя `automatic_matching_enabled = FALSE`.
- [ ] Все 490 FK разрешаются; `Белый` и `00` присутствуют; `RAL7016`, `RAL7016|30`, `RAL7016|МУАР`, `KDB74-Z8|МАСС`, `KDB74-Z8|ПРИТВОР` присутствуют.
- [ ] `RAL7016/30т → RAL7016|30` (пример из 33 текстов с `/`); соответствия `Без цвета` нет.
- [ ] Для всех строк `code_key` = `ColorCodeKey.of(code)` и `source_key` = `So7SourceKey.of(source_text)` (тест согласованности Java ↔ SQL).
- [ ] Unit-тесты нормализации: краевые / повторные пробелы, NBSP, кириллица, `Ё` не заменяется, `/` и `|` сохраняются, `RAL 7016` ≠ `RAL7016`.
- [ ] Повторное выполнение INSERT-блоков V59 вручную на тестовой БД не создаёт дублей и не меняет изменённую пользователем запись (тест имитирует изменение и повтор).

**Verification:**

```powershell
mvn -pl :tmp-reference-data -am verify
git diff --check
```

---

## COL-023 — Security

| | |
|---|---|
| **Objective** | Объявить четыре права `reference.*`, выдать их Security Administrator, отобразить область «Справочники» |
| **Depends on** | COL-010 |
| **Module** | `tmp-reference-data` (основной) + `tmp-bootstrap-app` + `tmp-ui-shell` (одна строка отображения) |
| **Required documents** | Spec §15; Security Specification (PermissionId, синхронизация); COL-000 §5.D |
| **Required code context** | `WarehousePermissionCatalog.java`, `WarehouseCapability.java`, `WarehouseAdminNavigationAccessEnsure.java`, `DefaultSystemRolePermissionEnsureService.java`, `PermissionNamespaceGroup.java` |
| **GUI** | нет (проверка экрана ролей — в COL-030A) |
| **Database impact** | Нет миграций; права синхронизируются существующим механизмом при старте |

**Allowed scope:**

- новые `tmp-reference-data/src/main/java/com/tmp/reference/security/ReferenceDataPermissions.java`, `ReferenceDataPermissionCatalog.java`;
- изменение `ReferenceDataCapability.java` (PermissionDescriptor-ы);
- новый `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/ReferenceDataAdminNavigationAccessEnsure.java` + регистрация bean;
- `tmp-ui-shell/.../roleadmin/PermissionNamespaceGroup.java` (`reference` → «Справочники», порядок после `production`);
- тесты затронутых модулей.

Исключение по модулям: регистрация прав по установленному образцу требует трёх модулей (объявление, ensure в композиции, отображение), атомарно.

**Forbidden:** изменения модели Security; новые роли; выдача прав кроме Security Administrator; права с иными кодами.

**Acceptance criteria:**

- [ ] Ровно четыре права: `reference.color.view`, `reference.color.edit`, `reference.color-mapping.edit`, `reference.color-setting.edit`, владелец — Capability `reference`.
- [ ] После старта роль Security Administrator имеет все четыре права; повторный старт идемпотентен.
- [ ] Экран ролей группирует область `reference` как «Справочники».
- [ ] Новых ролей не создано.

**Verification:**

```powershell
mvn -pl :tmp-reference-data -am verify -DskipITs
mvn -pl :tmp-bootstrap-app -am verify
mvn -pl :tmp-ui-shell -am verify -DskipITs
mvn -pl :tmp-architecture-tests -am test
git diff --check
```

---

## COL-020A — Color catalog: domain and persistence

| | |
|---|---|
| **Objective** | Доменная модель цвета и JDBC-репозиторий с регистронезависимой уникальностью |
| **Depends on** | COL-011 |
| **Module** | `tmp-reference-data` |
| **Required documents** | Spec §5.1–§5.3, §16.1; Database Specification §5 (optimistic locking) |
| **GUI** | нет |
| **Database impact** | Нет миграций; чтение/запись `reference.colors` в Testcontainers |

**Allowed scope:** новые `domain/Color.java`, `domain/ColorRepository.java` (порт), `persistence/JdbcColorRepository.java`; использование `ColorCodeKey` (COL-011); тесты модуля.

**Forbidden:** операции изменения кода и удаления; application-сервисы; UI.

**Acceptance criteria:**

- [ ] Правила кода §5.2 (не пусто, ≤ 31, без `/` `\`, не более одного `|` с непустыми частями) — в домене, с понятными ошибками.
- [ ] Регистронезависимый дубликат отклоняется (`ral7016` при `RAL7016`) как controlled business exception; написание сохраняется.
- [ ] Архивирование / восстановление на уровне агрегата; optimistic locking (`version`); `created_by/updated_by`.
- [ ] В репозитории нет методов удаления и изменения кода.
- [ ] Testcontainers IT: вставка, поиск по коду / ключу, фильтр активности, поиск, конфликт версии.

**Verification:**

```powershell
mvn -pl :tmp-reference-data -am verify
mvn -pl :tmp-architecture-tests -am test
git diff --check
```

---

## COL-021A — SO7 mappings: domain and persistence

| | |
|---|---|
| **Objective** | Доменная модель соответствия, нормализация ключа, итоговый цвет пары и JDBC-репозиторий |
| **Depends on** | COL-020A |
| **Module** | `tmp-reference-data` |
| **Required documents** | Spec §6.1–§6.4, §16.2 |
| **GUI** | нет |
| **Database impact** | Нет миграций; `reference.so7_color_mappings` в Testcontainers |

**Allowed scope:** новые `domain/So7ColorMapping.java`, `domain/So7ColorMappingRepository.java`, `domain/ColorPairComposition.java` (итоговая строка §6.3), `persistence/JdbcSo7ColorMappingRepository.java`; тесты модуля.

**Forbidden:** application-сервисы; сохранение из импорта; UI.

**Acceptance criteria:**

- [ ] `source_text` ≤ 64, не пустой, неизменяемый; ключ по §6.2; одна запись на ключ независимо от активности.
- [ ] Итоговый цвет: одинаковые стороны → один код; разные → `inside/outside` (`Белый/RAL7016|30`).
- [ ] Репозиторий: поиск по ключу, список с фильтром / поиском, массовое отключение по `color_id` (внутри или снаружи), подсчёт активных соответствий цвета, удаление записи соответствия.
- [ ] IT: UNIQUE ключа, FK, optimistic locking, массовое отключение.

**Verification:**

```powershell
mvn -pl :tmp-reference-data -am verify
mvn -pl :tmp-architecture-tests -am test
git diff --check
```

---

## COL-020B — Color catalog: application API

| | |
|---|---|
| **Objective** | Публичные `ColorCatalogQueryApi` / `ColorCatalogCommandApi` с правами и атомарным архивированием |
| **Depends on** | COL-020A, COL-021A, COL-023 |
| **Module** | `tmp-reference-data` |
| **Required documents** | Spec §5.3, §15.1, §17 |
| **GUI** | нет |
| **Database impact** | Нет миграций |

**Allowed scope:** новые `api/ColorCatalogQueryApi.java`, `api/ColorCatalogCommandApi.java`, `api/ColorDto.java` (+ фильтр как вложенный тип), `application/DefaultColorCatalogService.java`; регистрация как публичных сервисов Capability в `ReferenceDataCapability`; тесты.

**Forbidden:** изменение кода / удаление; изменение соответствий кроме отключения при архивировании; UI.

**Acceptance criteria:**

- [ ] `listColors`, `getArchiveImpact` — `reference.color.view`; `createColor`, `archiveColor`, `restoreColor` — `reference.color.edit`; без права — `AccessDeniedException` до любого чтения/записи.
- [ ] `archiveColor` в одной транзакции архивирует цвет и отключает все активные соответствия с ним внутри или снаружи; возвращает их число; при ошибке — откат обоих (IT с принудительным сбоем).
- [ ] `restoreColor` не включает соответствия.
- [ ] Наружу только DTO.

**Verification:**

```powershell
mvn -pl :tmp-reference-data -am verify
mvn -pl :tmp-architecture-tests -am test
git diff --check
```

---

## COL-021B — SO7 mappings: application API

| | |
|---|---|
| **Objective** | Публичные `So7ColorMappingQueryApi` / `So7ColorMappingCommandApi`, включая `saveMappingsFromImport` |
| **Depends on** | COL-020B |
| **Module** | `tmp-reference-data` |
| **Required documents** | Spec §6.4, §11, §15.1, §17 |
| **GUI** | нет |
| **Database impact** | Нет миграций |

**Allowed scope:** новые `api/So7ColorMappingQueryApi.java`, `api/So7ColorMappingCommandApi.java`, `api/So7ColorMappingDto.java`, `api/ImportMappingSaveReport.java` (кандидат и результаты как вложенные типы), `application/DefaultSo7ColorMappingService.java`; регистрация публичных сервисов; тесты.

**Forbidden:** перезапись существующей записи из импорта; изменение `source_text`; UI.

**Acceptance criteria:**

- [ ] Чтение — `reference.color.view`; изменения и `saveMappingsFromImport` — `reference.color-mapping.edit`.
- [ ] Создание / изменение пары / включение — только с двумя активными цветами; второй записи для ключа нет (controlled exception с текстом «Сопоставление для этого текста уже существует»).
- [ ] `saveMappingsFromImport`: результаты `SAVED`, `NOT_SAVED_MAPPING_EXISTS`, `NOT_SAVED_CONFLICT_IN_IMPORT`, `NOT_SAVED_COLOR_INACTIVE`, `NOT_SAVED_INVALID_SOURCE` по Spec §11; одинаковые пары одного ключа сохраняются один раз; бизнес-отказы не прерывают остальные; техническая ошибка откатывает всё; нарушение UNIQUE при гонке → `NOT_SAVED_MAPPING_EXISTS`.
- [ ] IT: все результаты, конкурентное создание одного ключа, откат при техническом сбое.

**Verification:**

```powershell
mvn -pl :tmp-reference-data -am verify
mvn -pl :tmp-architecture-tests -am test
git diff --check
```

---

## COL-022A — Global matching setting

| | |
|---|---|
| **Objective** | Чтение и изменение общесистемного переключателя с правами и optimistic locking |
| **Depends on** | COL-020B |
| **Module** | `tmp-reference-data` |
| **Required documents** | Spec §7, §15, §16.3, §17 |
| **GUI** | нет |
| **Database impact** | Нет миграций; строка `reference.color_matching_settings` |

**Allowed scope:** новые `domain/ColorMatchingSettingRepository.java`, `persistence/JdbcColorMatchingSettingRepository.java`, `api/ColorMatchingSettingApi.java` (+ DTO вложенным типом), `application/DefaultColorMatchingSettingService.java`, `security/ReferenceImportAccessPolicy.java` (правило §15.2: `reference.color.view` **или** полный набор пяти прав импорта OM по строковым `PermissionId`); регистрация; тесты. (5 новых production-файлов.)

**Forbidden:** персональные настройки; платформенный Settings framework; зависимость от OM.

**Acceptance criteria:**

- [ ] Чтение: `reference.color.view` или полный набор прав импорта; неполный набор (только `order.order.create`) — отказ.
- [ ] Изменение: только `reference.color-setting.edit`; конфликт версии — понятная ошибка.
- [ ] Значение по умолчанию после seed — OFF.

**Verification:**

```powershell
mvn -pl :tmp-reference-data -am verify
mvn -pl :tmp-architecture-tests -am test
git diff --check
```

---

## COL-022B — Import resolution API

| | |
|---|---|
| **Objective** | `ColorImportResolutionApi` для оркестрации импорта: состояние переключателя, сопоставление текстов, активные цвета, итоговый цвет пары, право сохранения |
| **Depends on** | COL-021B, COL-022A |
| **Module** | `tmp-reference-data` (+ тест композиции в `tmp-bootstrap-app`) |
| **Required documents** | Spec §6.2, §6.3, §8.4, §9, §15.2, §17 |
| **GUI** | нет |
| **Database impact** | Нет миграций |

**Allowed scope:** новые `api/ColorImportResolutionApi.java`, `api/SourceColorResolution.java`, `api/ResolvedColorPair.java`, `application/DefaultColorImportResolutionService.java`; регистрация публичного сервиса; тесты модуля; новый тест композиции `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/ReferenceImportPermissionSetConsistencyTest.java` (набор прав импорта в `ReferenceImportAccessPolicy` = права, требуемые `DefaultOrderImportService`).

**Forbidden:** изменение OM; кэширование соответствий между импортами без инвалидации; UI.

**Acceptance criteria:**

- [ ] `resolveSourceTexts`: для каждого непустого текста — ключ и состояние `MATCHED(pair)` (только активное соответствие с двумя активными цветами) / `MAPPING_INACTIVE` / `NO_MAPPING`; `null` / пустые тексты не передаются и не обрабатываются.
- [ ] `composePair` отклоняет архивные цвета; итоговый цвет по §6.3.
- [ ] Все операции — правило §15.2; `canSaveGlobalMappings()` = наличие `reference.color-mapping.edit`.
- [ ] `RAL7016/30т` → `RAL7016|30`; ` ral  7016 мат ` находит `RAL 7016 мат`; `Без цвета` → `NO_MAPPING` на seed-данных.
- [ ] Тест композиции набора прав зелёный.

**Verification:**

```powershell
mvn -pl :tmp-reference-data -am verify
mvn -pl :tmp-bootstrap-app -am verify -DskipITs
mvn -pl :tmp-architecture-tests -am test
git diff --check
```

---

## COL-030A — Reference Data JavaFX UI: screen, «Цвета TMP», switch

| | |
|---|---|
| **Objective** | Пункт «Справочники», экран раздела «Цвета» с вкладкой «Цвета TMP» и переключателем |
| **Depends on** | COL-020B, COL-022A, COL-023 |
| **Module** | `tmp-ui-shell` (+ регистрация в `tmp-bootstrap-app`) |
| **Required documents** | Spec §14.1, §14.3, §14.4, §15; TMP UI Standard §12.3, §13, §16, §19, §22, §23, §27 |
| **Required code context** | `WarehouseSettingsController.java` / `WarehouseSettingsViewModel` (образец), `UiShellScreens.java`, `UiShellAutoConfiguration.java`, `ShellNavigationIcons.java` |
| **GUI** | **да** (§4.3) |
| **Database impact** | Нет миграций; GUI-приёмка на изолированной `tmp_col_acceptance` |

**Allowed scope:** новые `tmp-ui-shell/.../screen/reference/ReferenceColorsController.java`, `ReferenceColorsViewModel.java`, FXML `reference-colors.fxml`; `NavigationContribution` в `ReferenceDataCapability`; изменения `UiShellScreens`, `UiShellAutoConfiguration`, `ShellNavigationIcons`, `tmp-ui-shell/pom.xml` (зависимость на `tmp-reference-data`); тесты ViewModel / FX.

**Forbidden:** вкладка «Сопоставления СО7» (COL-030B); действия «Изменить код» / «Удалить»; бизнес-правила в UI; изменения импорта.

**Acceptance criteria:**

- [ ] «Справочники» виден только с `reference.color.view`.
- [ ] Вкладка «Цвета TMP»: «Код цвета», «Статус» (badge); поиск; фильтр «Активные / Архив / Все»; пустое состояние.
- [ ] «Добавить» (поле «Код цвета»), «Архивировать» (подтверждение с числом отключаемых соответствий), «Восстановить» — только с `reference.color.edit`; ошибки домена — inline понятным текстом.
- [ ] Переключатель виден с `view`, изменяем только с `reference.color-setting.edit`, с подтверждением.
- [ ] Экран ролей показывает «Справочники» и четыре права.

**Verification:**

```powershell
mvn -pl :tmp-ui-shell -am verify -DskipITs
mvn -pl :tmp-bootstrap-app -am verify -DskipITs
mvn -pl :tmp-architecture-tests -am test
mvn -pl :tmp-bootstrap-app -am pre-integration-test -Ppackage -DskipTests
git diff --check
```

GUI: COL-AC-01, 25, 26, 27, 28 (часть каталога) на `tmp_col_acceptance`; приложение оставить открытым.

---

## COL-030B — Reference Data JavaFX UI: «Сопоставления СО7»

| | |
|---|---|
| **Objective** | Вкладка «Сопоставления СО7» (по умолчанию) с диалогом пары |
| **Depends on** | COL-021B, COL-030A |
| **Module** | `tmp-ui-shell` |
| **Required documents** | Spec §6.4, §14.2; TMP UI Standard (как COL-030A) |
| **GUI** | **да** (§4.3) |
| **Database impact** | Нет миграций; `tmp_col_acceptance` |

**Allowed scope:** новые `tmp-ui-shell/.../screen/reference/So7MappingsTabController.java`, `So7MappingsTabViewModel.java`, `So7MappingDialogSupport.java`, FXML вкладки; изменения `reference-colors.fxml`, `ReferenceColorsController/ViewModel`, `UiShellAutoConfiguration`; тесты.

**Forbidden:** бизнес-правила в UI (ключ, итоговый цвет — из API); изменения импорта.

**Acceptance criteria:**

- [ ] Вкладка открыта по умолчанию; колонки «Цвет СО7», «Внутри TMP», «Снаружи TMP», «Итоговый цвет», «Активно»; архивные стороны с пометкой «(архив)».
- [ ] Поиск по тексту СО7 / коду; фильтр «Активные / Отключённые / Все».
- [ ] «Добавить», «Изменить пару», «Включить» / «Отключить», «Удалить» — только с `reference.color-mapping.edit`; выбор сторон из активных цветов с поиском; дубликат ключа — понятная ошибка.

**Verification:** как COL-030A. GUI: COL-AC-01 (245 строк), 24 (часть справочника), 33 (поиск).

---

## COL-040A — Order Management color constraint contract

| | |
|---|---|
| **Objective** | Read-only публичный контракт OM для проверки итогового цвета строки спецификации (≤ 64, плейсхолдеры) |
| **Depends on** | COL-005 |
| **Module** | `tmp-order-management` |
| **Required documents** | Spec §10, §12; OM Specification §27.6, §28.5; ADR-039 п.7 |
| **Required code context** | `CommercialPlaceholderValidator.java`, `SpecificationLine.java`, Capability OM (регистрация публичных сервисов), `Stage5OrderManagementArchitectureTest.java` |
| **GUI** | нет |
| **Database impact** | Нет |

**Allowed scope:** новые `com/tmp/order/api/imports/SpecificationColorConstraint.java` (интерфейс + результат с кодом причины `BLANK` / `TOO_LONG` / `PLACEHOLDER`), `com/tmp/order/application/imports/DefaultSpecificationColorConstraint.java` (делегирует `CommercialPlaceholderValidator.isForbiddenPlaceholder` и лимиту 64 — единый источник правил); регистрация публичного сервиса в Capability OM / auto-configuration; тесты OM.

**Forbidden:** изменение `OrderImportValidator`, парсера, `DefaultOrderImportService`, `SpecificationLine`, payload, БД; зависимость OM от Reference Data; дублирование списка плейсхолдеров.

**Acceptance criteria:**

- [ ] Контракт: значение после `trim` не пустое, ≤ 64, не плейсхолдер (регистронезависимо); 64 символа — допустимо, 65 — нет; `n/a`, `-`, `—` отклоняются; `RAL7016/30т`, `Белый/RAL7016|30`, `Без цвета` — допустимы.
- [ ] Лимит 64 совпадает с ограничением `SpecificationLine` / схемы (тест).
- [ ] Существующие тесты OM и Stage 5 ArchUnit — зелёные без изменений.

**Verification:**

```powershell
mvn -pl :tmp-order-management -am verify
mvn -pl :tmp-architecture-tests -am test
git diff --check
```

---

## COL-040B — Import resolution orchestration

| | |
|---|---|
| **Objective** | Не-JavaFX координатор: OFF/ON, автоматическое разрешение, модель строк окна, применение выборов, проверка, пересборка пакетов, кандидаты на сохранение |
| **Depends on** | COL-022B, COL-040A |
| **Module** | `tmp-ui-shell` |
| **Required documents** | Spec §8, §9, §10, §11 (кандидаты); OM Spec §27.4 (публичные фабрики) |
| **Required code context** | `com.tmp.order.api.imports` (`OrderImportBatch`, `OrderImportPosition`, `OrderImportSpecificationLine`, фабрики), `OrderImportViewModel.java` (только чтение для понимания точки интеграции) |
| **GUI** | нет |
| **Database impact** | Нет |

**Allowed scope:** новые `tmp-ui-shell/.../screen/orderimport/colors/ColorResolutionCoordinator.java`, `ColorResolutionRow.java`, `ColorResolutionSession.java` (состояние выборов, массовые операции, результат); тесты.

**Forbidden:** JavaFX в координаторе; нормализация ключа / состав итогового цвета / список плейсхолдеров в UI (только вызовы API); изменение `OrderImportViewModel` (COL-041B); изменение OM.

**Acceptance criteria:**

- [ ] OFF: координатор возвращает исходные пакеты без изменений (тождество по ссылке / равенству) и не вызывает resolution API, кроме чтения переключателя.
- [ ] ON: строки — все строки спецификации с непустым цветом всех заказов; пустые — не включены и остаются `null`.
- [ ] Известные — предзаполнены парой; неизвестные / `MAPPING_INACTIVE` — «Требует выбора»; признак «нужно открыть окно» = есть неизвестные или недопустимые итоговые значения.
- [ ] Операции: пара / исходный / свой цвет для строки; массово к выделенным и ко всем с тем же ключом (обе стороны); переопределение сопоставленной строки помечает «Изменено вручную».
- [ ] Каждое итоговое значение проверяется `SpecificationColorConstraint`; ошибки на уровне строки.
- [ ] Пересборка через публичные фабрики сохраняет все остальные поля, порядок заказов / позиций / строк.
- [ ] Кандидаты на сохранение: только строки с флажком, парой из двух активных цветов и ключом без записи; конфликт одного ключа с разными парами помечается предупреждением; одинаковые пары дедуплицируются.
- [ ] Unit-тесты: `Цвет` в двух изделиях и дважды в одном изделии с разными парами; `RAL7016/30т`; `Без цвета`; конфликт ключа; OFF-тождество.

**Verification:**

```powershell
mvn -pl :tmp-ui-shell -am verify -DskipITs
mvn -pl :tmp-architecture-tests -am test
git diff --check
```

---

## COL-041A — Unified resolution dialog

| | |
|---|---|
| **Objective** | JavaFX-диалог единого окна разрешения поверх `ColorResolutionSession` |
| **Depends on** | COL-040B |
| **Module** | `tmp-ui-shell` |
| **Required documents** | Spec §8, §10, §14.5; TMP UI Standard §16, §22, §23, §27, §39.9 |
| **GUI** | нет (диалог ещё не подключён к импорту; FX-тесты); ручная приёмка — в COL-041B |
| **Database impact** | Нет |

**Allowed scope:** новые `tmp-ui-shell/.../screen/orderimport/colors/ColorResolutionDialog.java`, `ColorResolutionDialogViewModel.java`, `ColorPairCellEditors.java` (редакторы «Внутри TMP» / «Снаружи TMP» с поиском); при необходимости FXML диалога; тесты FX / ViewModel.

**Forbidden:** вызовы OM confirm / сохранения соответствий из диалога; бизнес-правила в UI; изменение импорта.

**Acceptance criteria:**

- [ ] Одна таблица с колонками Spec §8.2; фильтры «Требуют выбора / Все» и по тексту СО7; мультивыбор.
- [ ] Массовые команды Spec §8.5; режимы «Оставить исходный текст» / «Свой цвет»; ошибки у поля.
- [ ] Флажок «Сохранить сопоставление в справочнике» — по правилам §8.6 (нет права → скрыт; есть запись ключа → недоступен с подсказкой; по умолчанию включён); предупреждение о конфликте ключа.
- [ ] «Применить» доступно только когда все строки имеют допустимое значение; «Отмена» возвращает признак отмены.
- [ ] Оформление `TmpTheme`, порядок кнопок, autosize.

**Verification:**

```powershell
mvn -pl :tmp-ui-shell -am verify -DskipITs
mvn -pl :tmp-architecture-tests -am test
git diff --check
```

---

## COL-041B — Import integration and post-confirm mapping save

| | |
|---|---|
| **Objective** | Подключить координатор и окно к сценарию импорта; «Проверить цвета»; сохранение соответствий после confirm и отчёт |
| **Depends on** | COL-041A, COL-030B |
| **Module** | `tmp-ui-shell` (+ wiring в `tmp-bootstrap-app`) |
| **Required documents** | Spec §7, §8.7, §9, §11, §14.5; UI Standard §39.9 |
| **Required code context** | `OrderImportViewModel.java`, `OrderImportController.java`, FXML импорта, `UiShellAutoConfiguration.java`, `OrderImportViewModelTest.java` |
| **GUI** | **да** (§4.3) |
| **Database impact** | Нет миграций; GUI-приёмка создаёт тестовые заказы только в `tmp_col_acceptance` |

**Allowed scope:** изменения `OrderImportViewModel.java`, `OrderImportController.java`, FXML импорта, `UiShellAutoConfiguration.java`; новый `tmp-ui-shell/.../screen/orderimport/colors/ImportMappingSaveMessages.java` (тексты итогов §11); тесты (существующие тесты импорта не изменяются, добавляются новые).

**Forbidden:** изменения OM (`OrderImportService`, валидатор, парсер, confirm); сохранение соответствий до confirm или при ошибке; изменение поведения OFF.

**Acceptance criteria:**

- [ ] OFF: прежний сценарий; окна и «Проверить цвета» нет; существующие `OrderImportViewModelTest`, `DefaultOrderImportServiceTest`, `StxtFileAdapterTest`, `OrderIntakeStxtEndToEndIT` — без изменений и зелёные.
- [ ] ON: окно открывается автоматически только при неизвестных / недопустимых значениях; иначе preview сразу; «Проверить цвета» открывает то же окно и перестраивает preview.
- [ ] Отмена автоматически открытого окна — импорт не готовится, заказов нет.
- [ ] Confirm успешен → `saveMappingsFromImport` (если есть кандидаты) → итоговое сообщение; техническая ошибка сохранения — сообщение, заказ ACTIVE; confirm неуспешен / отменён → сохранение не вызывается.
- [ ] Импортёр без `reference.*` с полным набором прав импорта проходит сценарий; флажок сохранения скрыт.

**Verification:**

```powershell
mvn -pl :tmp-ui-shell -am verify -DskipITs
mvn -pl :tmp-order-management -am verify
mvn -pl :tmp-bootstrap-app -am verify -DskipITs
mvn -pl :tmp-architecture-tests -am test
mvn -pl :tmp-bootstrap-app -am pre-integration-test -Ppackage -DskipTests
git diff --check
```

GUI: COL-AC-03 … 23, 32 на `tmp_col_acceptance` с образцом `docs/Input/Итоговый формат выгрузки.stxt` и подготовленными тестовыми `.stxt` (вне `src/main`); приложение оставить открытым.

---

## COL-050A — Warehouse regression

| | |
|---|---|
| **Objective** | Характеризационные тесты: разовые и составные цвета проходят Warehouse без изменений поведения |
| **Depends on** | COL-005 (может выполняться независимо от нового кода; в очереди — после COL-041B) |
| **Module** | `tmp-warehouse` (только тесты) |
| **Required documents** | Spec §13; Warehouse Spec §4, §15.1.1–§15.1.2; COL-000 §5.C |
| **GUI** | нет |
| **Database impact** | Нет (Testcontainers) |

**Allowed scope:** только `tmp-warehouse/src/test/**`.

**Forbidden:** любые изменения `tmp-warehouse/src/main/**`.

**Acceptance criteria:**

- [ ] Приёмка / резолв MaterialReference / Demand с цветами `RAL7016|30`, `Белый/RAL7016|30`, `Без цвета`, произвольный текст ≤ 64 — штатно.
- [ ] `RAL7016`, `RAL7016|30`, `ral7016` — разные MaterialReference; Demand с `RAL7016|30` не находит остаток `RAL7016` (`MATERIAL_UNMATCHED`), как и сейчас.
- [ ] Перемещение / история сохраняют строку цвета без изменений.

**Verification:**

```powershell
mvn -pl :tmp-warehouse -am verify
git diff --check
```

---

## COL-050B — Production regression

| | |
|---|---|
| **Objective** | Характеризационные тесты: итоговый цвет проходит Production Launch / потребности без изменений |
| **Depends on** | COL-005 (в очереди — после COL-050A) |
| **Module** | `tmp-production` (только тесты) |
| **Required documents** | Spec §13; COL-000 §4, §5.C |
| **GUI** | нет |
| **Database impact** | Нет (Testcontainers) |

**Allowed scope:** только `tmp-production/src/test/**`.

**Forbidden:** любые изменения `tmp-production/src/main/**`.

**Acceptance criteria:**

- [ ] `SpecificationMaterialIdentity` / агрегирование потребности: `RAL7016` и `RAL7016|30` — разные строки; `Белый/RAL7016|30` сохраняется точно.
- [ ] Launch по заказу с разовым цветом — штатно; цвет в потребности = цвет спецификации.
- [ ] Изменение соответствия в Reference Data не участвует (Production не зависит от Reference Data — ArchUnit COL-010).

**Verification:**

```powershell
mvn -pl :tmp-production -am verify
git diff --check
```

---

## COL-060A — Acceptance environment preparation (Q-01)

| | |
|---|---|
| **Objective** | Подготовить среду финальной приёмки: чистая изолированная БД **или** контролируемый сброс тестовых данных `tmp_pilot` |
| **Depends on** | COL-041B, COL-050A, COL-050B |
| **Module** | — (операции с окружением; скрипт сброса — вне production-кода) |
| **Required documents** | Spec §19; `docs/pilot/PILOT-ENVIRONMENT.md` |
| **GUI** | нет |
| **Database impact** | **Destructive (вариант 2) — только по отдельному явному разрешению пользователя** |

**Порядок:**

1. Агент предлагает пользователю вариант: (1) чистая изолированная БД (рекомендуется для первой приёмки) или (2) сброс тестовых данных `tmp_pilot`. Без выбора пользователя — остановка.
2. Вариант 1: создание новой пустой БД с уникальным именем по согласию пользователя; запуск пакета применяет все миграции.
3. Вариант 2:
   - `scripts/pilot/backup-tmp-pilot.ps1`, затем `scripts/pilot/verify-tmp-pilot-backup.ps1` — оба PASS;
   - агент готовит и показывает пользователю перечень таблиц и SQL сброса: заказы OM и их документы / payload, Production-состояния и документы, складские остатки, движения, операции, перемещения, Demand и связанные тестовые операции; **сохраняются** `security.*`, структура складов (склады, ячейки, ответственные, единицы измерения), `reference.*`, `flyway_schema_history`, системная конфигурация; удаление `warehouse.material_references` — только если пользователь отдельно это подтвердит;
   - выполнение — только после явного письменного разрешения пользователя; затем контролируемый `run-tmp-pilot.ps1 -ApplyMigrations` для V58 / V59 и повторный бэкап.

**Forbidden:** любые действия с БД без разрешения; сброс пользователей, ролей, справочных данных; восстановление поверх пилота; коммит дампов / паролей.

**Acceptance criteria:**

- [ ] Решение пользователя и разрешение зафиксированы в IMPLEMENTATION-LOG.
- [ ] Вариант 2: бэкап и проверка восстановления PASS до сброса; после сброса пользователи / роли / структура складов / `reference.*` на месте; заказов и остатков нет; Flyway = V59.
- [ ] Вариант 1: новая БД на V59; 860 / 245 / OFF.

**Verification:** read-only SQL-счётчики до/после (заказы, остатки, пользователи, роли, склады, `reference.colors`, `reference.so7_color_mappings`), вывод скриптов бэкапа / проверки.

---

## COL-060B — Packaged acceptance

| | |
|---|---|
| **Objective** | Полная регрессия, свежая пакетная сборка и ручная приёмка COL-AC-01 … 33 |
| **Depends on** | COL-060A |
| **Module** | — |
| **Required documents** | Spec §18, §20; этот план §8 |
| **GUI** | **да** (§4.3; БД — подготовленная в COL-060A) |
| **Database impact** | Только тестовые заказы / операции приёмки в подготовленной БД |

**Allowed scope:** документы control (логи, STATUS, WORK-QUEUE, план, Stage Manifest); исправления — только через отдельную corrective-задачу.

**Acceptance criteria:**

- [ ] `mvn clean verify` PASS на текущем HEAD.
- [ ] Собран и запущен **свежий** `TMP.exe`; приложение оставлено открытым.
- [ ] Пользователь подтвердил COL-AC-01 … 33 (результаты по каждому сценарию в VERIFICATION-LOG).
- [ ] Spec v1.0 соответствует коду; расхождения — blocker.
- [ ] Commit / push не выполнены.

**Verification:**

```powershell
mvn clean verify
mvn -pl :tmp-bootstrap-app -am pre-integration-test -Ppackage -DskipTests
git diff --check
```

---

# 6. Зависимости и порядок

```text
COL-005 (DONE)
  └─ COL-010 Module and schema (V58)
       ├─ COL-011 Initial data (V59)
       │    └─ COL-020A Color domain + persistence
       │         └─ COL-021A Mapping domain + persistence
       └─ COL-023 Security ─────────────┐
                                        ↓
            COL-020B Color catalog API (020A, 021A, 023)
              ├─ COL-021B Mapping API
              │    └──────────────┐
              └─ COL-022A Setting ┤
                                  ↓
                     COL-022B Import resolution API
  COL-030A UI catalog + switch (020B, 022A, 023)
    └─ COL-030B UI mappings (021B)
  COL-040A OM color constraint contract (только COL-005)
  COL-040B Coordinator (022B, 040A)
    └─ COL-041A Dialog
         └─ COL-041B Import integration (+ 030B)
  COL-050A Warehouse regression, COL-050B Production regression (только COL-005)
  COL-060A Acceptance environment (041B, 050A, 050B; разрешение пользователя)
    └─ COL-060B Packaged acceptance
```

Порядок выполнения в очереди (последовательно): COL-010 → COL-011 → COL-023 → COL-020A → COL-021A → COL-020B → COL-021B → COL-022A → COL-022B → COL-030A → COL-030B → COL-040A → COL-040B → COL-041A → COL-041B → COL-050A → COL-050B → COL-060A → COL-060B.

---

# 7. Тестовые данные и окружение

- Эталон начальных данных — согласованный Excel.
- Образец выгрузки: `docs/Input/Итоговый формат выгрузки.stxt` (208 / 287 строк `Без цвета`; `20102026.stxt` в репозитории отсутствует). Дополнительные тестовые `.stxt` — в `src/test/resources` соответствующих модулей.
- Обязательные случаи: повторяющийся `Цвет` в разных строках, две стороны, `RAL7016/30т` как единый текст, `Без цвета`, пустой цвет, недопустимый свой цвет, конфликт ключа.
- Миграции разрабатываются и проверяются в Testcontainers; GUI — в `tmp_col_acceptance` (§4.3); `tmp_pilot` — только в COL-060A по разрешению.
- Секреты БД не записываются в отчёты и Git.

---

# 8. Definition of Done (Colors track)

1. TMP-SPEC-015 v1.0 соответствует фактическому коду.
2. Путь импорта при `OFF` не изменён (регрессионные тесты без изменений зелёные).
3. Seed 860 / 245 / OFF устойчив к повтору.
4. В UI нет карточек / наименований / изменения кода / удаления цветов.
5. Единое окно: все строки с непустым цветом, две стороны, разовое переопределение, исходный / свой цвет, массовые действия, флажок сохранения по праву, «Проверить цвета».
6. Сопоставление не зависит от `CLRNO`; исходный `/` не разбирается.
7. Соответствия сохраняются только после успешного confirm отдельной TX; без молчаливой перезаписи.
8. Warehouse / Production проходят регрессию с разовыми значениями; их production-код не изменён.
9. Неизменяемость ACTIVE Specification сохранена.
10. Права, архивирование, переключатель проверены на реальных ролях.
11. ArchUnit: нет зависимостей OM / Warehouse / Production → Reference Data и обратно.
12. Пользователь выполнил ручную приёмку в актуальном `TMP.exe`; затем может санкционировать commit.

---

# 9. Технические риски реализации

| Риск | Задача | Мера |
|---|---|---|
| V58 / V59 заняты параллельной работой | COL-010 / COL-011 | Проверка перед началом; при занятости — blocker |
| Несовпадение ключей seed и Java | COL-011 | Тест согласованности для всех 860 / 245 строк |
| Нарушение OFF-пути импорта | COL-040B / COL-041B | OFF-тождество в координаторе; существующие тесты импорта без изменений |
| Объём окна (сотни строк) | COL-041A | Фильтры, группировка, массовые действия; производительность таблицы на 300+ строк проверяется в FX-тесте |
| Расхождение набора прав импорта OM и Reference Data | COL-022B | Тест композиции в bootstrap |
| Неверная оценка размера задачи | все | При превышении лимитов — декомпозиция до кода (governance §4) |
| Работа со «старой» сборкой при GUI-приёмке | COL-030A/B, 041B, 060B | Обязательная пересборка перед запуском (§4.3) |
| Случайная работа с `tmp_pilot` | все | Только `tmp_col_acceptance` до COL-060A |

---

# 10. Состояние задач

| ID | Задача | Модуль | GUI | DB | Статус |
|---|---|---|---|---|---|
| COL-000 | Read-only audit | docs | — | — | DONE |
| COL-005 | Architecture finalization | docs | — | — | DONE |
| COL-010 | Module and schema | reference-data | нет | V58 | READY |
| COL-011 | Initial data | reference-data | нет | V59 | PLANNED |
| COL-023 | Security | reference-data (+bootstrap, ui-shell) | нет | — | PLANNED |
| COL-020A | Color domain + persistence | reference-data | нет | — | PLANNED |
| COL-021A | Mapping domain + persistence | reference-data | нет | — | PLANNED |
| COL-020B | Color catalog API | reference-data | нет | — | PLANNED |
| COL-021B | Mapping API | reference-data | нет | — | PLANNED |
| COL-022A | Global matching setting | reference-data | нет | — | PLANNED |
| COL-022B | Import resolution API | reference-data | нет | — | PLANNED |
| COL-030A | UI: screen, «Цвета TMP», switch | ui-shell | да | — | PLANNED |
| COL-030B | UI: «Сопоставления СО7» | ui-shell | да | — | PLANNED |
| COL-040A | OM color constraint contract | order-management | нет | — | PLANNED |
| COL-040B | Import resolution coordinator | ui-shell | нет | — | PLANNED |
| COL-041A | Unified resolution dialog | ui-shell | нет | — | PLANNED |
| COL-041B | Import integration + post-confirm save | ui-shell | да | — | PLANNED |
| COL-050A | Warehouse regression | warehouse (tests) | нет | — | PLANNED |
| COL-050B | Production regression | production (tests) | нет | — | PLANNED |
| COL-060A | Acceptance environment (Q-01) | — | нет | destructive по разрешению | PLANNED |
| COL-060B | Packaged acceptance | — | да | — | PLANNED |

Итого задач реализации: **19** (COL-010 … COL-060B).

---

# 11. История

| Version | Change |
|---|---|
| 0.9 (2026-11-08) | Предварительный пошаговый план до аудита. |
| 1.0 (2026-11-08) | **COL-005.** План согласован с ADR-039 и TMP-SPEC-015 v1.0: исходные этапы COL-010 … COL-060 разбиты на 19 задач по лимитам governance (020/021/022 → A/B, 030 → A/B, 040 → A (контракт OM) / B (координатор), 041 → A/B, 050 → A/B, 060 → A (среда, Q-01) / B); для каждой задачи — цель, зависимости, allowed scope, запреты, критерии, команды проверки, GUI и влияние на БД; протокол GUI-приёмки; резерв V58 / V59; задачи зарегистрированы в WORK-QUEUE и Stage Manifest. |
