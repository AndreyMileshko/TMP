package com.tmp.warehouse.testsupport;

import com.tmp.document.DefaultDocumentEngine;
import com.tmp.document.DefaultDocumentProcessorRegistry;
import com.tmp.document.TransactionAfterCommitEventPublisher;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
import com.tmp.document.persistence.JdbcDocumentStorageAdapter;
import com.tmp.document.persistence.JdbcDocumentVersionAdapter;
import com.tmp.document.persistence.JdbcLifecycleJournalAdapter;
import com.tmp.security.api.AuthorizationService;
import com.tmp.warehouse.application.DefaultWarehouseApi;
import com.tmp.warehouse.application.FixedMaterialReferenceDisplayPort;
import com.tmp.warehouse.application.WarehouseAdjustmentService;
import com.tmp.warehouse.application.WarehouseConsumptionService;
import com.tmp.warehouse.application.WarehouseMoveService;
import com.tmp.warehouse.application.WarehouseOperationEngine;
import com.tmp.warehouse.application.WarehouseReceiptService;
import com.tmp.warehouse.application.WarehouseReservationLinkService;
import com.tmp.warehouse.application.WarehouseResponsibilityGuard;
import com.tmp.warehouse.application.WarehouseTransferDocumentService;
import com.tmp.warehouse.application.WarehouseTransferService;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import com.tmp.warehouse.domain.repository.WarehouseUserResponsibilityRepository;
import com.tmp.warehouse.persistence.JdbcMaterialReservationLinkRepository;
import com.tmp.warehouse.persistence.JdbcTransferOperationContextRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseTransferDocumentRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseUserResponsibilityRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Builds {@link DefaultWarehouseApi} for integration tests. */
public final class WarehouseIntegrationTestSupport {

    private WarehouseIntegrationTestSupport() {}

    public record ApiBundle(
            DefaultWarehouseApi api,
            JdbcTemplate jdbc,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts,
            MaterialReferenceRepository materials,
            StockPositionRepository stockPositions,
            WarehouseCatalogRepository catalog,
            WarehouseUserResponsibilityRepository responsibilities,
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository transferDocuments,
            WarehouseTransferDocumentService transferDocumentService) {}

    /** Test helper: skips responsibility checks. Never use in production wiring. */
    public static WarehouseResponsibilityGuard permitAllResponsibility() {
        return warehouseId -> {};
    }

    /**
     * Non-functional Transfer Document service for unit tests that construct {@link
     * DefaultWarehouseApi} but never exercise Transfer Document APIs.
     */
    public static WarehouseTransferDocumentService unusedTransferDocumentService(
            WarehouseCatalogRepository catalog, MaterialReferenceRepository materials) {
        return new WarehouseTransferDocumentService(
                new UnsupportedDocumentEngine(),
                new UnsupportedTransferDocumentRepository(),
                catalog,
                materials,
                permitAllResponsibility(),
                new TransactionTemplate(new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                    @Override
                    protected Object doGetTransaction() {
                        return new Object();
                    }

                    @Override
                    protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {}

                    @Override
                    protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {}

                    @Override
                    protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                }));
    }

    public static ApiBundle createApiBundle(DataSource dataSource, Clock clock) {
        return createApiBundle(
                dataSource,
                clock,
                authorizationAllowAll(),
                UnauthenticatedAuthenticationService.INSTANCE,
                permitAllResponsibility());
    }

    public static ApiBundle createApiBundle(
            DataSource dataSource,
            Clock clock,
            AuthorizationService authorization,
            com.tmp.security.api.AuthenticationService authentication,
            WarehouseResponsibilityGuard responsibilityGuard) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var stockJdbc =
                new com.tmp.warehouse.persistence.JdbcWarehouseStockRepository(jdbc, clock);
        MaterialReferenceRepository materials =
                new com.tmp.warehouse.persistence.JdbcMaterialReferenceRepository(jdbc, clock);
        WarehouseCatalogRepository catalog =
                new com.tmp.warehouse.persistence.JdbcWarehouseCatalogRepository(jdbc, clock);
        WarehouseUserResponsibilityRepository responsibilities =
                new JdbcWarehouseUserResponsibilityRepository(jdbc, clock);
        WarehouseOperationRepository operations =
                new com.tmp.warehouse.persistence.JdbcWarehouseOperationRepository(stockJdbc, clock);
        TransferOperationContextRepository transferContexts =
                new JdbcTransferOperationContextRepository(jdbc);
        StockPositionRepository stockPositions =
                new com.tmp.warehouse.persistence.JdbcStockPositionRepository(stockJdbc);
        var movements = new com.tmp.warehouse.persistence.JdbcWarehouseMovementRepository(jdbc);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        WarehouseOperationEngine engine =
                new WarehouseOperationEngine(
                        operations, stockPositions, movements, tx, clock);

        WarehouseTransferDocumentRepository transferDocumentRepository =
                new JdbcWarehouseTransferDocumentRepository(jdbc, clock);
        DocumentEngine documentEngine = createDocumentEngine(jdbc);
        documentEngine.registerProcessor(
                new WarehouseTransferDocumentProcessor(transferDocumentRepository));
        WarehouseTransferDocumentService transferDocumentService =
                new WarehouseTransferDocumentService(
                        documentEngine,
                        transferDocumentRepository,
                        catalog,
                        materials,
                        responsibilityGuard,
                        tx);

        DefaultWarehouseApi api =
                new DefaultWarehouseApi(
                        authorization,
                        authentication,
                        responsibilityGuard,
                        responsibilities,
                        catalog,
                        stockPositions,
                        materials,
                        new FixedMaterialReferenceDisplayPort(),
                        new WarehouseReservationLinkService(
                                new JdbcMaterialReservationLinkRepository(jdbc), clock),
                        new WarehouseReceiptService(engine, stockPositions, materials),
                        new WarehouseMoveService(engine),
                        new WarehouseTransferService(engine, operations, transferContexts, tx),
                        transferDocumentService,
                        new WarehouseConsumptionService(engine, stockPositions),
                        new WarehouseAdjustmentService(engine, stockPositions),
                        operations,
                        transferContexts);
        return new ApiBundle(
                api,
                jdbc,
                operations,
                transferContexts,
                materials,
                stockPositions,
                catalog,
                responsibilities,
                documentEngine,
                transferDocumentRepository,
                transferDocumentService);
    }

    public static DocumentEngine createDocumentEngine(JdbcTemplate jdbc) {
        return new DefaultDocumentEngine(
                new DefaultDocumentProcessorRegistry(),
                new JdbcDocumentStorageAdapter(jdbc),
                new JdbcLifecycleJournalAdapter(jdbc),
                new JdbcDocumentVersionAdapter(jdbc),
                new TransactionAfterCommitEventPublisher());
    }

    private static AuthorizationService authorizationAllowAll() {
        return new AuthorizationService() {
            @Override
            public boolean hasPermission(com.tmp.security.api.PermissionId permissionId) {
                return true;
            }

            @Override
            public void requirePermission(com.tmp.security.api.PermissionId permissionId) {}

            @Override
            public java.util.Set<com.tmp.security.api.PermissionId> effectivePermissions() {
                return java.util.Set.of();
            }
        };
    }

    private static final class UnsupportedDocumentEngine implements DocumentEngine {
        @Override
        public DocumentProcessorRegistration registerProcessor(DocumentProcessor processor) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public DocumentMetadata createDocument(CreateDocumentCommand command) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public DocumentMetadata updateDocument(UpdateDocumentCommand command) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public DocumentMetadata postDocument(UUID documentId) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public DocumentMetadata unpostDocument(UUID documentId) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public DocumentMetadata closeDocument(UUID documentId) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public void deleteDocument(UUID documentId) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public Optional<DocumentMetadata> findById(UUID documentId) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public List<DocumentMetadata> search(DocumentQuery query) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public List<DocumentTypeDescriptor> registeredTypes() {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public DocumentEngineStatus status() {
            throw new UnsupportedOperationException("not used in unit test");
        }
    }

    private static final class UnsupportedTransferDocumentRepository
            implements WarehouseTransferDocumentRepository {
        @Override
        public void insert(WarehouseTransferDocument document) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public Optional<WarehouseTransferDocument> findByDocumentId(UUID documentId) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public void update(WarehouseTransferDocument document, long expectedPayloadRevision) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public void deleteByDocumentId(UUID documentId) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public boolean existsByDocumentId(UUID documentId) {
            throw new UnsupportedOperationException("not used in unit test");
        }
    }
}
