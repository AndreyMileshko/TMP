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
    static final ArchRule warehouseDomainHasNoJavaFx =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.warehouse..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("javafx..")
                    .because("Warehouse module must not depend on JavaFX; UI lives in tmp-ui-shell");
}
