package com.tmp.production;

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
import com.tmp.production.config.ProductionWarehouseProperties;
import com.tmp.production.domain.repository.MaterialTransferTemplateRepository;
import com.tmp.production.domain.repository.ProductionCancellationQuery;
import com.tmp.production.domain.repository.ProductionCancellationRepository;
import com.tmp.production.domain.repository.ProductionHistoryRepository;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.production.domain.repository.ProductionReleaseRepository;
import com.tmp.production.persistence.JdbcMaterialTransferTemplateRepository;
import com.tmp.production.persistence.JdbcProductionCancellationRepository;
import com.tmp.production.persistence.JdbcProductionHistoryRepository;
import com.tmp.production.persistence.JdbcProductionItemStateRepository;
import com.tmp.production.persistence.JdbcProductionMaterialTransferRepository;
import com.tmp.production.persistence.JdbcProductionReleaseRepository;
import com.tmp.production.security.ProductionCapability;
import com.tmp.security.api.AuthorizationService;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Registers Production Capability contributions and read/write runtime beans.
 *
 * <p>Does not create users, roles, or Production-owned authorization tables. Does not invent fake
 * Warehouse ids — {@link ProductionWarehouseScope} must be provided explicitly or configured via
 * {@code tmp.production.warehouse.main-warehouse-id} / {@code production-warehouse-id}.
 */
@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "com.tmp.document.DocumentEngineAutoConfiguration",
            "com.tmp.security.SecurityAutoConfiguration",
            "com.tmp.order.OrderManagementAutoConfiguration",
            "com.tmp.warehouse.WarehouseAutoConfiguration"
        })
@EnableConfigurationProperties(ProductionWarehouseProperties.class)
public class ProductionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    ProductionWarehouseScope productionWarehouseScope(ProductionWarehouseProperties properties) {
        if (!properties.isComplete()) {
            throw new IllegalStateException(
                    "Production warehouse scope is not configured. Required:"
                            + " tmp.production.warehouse.main-warehouse-id (main warehouse ID) and"
                            + " tmp.production.warehouse.production-warehouse-id (production"
                            + " warehouse ID). Provide both explicitly or register a"
                            + " ProductionWarehouseScope bean.");
        }
        return new ProductionWarehouseScope(
                properties.getMainWarehouseId(), properties.getProductionWarehouseId());
    }

    @Bean
    ProductionItemStateRepository productionItemStateRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcProductionItemStateRepository(jdbcTemplate, clock);
    }

    @Bean
    ProductionCancellationRepository productionCancellationRepository(
            JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcProductionCancellationRepository(jdbcTemplate, clock);
    }

    @Bean
    ProductionHistoryRepository productionHistoryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcProductionHistoryRepository(jdbcTemplate);
    }

    @Bean
    MaterialTransferTemplateRepository materialTransferTemplateRepository(
            JdbcTemplate jdbcTemplate, Clock clock, PlatformTransactionManager transactionManager) {
        return new JdbcMaterialTransferTemplateRepository(jdbcTemplate, clock, transactionManager);
    }

    @Bean
    ProductionMaterialTransferRepository productionMaterialTransferRepository(
            JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        return new JdbcProductionMaterialTransferRepository(jdbcTemplate, transactionManager);
    }

    @Bean
    ProductionReleaseRepository productionReleaseRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcProductionReleaseRepository(jdbcTemplate, clock);
    }

    @Bean
    ProductionOrderViewService productionOrderViewService(
            ProductionItemStateRepository itemStateRepository,
            ProductionCancellationRepository cancellationRepository) {
        return new ProductionOrderViewService(
                itemStateRepository, (ProductionCancellationQuery) cancellationRepository);
    }

    @Bean
    ProductionHistoryService productionHistoryService(ProductionHistoryRepository repository, Clock clock) {
        return new ProductionHistoryService(repository, clock);
    }

    @Bean
    OrderSpecificationQueryPort orderSpecificationQueryPort(OrderQueryService orderQueryService) {
        return new DefaultOrderSpecificationQueryAdapter(orderQueryService);
    }

    @Bean
    OrderForProductionQueryPort orderForProductionQueryPort(OrderQueryService orderQueryService) {
        return new DefaultOrderForProductionQueryAdapter(orderQueryService);
    }

    @Bean
    ProductionFoundationQueryService productionFoundationQueryService(
            OrderSpecificationQueryPort specificationQueryPort) {
        return new ProductionFoundationQueryService(specificationQueryPort);
    }

    @Bean
    WarehouseAvailabilityQueryPort warehouseAvailabilityQueryPort(
            @Qualifier("warehouseQueryApi") WarehouseQueryApi warehouseQueryApi) {
        return new DefaultWarehouseAvailabilityQueryAdapter(warehouseQueryApi);
    }

    @Bean
    CurrentMaterialAvailabilityQueryService currentMaterialAvailabilityQueryService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQueryService,
            WarehouseAvailabilityQueryPort warehouseQueryPort,
            ProductionWarehouseScope warehouseScope,
            Clock clock) {
        return new CurrentMaterialAvailabilityQueryService(
                orderViewService, foundationQueryService, warehouseQueryPort, warehouseScope, clock);
    }

    @Bean
    ProductionLaunchPayloadHolder productionLaunchPayloadHolder() {
        return new ProductionLaunchPayloadHolder();
    }

    @Bean
    ProductionLaunchProcessor productionLaunchProcessor(
            ProductionItemStateRepository itemStateRepository,
            @Qualifier("transactionalEventPublisher") TransactionalEventPublisher eventPublisher,
            ProductionLaunchPayloadHolder payloadHolder,
            ProductionHistoryService historyService) {
        return new ProductionLaunchProcessor(
                itemStateRepository, eventPublisher, payloadHolder, historyService);
    }

    @Bean
    ProductionReleaseProcessor productionReleaseProcessor(
            ProductionReleaseRepository releaseRepository,
            ProductionItemStateRepository itemStateRepository,
            @Qualifier("transactionalEventPublisher") TransactionalEventPublisher eventPublisher,
            ProductionHistoryService historyService) {
        return new ProductionReleaseProcessor(
                releaseRepository, itemStateRepository, eventPublisher, historyService);
    }

    @Bean
    ProductionCancellationProcessor productionCancellationProcessor(
            ProductionCancellationRepository cancellationRepository,
            ProductionItemStateRepository itemStateRepository,
            @Qualifier("transactionalEventPublisher") TransactionalEventPublisher eventPublisher,
            ProductionHistoryService historyService) {
        return new ProductionCancellationProcessor(
                cancellationRepository, itemStateRepository, eventPublisher, historyService);
    }

    @Bean
    ProductionDocumentProcessorRegistrar productionDocumentProcessorRegistrar(
            DocumentEngine documentEngine,
            ProductionLaunchProcessor launchProcessor,
            ProductionReleaseProcessor releaseProcessor,
            ProductionCancellationProcessor cancellationProcessor) {
        return new ProductionDocumentProcessorRegistrar(
                documentEngine, launchProcessor, releaseProcessor, cancellationProcessor);
    }

    @Bean
    ProductionLaunchService productionLaunchService(
            DocumentEngine documentEngine,
            ProductionLaunchPayloadHolder payloadHolder,
            OrderForProductionQueryPort orderForProductionQuery,
            Clock clock) {
        return new ProductionLaunchService(
                documentEngine, payloadHolder, orderForProductionQuery, clock);
    }

    @Bean
    CheckMaterialAvailabilityService checkMaterialAvailabilityService(
            CurrentMaterialAvailabilityQueryService currentMaterialAvailabilityQueryService,
            ProductionHistoryService historyService,
            PlatformTransactionManager transactionManager) {
        return new CheckMaterialAvailabilityService(
                currentMaterialAvailabilityQueryService, historyService, transactionManager);
    }

    @Bean
    MaterialTransferTemplateService materialTransferTemplateService(
            CheckMaterialAvailabilityService checkMaterialAvailabilityService,
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQueryService,
            ProductionWarehouseScope warehouseScope,
            MaterialTransferTemplateRepository templateRepository,
            Clock clock) {
        return new MaterialTransferTemplateService(
                checkMaterialAvailabilityService,
                orderViewService,
                foundationQueryService,
                warehouseScope,
                templateRepository,
                clock);
    }

    @Bean
    ConfirmMaterialTransferService confirmMaterialTransferService(
            MaterialTransferTemplateRepository templateRepository,
            ProductionMaterialTransferRepository transferRepository,
            @Qualifier("warehouseCommandApi") WarehouseCommandApi warehouseCommandApi,
            @Qualifier("warehouseQueryApi") WarehouseQueryApi warehouseQueryApi,
            ProductionHistoryService historyService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        return new ConfirmMaterialTransferService(
                templateRepository,
                transferRepository,
                warehouseCommandApi,
                warehouseQueryApi,
                historyService,
                transactionManager,
                clock);
    }

    @Bean
    ConfirmMaterialReceiptService confirmMaterialReceiptService(
            ProductionMaterialTransferRepository transferRepository,
            @Qualifier("warehouseCommandApi") WarehouseCommandApi warehouseCommandApi,
            @Qualifier("warehouseQueryApi") WarehouseQueryApi warehouseQueryApi,
            ProductionHistoryService historyService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        return new ConfirmMaterialReceiptService(
                transferRepository,
                warehouseCommandApi,
                warehouseQueryApi,
                historyService,
                transactionManager,
                clock);
    }

    @Bean
    ReleaseProductsService releaseProductsService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQueryService,
            WarehouseAvailabilityQueryPort warehouseAvailabilityQueryPort,
            @Qualifier("warehouseCommandApi") WarehouseCommandApi warehouseCommandApi,
            @Qualifier("warehouseQueryApi") WarehouseQueryApi warehouseQueryApi,
            ProductionWarehouseScope warehouseScope,
            DocumentEngine documentEngine,
            ProductionReleaseRepository releaseRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        ProductionReleaseDocumentService releaseDocumentService =
                new ProductionReleaseDocumentService(documentEngine, releaseRepository, clock);
        return new ReleaseProductsService(
                orderViewService,
                foundationQueryService,
                warehouseAvailabilityQueryPort,
                warehouseCommandApi,
                warehouseQueryApi,
                warehouseScope,
                releaseDocumentService,
                transactionManager,
                clock);
    }

    @Bean
    CancelOrderProductionService cancelOrderProductionService(
            ProductionOrderViewService orderViewService,
            ProductionCancellationRepository cancellationRepository,
            DocumentEngine documentEngine,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        ProductionCancellationDocumentService cancellationDocumentService =
                new ProductionCancellationDocumentService(
                        documentEngine, cancellationRepository, clock);
        return new CancelOrderProductionService(
                orderViewService,
                (ProductionCancellationQuery) cancellationRepository,
                cancellationDocumentService,
                transactionManager,
                clock);
    }

    @Bean
    ProductionQueryApi productionQueryApi(
            AuthorizationService authorizationService,
            ProductionOrderViewService orderViewService,
            CurrentMaterialAvailabilityQueryService materialAvailabilityQueryService,
            ProductionHistoryService historyService) {
        return new DefaultProductionQueryApi(
                authorizationService, orderViewService, materialAvailabilityQueryService, historyService);
    }

    @Bean
    ProductionApplicationApi productionApplicationApi(
            AuthorizationService authorizationService,
            ProductionWarehouseScope warehouseScope,
            ProductionLaunchService launchService,
            CheckMaterialAvailabilityService checkMaterialAvailabilityService,
            MaterialTransferTemplateService transferTemplateService,
            ConfirmMaterialTransferService confirmMaterialTransferService,
            ConfirmMaterialReceiptService confirmMaterialReceiptService,
            ReleaseProductsService releaseProductsService,
            CancelOrderProductionService cancelOrderProductionService,
            ProductionMaterialTransferRepository materialTransferRepository) {
        return new DefaultProductionApplicationApi(
                authorizationService,
                warehouseScope,
                launchService,
                checkMaterialAvailabilityService,
                transferTemplateService,
                confirmMaterialTransferService,
                confirmMaterialReceiptService,
                releaseProductsService,
                cancelOrderProductionService,
                materialTransferRepository);
    }

    @Bean
    ProductionCapability productionCapability(@Lazy ProductionQueryApi productionQueryApi) {
        return new ProductionCapability(productionQueryApi);
    }

    /** Registers Production document processors on the Document Engine at startup. */
    static final class ProductionDocumentProcessorRegistrar {

        private final DocumentEngine documentEngine;
        private final ProductionLaunchProcessor launchProcessor;
        private final ProductionReleaseProcessor releaseProcessor;
        private final ProductionCancellationProcessor cancellationProcessor;

        ProductionDocumentProcessorRegistrar(
                DocumentEngine documentEngine,
                ProductionLaunchProcessor launchProcessor,
                ProductionReleaseProcessor releaseProcessor,
                ProductionCancellationProcessor cancellationProcessor) {
            this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
            this.launchProcessor = Objects.requireNonNull(launchProcessor, "launchProcessor");
            this.releaseProcessor = Objects.requireNonNull(releaseProcessor, "releaseProcessor");
            this.cancellationProcessor =
                    Objects.requireNonNull(cancellationProcessor, "cancellationProcessor");
        }

        @PostConstruct
        void register() {
            documentEngine.registerProcessor(launchProcessor);
            documentEngine.registerProcessor(releaseProcessor);
            documentEngine.registerProcessor(cancellationProcessor);
        }
    }
}
