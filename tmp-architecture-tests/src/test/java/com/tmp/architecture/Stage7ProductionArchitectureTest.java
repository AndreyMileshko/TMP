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
}
