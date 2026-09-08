package com.tmp.production.testsupport;

import com.tmp.document.DefaultDocumentEngine;
import com.tmp.document.DefaultDocumentProcessorRegistry;
import com.tmp.document.TransactionAfterCommitEventPublisher;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.persistence.JdbcDocumentStorageAdapter;
import com.tmp.document.persistence.JdbcDocumentVersionAdapter;
import com.tmp.document.persistence.JdbcLifecycleJournalAdapter;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionSummary;
import com.tmp.warehouse.application.WarehouseResponsibilityGuard;
import com.tmp.warehouse.application.WarehouseTransferDocumentService;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseTransferDocumentRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Production-module test doubles for Warehouse responsibility / auth helpers formerly shipped in
 * Warehouse main sources.
 */
public final class WarehouseTestDoubles {

    private WarehouseTestDoubles() {}

    public static WarehouseResponsibilityGuard permitAllResponsibility() {
        return warehouseId -> {};
    }

    public static AuthenticationService unauthenticated() {
        return Unauthenticated.INSTANCE;
    }

    public static WarehouseTransferDocumentService transferDocumentService(
            JdbcTemplate jdbc,
            Clock clock,
            WarehouseCatalogRepository catalog,
            MaterialReferenceRepository materials,
            WarehouseResponsibilityGuard responsibilityGuard,
            TransactionTemplate tx) {
        Objects.requireNonNull(tx, "tx");
        var repository = new JdbcWarehouseTransferDocumentRepository(jdbc, clock);
        DocumentEngine documentEngine =
                new DefaultDocumentEngine(
                        new DefaultDocumentProcessorRegistry(),
                        new JdbcDocumentStorageAdapter(jdbc),
                        new JdbcLifecycleJournalAdapter(jdbc),
                        new JdbcDocumentVersionAdapter(jdbc),
                        new TransactionAfterCommitEventPublisher());
        documentEngine.registerProcessor(new WarehouseTransferDocumentProcessor(repository));
        return new WarehouseTransferDocumentService(
                documentEngine, repository, catalog, materials, responsibilityGuard, tx);
    }

    private static final class Unauthenticated implements AuthenticationService {
        private static final Unauthenticated INSTANCE = new Unauthenticated();

        @Override
        public SessionSummary login(Login login, char[] password) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public SessionSummary completePasswordSetup(
                Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void logout() {}

        @Override
        public Optional<SessionSummary> currentSession() {
            return Optional.empty();
        }

        @Override
        public boolean isAuthenticated() {
            return false;
        }
    }
}
