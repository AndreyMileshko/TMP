package com.tmp.ui.shell.screen.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.AuditEventSummary;
import com.tmp.security.api.AuditQueryService;
import com.tmp.security.api.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecurityAuditViewModelTest {

    @Test
    void refreshAndPaginationDelegate() {
        FakeAudit audit = new FakeAudit();
        for (int i = 0; i < 25; i++) {
            audit.events.add(event("OP-" + i));
        }
        SecurityAuditViewModel viewModel = new SecurityAuditViewModel(audit);
        assertEquals(20, viewModel.events().size());
        assertEquals(25, viewModel.totalCountProperty().get());
        assertFalse(viewModel.canGoPreviousProperty().get());
        assertTrue(viewModel.canGoNextProperty().get());
        assertEquals("Страница 1 · 25 событий", viewModel.pageLabelText());

        viewModel.nextPage();
        assertEquals(1, viewModel.pageIndexProperty().get());
        assertEquals(5, viewModel.events().size());
        assertTrue(viewModel.canGoPreviousProperty().get());
        assertFalse(viewModel.canGoNextProperty().get());
        assertEquals("Страница 2 · 25 событий", viewModel.pageLabelText());

        viewModel.operationFilterProperty().set("OP-1");
        viewModel.applyFilters();
        assertEquals(0, viewModel.pageIndexProperty().get());
        assertEquals("OP-1", audit.lastOperationFilter);
    }

    @Test
    void resetFiltersClearsStateAndReloadsFirstPage() {
        FakeAudit audit = new FakeAudit();
        audit.events.add(event("USER_CREATED"));
        SecurityAuditViewModel viewModel = new SecurityAuditViewModel(audit);
        viewModel.operationFilterProperty().set("USER_CREATED");
        viewModel.fromDateProperty().set(LocalDate.of(2026, 1, 1));
        viewModel.toDateProperty().set(LocalDate.of(2026, 12, 31));
        viewModel.nextPage();
        viewModel.resetFilters();

        assertEquals("", viewModel.operationFilterProperty().get());
        assertEquals(null, viewModel.fromDateProperty().get());
        assertEquals(null, viewModel.toDateProperty().get());
        assertEquals(0, viewModel.pageIndexProperty().get());
        assertEquals(null, audit.lastFrom);
        assertEquals(null, audit.lastTo);
        assertEquals(null, audit.lastOperationFilter);
        assertFalse(viewModel.filtersActiveProperty().get());
    }

    @Test
    void dateFiltersConvertToDayBoundaries() {
        FakeAudit audit = new FakeAudit();
        SecurityAuditViewModel viewModel = new SecurityAuditViewModel(audit);
        LocalDate day = LocalDate.of(2026, 9, 2);
        viewModel.fromDateProperty().set(day);
        viewModel.toDateProperty().set(day);
        viewModel.applyFilters();

        assertEquals(SecurityAuditViewModel.toStartOfDayInstant(day), audit.lastFrom);
        assertEquals(SecurityAuditViewModel.toEndOfDayInstant(day), audit.lastTo);
    }

    @Test
    void emptyStateReflectsActiveFilters() {
        FakeAudit audit = new FakeAudit();
        SecurityAuditViewModel viewModel = new SecurityAuditViewModel(audit);
        assertEquals("Событий аудита пока нет", viewModel.emptyStateTitleProperty().get());
        assertEquals("", viewModel.emptyStateHintProperty().get());

        viewModel.operationFilterProperty().set("USER_CREATED");
        viewModel.applyFilters();
        assertEquals("Событий не найдено", viewModel.emptyStateTitleProperty().get());
        assertEquals("Измените условия фильтра.", viewModel.emptyStateHintProperty().get());
    }

    @Test
    void invalidOperationShowsFriendlyError() {
        FakeAudit audit = new FakeAudit();
        audit.failOnOperation = "BROKEN";
        SecurityAuditViewModel viewModel = new SecurityAuditViewModel(audit);
        viewModel.operationFilterProperty().set("BROKEN");
        viewModel.applyFilters();

        assertTrue(viewModel.errorMessageProperty().get().contains("Неверный код операции"));
        assertTrue(viewModel.events().isEmpty());
    }

    private static AuditEventSummary event(String operation) {
        return new AuditEventSummary(
                AuditEventId.of(UUID.randomUUID()),
                Instant.parse("2026-07-23T04:00:00Z"),
                null,
                "admin",
                operation,
                "USER",
                "id",
                "desc",
                "SUCCESS");
    }

    private static final class FakeAudit implements AuditQueryService {
        private final List<AuditEventSummary> events = new ArrayList<>();
        private String lastOperationFilter;
        private Instant lastFrom;
        private Instant lastTo;
        private String failOnOperation;

        @Override
        public List<AuditEventSummary> queryAuditEvents(
                Instant from,
                Instant to,
                UserId actorUserId,
                String operation,
                int pageIndex,
                int pageSize) {
            lastFrom = from;
            lastTo = to;
            lastOperationFilter = operation;
            if (failOnOperation != null && failOnOperation.equals(operation)) {
                throw new IllegalArgumentException("No enum constant");
            }
            List<AuditEventSummary> filtered = events.stream()
                    .filter(e -> operation == null || e.operation().equals(operation))
                    .toList();
            int fromIdx = pageIndex * pageSize;
            int toIdx = Math.min(filtered.size(), fromIdx + pageSize);
            if (fromIdx >= filtered.size()) {
                return List.of();
            }
            return filtered.subList(fromIdx, toIdx);
        }

        @Override
        public long countAuditEvents(Instant from, Instant to, UserId actorUserId, String operation) {
            lastFrom = from;
            lastTo = to;
            lastOperationFilter = operation;
            if (failOnOperation != null && failOnOperation.equals(operation)) {
                throw new IllegalArgumentException("No enum constant");
            }
            return events.stream()
                    .filter(e -> operation == null || e.operation().equals(operation))
                    .count();
        }
    }
}
