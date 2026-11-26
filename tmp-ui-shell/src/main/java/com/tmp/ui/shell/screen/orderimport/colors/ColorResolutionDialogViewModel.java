package com.tmp.ui.shell.screen.orderimport.colors;

import com.tmp.order.api.imports.SpecificationColorConstraint;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ResolvedColorPair;
import com.tmp.reference.api.SourceColorResolution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Presentation state for the unified SO7 color resolution dialog (COL-041A). Delegates mutations to
 * {@link ColorResolutionSession}; does not reimplement business rules.
 */
public final class ColorResolutionDialogViewModel {

    /** Table filter: rows needing attention vs all colored lines. */
    public enum DisplayFilter {
        NEEDS_CHOICE,
        ALL
    }

    private ColorResolutionSession session;
    private DisplayFilter filter = DisplayFilter.NEEDS_CHOICE;
    private String searchText = "";
    private final Map<ColorResolutionRow.Id, UUID> draftInside = new HashMap<>();
    private final Map<ColorResolutionRow.Id, UUID> draftOutside = new HashMap<>();
    private Runnable changeListener;

    public ColorResolutionDialogViewModel(ColorResolutionSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public void setOnChange(Runnable listener) {
        this.changeListener = listener;
    }

    public ColorResolutionSession session() {
        return session;
    }

    public DisplayFilter filter() {
        return filter;
    }

    public String searchText() {
        return searchText;
    }

    public void setFilter(DisplayFilter newFilter) {
        Objects.requireNonNull(newFilter, "newFilter");
        if (filter == newFilter) {
            return;
        }
        filter = newFilter;
        pruneSelectionToVisible();
        fireChange();
    }

    public void setSearchText(String text) {
        String normalized = text == null ? "" : text;
        if (searchText.equals(normalized)) {
            return;
        }
        searchText = normalized;
        pruneSelectionToVisible();
        fireChange();
    }

    public List<ColorResolutionRow> visibleRows() {
        String needle = searchText.trim().toLowerCase(Locale.ROOT);
        List<ColorResolutionRow> result = new ArrayList<>();
        for (ColorResolutionRow row : session.rows()) {
            if (filter == DisplayFilter.NEEDS_CHOICE && row.userState() == ColorResolutionRow.UserState.MATCHED) {
                continue;
            }
            if (!needle.isEmpty()) {
                if (!row.sourceText().toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
            }
            result.add(row);
        }
        return result;
    }

    public boolean hasVisibleRows() {
        return !visibleRows().isEmpty();
    }

    public void setRowHighlighted(ColorResolutionRow.Id rowId, boolean highlighted) {
        Objects.requireNonNull(rowId, "rowId");
        Set<ColorResolutionRow.Id> selected = highlightedIds();
        if (highlighted) {
            selected.add(rowId);
        } else {
            selected.remove(rowId);
        }
        replaceSession(session.withSelection(selected));
    }

    public boolean isRowHighlighted(ColorResolutionRow.Id rowId) {
        ColorResolutionRow row = findRow(rowId);
        return row != null && row.highlighted();
    }

    public List<ColorResolutionRow> highlightedVisibleRows() {
        Set<ColorResolutionRow.Id> visible = visibleRowIds();
        List<ColorResolutionRow> result = new ArrayList<>();
        for (ColorResolutionRow row : session.rows()) {
            if (row.highlighted() && visible.contains(row.id())) {
                result.add(row);
            }
        }
        return result;
    }

    public boolean canApplyMassPairToHighlighted(UUID insideColorId, UUID outsideColorId) {
        return insideColorId != null && outsideColorId != null && !highlightedVisibleRows().isEmpty();
    }

    public void applyMassPairToHighlighted(UUID insideColorId, UUID outsideColorId) {
        replaceSession(session.applyCatalogPairToHighlighted(insideColorId, outsideColorId));
    }

    public boolean canApplyMassPairToSourceKey(UUID insideColorId, UUID outsideColorId) {
        if (insideColorId == null || outsideColorId == null) {
            return false;
        }
        List<ColorResolutionRow> highlighted = highlightedVisibleRows();
        if (highlighted.isEmpty()) {
            return false;
        }
        String key = highlighted.get(0).sourceKey();
        for (ColorResolutionRow row : highlighted) {
            if (!key.equals(row.sourceKey())) {
                return false;
            }
        }
        return true;
    }

    public void applyMassPairToSourceKey(UUID insideColorId, UUID outsideColorId) {
        List<ColorResolutionRow> highlighted = highlightedVisibleRows();
        if (highlighted.isEmpty()) {
            return;
        }
        String key = highlighted.get(0).sourceKey();
        replaceSession(session.applyCatalogPairToSourceKey(key, insideColorId, outsideColorId));
    }

    public boolean canKeepSourceTextForHighlighted() {
        return !highlightedVisibleRows().isEmpty();
    }

    public void keepSourceTextForHighlighted() {
        replaceSession(session.keepSourceTextForHighlighted());
    }

    public void keepSourceTextForAllNeedsChoice() {
        replaceSession(session.keepSourceTextForAllNeedsChoice());
    }

    public void applyCustomColorToHighlighted(String customColor) {
        replaceSession(session.applyCustomColorToHighlighted(customColor));
    }

    public void toggleSaveMapping(ColorResolutionRow.Id rowId, boolean selected) {
        Objects.requireNonNull(rowId, "rowId");
        Set<ColorResolutionRow.Id> previous = highlightedIds();
        ColorResolutionSession next =
                session.withSelection(Set.of(rowId)).setSaveMappingForAvailableHighlighted(selected);
        session = next.withSelection(previous);
        fireChange();
    }

    public void applyInsideColor(ColorResolutionRow.Id rowId, UUID insideColorId) {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(insideColorId, "insideColorId");
        ColorResolutionRow row = requireRow(rowId);
        ResolvedColorPair existing = row.catalogPair();
        if (existing != null) {
            applyPairToSingleRow(rowId, insideColorId, existing.outsideColorId());
            return;
        }
        UUID outsideDraft = draftOutside.get(rowId);
        if (outsideDraft != null) {
            applyPairToSingleRow(rowId, insideColorId, outsideDraft);
            clearDraft(rowId);
        } else {
            draftInside.put(rowId, insideColorId);
            fireChange();
        }
    }

    public void applyOutsideColor(ColorResolutionRow.Id rowId, UUID outsideColorId) {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(outsideColorId, "outsideColorId");
        ColorResolutionRow row = requireRow(rowId);
        ResolvedColorPair existing = row.catalogPair();
        if (existing != null) {
            applyPairToSingleRow(rowId, existing.insideColorId(), outsideColorId);
            return;
        }
        UUID insideDraft = draftInside.get(rowId);
        if (insideDraft != null) {
            applyPairToSingleRow(rowId, insideDraft, outsideColorId);
            clearDraft(rowId);
        } else {
            draftOutside.put(rowId, outsideColorId);
            fireChange();
        }
    }

    public void cancelDraft(ColorResolutionRow.Id rowId) {
        if (draftInside.remove(rowId) == null && draftOutside.remove(rowId) == null) {
            return;
        }
        fireChange();
    }

    public UUID draftInsideId(ColorResolutionRow.Id rowId) {
        return draftInside.get(rowId);
    }

    public UUID draftOutsideId(ColorResolutionRow.Id rowId) {
        return draftOutside.get(rowId);
    }

    public String displayCodeForColorId(UUID colorId) {
        if (colorId == null) {
            return "";
        }
        for (ColorDto color : session.activeColors()) {
            if (color.id().equals(colorId)) {
                return color.code();
            }
        }
        return "";
    }

    public String insideDisplay(ColorResolutionRow row) {
        UUID draft = draftInside.get(row.id());
        if (draft != null) {
            return displayCodeForColorId(draft);
        }
        ResolvedColorPair pair = row.catalogPair();
        return pair == null ? "" : pair.insideCode();
    }

    public String outsideDisplay(ColorResolutionRow row) {
        UUID draft = draftOutside.get(row.id());
        if (draft != null) {
            return displayCodeForColorId(draft);
        }
        ResolvedColorPair pair = row.catalogPair();
        return pair == null ? "" : pair.outsideCode();
    }

    public String userStateLabel(ColorResolutionRow.UserState state) {
        return switch (state) {
            case MATCHED -> "Сопоставлено";
            case NEEDS_CHOICE -> "Требует выбора";
            case MANUALLY_CHANGED -> "Изменено вручную";
        };
    }

    public String constraintErrorMessage(ColorResolutionRow row) {
        SpecificationColorConstraint.Result result = row.constraintResult();
        if (result == null || result.isAllowed()) {
            return "";
        }
        return switch (result.rejectionReason()) {
            case BLANK -> "Укажите цвет";
            case TOO_LONG -> "Цвет длиннее 64 символов";
            case PLACEHOLDER -> "Значение „" + row.finalColor() + "“ не допускается как цвет";
        };
    }

    public boolean hasMappingSaveConflicts() {
        return !session.mappingSaveConflicts().isEmpty();
    }

    public String mappingSaveConflictWarning() {
        return "Для одного текста СО7 выбраны разные пары — сопоставление не будет сохранено";
    }

    public boolean isApplyEnabled() {
        return session.isReady();
    }

    public boolean showSaveMappingColumn() {
        return session.globalSaveAllowed();
    }

    public String saveMappingUnavailableTooltip(ColorResolutionRow row) {
        if (row.lookupStatus() == SourceColorResolution.Status.MAPPING_INACTIVE) {
            return "Существующее соответствие меняется через «Справочники → Цвета»";
        }
        if (row.userState() == ColorResolutionRow.UserState.MATCHED) {
            return "Сопоставление уже задано справочником";
        }
        if (row.choiceMode() == ColorResolutionRow.ChoiceMode.SOURCE_TEXT
                || row.choiceMode() == ColorResolutionRow.ChoiceMode.CUSTOM_COLOR) {
            return "Сохранение доступно только для пары цветов каталога TMP";
        }
        return "";
    }

    public String productLineLabel(ColorResolutionRow row) {
        String pos = nullToEmpty(row.externalPositionNumber());
        String code = nullToEmpty(row.productCode());
        String name = nullToEmpty(row.productName());
        return pos + " / " + code + " / " + name;
    }

    private void applyPairToSingleRow(ColorResolutionRow.Id rowId, UUID insideColorId, UUID outsideColorId) {
        Set<ColorResolutionRow.Id> previous = highlightedIds();
        session = session.withSelection(Set.of(rowId)).applyCatalogPairToHighlighted(insideColorId, outsideColorId);
        session = session.withSelection(previous);
        fireChange();
    }

    private void pruneSelectionToVisible() {
        Set<ColorResolutionRow.Id> visible = visibleRowIds();
        Set<ColorResolutionRow.Id> selected = new HashSet<>();
        for (ColorResolutionRow row : session.rows()) {
            if (row.highlighted() && visible.contains(row.id())) {
                selected.add(row.id());
            }
        }
        ColorResolutionSession next = session.withSelection(selected);
        if (next != session) {
            session = next;
        }
    }

    private Set<ColorResolutionRow.Id> visibleRowIds() {
        return visibleRows().stream().map(ColorResolutionRow::id).collect(Collectors.toSet());
    }

    private Set<ColorResolutionRow.Id> highlightedIds() {
        Set<ColorResolutionRow.Id> selected = new HashSet<>();
        for (ColorResolutionRow row : session.rows()) {
            if (row.highlighted()) {
                selected.add(row.id());
            }
        }
        return selected;
    }

    private ColorResolutionRow findRow(ColorResolutionRow.Id rowId) {
        for (ColorResolutionRow row : session.rows()) {
            if (row.id().equals(rowId)) {
                return row;
            }
        }
        return null;
    }

    private ColorResolutionRow requireRow(ColorResolutionRow.Id rowId) {
        ColorResolutionRow row = findRow(rowId);
        if (row == null) {
            throw new IllegalArgumentException("Unknown row id: " + rowId);
        }
        return row;
    }

    private void clearDraft(ColorResolutionRow.Id rowId) {
        draftInside.remove(rowId);
        draftOutside.remove(rowId);
    }

    private void replaceSession(ColorResolutionSession next) {
        session = Objects.requireNonNull(next, "next");
        fireChange();
    }

    private void fireChange() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}