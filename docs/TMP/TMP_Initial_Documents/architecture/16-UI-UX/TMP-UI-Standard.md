# TMP UI Standard

**Document ID:** TMP-UI-STD-001  
**Status:** Accepted  
**Version:** 1.1

---

# 1. Назначение

Настоящий документ устанавливает **обязательный визуальный и UX-стандарт** TOP Manufacturing Platform (TMP).

Стандарт определяет единый язык интерфейса для всех существующих и будущих модулей. Модули выбирают только **семантику** (action, secondary, danger, status-success и т.д.); внешний вид задаёт глобальная тема TMP.

Стандарт дополняет [UI/UX Specification](UI-UX-Specification.md) и не заменяет архитектурные правила UI.

---

# 2. Область действия

## 2.1 Обязательность

Стандарт **MUST** соблюдаться для:

- `tmp-ui-shell`;
- всех domain/business UI modules;
- всех JavaFX screens, FXML, dialogs, navigation;
- reusable controls и будущих UI-компонентов.

## 2.2 Исключения

Отклонение допускается **только** если одновременно выполнены все условия:

1. стандарт технически неприменим;
2. есть объективная UX-причина;
3. отклонение документировано (комментарий в задаче, ADR или запись в Implementation Log);
4. отклонение **не** создаёт параллельный дизайн-язык.

---

# 3. UX-принципы TMP

1. Простота пользовательских сценариев.
2. Минимум шагов.
3. Предсказуемость интерфейса.
4. Высокая информационная плотность без перегрузки.
5. Desktop-first.
6. Keyboard-friendly.
7. Production/enterprise orientation.
8. Минимум декоративных эффектов.
9. Семантические цвета.
10. Единое поведение одинаковых controls.
11. Видимое состояние системы.
12. Пользователь видит только релевантные действия.
13. Недоступные действия не должны вводить в заблуждение.
14. Ошибка объясняет, что произошло и что делать дальше.
15. Никаких технических exception/error strings пользователю.

---

# 4. Design Tokens

## 4.1 Источник истины

Канонические значения токенов определены в `tmp-ui-shell/src/main/resources/com/tmp/ui/shell/theme/tmp-theme.css`.

Документ ссылается на **semantic token**, а не дублирует hex в каждом разделе.

## 4.2 Цветовая семантика

| Семантика | Назначение | Токены |
|---|---|---|
| DARK | shell, topbar | `-tmp-dark`, `-tmp-dark-hover` |
| BLUE | system accent, navigation, selected, focus, info | `-tmp-primary`, `-tmp-primary-hover`, `-tmp-primary-pressed`, `-tmp-primary-soft`, `-tmp-focus` |
| GREEN | executable/available action, success | `-tmp-action`, `-tmp-action-hover`, `-tmp-action-pressed`, `-tmp-action-soft`, `-tmp-success`, `-tmp-success-soft` |
| GRAY | neutral, secondary, disabled, inactive | `-tmp-bg`, `-tmp-surface`, `-tmp-surface-alt`, `-tmp-surface-hover`, `-tmp-text-secondary`, `-tmp-text-disabled`, `-tmp-border` |
| RED | destructive, error | `-tmp-danger`, `-tmp-danger-hover`, `-tmp-danger-soft` |
| ORANGE/YELLOW | warning, attention | `-tmp-warning`, `-tmp-warning-soft` |

## 4.3 Обязательные токены

Минимальный набор (MUST существовать в теме):

- фон и поверхности: `-tmp-bg`, `-tmp-surface`, `-tmp-surface-alt`, `-tmp-surface-hover`;
- текст: `-tmp-text`, `-tmp-text-secondary`, `-tmp-text-disabled`;
- границы: `-tmp-border`, `-tmp-border-strong`;
- primary/accent: `-tmp-primary`, `-tmp-primary-hover`, `-tmp-primary-pressed`, `-tmp-primary-soft`;
- action: `-tmp-action`, `-tmp-action-hover`, `-tmp-action-pressed`, `-tmp-action-soft`;
- статусы: `-tmp-success`, `-tmp-success-soft`, `-tmp-warning`, `-tmp-warning-soft`, `-tmp-danger`, `-tmp-danger-hover`, `-tmp-danger-soft`;
- focus: `-tmp-focus`.

## 4.4 Запрет raw colors

Business screen CSS **MUST NOT** использовать собственные hex/rgb цвета, если подходящий semantic token существует.

**MUST NOT:**

```css
-fx-background-color: #3F7F5F;
```

**MUST:**

```css
-fx-background-color: -tmp-action;
```

Raw color допустим **только**:

- при добавлении нового semantic token в Design System (`tmp-theme.css`);
- для специфического data visualization use case, не покрываемого общей palette (с документированием).

---

# 5. Typography

## 5.1 Шрифт

Primary font: **Segoe UI** с системным fallback (`-fx-font-family: "Segoe UI", system-ui, sans-serif`).

## 5.2 Шкала размеров

| Роль | Размер | Style class |
|---|---|---|
| Screen title | 22–24 px | `.tmp-screen-title` |
| Section title | 18 px | `.tmp-section-title` |
| Dialog title | 16–18 px | `.tmp-dialog-title` |
| Body / control | 13 px | default `.root` |
| Secondary | 12 px | `.tmp-text-small`, `.tmp-text-muted` |
| Metadata | 11–12 px | `.tmp-text-small` |

## 5.3 Запрещённые размеры

**MUST NOT** использовать произвольные размеры (14.5, 17, 19, 27 px и т.п.) без объективной причины и документирования.

---

# 6. Spacing

## 6.1 Шкала

Допустимые значения padding, gap, margin: **4, 8, 12, 16, 24, 32 px**.

## 6.2 Правило

**MUST** преимущественно использовать значения из шкалы. Случайные значения (7, 11, 19, 27 px) **MUST NOT** применяться без объективной причины.

---

# 7. Border Radius

| Элемент | Radius |
|---|---|
| Controls (button, input) | 6 px |
| Cards / panels / sections | 8 px |
| Dialogs / large surfaces | 8–10 px |
| Status badges | 4 px (pill/small rounded) |

**MUST NOT** задавать модуль-specific radius вне шкалы.

---

# 8. Screen Structure

## 8.1 Archetype обычного бизнес-экрана

```text
Screen
  ├── Header (title, optional subtitle, optional primary actions)
  ├── optional Toolbar / Filters
  └── Content Area
```

## 8.2 Reusable CSS classes

| Class | Назначение |
|---|---|
| `.tmp-screen` | корневой контейнер экрана |
| `.tmp-screen-header` | заголовочная зона |
| `.tmp-screen-title` | заголовок экрана |
| `.tmp-screen-subtitle` | подзаголовок / контекст |
| `.tmp-screen-actions` | primary actions в header |
| `.tmp-toolbar` | панель фильтров / команд |
| `.tmp-content` | основная рабочая область |
| `.tmp-content-compact` | bounded/compact content area (max ~760 px) |
| `.tmp-table-compact` | compact administration table (bounded width) |
| `.tmp-auth-form-field` | compact authentication form control (~250 px) |
| `.tmp-screen-compact` | compact variant (16 px padding) |

## 8.3 Outer padding

- Desktop normal: **24 px** (`.tmp-screen`).
- Compact workbench: **16 px** (`.tmp-screen-compact`) — только для плотных production screens.

**MUST NOT** использовать произвольный outer padding per module.

## 8.4 Bounded / compact content width

Не каждый экран обязан заполнять всю доступную ширину рабочей области.

Для следующих archetypes **SHOULD** использовать bounded/compact width, если растягивание не повышает информационную ценность:

- authentication forms (Login, activation);
- небольших administration forms;
- tables с малым количеством компактных колонок (Users, Roles).

Workbench screens (Warehouse, Production) **MAY** использовать всю рабочую площадь.

Administration screens (Users, Roles) **MAY** использовать compact/bounded data area (`.tmp-content-compact`, `.tmp-table-compact`).

Authentication form controls **SHOULD** использовать `.tmp-auth-form-field` (~250 px) и **MUST NOT** растягиваться на desktop width.

Bounded content **SHOULD** быть выровнен по левому краю; свободное место справа допустимо.

---

# 9. Screen Archetypes

Разрешённые archetypes (MUST выбирать подходящий, не унифицировать геометрию):

| Archetype | Примеры | Характер |
|---|---|---|
| A. LIST / ADMINISTRATION | Users, Roles, Security Audit | таблица + toolbar |
| B. MASTER–DETAIL | list + selected object details | SplitPane или двухколоночный layout |
| C. WORKBENCH | Warehouse, Production | плотная рабочая область, множество команд |
| D. FORM | создание/редактирование объекта | GridPane, label/control |
| E. DIALOG | короткое действие, подтверждение | modal Dialog |
| F. WIZARD | multi-step scenario | только когда действительно нужен последовательный workflow |

Стандарт обеспечивает **единый язык**, а не одинаковую геометрию всех экранов.

---

# 10. Buttons

## 10.1 Семантические типы

| Type | Class | Цвет | Примеры |
|---|---|---|---|
| ACTION | `.tmp-button-action` / `.tmp-button-primary` / default `.button` | green | Создать, Сохранить, Подтвердить |
| SECONDARY | `.tmp-button-secondary` | neutral | Отмена, Закрыть, Назад |
| DANGER | `.tmp-button-danger` | red | Удалить, irreversible destructive |
| GHOST | `.tmp-button-ghost` | no heavy background | Выход, auxiliary toolbar |

## 10.2 Priority

На одной компактной форме **SHOULD** быть только одно визуально главное ACTION, если сценарий имеет одно главное действие.

Workbench **MAY** содержать несколько action buttons с логической группировкой.

## 10.3 States

Каждая button **MUST** иметь: normal, hover, pressed, focused, disabled.

Disabled **MUST**:

- быть neutral gray;
- **NOT** выглядеть как зелёная/кликабельная;
- сохранять читаемый текст.

## 10.4 Icon buttons

Icon-only button допустима **только** если: действие общеизвестно, есть Tooltip, icon однозначна.

Для важных business actions **SHOULD** использовать icon + text или text only.

**MUST NOT** использовать emoji как иконки.

---

# 11. Icons

- **MUST** использовать JavaFX SVGPath или approved local vector assets.
- Monochrome, единый visual weight.
- Типовые размеры: **16, 18, 20 px**.
- **MUST NOT** использовать OS-dependent Unicode pictograms.
- **MUST NOT** использовать случайные PNG разных стилей.
- Icon **MUST NOT** быть единственным носителем значения критического действия.

---

# 12. Tables

## 12.1 Appearance

TableView **MUST** следовать глобальному стилю темы:

- header: clean, semibold, no heavy gradient;
- row height: ~36 px;
- hover: soft;
- selected: blue/system accent soft background (`-tmp-primary-soft`);
- empty: human-readable empty state (см. §25).

Table header **MUST** have a distinct neutral background darker than normal/alternating data rows. Column header and data rows **MUST NOT** use the same background token. Header background **MUST** use dedicated semantic token `-tmp-table-header-bg` (defined globally in `tmp-theme.css`). Header **MUST NOT** imitate selected/hover state.

**SHOULD NOT** использовать тяжёлую полную grid-сетку без специфической необходимости.

## 12.2 Column rules

- текстовые данные — left;
- числовые — right;
- short status — center/consistent;
- даты — единый формат;
- денежные значения — right;
- resizable behavior **SHOULD** учитывать содержимое и рабочее разрешение.

## 12.3 Row actions

Предпочтительный pattern TMP: **selection + ContextMenu** для object-specific administration actions.

**MUST NOT** дублировать одно действие через ContextMenu, column actions и toolbar одновременно.

---

# 13. Status Badges

STATUS **MUST** отображаться как badge, а не случайный цветной текст.

| Class | Семантика |
|---|---|
| `.tmp-badge` | base |
| `.tmp-badge-success` | ACTIVE, COMPLETED |
| `.tmp-badge-warning` | attention |
| `.tmp-badge-danger` | DELETED, CANCELLED |
| `.tmp-badge-info` | IN_PROGRESS, APPROVED |
| `.tmp-badge-neutral` | DRAFT, inactive |

Конкретный mapping бизнес-статусов определяется на Stage 3.x; визуальная система единая.

---

# 14. Forms

## 14.1 Structure

```text
Label → Control → optional help/error
```

Labels **MUST** быть короткими и единообразными. Input width определяется типом данных.

## 14.2 Layout

**SHOULD** использовать GridPane для label/control alignment.

Длинные формы **MUST NOT** строиться случайными HBox/VBox комбинациями, если GridPane решает задачу лучше.

## 14.3 Classes

| Class | Назначение |
|---|---|
| `.tmp-form` | form container |
| `.tmp-form-label` | field label |
| `.tmp-form-help` | help text |
| `.tmp-input-error` | error state on input |

## 14.4 Required fields

Единый pattern: **звёздочка в label** (`Логин *`).

**MUST NOT** смешивать звёздочки, «(обязательно)», красные borders и placeholder «required» без единого правила.

---

# 15. Validation

Validation error **MUST**:

- находиться рядом с формой;
- быть видима без нового dialog;
- не закрывать кнопки;
- не обрезаться;
- не содержать exception class names.

**MUST NOT** открывать recursive dialogs после validation error.

Использовать `.tmp-text-error`, `.tmp-input-error`, `.tmp-message-error`.

---

# 16. Dialogs

## 16.1 Structure

```text
Title → content → validation/error → actions
```

## 16.2 Button order (Windows/TMP)

Обычная форма: **[Primary/Action] [Cancel/Secondary]**

Danger confirmation: **[Удалить (danger)] [Отмена (secondary)]**

## 16.3 Sizing

Dialog **MUST**: autosize, сохранять buttons visible, переносить error text, иметь разумный min width.

**MUST NOT** задавать случайные фиксированные heights.

Dialog **MUST** применять `TmpTheme.apply(dialogPane)`.

---

# 17. Confirmation

- Обычное обратимое действие: простой confirmation, если действительно нужен.
- Опасное irreversible/high-impact: explicit confirmation.
- Typed confirmation (`УДАЛИТЬ`) **MAY** использоваться для особо опасных операций (как в Users).
- **MUST NOT** требовать typed confirmation для каждой мелочи.

---

# 18. Context Menu

Порядок:

```text
common actions
secondary actions
──────────────
destructive action (danger style: .tmp-menu-item-danger)
```

**MUST NOT** создавать ContextMenu из 15+ действий без группировки.

---

# 19. Tabs

TabPane **MUST** использовать глобальный стиль темы (`.tab-pane`).

- selected: blue/system accent;
- hover: soft neutral;
- consistent height.

Tabs **MUST NOT**:

- иметь индивидуальные цвета каждого модуля;
- использовать gradients;
- использовать arbitrary fonts.

Tabs **MUST NOT** использоваться для последовательного workflow (использовать Wizard).

---

# 20. Panels / Cards / Sections

Card **SHOULD** использоваться только для смыслового блока.

| Class | Назначение |
|---|---|
| `.tmp-card` | grouped block with surface, border, radius, optional shadow |
| `.tmp-section` | flat/alternate surface section |
| `.tmp-section-title` | section heading |
| `.tmp-toolbar` | section actions / filters |

Padding card: **16–24 px**.

**MUST NOT** оборачивать каждую таблицу и каждый Label в card без причины.

---

# 21. Toolbar

Toolbar **SHOULD** содержать: primary actions, filters, search, batch actions.

**MUST NOT** смешивать в одной полосе несвязанные команды, фильтры и destructive actions без grouping (spacing / separators).

---

# 22. Search / Filter

- Search: clear placeholder, standard size, **MUST NOT** запускать modal dialog.
- Filter: compact, понятная semantic label.
- Filter, меняющий только отображение, **SHOULD NOT** выполнять DB reload без необходимости.

---

# 23. Empty States

**MUST NOT** показывать стандартный «No content in table» в финальном UI.

Использовать human-readable empty state:

| Class | Назначение |
|---|---|
| `.tmp-empty-state` | container |
| `.tmp-empty-state-title` | primary message |
| `.tmp-empty-state-hint` | secondary hint |

**MUST NOT** использовать большие иллюстрации.

---

# 24. Loading State

При заметном времени операции **SHOULD** использовать ProgressIndicator, disabled relevant action, concise status.

**MUST NOT** блокировать весь интерфейс без необходимости.

**MUST NOT** показывать бесконечный spinner для instant local operations.

---

# 25. Success Feedback

**MUST NOT** показывать modal «Успешно» после каждой простой операции.

Предпочтение: изменение видно в UI; compact success message (`.tmp-text-success`, `.tmp-message-success`).

Toast/notification framework — out of scope Stage 3.0; правило зафиксировано для будущей инфраструктуры.

---

# 26. Error Feedback

| Уровень | Способ |
|---|---|
| FIELD ERROR | рядом с полем (`.tmp-text-error`, `.tmp-input-error`) |
| OPERATION ERROR | inline message / dialog (`.tmp-message-error`) |
| CRITICAL APP ERROR | user-friendly dialog + technical logging |

**MUST NOT** показывать пользователю: NullPointerException, SQLState, stack trace, AccessDeniedException class name.

---

# 27. Permission UX

- Navigation item без view permission: **HIDDEN**.
- Object action без permission: **HIDDEN** (preferred) или **DISABLED** с понятной причиной, если важно пользователю.
- Backend permission check **MUST** оставаться всегда.

## 27.1 Hidden vs Disabled

| Ситуация | Behavior |
|---|---|
| Нет permission | **HIDDEN** |
| Permission есть, но состояние объекта не позволяет | **DISABLED** или **HIDDEN** per object action standard |

**MUST NOT** использовать role-name hardcode в UI.

---

# 28. Shell / Sidebar

Stage 2 Shell — эталон. **MUST NOT** переделывать его в рамках business screen work.

- dark topbar (`-tmp-dark`);
- blue system accent;
- sidebar navigation;
- SVG navigation icons;
- current user, logout;
- permission-filtered navigation.

Будущие модули **MUST** интегрироваться в Shell, **MUST NOT** создавать собственную главную навигацию.

---

# 29. Window / Resize / Scroll

## 29.1 Window

- Minimum: **1024×700**.
- Target: 1366×768, 1600×900, Full HD.
- **MUST NOT** использовать фиксированный screen width.

## 29.2 Resize priority

1. Table/workspace grows.
2. Main content grows.
3. Fixed labels/buttons сохраняют разумный размер.
4. Sidebar сохраняет утверждённую ширину.
5. **MUST NOT** растягивать кнопки на сотни px без причины.

## 29.3 Scrolling

**MUST NOT** оборачивать весь экран в ScrollPane автоматически.

ScrollPane **SHOULD** применяться только к content area. Toolbar/header **SHOULD** оставаться видимыми.

---

# 30. SplitPane / TreeView / ListView / Controls

- SplitPane: допустим для master-detail/workbench; divider position — разумный default.
- TreeView / ListView: единый visual style темы (row height, hover, selected).
- ComboBox / DatePicker / Spinner: **MUST** использовать Design System; **MUST NOT** создавать custom control без необходимости.
- Scrollbar / Tooltip: следовать текущим Theme rules.

---

# 31. Hover / Animation

Допустимы: subtle hover, pressed, focus, selection, very short transition.

**MUST NOT**: decorative animations, bouncing, rotating, scaling, long fade, parallax, glassmorphism.

---

# 32. Accessibility

**MUST**:

- keyboard focus и navigation;
- readable contrast;
- disabled still readable;
- **NOT** кодировать статус только цветом;
- tooltip для icon-only action;
- text labels для critical actions.

---

# 33. Language / Terminology

- Runtime UI TMP — **русский**, кроме технически принятых терминов.
- **MUST NOT** смешивать «Users» и «Пользователи» в одном финальном UI.
- Один бизнес-объект = одно название по всему приложению.
- Glossary **MAY** ссылаться на domain terminology из project docs.

---

# 34. CSS Architecture

## 34.1 Global theme

`tmp-theme.css` — единственный источник colors, buttons, tables, inputs, badges, dialogs, tabs.

Применение: `TmpTheme.apply(scene|parent|dialogPane)`.

## 34.2 Local CSS policy

Разрешены: `tmp-theme.css`, shell-specific CSS, screen-specific CSS.

Screen CSS **MUST** содержать **только**:

- layout-specific classes;
- screen-specific geometry;
- уникальные визуальные структуры.

Screen CSS **MUST NOT** дублировать colors, buttons, tables, inputs, fonts, badges, dialog styles.

## 34.3 Inline CSS policy

**MUST NOT** использовать `style="..."` в FXML для постоянного визуального оформления.

**MUST NOT** использовать `node.setStyle(...)` для постоянного визуального оформления.

Допустимо только для truly dynamic visual property, если style classes / pseudo classes не подходят.

Предпочтение: `styleClass`, `pseudoClassStateChanged()`.

## 34.4 Pseudo-class standard

Dynamic states **SHOULD** использовать JavaFX pseudo classes (`:selected`, `:disabled`, custom `:error`).

**MUST NOT** создавать custom pseudo class без необходимости.

---

# 35. Reusable Style Classes (Design System)

Полный перечень канонических classes в `tmp-theme.css`:

**Screen:** `.tmp-screen`, `.tmp-screen-compact`, `.tmp-screen-header`, `.tmp-screen-title`, `.tmp-screen-subtitle`, `.tmp-screen-actions`, `.tmp-toolbar`, `.tmp-content`

**Sections:** `.tmp-section`, `.tmp-section-title`, `.tmp-card`, `.tmp-surface`

**Buttons:** `.tmp-button-action`, `.tmp-button-primary`, `.tmp-button-secondary`, `.tmp-button-danger`, `.tmp-button-ghost`

**Messages:** `.tmp-text-error`, `.tmp-text-warning`, `.tmp-text-success`, `.tmp-text-muted`, `.tmp-message-error`, `.tmp-message-success`, `.tmp-message-info`

**Badges:** `.tmp-badge`, `.tmp-badge-success`, `.tmp-badge-warning`, `.tmp-badge-danger`, `.tmp-badge-info`, `.tmp-badge-neutral`

**Forms:** `.tmp-form`, `.tmp-form-label`, `.tmp-form-help`, `.tmp-input-error`

**Empty state:** `.tmp-empty-state`, `.tmp-empty-state-title`, `.tmp-empty-state-hint`

**Menus:** `.tmp-menu-item-danger`

**Typography:** `.tmp-text-small`, `.tmp-text-disabled`, `.tmp-dialog-title`

---

# 36. UI Compliance Checklist

Каждый новый или изменённый screen **MUST** пройти checklist перед статусом UI-complete:

- [ ] Uses TmpTheme
- [ ] No raw colors when token exists
- [ ] No inline permanent CSS
- [ ] Uses spacing scale
- [ ] Uses standard typography
- [ ] Uses standard button semantics
- [ ] Disabled state correct
- [ ] Danger actions red
- [ ] Tables follow standard
- [ ] Status uses standard badge
- [ ] Errors are user-facing
- [ ] Dialog buttons remain visible
- [ ] Keyboard focus preserved
- [ ] Permission UX correct
- [ ] Resize tested at 1024×700
- [ ] Empty state is human-readable
- [ ] No business logic in controller styling
- [ ] No new UI dependency without architecture decision

---

# 37. Definition of Done for Future Screens

Business screen **не считается UI-complete**, пока не выполнен UI Compliance Checklist (§36).

При Stage 3.x agent **MUST** включать UI compliance в итоговый отчёт.

---

# 38. Screen Migration Plan

Рекомендуемый порядок модернизации business screens (implementation plan, не часть normative standard):

1. Stage 3.1 — Users
2. Stage 3.2 — Roles
3. Stage 3.3 — Security Audit
4. Stage 3.4 — Orders
5. Stage 3.5 — Warehouse
6. Stage 3.6 — Production

---

# 39. Orders (Stage 3.4)

Orders list and editor follow this operational UX. Commercial `OrderStatus` (`DRAFT` / `APPROVED` / `ACTIVE` / `CANCELLED`) remains the Order Management lifecycle and **MUST NOT** gain production-derived values.

## 39.1 Operational status

User-facing status is a separate read-model concept. Captions **MUST** be Russian; raw enums **MUST NOT** be shown.

| Operational status | Caption | Indicator |
|---|---|---|
| EDITING | Редактируется | neutral / gray |
| AWAITING_PRODUCTION | Ожидает производства | warning / yellow |
| IN_PRODUCTION | В производстве | info / blue |
| COMPLETED | Выполнен | success / green |
| PARTIALLY_COMPLETED | Частично выполнен | warning-strong / orange |
| CANCELLED | Отменён | danger / red |

Status cell **MUST** be a colored indicator **plus** text (not color-only, not emoji).

Derivation (manufacturing complete = all ordered items released; warehouse/shipment/installation are out of scope):

| Condition | Status |
|---|---|
| Commercial `DRAFT` or leftover `APPROVED` | Редактируется |
| `ACTIVE`, 0 manufactured | Ожидает производства |
| `ACTIVE`, partial manufactured, work remains | В производстве |
| All items manufactured | Выполнен |
| Cancelled with 0 manufactured | Отменён |
| Manufactured > 0 and remaining volume cancelled | Частично выполнен (terminal; not «Отменён») |

## 39.2 List

Header: **Заказы**. Subtitle: управление заказами и их производственным состоянием.

Actions: **+ Новый заказ** (primary), **Импорт** (secondary). No **Открыть** toolbar button.

Columns exactly: Номер заказа, Заказчик, Дата создания, Изделий, Статус. `customerRef` is not shown.

Date format: `dd.MM.yyyy HH:mm` in the application timezone. Empty customer: `—` / «Без заказчика».

Open: double-click on a non-empty row, or Enter on the selected row. Click selects only. Empty-space double-click does nothing.

## 39.3 Filters

- One quick search: order number **OR** customer name (partial, case-insensitive). Not persisted between sessions. Restored by in-session Back memento.
- Status checkboxes; default for a new user: all except «Отменён». Immediate refresh (small debounce allowed). Persisted per user.
- Customer Excel-like multi-select on stable `customerRef`; UI shows `customerName`. Select-all means no customer predicate. Persisted per user. Stale ids ignored.
- Period is mandatory (created-at). Presets: Сегодня, Последние 7 дней, Последние 30 дней (new-user default), Текущий месяц, Другой период. Dynamic presets recompute on login; custom stores exact dates. Half-open day bounds.

Persistent filters are server-side, keyed by immutable user id. Quick search, selected row, page index, and navigation history are session-only.

## 39.4 Editor lifecycle

User actions: **Сохранить**, **Передать в работу**, **Отменить заказ** (DRAFT), **Позиции заказа**.

**MUST NOT** show: Сохранить черновик, Провести, Утвердить.

Save creates/updates an editable `DRAFT`. Transfer to Work is one application operation (approve+activate) with confirmation that the order becomes fully immutable. After transfer, header / items / specifications are read-only; backend also rejects mutations. New production launch requires a new order, not Revision N+1.

## 39.5 Shell history

Topbar **← / →** (tooltips Назад / Вперёд) is a session-only browser-like history for content screens. Back/Forward do not push. Branching clears forward. Logout clears history. Restored routes re-check permissions. Orders list memento restores search, filters, page, and selected row.

Keyboard: Alt+Left / Alt+Right when they do not conflict with OS/JavaFX.

---

# 40. Связанные документы

- [UI/UX Specification](UI-UX-Specification.md) — архитектура UI
- [Development Guide](../17-Development-Guide/Development-Guide.md)
- [Code Quality Standards](../17-Development-Guide/Code%20Quality%20Standards.md)
- `tmp-ui-shell/.../theme/tmp-theme.css` — Design System implementation
- `tmp-ui-shell/.../theme/TmpTheme.java` — theme registration

---

# 41. Architecture Rules

**UI-STD-001** — All JavaFX visual implementation MUST comply with TMP UI Standard.

**UI-STD-002** — Modules MUST NOT define independent color, typography, spacing, or button appearance.

**UI-STD-003** — Business screen CSS MUST NOT duplicate Design System styles.

**UI-STD-004** — Inline permanent CSS in FXML or Java is forbidden.

**UI-STD-005** — Future screens MUST pass UI Compliance Checklist before UI-complete status.
