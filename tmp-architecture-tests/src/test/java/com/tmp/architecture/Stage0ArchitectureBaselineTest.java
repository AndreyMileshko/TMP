package com.tmp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.tmp",
        importOptions = ImportOption.DoNotIncludeTests.class)
class Stage0ArchitectureBaselineTest {

    @ArchTest
    static final ArchRule uiShellDoesNotDependOnInfrastructure =
            noClasses()
                    .that().resideInAPackage("com.tmp.ui..")
                    .should().dependOnClassesThat().resideInAPackage("com.tmp.infra..")
                    .because("UI shell must not depend on infrastructure packages");

    @ArchTest
    static final ArchRule infrastructureDoesNotDependOnUi =
            noClasses()
                    .that().resideInAPackage("com.tmp.infra..")
                    .should().dependOnClassesThat().resideInAPackage("com.tmp.ui..")
                    .because("Infrastructure must not depend on JavaFX UI packages");

    @ArchTest
    static final ArchRule uiShellDoesNotDependOnSpring =
            noClasses()
                    .that().resideInAPackage("com.tmp.ui..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .because("JavaFX shell remains independent from Spring");
}
