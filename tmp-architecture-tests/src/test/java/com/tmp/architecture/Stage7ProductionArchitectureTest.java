package com.tmp.architecture;

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
                            "com.tmp.cut..application..",
                            "com.tmp.cut..domain..",
                            "com.tmp.cut..persistence..")
                    .because(
                            "Production must interact cross-capability only via public contracts "
                                    + "and must not depend on internal application/domain/persistence packages");
}
