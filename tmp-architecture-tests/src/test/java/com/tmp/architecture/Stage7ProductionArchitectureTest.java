package com.tmp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tmp.order.api.OrderQueryService;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.domain.repository.ProductionHistoryRepository;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Stage 7 Production architecture boundaries.
 */
@AnalyzeClasses(
        packages = "com.tmp",
        importOptions = ImportOption.DoNotIncludeTests.class)
class Stage7ProductionArchitectureTest {

    @ArchTest
    static final ArchRule productionDoesNotDependOnOtherCapabilityInternals =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse.application..",
                            "com.tmp.warehouse.domain..",
                            "com.tmp.warehouse.persistence..",
                            "com.tmp.order.application..",
                            "com.tmp.order.domain..",
                            "com.tmp.order.persistence..",
                            "com.tmp.cutting.application..",
                            "com.tmp.cutting.domain..",
                            "com.tmp.cutting.persistence..")
                    .because(
                            "Production must interact cross-capability only via public contracts "
                                    + "and must not depend on internal application/domain/persistence packages");

    @ArchTest
    static final ArchRule productionPersistenceDependsOnlyOnProductionDomainAndPlatform =
            classes()
                    .that()
                    .resideInAPackage("com.tmp.production.persistence..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production.domain..",
                            "com.tmp.production.persistence..",
                            "java..",
                            "javax..",
                            "jakarta..",
                            "org.springframework..",
                            "edu.umd.cs.findbugs..")
                    .because(
                            "Production persistence must map Production domain state via JDBC only, "
                                    + "without Warehouse/Order/Cutting internals");

    @ArchTest
    static final ArchRule onlyProcessorAndViewQueryUseRepository =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production..")
                    .and()
                    .haveSimpleNameNotContaining("Processor")
                    .and()
                    .haveSimpleNameNotEndingWith("ViewService")
                    .and()
                    .haveSimpleNameNotEndingWith("AutoConfiguration")
                    .and()
                    .resideOutsideOfPackage("com.tmp.production.persistence..")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("ProductionItemStateRepository")
                    .because(
                            "Repository writes belong to Processor; read-only ViewService may query; "
                                    + "persistence adapters implement the port");

    @ArchTest
    static final ArchRule foundationQueryMustNotUseCurrentSpecificationApi =
            noClasses()
                    .that()
                    .haveSimpleName("ProductionFoundationQueryService")
                    .should()
                    .callMethod(OrderQueryService.class, "getCurrentItemSpecification")
                    .because(
                            "Post-launch reads must use frozen SpecificationId via getSpecificationById only");

    @ArchTest
    static final ArchRule foundationQueryMustNotResolveCurrentForLaunch =
            noClasses()
                    .that()
                    .haveSimpleName("ProductionFoundationQueryService")
                    .should()
                    .callMethod(OrderSpecificationQueryPort.class, "resolveCurrentForLaunch")
                    .because(
                            "Production Foundation freeze uses current specification only at Launch");

    @ArchTest
    static final ArchRule launchProcessorMustNotTouchOrderQuery =
            noClasses()
                    .that()
                    .haveSimpleName("ProductionLaunchProcessor")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.order..")
                    .because(
                            "Launch processor persists frozen foundation from payload; OM query stays in Launch service");

    @ArchTest
    static final ArchRule productionDomainDoesNotDependOnOrderApi =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.order..")
                    .because("Production domain must not depend on Order Management types");

    @ArchTest
    static final ArchRule orderProductionViewCalculatorIsPure =
            noClasses()
                    .that()
                    .haveSimpleName("OrderProductionViewCalculator")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production.domain.repository..",
                            "com.tmp.production.persistence..",
                            "com.tmp.production.application..",
                            "com.tmp.order..",
                            "com.tmp.warehouse..",
                            "com.tmp.cutting..",
                            "org.springframework..")
                    .because(
                            "Order Production View calculator is pure domain logic with no I/O");

    @ArchTest
    static final ArchRule orderProductionViewServiceDoesNotDependOnWarehouseOrCutting =
            noClasses()
                    .that()
                    .haveSimpleName("ProductionOrderViewService")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse..",
                            "com.tmp.cutting..",
                            "com.tmp.order.application..",
                            "com.tmp.order.domain..",
                            "com.tmp.order.persistence..")
                    .because(
                            "Computed Order Production View stays inside Production and does not "
                                    + "touch Warehouse/Cutting or OM internals");

    @ArchTest
    static final ArchRule noOrderProductionStateEntity =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production..")
                    .and()
                    .haveSimpleName("OrderProductionStateEntity")
                    .should()
                    .beInterfaces()
                    .because("Order-level Production status is computed; no OrderProductionStateEntity")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noOrderProductionRepository =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production..")
                    .and()
                    .haveSimpleName("OrderProductionRepository")
                    .should()
                    .beInterfaces()
                    .because("Order-level Production status is computed; no OrderProductionRepository")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule persistenceAdapterDoesNotDependOnOrderProductionView =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production.persistence..")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("OrderProductionView")
                    .because(
                            "JDBC adapters return rows; Order Production View aggregation stays in domain");

    @ArchTest
    static final ArchRule persistenceAdapterDoesNotDependOnOrderProductionViewStatus =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production.persistence..")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("OrderProductionViewStatus")
                    .because(
                            "JDBC adapters return rows; Order Production View aggregation stays in domain");

    @ArchTest
    static final ArchRule persistenceAdapterDoesNotDependOnOrderProductionViewCalculator =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production.persistence..")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("OrderProductionViewCalculator")
                    .because(
                            "JDBC adapters return rows; Order Production View aggregation stays in domain");

    @ArchTest
    static final ArchRule materialAvailabilityMustNotUseWarehouseInternals =
            noClasses()
                    .that()
                    .haveSimpleName("CheckMaterialAvailabilityService")
                    .or()
                    .haveSimpleName("DefaultWarehouseAvailabilityQueryAdapter")
                    .or()
                    .haveSimpleName("SpecificationMaterialRequirementCalculator")
                    .or()
                    .haveSimpleName("ProductionWarehouseScope")
                    .or()
                    .haveSimpleNameContaining("MaterialAvailability")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse.application..",
                            "com.tmp.warehouse.domain..",
                            "com.tmp.warehouse.persistence..")
                    .because(
                            "Material availability must use WarehouseQueryApi read boundary only");

    @ArchTest
    static final ArchRule materialAvailabilityMustNotUseWarehouseCommandApi =
            noClasses()
                    .that()
                    .haveSimpleName("CheckMaterialAvailabilityService")
                    .or()
                    .haveSimpleName("DefaultWarehouseAvailabilityQueryAdapter")
                    .or()
                    .haveSimpleNameContaining("MaterialAvailability")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("WarehouseCommandApi")
                    .because("Material availability is read-only and must not call Warehouse commands");

    @ArchTest
    static final ArchRule materialAvailabilityMustNotUseCurrentSpecificationApi =
            noClasses()
                    .that()
                    .haveSimpleName("CheckMaterialAvailabilityService")
                    .should()
                    .callMethod(OrderQueryService.class, "getCurrentItemSpecification")
                    .because(
                            "Material check must resolve frozen SpecificationId only");

    @ArchTest
    static final ArchRule materialAvailabilityMustNotResolveCurrentForLaunch =
            noClasses()
                    .that()
                    .haveSimpleName("CheckMaterialAvailabilityService")
                    .should()
                    .callMethod(OrderSpecificationQueryPort.class, "resolveCurrentForLaunch")
                    .because(
                            "Material check must resolve frozen SpecificationId only");

    @ArchTest
    static final ArchRule warehouseAvailabilityAdapterUsesQueryApiOnly =
            classes()
                    .that()
                    .haveSimpleName("DefaultWarehouseAvailabilityQueryAdapter")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production.application.port..",
                            "com.tmp.warehouse.api..",
                            "java..")
                    .because(
                            "Warehouse availability adapter must depend on WarehouseQueryApi only");

    @ArchTest
    static final ArchRule materialRequirementCalculatorIsPure =
            noClasses()
                    .that()
                    .haveSimpleName("SpecificationMaterialRequirementCalculator")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production.domain.repository..",
                            "com.tmp.production.persistence..",
                            "com.tmp.warehouse..",
                            "com.tmp.cutting..",
                            "org.springframework..")
                    .because(
                            "Requirement calculator is pure and must not touch Warehouse or persistence");

    @ArchTest
    static final ArchRule cuttingPlanLinkTypesAreProductionOwned =
            classes()
                    .that()
                    .resideInAPackage("com.tmp.production..")
                    .and()
                    .haveNameMatching(
                            ".*\\.(CuttingPlanId|ProductionCuttingPlanLink|CuttingPlanLinks|MaterialReferenceId)")
                    .should()
                    .resideInAPackage("com.tmp.production.domain..")
                    .because("Cutting Plan link identity types are Production-owned opaque references");

    @ArchTest
    static final ArchRule cuttingPlanLinkCodeDoesNotDependOnCuttingCapability =
            noClasses()
                    .that()
                    .haveSimpleName("CuttingPlanId")
                    .or()
                    .haveSimpleName("ProductionCuttingPlanLink")
                    .or()
                    .haveSimpleName("CuttingPlanLinks")
                    .or()
                    .haveSimpleName("MaterialReferenceId")
                    .or()
                    .haveSimpleName("ProductionItemState")
                    .or()
                    .haveSimpleName("JdbcProductionItemStateRepository")
                    .or()
                    .haveSimpleName("ProductionLaunchLine")
                    .or()
                    .haveSimpleName("ProductionLaunchProcessor")
                    .or()
                    .haveSimpleName("ProductionLaunchService")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.cutting..")
                    .because("Production Cutting Plan links must not depend on Stage 8 Cutting types");

    @ArchTest
    static final ArchRule noCuttingPlanRevisionTypeInProduction =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production..")
                    .and()
                    .haveNameMatching(".*Cutting(Plan)?Revision.*")
                    .should()
                    .beInterfaces()
                    .because("Cutting Plan Revision is forbidden in Production (ADR-034)")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule productionDoesNotDependOnAnyCuttingPackage =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.cutting..")
                    .because("tmp-production must not depend on tmp-cutting / Stage 8 runtime");

    @ArchTest
    static final ArchRule materialTransferTemplateMustNotUseWarehouseCommandApi =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("MaterialTransferTemplate")
                    .or()
                    .haveSimpleName("MaterialTransferRecommendationCalculator")
                    .or()
                    .haveSimpleName("JdbcMaterialTransferTemplateRepository")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("WarehouseCommandApi")
                    .because(
                            "STAGE7-009 Material Transfer Template must not call Warehouse commands");

    @ArchTest
    static final ArchRule materialTransferTemplateMustNotUseWarehouseInternals =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("MaterialTransferTemplate")
                    .or()
                    .haveSimpleName("MaterialTransferRecommendationCalculator")
                    .or()
                    .haveSimpleName("JdbcMaterialTransferTemplateRepository")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse.application..",
                            "com.tmp.warehouse.domain..",
                            "com.tmp.warehouse.persistence..")
                    .because(
                            "Material Transfer Template stays Production-owned and must not touch "
                                    + "Warehouse internals");

    @ArchTest
    static final ArchRule materialTransferTemplateMustNotDependOnCuttingRuntime =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("MaterialTransferTemplate")
                    .or()
                    .haveSimpleName("MaterialTransferRecommendationCalculator")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.cutting..")
                    .because("Mere CuttingPlanId must not pull Stage 8 Cutting runtime");

    @ArchTest
    static final ArchRule materialTransferTemplateMustNotUseCurrentSpecificationApi =
            noClasses()
                    .that()
                    .haveSimpleName("MaterialTransferTemplateService")
                    .should()
                    .callMethod(OrderQueryService.class, "getCurrentItemSpecification")
                    .because(
                            "Transfer template must resolve frozen SpecificationId only via Material Check path");

    @ArchTest
    static final ArchRule materialTransferTemplateIsNotDocumentProcessor =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("MaterialTransferTemplate")
                    .should()
                    .implement(com.tmp.document.api.DocumentProcessor.class)
                    .because(
                            "Material Transfer Template is an editable preparation model, not a "
                                    + "Document Engine business document");

    @ArchTest
    static final ArchRule materialTransferTemplateServiceDoesNotUseItemStateRepositoryDirectly =
            noClasses()
                    .that()
                    .haveSimpleName("MaterialTransferTemplateService")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("ProductionItemStateRepository")
                    .because(
                            "Template service reads item state via ProductionOrderViewService only");
    @ArchTest
    static final ArchRule confirmMaterialTransferUsesWarehousePublicApiOnly =
            classes()
                    .that()
                    .haveSimpleName("ConfirmMaterialTransferService")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production..",
                            "com.tmp.warehouse.api..",
                            "java..",
                            "org.springframework.transaction..",
                            "edu.umd.cs.findbugs.annotations..")
                    .because(
                            "STAGE7-010 confirmation must orchestrate only via Warehouse public API");

    @ArchTest
    static final ArchRule confirmMaterialTransferMustNotUseWarehouseInternals =
            noClasses()
                    .that()
                    .haveSimpleName("ConfirmMaterialTransferService")
                    .or()
                    .haveSimpleName("ConfirmMaterialTransferCommand")
                    .or()
                    .haveSimpleName("JdbcProductionMaterialTransferRepository")
                    .or()
                    .haveSimpleNameContaining("ProductionMaterialTransfer")
                    .or()
                    .haveSimpleName("WarehouseTransferOperationRef")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse.application..",
                            "com.tmp.warehouse.domain..",
                            "com.tmp.warehouse.persistence..")
                    .because(
                            "Production logical transfer grouping must not touch Warehouse internals");

    @ArchTest
    static final ArchRule confirmMaterialReceiptUsesWarehousePublicApiOnly =
            classes()
                    .that()
                    .haveSimpleName("ConfirmMaterialReceiptService")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production..",
                            "com.tmp.warehouse.api..",
                            "java..",
                            "org.springframework.transaction..",
                            "edu.umd.cs.findbugs.annotations..")
                    .because(
                            "STAGE7-011 receipt confirmation must orchestrate only via Warehouse"
                                    + " public API");

    @ArchTest
    static final ArchRule confirmMaterialReceiptMustNotUseWarehouseInternals =
            noClasses()
                    .that()
                    .haveSimpleName("ConfirmMaterialReceiptService")
                    .or()
                    .haveSimpleName("ConfirmMaterialReceiptCommand")
                    .or()
                    .haveSimpleName("MaterialReceiptConfirmationResult")
                    .or()
                    .haveSimpleName("MaterialReceiptConfirmationException")
                    .or()
                    .haveSimpleName("ProductionMaterialTransferNotFoundException")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse.application..",
                            "com.tmp.warehouse.domain..",
                            "com.tmp.warehouse.persistence..")
                    .because(
                            "STAGE7-011 receipt initiation must not depend on Warehouse internals");

    @ArchTest
    static final ArchRule confirmMaterialReceiptMustNotDependOnStockPosition =
            noClasses()
                    .that()
                    .haveSimpleName("ConfirmMaterialReceiptService")
                    .or()
                    .haveSimpleName("ConfirmMaterialReceiptCommand")
                    .or()
                    .haveSimpleName("MaterialReceiptConfirmationResult")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("StockPosition")
                    .because("Production must not write or depend on Stock Position");

    @ArchTest
    static final ArchRule productionReleaseMustNotDependOnWarehouse =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("ProductionRelease")
                    .or()
                    .haveSimpleName("JdbcProductionReleaseRepository")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.warehouse..")
                    .because(
                            "STAGE7-012 Production Release must not call Warehouse API or touch "
                                    + "Warehouse packages; Consumption is STAGE7-013");

    @ArchTest
    static final ArchRule productionReleaseMustNotDependOnOrderOrCuttingInternals =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("ProductionRelease")
                    .or()
                    .haveSimpleName("JdbcProductionReleaseRepository")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.order.application..",
                            "com.tmp.order.domain..",
                            "com.tmp.order.persistence..",
                            "com.tmp.cutting..")
                    .because(
                            "Production Release processor loads durable payload only; no OM/Cutting "
                                    + "queries");

    @ArchTest
    static final ArchRule productionReleaseProcessorDoesNotQueryExternally =
            noClasses()
                    .that()
                    .haveSimpleName("ProductionReleaseProcessor")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.order..",
                            "com.tmp.warehouse..",
                            "com.tmp.cutting..")
                    .because(
                            "Release processor must not perform external capability queries");

    @ArchTest
    static final ArchRule releaseProductsServiceIsUserFacingReleaseBoundary =
            classes()
                    .that()
                    .haveSimpleName("ReleaseProductsService")
                    .should()
                    .resideInAPackage("com.tmp.production.application..")
                    .because("User-facing Release orchestration belongs to ReleaseProductsService");

    @ArchTest
    static final ArchRule productionReleaseDocumentServiceIsInternalGateway =
            noClasses()
                    .that()
                    .doNotHaveSimpleName("ReleaseProductsService")
                    .and()
                    .doNotHaveSimpleName("ProductionReleaseDocumentService")
                    .and()
                    .doNotHaveSimpleName("ProductionReleaseDocumentServiceTest")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("ProductionReleaseDocumentService")
                    .because(
                            "Production Release document gateway is internal; UI, bootstrap, public API"
                                    + " and other orchestrators must use ReleaseProductsService only");

    @ArchTest
    static final ArchRule releaseProductsUsesWarehousePublicApiOnly =
            classes()
                    .that()
                    .haveSimpleName("ReleaseProductsService")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production..",
                            "com.tmp.warehouse.api..",
                            "com.tmp.document.api..",
                            "java..",
                            "org.springframework.transaction..",
                            "edu.umd.cs.findbugs.annotations..")
                    .because(
                            "STAGE7-013 Release orchestration must use Warehouse public API only");

    @ArchTest
    static final ArchRule productionOrderStateLockServiceUsesViewServiceOnly =
            classes()
                    .that()
                    .haveSimpleName("ProductionOrderStateLockService")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production.application..",
                            "com.tmp.production.domain..",
                            "java..")
                    .because(
                            "Whole-order Production locking boundary delegates to"
                                    + " ProductionOrderViewService; no direct repository access");

    @ArchTest
    static final ArchRule releaseProductsMustNotUseWarehouseInternals =
            noClasses()
                    .that()
                    .haveSimpleName("ReleaseProductsService")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse.application..",
                            "com.tmp.warehouse.domain..",
                            "com.tmp.warehouse.persistence..")
                    .because("ReleaseProductsService must not depend on Warehouse internals");

    @ArchTest
    static final ArchRule productionCancellationMustNotDependOnWarehouse =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("ProductionCancellation")
                    .or()
                    .haveSimpleName("JdbcProductionCancellationRepository")
                    .or()
                    .haveSimpleName("CancelOrderProductionService")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.warehouse..")
                    .because(
                            "STAGE7-014 Production Cancellation must not call Warehouse API or"
                                    + " touch Warehouse packages");

    @ArchTest
    static final ArchRule productionCancellationMustNotDependOnOrderOrCuttingInternals =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("ProductionCancellation")
                    .or()
                    .haveSimpleName("JdbcProductionCancellationRepository")
                    .or()
                    .haveSimpleName("CancelOrderProductionService")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.order.application..",
                            "com.tmp.order.domain..",
                            "com.tmp.order.persistence..",
                            "com.tmp.cutting..")
                    .because(
                            "Production Cancellation processor loads durable payload only; no"
                                    + " OM/Cutting queries");

    @ArchTest
    static final ArchRule productionCancellationProcessorDoesNotQueryExternally =
            noClasses()
                    .that()
                    .haveSimpleName("ProductionCancellationProcessor")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.order..",
                            "com.tmp.warehouse..",
                            "com.tmp.cutting..")
                    .because(
                            "Cancellation processor must not perform external capability queries");

    @ArchTest
    static final ArchRule cancelOrderProductionServiceIsUserFacingCancellationBoundary =
            classes()
                    .that()
                    .haveSimpleName("CancelOrderProductionService")
                    .should()
                    .resideInAPackage("com.tmp.production.application..")
                    .because(
                            "User-facing Cancellation orchestration belongs to"
                                    + " CancelOrderProductionService");

    @ArchTest
    static final ArchRule productionCancellationDocumentServiceIsInternalGateway =
            noClasses()
                    .that()
                    .doNotHaveSimpleName("CancelOrderProductionService")
                    .and()
                    .doNotHaveSimpleName("ProductionCancellationDocumentService")
                    .and()
                    .doNotHaveSimpleName("ProductionCancellationDocumentServiceTest")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("ProductionCancellationDocumentService")
                    .because(
                            "Production Cancellation document gateway is internal; UI, bootstrap,"
                                    + " public API and other orchestrators must use"
                                    + " CancelOrderProductionService only");

    @ArchTest
    static final ArchRule productionOrderViewServiceMayUseCancellationQueryOnly =
            classes()
                    .that()
                    .haveSimpleName("ProductionOrderViewService")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production.application..",
                            "com.tmp.production.domain..",
                            "java..")
                    .because(
                            "Order Production View reads cancellation evidence via"
                                    + " ProductionCancellationQuery port only");

    @ArchTest
    static final ArchRule productionDomainEventsImplementDomainEvent =
            classes()
                    .that()
                    .resideInAPackage("com.tmp.production.application.event..")
                    .and()
                    .areTopLevelClasses()
                    .and()
                    .haveSimpleNameNotEndingWith("Test")
                    .should()
                    .implement(com.tmp.core.api.event.DomainEvent.class)
                    .because("Production business events must implement public DomainEvent contract");

    @ArchTest
    static final ArchRule productionEventProcessorsUsePublicTransactionalPublisher =
            classes()
                    .that()
                    .haveSimpleName("ProductionLaunchProcessor")
                    .or()
                    .haveSimpleName("ProductionReleaseProcessor")
                    .or()
                    .haveSimpleName("ProductionCancellationProcessor")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(com.tmp.document.api.TransactionalEventPublisher.class)
                    .because(
                            "Production event scheduling must use public TransactionalEventPublisher");

    @ArchTest
    static final ArchRule productionProcessorsMustNotUseDocumentEngineInternals =
            noClasses()
                    .that()
                    .haveSimpleName("ProductionLaunchProcessor")
                    .or()
                    .haveSimpleName("ProductionReleaseProcessor")
                    .or()
                    .haveSimpleName("ProductionCancellationProcessor")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.document.internal..")
                    .because(
                            "Production processors must not import Document Engine internal packages");

    @ArchTest
    static final ArchRule productionDomainEventsMustNotDependOnRepositories =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production.application.event..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production.domain.repository..",
                            "com.tmp.production.persistence..",
                            "org.springframework..")
                    .because(
                            "Production domain events are immutable notification facts, not persistence types");

    @ArchTest
    static final ArchRule productionHistoryOwnedByProductionModule =
            classes()
                    .that()
                    .haveSimpleName("ProductionHistoryEntry")
                    .or()
                    .haveSimpleName("ProductionHistoryRepository")
                    .or()
                    .haveSimpleName("ProductionHistoryService")
                    .or()
                    .haveSimpleName("JdbcProductionHistoryRepository")
                    .should()
                    .resideInAPackage("com.tmp.production..")
                    .because("Production Spec §22 business history is owned by tmp-production");

    @ArchTest
    static final ArchRule productionHistoryMustNotDependOnSecurityAudit =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("ProductionHistory")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.security..")
                    .because("Security Audit must not own Production business history");

    @ArchTest
    static final ArchRule productionHistoryMustNotDependOnAnalytics =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("ProductionHistory")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.analytics..")
                    .because("Production history must not send data to Analytics");

    @ArchTest
    static final ArchRule productionHistoryMustNotDependOnForeignInternals =
            noClasses()
                    .that()
                    .haveSimpleNameContaining("ProductionHistory")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse.application..",
                            "com.tmp.warehouse.domain..",
                            "com.tmp.warehouse.persistence..",
                            "com.tmp.order.application..",
                            "com.tmp.order.domain..",
                            "com.tmp.order.persistence..",
                            "com.tmp.cutting..")
                    .because(
                            "Production history store must not depend on Warehouse/OM/Cutting internals");

    @ArchTest
    static final ArchRule productionHistoryRepositoryIsAppendOnly =
            methods()
                    .that()
                    .areDeclaredIn(ProductionHistoryRepository.class)
                    .should()
                    .haveName("append")
                    .orShould()
                    .haveName("listByOrder")
                    .because("Production history write boundary is append-only");

    @ArchTest
    static final ArchRule productionPublicApiIsDtoOnlyAndIndependent =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production.api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production.application..",
                            "com.tmp.production.domain..",
                            "com.tmp.production.persistence..",
                            "com.tmp.order..",
                            "com.tmp.warehouse..",
                            "com.tmp.cutting..",
                            "com.tmp.security..")
                    .because("Public Query API is DTO-only and must not depend on internal capability types");

    @ArchTest
    static final ArchRule productionPublicQueryApiIsReadOnly =
            methods()
                    .that()
                    .areDeclaredIn(ProductionQueryApi.class)
                    .should()
                    .haveNameNotStartingWith("accept")
                    .andShould()
                    .haveNameNotStartingWith("launch")
                    .andShould()
                    .haveNameNotStartingWith("checkMaterials")
                    .andShould()
                    .haveNameNotStartingWith("createTransfer")
                    .andShould()
                    .haveNameNotStartingWith("confirmReceipt")
                    .andShould()
                    .haveNameNotStartingWith("release")
                    .andShould()
                    .haveNameNotStartingWith("cancel")
                    .andShould()
                    .haveNameNotStartingWith("create")
                    .andShould()
                    .haveNameNotStartingWith("update")
                    .andShould()
                    .haveNameNotStartingWith("delete")
                    .andShould()
                    .haveNameNotStartingWith("post");

    @ArchTest
    static final ArchRule defaultProductionQueryApiDoesNotTouchWarehouseOmCuttingInternals =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production.application..")
                    .and()
                    .haveSimpleName("DefaultProductionQueryApi")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.warehouse..", "com.tmp.order..", "com.tmp.cutting..")
                    .because(
                            "Public Query API implementation must only read via Production-owned services/ports");

    @ArchTest
    static final ArchRule productionItemStateMustNotDependOnHistory =
            noClasses()
                    .that()
                    .haveSimpleName("ProductionItemState")
                    .or()
                    .haveSimpleName("OrderProductionViewCalculator")
                    .or()
                    .haveSimpleName("ProductionOrderViewService")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleNameContaining("ProductionHistory")
                    .because(
                            "Current Production state must not be reconstructed from history"
                                    + " (not event sourcing)");

    @ArchTest
    static final ArchRule productionSecurityDependsOnPublicSecurityAndCapabilityApiOnly =
            classes()
                    .that()
                    .resideInAPackage("com.tmp.production.security..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.production.security..",
                            "com.tmp.production.api..",
                            "com.tmp.security.api..",
                            "com.tmp.capability.api..",
                            "java..",
                            "edu.umd.cs.findbugs..")
                    .because(
                            "Production permission contribution uses Security and Capability public"
                                    + " contracts only");

    @ArchTest
    static final ArchRule productionSecurityMustNotDependOnWarehouseInternals =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production.security..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse.application..",
                            "com.tmp.warehouse.domain..",
                            "com.tmp.warehouse.persistence..",
                            "com.tmp.warehouse.security..")
                    .because(
                            "Production permission catalogue must not depend on Warehouse internals;"
                                    + " Warehouse permissions are referenced in tests only");
}
