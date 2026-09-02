package com.tmp.ui.shell.screen.audit;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuditEventSummary;
import com.tmp.security.api.AuditQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Read-only Security audit ViewModel.
 */
public final class SecurityAuditViewModel {

    static final int PAGE_SIZE = 20;
    static final String LOAD_ERROR_MESSAGE = "Не удалось загрузить журнал аудита";

    private final AuditQueryService auditQuery;
    private final ObservableList<AuditEventSummary> events = FXCollections.observableArrayList();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty operationFilter = new SimpleStringProperty("");
    private final ObjectProperty<LocalDate> fromDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> toDate = new SimpleObjectProperty<>();
    private final IntegerProperty pageIndex = new SimpleIntegerProperty(0);
    private final IntegerProperty totalCount = new SimpleIntegerProperty(0);
    private final BooleanProperty canGoPrevious = new SimpleBooleanProperty(false);
    private final BooleanProperty canGoNext = new SimpleBooleanProperty(false);
    private final BooleanProperty filtersActive = new SimpleBooleanProperty(false);
    private final StringProperty emptyStateTitle = new SimpleStringProperty("");
    private final StringProperty emptyStateHint = new SimpleStringProperty("");

    public SecurityAuditViewModel(AuditQueryService auditQuery) {
        this.auditQuery = Objects.requireNonNull(auditQuery, "auditQuery");
        refresh();
    }

    public ObservableList<AuditEventSummary> events() {
        return events;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty operationFilterProperty() {
        return operationFilter;
    }

    public ObjectProperty<LocalDate> fromDateProperty() {
        return fromDate;
    }

    public ObjectProperty<LocalDate> toDateProperty() {
        return toDate;
    }

    public IntegerProperty pageIndexProperty() {
        return pageIndex;
    }

    public IntegerProperty totalCountProperty() {
        return totalCount;
    }

    public BooleanProperty canGoPreviousProperty() {
        return canGoPrevious;
    }

    public BooleanProperty canGoNextProperty() {
        return canGoNext;
    }

    public BooleanProperty filtersActiveProperty() {
        return filtersActive;
    }

    public StringProperty emptyStateTitleProperty() {
        return emptyStateTitle;
    }

    public StringProperty emptyStateHintProperty() {
        return emptyStateHint;
    }

    public void refresh() {
        errorMessage.set("");
        updateFiltersActive();
        try {
            Instant from = toStartOfDayInstant(fromDate.get());
            Instant to = toEndOfDayInstant(toDate.get());
            String operation = blankToNull(operationFilter.get());
            events.setAll(auditQuery.queryAuditEvents(
                    from, to, null, operation, pageIndex.get(), PAGE_SIZE));
            totalCount.set((int) auditQuery.countAuditEvents(from, to, null, operation));
            updatePaginationFlags();
            updateEmptyState();
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage());
            events.clear();
            totalCount.set(0);
            updatePaginationFlags();
            updateEmptyState();
        } catch (IllegalArgumentException ex) {
            errorMessage.set("Неверный код операции. Используйте технический идентификатор, например USER_CREATED.");
            events.clear();
            totalCount.set(0);
            updatePaginationFlags();
            updateEmptyState();
        } catch (RuntimeException ex) {
            errorMessage.set(LOAD_ERROR_MESSAGE);
            events.clear();
            totalCount.set(0);
            updatePaginationFlags();
            updateEmptyState();
        }
    }

    public void nextPage() {
        if (canGoNext.get()) {
            pageIndex.set(pageIndex.get() + 1);
            refresh();
        }
    }

    public void previousPage() {
        if (canGoPrevious.get()) {
            pageIndex.set(pageIndex.get() - 1);
            refresh();
        }
    }

    public void applyFilters() {
        pageIndex.set(0);
        refresh();
    }

    public void resetFilters() {
        operationFilter.set("");
        fromDate.set(null);
        toDate.set(null);
        pageIndex.set(0);
        refresh();
    }

    String pageLabelText() {
        return "Страница " + (pageIndex.get() + 1) + " · " + totalCount.get() + " событий";
    }

    private void updatePaginationFlags() {
        int maxPage = Math.max(0, (totalCount.get() - 1) / PAGE_SIZE);
        canGoPrevious.set(pageIndex.get() > 0);
        canGoNext.set(pageIndex.get() < maxPage);
    }

    private void updateFiltersActive() {
        filtersActive.set(
                blankToNull(operationFilter.get()) != null
                        || fromDate.get() != null
                        || toDate.get() != null);
    }

    private void updateEmptyState() {
        if (filtersActive.get()) {
            emptyStateTitle.set("Событий не найдено");
            emptyStateHint.set("Измените условия фильтра.");
        } else {
            emptyStateTitle.set("Событий аудита пока нет");
            emptyStateHint.set("");
        }
    }

    static Instant toStartOfDayInstant(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    static Instant toEndOfDayInstant(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atTime(23, 59, 59, 999_999_999).atZone(ZoneId.systemDefault()).toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
