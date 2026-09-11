package com.tmp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Stage 6 Warehouse architecture boundaries for UI and Public API usage.
 */
@AnalyzeClasses(
        packages = "com.tmp",
        importOptions = ImportOption.DoNotIncludeTests.class)
class Stage6WarehouseArchitectureTest {

    @ArchTest
    static final ArchRule uiShellUsesOnlyWarehousePublicApi =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.ui.shell..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse.application..",
                            "com.tmp.warehouse.persistence..",
                            "com.tmp.warehouse.domain..",
                            "com.tmp.warehouse.security..")
                    .because(
                            "Warehouse UI must call only com.tmp.warehouse.api Public API; "
                                    + "no application/persistence/domain/security internals");

    @ArchTest
    static final ArchRule externalModulesUseOnlyWarehousePublicApi =
            noClasses()
                    .that()
                    .resideInAnyPackage("com.tmp.ui.shell..", "com.tmp.order..", "com.tmp.bootstrap..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.warehouse.application..",
                            "com.tmp.warehouse.persistence..",
                            "com.tmp.warehouse.domain..",
                            "com.tmp.warehouse.security..")
                    .because(
                            "Cross-capability modules must use com.tmp.warehouse.api public contracts only");

    @ArchTest
    static final ArchRule warehouseUsesOnlyDocumentEnginePublicApi =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.warehouse..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.tmp.document.persistence..")
                    .because(
                            "Warehouse may use only com.tmp.document.api; Document Engine "
                                    + "persistence adapters stay inside Document Engine");

    @ArchTest
    static final ArchRule warehouseDoesNotAccessSecurityInternals =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.warehouse..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.security.application..",
                            "com.tmp.security.persistence..",
                            "com.tmp.security.domain..")
                    .because(
                            "Warehouse may use only com.tmp.security.api; responsibility uses opaque"
                                    + " user ids and must not query Security persistence");

    @ArchTest
    static final ArchRule warehouseDomainHasNoJavaFx =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.warehouse..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("javafx..")
                    .because("Warehouse module must not depend on JavaFX; UI lives in tmp-ui-shell");
    @ArchTest
    static final ArchRule uiShellMustNotDependOnWarehouseDemandCommandApi =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.ui.shell..")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("WarehouseDemandCommandApi")
                    .because("WarehouseDemandCommandApi is trusted backend orchestration, not a UI API");

    @ArchTest
    static final ArchRule uiShellMustNotDependOnWarehouseReferenceQueryApi =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.ui.shell..")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("WarehouseReferenceQueryApi")
                    .because("UI must not call the no-RBAC WarehouseReferenceQueryApi");

    @ArchTest
    static final ArchRule noForbiddenWarehouseSettingsConfigurationTypes =
            noClasses()
                    .that()
                    .resideInAnyPackage("com.tmp.warehouse..", "com.tmp.ui.shell.screen.warehouse..")
                    .should()
                    .haveSimpleNameContaining("MaterialWarehouseMapping")
                    .orShould()
                    .haveSimpleNameContaining("PreferredSourceWarehouse")
                    .orShould()
                    .haveSimpleNameContaining("DefaultDestinationCell")
                    .orShould()
                    .haveSimpleNameContaining("RoutingPriority")
                    .orShould()
                    .haveSimpleNameContaining("WarehouseConfiguration")
                    .because(
                            "Stage 3.5.13 Settings must not introduce material→warehouse mapping, "
                                    + "preferred source, default destination cell, or routing priority");
}
