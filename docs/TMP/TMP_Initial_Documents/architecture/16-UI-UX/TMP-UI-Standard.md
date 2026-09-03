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
| BLUE | system accent, navigation, focus, info | `-tmp-primary`, `-tmp-primary-hover`, `-tmp-primary-pressed`, `-tmp-primary-soft`, `-tmp-focus` |
| GREEN (table) | TableView selected row | `-tmp-table-selection-bg`, `-tmp-table-selection-hover-bg` |
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
- selected: soft translucent green background (`-tmp-table-selection-bg`); visible for both focused and unfocused TableView; selected text remains normal readable `-tmp-text`. Screen-specific TableView selection colors are **prohibited** — global `tmp-theme.css` is the source of truth.
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
- TreeView / ListView: единый visual style темы (row height, hover); selection follows soft translucent green TableView rule when applicable.
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
| EDITING | Редактируется | neutral / gray indicator |
| AWAITING_PRODUCTION | Ожидает производства | bright amber indicator |
| IN_PRODUCTION | В производстве | bright blue indicator |
| COMPLETED | Выполнен | bright green indicator |
| PARTIALLY_COMPLETED | Частично выполнен | bright orange indicator |
| CANCELLED | Отменён | bright red indicator |
| STATUS_UNAVAILABLE | Статус недоступен | muted / unavailable indicator |

Status cell **MUST** be a colored indicator **plus** text (not color-only, not emoji). Indicator colors use dedicated `-tmp-status-indicator-*` tokens and **MUST NOT** reuse/brighten generic `-tmp-success` / `-tmp-danger` / `-tmp-warning` / `-tmp-info` message/button palette.

`STATUS_UNAVAILABLE` is presentation-only. It **MUST NOT** be added to commercial `OrderStatus`. It means Production facts could not be read (AccessDenied or technical failure). The UI **MUST NOT** treat a failed Production read as zero manufactured / «Ожидает производства». Unavailable rows remain in the list and are always included by the status filter (no dedicated checkbox for this rare state).

Derivation (manufacturing complete = all ordered items released; warehouse/shipment/installation are out of scope):

| Condition | Status |
|---|---|
| Commercial `DRAFT` or leftover `APPROVED` | Редактируется |
| `ACTIVE`, Production facts unavailable | Статус недоступен |
| `ACTIVE`, 0 manufactured (confirmed Production facts) | Ожидает производства |
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
- Status checkboxes; default for a new user: all except «Отменён» and «Статус недоступен». Immediate refresh (small debounce allowed). Persisted per user. **At least one status checkbox MUST remain selected**; the last selected checkbox cannot be cleared (disabled or immediately re-selected). Empty/corrupt persisted status sets fall back to defaults.
- Customer Excel-like multi-select on stable `customerRef`; UI shows `customerName`. Select-all means no customer predicate. Persisted per user. Customer option catalogue is **period-independent** (distinct known customers from Order Management across all Orders); **one `customerRef` = one option**, display name = latest known name from the newest Order. Period and customer filters are independent. Reconciliation runs only after a **successful** catalogue load: `persisted ∩ known`; a customer without Orders in the current period is **not** stale. True stale refs are dropped and cleaned preference may be rewritten once. If a subset becomes empty after successful reconciliation, the filter returns to «Заказчики: Все» (no invisible filter). Catalogue load failure must not clear selection, must not rewrite preference, must surface a user-facing error (technical exception logged), and **must not open** the customer popup. Empty option rows must not be interpreted as «select all».
- Period is mandatory (created-at). Presets: Сегодня, Последние 7 дней, Последние 30 дней (new-user default), Текущий месяц, Другой период. Dynamic presets recompute on login; custom stores exact dates. Half-open day bounds.

Persistent filters are server-side, keyed by immutable user id. Quick search, selected row, page index, and navigation history are session-only.

## 39.4 Editor lifecycle

User actions: **Сохранить**, **Передать в работу**, **Отменить заказ** (DRAFT), **Позиции заказа**.

**MUST NOT** show: Сохранить черновик, Провести, Утвердить.

**+ Новый заказ** always opens an explicit CREATE route (`openCreate()`), never reopens a previous `currentOrderId` from the long-lived editor ViewModel. Create route requires `order.order.create`. After the first successful Save, Shell history replaces the create entry with a stable existing-order entry so Back/Forward cannot reopen a blank create form.

Save creates/updates an editable `DRAFT`. Transfer to Work is one application operation (approve+activate) with confirmation that the order becomes fully immutable. After transfer, header / items / specifications are read-only; backend also rejects mutations. New production launch requires a new order, not Revision N+1.

## 39.5 Shell history

Topbar **← / →** (tooltips Назад / Вперёд) is a session-only browser-like history for content screens. Back/Forward do not push. Branching clears forward. Logout clears history. Restored routes re-check permissions. Orders list memento restores search, filters, page, and selected row. Order item list memento restores page index and selected item after Back from an item card.

Keyboard: Alt+Left / Alt+Right when they do not conflict with OS/JavaFX.

## 39.6 Order items list

Header: **Позиции заказа**.

Actions: **Создать позицию** only when the parent Order is `DRAFT` and the user has create permission. No permanent **Открыть** toolbar button.

Columns exactly: Код позиции, Наименование, Количество, Статус. Do **not** show «Активная редакция» or commercial `ACTIVE`/`DRAFT` as the Status column value.

Status cell uses the same operational indicator pattern as the Orders list (`OperationalStatusIndicator`: colored dot + Russian caption), derived via `OrderItemOperationalStatusDeriver` from parent Order status, commercial item status, and a discriminated Production read result (`ItemProductionReadResult`):

- successful query with item state → derive from Production facts;
- successful query with no Production row (not yet accepted) → **Ожидает производства**;
- AccessDenied / technical failure → **Статус недоступен** — never invent zeros as «Ожидает производства».

Quantity comes from the item editor snapshot (`orderedQuantity`), formatted without trailing zeros. Missing snapshot → empty cell.

Pagination footer (TMP Standard): **← Предыдущая** / `Страница N · M позиций` / **Следующая →**. Default page size 50. Production item states for the visible page are loaded via one batch Public Query (`getItemProductionStatesByOrderId`), not N+1.

Open: double-click a non-empty row, or Enter on the selected row. Single click selects only. Empty-area double-click does nothing.

Table uses constrained column resize; Наименование gets the larger share of width.

## 39.7 Order item card

Title: **Позиция {productCode}** or **Новая позиция**. Header status is an operational indicator graphic (not raw commercial enum text). Uses the same `ItemProductionReadResult` / `OrderItemOperationalStatusDeriver` semantics as the item list.

Editable while parent Order is `DRAFT`: Код позиции, Наименование, Комментарий, Внешний номер позиции, Количество изделий.

Visible actions when editable: **Сохранить** (application facade `saveNewItem` / `saveExistingItem` — not a technical begin/save/post chain in the controller), **Открыть спецификацию** (resolves current draft-or-active via `CurrentOrderItemSpecificationUiService`), **Отменить позицию** (danger secondary, when cancel is allowed).

**MUST NOT** show: active/draft revision labels, copy-from-revision, commercial draft/post, create revision, save qty draft, post revision, approve, separate open-active / open-draft specification buttons.

After transfer (parent not `DRAFT`): fields are read-only labels; only **Открыть спецификацию** (plus shell history) remains. Hide Save and Cancel — no wall of disabled technical buttons.

## 39.8 Specification editor

Title: **Спецификация позиции {productCode}**. Subtitle/label: **Количество изделий: N** (read-only; quantity edits belong on the item card). Spec persistence preserves the existing ordered quantity from the snapshot.

**MUST NOT** show: revision number / revision status, permanent line form under the table, permanent Добавить/Изменить/Удалить/Выше/Ниже/Очистить/Сохранить черновик/Провести изменение button wall.

Editable UX: secondary **+ Добавить материал** opens a TMP-themed modal (Артикул, Наименование, Цвет, Длина, мм, Количество, Единица измерения). Double-click / Enter edits the selected row via the same dialog. Context menu: Изменить, Удалить, Переместить выше, Переместить ниже (row order has business meaning — line numbers on save). Delete uses a simple OK/Cancel confirmation (no typed DELETE). After add/edit/delete/move, persist immediately through the existing revision-update document API internally; the user does not see Save draft / Post.

Table width: compact Цвет / Длина, мм / Количество / Единица измерения (bounded max widths). Remaining space is shared by **Артикул (~35%)** and **Наименование (~65%)** — Unit must not absorb leftover width. Display decimals via `DecimalUiFormat`.

Read-only: hide add button and mutation context menu; double-click does nothing; table remains selectable/scrollable.

## 39.9 Order import

Single-screen import-first workflow (not a multi-step wizard).

Header: **Импорт заказа**. Subtitle: загрузка заказов и спецификаций из файла расчётной программы. Requires `order.order.create`.

Empty state: **Выбрать файл** + STXT/TXT hint. No empty read-only filename TextField. Selecting a file auto-runs parse + preview validation (no permanent **Проверить**). Selected file shows the file name with **Выбрать другой файл**; full path may be a tooltip.

After preview: readiness status (**Готов к импорту** / warnings / blocked), multi-order-aware summary (`orderCount`, numbers, positions, products, specification lines), and one unified **Замечания проверки** table (Тип / Где / Сообщение; errors first). No problems → compact «Ошибок и предупреждений нет» (no large empty tables). Errors block Import; warnings do not.

Import confirmation explains existing ACTIVE / immutable landing (business semantics unchanged). Success state: single order → **Открыть заказ** / **К списку заказов** / **Импортировать ещё**; multi-order → list + import another (no arbitrary open-first). Shell **← Назад** replaces a duplicate local Cancel when history is available.

## 39.10 Global selection and status presentation

TableView selection uses soft translucent green (`-tmp-table-selection-bg`) from the global theme for both focused and unfocused tables. Screen-specific selection colors are prohibited.

Operational status cells and headers reuse `OperationalStatusIndicator` (Circle radius 6 + caption) with dedicated `tmp-status-dot-*` classes. Do not reuse/brighten the generic `-tmp-success` / `-tmp-danger` / `-tmp-warning` / `-tmp-info` message/button palette for status dots.

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
