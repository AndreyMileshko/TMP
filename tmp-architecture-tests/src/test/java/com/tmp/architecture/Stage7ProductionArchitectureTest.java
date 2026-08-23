package com.tmp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tmp.order.api.OrderQueryService;
import com.tmp.production.application.port.OrderSpecificationQueryPort;
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
                    .resideInAPackage("com.tmp.production..")
                    .and()
                    .resideOutsideOfPackage("com.tmp.production.application.internal..")
                    .and()
                    .haveSimpleNameNotContaining("ReleaseProducts")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("ProductionReleaseDocumentService")
                    .because(
                            "Production Release document gateway is internal; business workflow must"
                                    + " use ReleaseProductsService");

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
}
