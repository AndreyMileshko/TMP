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
class Stage2DocumentEngineArchitectureTest {

    @ArchTest
    static final ArchRule documentEngineDoesNotDependOnBusinessModules =
            noClasses()
                    .that().resideInAPackage("com.tmp.document..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.tmp.order..",
                            "com.tmp.warehouse..",
                            "com.tmp.production..",
                            "com.tmp.cutting..",
                            "com.tmp.analytics..")
                    .because("Document Engine must remain independent from business modules");

    @ArchTest
    static final ArchRule documentEngineUsesOnlyCorePublicApi =
            noClasses()
                    .that().resideInAPackage("com.tmp.document..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.core..")
                                    .and(resideOutsideOfPackage("com.tmp.core.api..")))
                    .because("Document Engine may depend on Platform Core only through com.tmp.core.api..");

    @ArchTest
    static final ArchRule externalModulesUseOnlyDocumentPublicApi =
            noClasses()
                    .that().resideOutsideOfPackage("com.tmp.document..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.document..")
                                    .and(resideOutsideOfPackage("com.tmp.document.api..")))
                    .because("External modules may depend on Document Engine only through com.tmp.document.api..");

    @ArchTest
    static final ArchRule uiShellDoesNotDependOnDocumentEngine =
            noClasses()
                    .that().resideInAPackage("com.tmp.ui..")
                    .should().dependOnClassesThat().resideInAPackage("com.tmp.document..")
                    .because("UI shell must remain independent from Document Engine; bootstrap bridges content");
}
