package com.tmp.production.integration.publicboundary;

import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.OrderQueryService;
import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.application.CancelOrderProductionService;
import com.tmp.production.application.CheckMaterialAvailabilityService;
import com.tmp.production.application.ConfirmMaterialReceiptService;
import com.tmp.production.application.ConfirmMaterialTransferService;
import com.tmp.production.application.CurrentMaterialAvailabilityQueryService;
import com.tmp.production.application.DefaultProductionApplicationApi;
import com.tmp.production.application.DefaultProductionQueryApi;
import com.tmp.production.application.MaterialTransferTemplateService;
import com.tmp.production.application.ProductionFoundationQueryService;
import com.tmp.production.application.ProductionHistoryService;
import com.tmp.production.application.ProductionLaunchService;
import com.tmp.production.application.ProductionOrderViewService;
import com.tmp.production.application.ProductionWarehouseScope;
import com.tmp.production.application.ReleaseProductsService;
import com.tmp.production.application.document.ProductionCancellationProcessor;
import com.tmp.production.application.document.ProductionLaunchPayloadHolder;
import com.tmp.production.application.document.ProductionLaunchProcessor;
import com.tmp.production.application.document.ProductionReleaseProcessor;
import com.tmp.production.application.internal.ProductionCancellationDocumentService;
import com.tmp.production.application.internal.ProductionReleaseDocumentService;
import com.tmp.production.application.port.DefaultOrderForProductionQueryAdapter;
import com.tmp.production.application.port.DefaultOrderSpecificationQueryAdapter;
import com.tmp.production.application.port.DefaultWarehouseAvailabilityQueryAdapter;
import com.tmp.production.application.port.OrderForProductionQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.domain.repository.MaterialTransferTemplateRepository;
import com.tmp.production.domain.repository.ProductionCancellationQuery;
import com.tmp.production.domain.repository.ProductionCancellationRepository;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.production.domain.repository.ProductionReleaseRepository;
import com.tmp.production.persistence.JdbcMaterialTransferTemplateRepository;
import com.tmp.production.persistence.JdbcProductionCancellationRepository;
import com.tmp.production.persistence.JdbcProductionHistoryRepository;
import com.tmp.production.persistence.JdbcProductionItemStateRepository;
import com.tmp.production.persistence.JdbcProductionMaterialTransferRepository;
import com.tmp.production.persistence.JdbcProductionReleaseRepository;
import com.tmp.security.api.AuthorizationService;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Composes Production runtime against real Document Engine / JDBC / public OM & Warehouse APIs.
 * Processors are registered once on the shared Document Engine.
 */
final class ProductionPublicBoundaryComposition {

    private static final AtomicBoolean PROCESSORS_REGISTERED = new AtomicBoolean();
    private static final ProductionLaunchPayloadHolder SHARED_LAUNCH_PAYLOAD_HOLDER =
            new ProductionLaunchPayloadHolder();

    private final ControllableProductionItemStateRepository itemStates;
    private final ControllableProductionHistoryRepository historyRepository;
    private final ProductionMaterialTransferRepository materialTransfers;
    private final ProductionApplicationApi applicationApi;
    private final ProductionQueryApi queryApi;
    private final ProductionWarehouseScope warehouseScope;

    private ProductionPublicBoundaryComposition(
            ControllableProductionItemStateRepository itemStates,
            ControllableProductionHistoryRepository historyRepository,
            ProductionMaterialTransferRepository materialTransfers,
            ProductionApplicationApi applicationApi,
            ProductionQueryApi queryApi,
            ProductionWarehouseScope warehouseScope) {
        this.itemStates = itemStates;
        this.historyRepository = historyRepository;
        this.materialTransfers = materialTransfers;
        this.applicationApi = applicationApi;
        this.queryApi = queryApi;
        this.warehouseScope = warehouseScope;
    }

    static ProductionPublicBoundaryComposition wire(
            JdbcTemplate jdbc,
            PlatformTransactionManager txManager,
            Clock clock,
            DocumentEngine documentEngine,
            TransactionalEventPublisher eventPublisher,
            AuthorizationService authorizationService,
            OrderQueryService orderQueryService,
            WarehouseQueryApi warehouseQueryApi,
            WarehouseCommandApi warehouseCommandApi,
            UUID mainWarehouseId,
            UUID productionWarehouseId) {
        Objects.requireNonNull(jdbc, "jdbc");
        Objects.requireNonNull(txManager, "txManager");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(documentEngine, "documentEngine");
        Objects.requireNonNull(eventPublisher, "eventPublisher");
        Objects.requireNonNull(authorizationService, "authorizationService");
        Objects.requireNonNull(orderQueryService, "orderQueryService");
        Objects.requireNonNull(warehouseQueryApi, "warehouseQueryApi");
        Objects.requireNonNull(warehouseCommandApi, "warehouseCommandApi");

        ControllableProductionItemStateRepository itemStates =
                new ControllableProductionItemStateRepository(
                        new JdbcProductionItemStateRepository(jdbc, clock));
        ControllableProductionHistoryRepository historyRepository =
                new ControllableProductionHistoryRepository(
                        new JdbcProductionHistoryRepository(jdbc));
        PublicBoundaryRepositoryPorts.bind(itemStates, historyRepository);
        ProductionHistoryService historyService =
                new ProductionHistoryService(PublicBoundaryRepositoryPorts.history(), clock);

        ProductionCancellationRepository cancellationRepository =
                new JdbcProductionCancellationRepository(jdbc, clock);
        ProductionReleaseRepository releaseRepository =
                new JdbcProductionReleaseRepository(jdbc, clock);
        MaterialTransferTemplateRepository templates =
                new JdbcMaterialTransferTemplateRepository(jdbc, clock, txManager);
        ProductionMaterialTransferRepository materialTransfers =
                new JdbcProductionMaterialTransferRepository(jdbc, txManager);

        ProductionWarehouseScope scope =
                new ProductionWarehouseScope(mainWarehouseId, productionWarehouseId);
        OrderSpecificationQueryPort specificationQuery =
                new DefaultOrderSpecificationQueryAdapter(orderQueryService);
        OrderForProductionQueryPort orderForProductionQuery =
                new DefaultOrderForProductionQueryAdapter(orderQueryService);
        WarehouseAvailabilityQueryPort warehouseAvailabilityQuery =
                new DefaultWarehouseAvailabilityQueryAdapter(warehouseQueryApi);

        ProductionOrderViewService orderViewService =
                new ProductionOrderViewService(
                        PublicBoundaryRepositoryPorts.itemStates(),
                        (ProductionCancellationQuery) cancellationRepository);
        ProductionFoundationQueryService foundationQuery =
                new ProductionFoundationQueryService(specificationQuery);
        CurrentMaterialAvailabilityQueryService currentAvailability =
                new CurrentMaterialAvailabilityQueryService(
                        orderViewService, foundationQuery, warehouseAvailabilityQuery, scope, clock);

        ProductionLaunchPayloadHolder payloadHolder = SHARED_LAUNCH_PAYLOAD_HOLDER;
        if (PROCESSORS_REGISTERED.compareAndSet(false, true)) {
            org.springframework.transaction.support.TransactionTemplate requiresNew =
                    new org.springframework.transaction.support.TransactionTemplate(txManager);
            requiresNew.setPropagationBehavior(
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.executeWithoutResult(
                    status -> {
                        documentEngine.registerProcessor(
                                new ProductionLaunchProcessor(
                                        PublicBoundaryRepositoryPorts.itemStates(),
                                        eventPublisher,
                                        payloadHolder,
                                        historyService));
                        documentEngine.registerProcessor(
                                new ProductionReleaseProcessor(
                                        releaseRepository,
                                        PublicBoundaryRepositoryPorts.itemStates(),
                                        eventPublisher,
                                        historyService));
                        documentEngine.registerProcessor(
                                new ProductionCancellationProcessor(
                                        cancellationRepository,
                                        PublicBoundaryRepositoryPorts.itemStates(),
                                        eventPublisher,
                                        historyService));
                    });
        }

        ProductionLaunchService launchService =
                new ProductionLaunchService(
                        documentEngine, payloadHolder, orderForProductionQuery, clock);
        CheckMaterialAvailabilityService checkService =
                new CheckMaterialAvailabilityService(
                        currentAvailability, historyService, txManager);
        MaterialTransferTemplateService transferTemplateService =
                new MaterialTransferTemplateService(
                        checkService, orderViewService, foundationQuery, scope, templates, clock);
        ConfirmMaterialTransferService confirmTransferService =
                new ConfirmMaterialTransferService(
                        templates,
                        materialTransfers,
                        warehouseCommandApi,
                        warehouseQueryApi,
                        historyService,
                        txManager,
                        clock);
        ConfirmMaterialReceiptService confirmReceiptService =
                new ConfirmMaterialReceiptService(
                        materialTransfers,
                        warehouseCommandApi,
                        warehouseQueryApi,
                        historyService,
                        txManager,
                        clock);
        ProductionReleaseDocumentService releaseDocumentService =
                new ProductionReleaseDocumentService(documentEngine, releaseRepository, clock);
        ReleaseProductsService releaseProductsService =
                new ReleaseProductsService(
                        orderViewService,
                        foundationQuery,
                        warehouseAvailabilityQuery,
                        warehouseCommandApi,
                        warehouseQueryApi,
                        scope,
                        releaseDocumentService,
                        txManager,
                        clock);
        ProductionCancellationDocumentService cancellationDocumentService =
                new ProductionCancellationDocumentService(
                        documentEngine, cancellationRepository, clock);
        CancelOrderProductionService cancelService =
                new CancelOrderProductionService(
                        orderViewService,
                        (ProductionCancellationQuery) cancellationRepository,
                        cancellationDocumentService,
                        txManager,
                        clock);

        ProductionApplicationApi applicationApi =
                new DefaultProductionApplicationApi(
                        authorizationService,
                        scope,
                        launchService,
                        checkService,
                        transferTemplateService,
                        confirmTransferService,
                        confirmReceiptService,
                        releaseProductsService,
                        cancelService,
                        materialTransfers);
        ProductionQueryApi queryApi =
                new DefaultProductionQueryApi(
                        authorizationService, orderViewService, currentAvailability, historyService);

        return new ProductionPublicBoundaryComposition(
                itemStates,
                historyRepository,
                materialTransfers,
                applicationApi,
                queryApi,
                scope);
    }

    ControllableProductionItemStateRepository itemStates() {
        return itemStates;
    }

    ControllableProductionHistoryRepository historyRepository() {
        return historyRepository;
    }

    ProductionMaterialTransferRepository materialTransfers() {
        return materialTransfers;
    }

    ProductionApplicationApi applicationApi() {
        return applicationApi;
    }

    ProductionQueryApi queryApi() {
        return queryApi;
    }

    ProductionWarehouseScope warehouseScope() {
        return warehouseScope;
    }
}
