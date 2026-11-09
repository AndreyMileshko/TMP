package com.tmp.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * COL-010 Reference Data architecture boundaries (ADR-039 / TMP-SPEC-015 §3).
 */
@AnalyzeClasses(
        packages = "com.tmp",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ReferenceDataArchitectureTest {

    @ArchTest
    static final ArchRule externalModulesUseOnlyReferenceDataPublicApi =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("com.tmp.reference..")
                    .should()
                    .dependOnClassesThat(
                            resideInAnyPackage("com.tmp.reference..")
                                    .and(resideOutsideOfPackage("com.tmp.reference.api..")))
                    .because(
                            "COL-010: external modules may depend on Reference Data only through "
                                    + "com.tmp.reference.api");

    @ArchTest
    static final ArchRule referenceDataDoesNotDependOnBusinessUiOrBootstrap =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.reference..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.order..",
                            "com.tmp.warehouse..",
                            "com.tmp.production..",
                            "com.tmp.ui..",
                            "com.tmp.bootstrap..",
                            "javafx..")
                    .because(
                            "COL-010: Reference Data must not depend on Order Management, Warehouse,"
                                    + " Production, UI or bootstrap");

    @ArchTest
    static final ArchRule orderWarehouseProductionDoNotDependOnReferenceData =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "com.tmp.order..", "com.tmp.warehouse..", "com.tmp.production..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.tmp.reference..")
                    .because(
                            "COL-010: Order Management, Warehouse and Production must not depend on"
                                    + " Reference Data");

    @ArchTest
    static final ArchRule referenceDataDomainIsFrameworkFree =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.reference.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "org.springframework.jdbc..",
                            "java.sql..",
                            "javafx..")
                    .because(
                            "COL-010: Reference Data domain must stay free of Spring / JDBC / JavaFX")
                    .allowEmptyShould(true);
}