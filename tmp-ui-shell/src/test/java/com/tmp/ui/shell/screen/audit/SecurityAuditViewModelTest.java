package com.tmp.ui.shell.screen.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.AuditEventSummary;
import com.tmp.security.api.AuditQueryService;
import com.tmp.security.api.UserId;
import java.time.Instant;
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
        viewModel.nextPage();
        assertEquals(1, viewModel.pageIndexProperty().get());
        assertEquals(5, viewModel.events().size());
        viewModel.operationFilterProperty().set("OP-1");
        viewModel.applyFilters();
        assertEquals(0, viewModel.pageIndexProperty().get());
        assertEquals("OP-1", audit.lastOperationFilter);
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

        @Override
        public List<AuditEventSummary> queryAuditEvents(
                Instant from,
                Instant to,
                UserId actorUserId,
                String operation,
                int pageIndex,
                int pageSize) {
            lastOperationFilter = operation;
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
            lastOperationFilter = operation;
            return events.stream()
                    .filter(e -> operation == null || e.operation().equals(operation))
                    .count();
        }
    }
}
