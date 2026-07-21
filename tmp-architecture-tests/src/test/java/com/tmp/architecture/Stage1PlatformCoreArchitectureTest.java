package com.tmp.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.tmp",
        importOptions = ImportOption.DoNotIncludeTests.class)
class Stage1PlatformCoreArchitectureTest {

    @ArchTest
    static final ArchRule platformCoreDoesNotDependOnUi =
            noClasses()
                    .that().resideInAPackage("com.tmp.core..")
                    .should().dependOnClassesThat().resideInAPackage("com.tmp.ui..")
                    .because("Platform Core must remain independent from JavaFX UI");

    @ArchTest
    static final ArchRule platformCoreDoesNotDependOnInfrastructure =
            noClasses()
                    .that().resideInAPackage("com.tmp.core..")
                    .should().dependOnClassesThat().resideInAPackage("com.tmp.infra..")
                    .because("Platform Core must remain independent from database infrastructure");

    @ArchTest
    static final ArchRule externalModulesUseOnlyCorePublicApi =
            noClasses()
                    .that().resideOutsideOfPackage("com.tmp.core..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.core..")
                                    .and(resideOutsideOfPackage("com.tmp.core.api..")))
                    .because("External modules may depend on com.tmp.core only through com.tmp.core.api..");

    @ArchTest
    static final ArchRule uiShellDoesNotDependOnPlatformCore =
            noClasses()
                    .that().resideInAPackage("com.tmp.ui..")
                    .should().dependOnClassesThat().resideInAPackage("com.tmp.core..")
                    .because("UI shell must remain independent from Platform Core; bootstrap bridges status text");
}
