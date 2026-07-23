package com.tmp.ui.shell.screen.audit;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuditEventSummary;
import com.tmp.security.api.AuditQueryService;
import java.util.Objects;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
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

    private static final int PAGE_SIZE = 20;

    private final AuditQueryService auditQuery;
    private final ObservableList<AuditEventSummary> events = FXCollections.observableArrayList();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty operationFilter = new SimpleStringProperty("");
    private final IntegerProperty pageIndex = new SimpleIntegerProperty(0);
    private final IntegerProperty totalCount = new SimpleIntegerProperty(0);

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

    public IntegerProperty pageIndexProperty() {
        return pageIndex;
    }

    public IntegerProperty totalCountProperty() {
        return totalCount;
    }

    public void refresh() {
        errorMessage.set("");
        try {
            String operation = blankToNull(operationFilter.get());
            events.setAll(auditQuery.queryAuditEvents(
                    null, null, null, operation, pageIndex.get(), PAGE_SIZE));
            totalCount.set((int) auditQuery.countAuditEvents(null, null, null, operation));
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage());
            events.clear();
        } catch (RuntimeException ex) {
            errorMessage.set(ex.getMessage() == null ? "РћС€РёР±РєР° Р·Р°РіСЂСѓР·РєРё Р°СѓРґРёС‚Р°" : ex.getMessage());
            events.clear();
        }
    }

    public void nextPage() {
        int maxPage = Math.max(0, (totalCount.get() - 1) / PAGE_SIZE);
        if (pageIndex.get() < maxPage) {
            pageIndex.set(pageIndex.get() + 1);
            refresh();
        }
    }

    public void previousPage() {
        if (pageIndex.get() > 0) {
            pageIndex.set(pageIndex.get() - 1);
            refresh();
        }
    }

    public void applyFilters() {
        pageIndex.set(0);
        refresh();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
