package com.tmp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
    static final ArchRule onlyProcessorUsesRepository =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.production..")
                    .and()
                    .haveSimpleNameNotContaining("Processor")
                    .and()
                    .resideOutsideOfPackage("com.tmp.production.persistence..")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("ProductionItemStateRepository")
                    .because(
                            "Repository is called only by Processor and persistence adapters");
}
