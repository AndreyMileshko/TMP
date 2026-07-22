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
class Stage3CapabilityEngineArchitectureTest {

    @ArchTest
    static final ArchRule capabilityEngineDoesNotDependOnBusinessModules =
            noClasses()
                    .that().resideInAPackage("com.tmp.capability..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.tmp.order..",
                            "com.tmp.warehouse..",
                            "com.tmp.production..",
                            "com.tmp.cutting..",
                            "com.tmp.analytics..",
                            "com.tmp.security..")
                    .because("Capability Engine must remain independent from business modules");

    @ArchTest
    static final ArchRule capabilityEngineUsesOnlyCorePublicApi =
            noClasses()
                    .that().resideInAPackage("com.tmp.capability..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.core..")
                                    .and(resideOutsideOfPackage("com.tmp.core.api..")))
                    .because("Capability Engine may depend on Platform Core only through com.tmp.core.api..");

    @ArchTest
    static final ArchRule capabilityEngineUsesOnlyDocumentPublicApi =
            noClasses()
                    .that().resideInAPackage("com.tmp.capability..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.document..")
                                    .and(resideOutsideOfPackage("com.tmp.document.api..")))
                    .because("Capability Engine may depend on Document Engine only through com.tmp.document.api..");

    @ArchTest
    static final ArchRule externalModulesUseOnlyCapabilityPublicApi =
            noClasses()
                    .that().resideOutsideOfPackage("com.tmp.capability..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.capability..")
                                    .and(resideOutsideOfPackage("com.tmp.capability.api..")))
                    .because("External modules may depend on Capability Engine only through com.tmp.capability.api..");

    @ArchTest
    static final ArchRule platformCoreDoesNotDependOnCapabilityEngine =
            noClasses()
                    .that().resideInAPackage("com.tmp.core..")
                    .should().dependOnClassesThat().resideInAPackage("com.tmp.capability..")
                    .because("Platform Core must remain independent from Capability Engine");

    @ArchTest
    static final ArchRule documentEngineDoesNotDependOnCapabilityEngine =
            noClasses()
                    .that().resideInAPackage("com.tmp.document..")
                    .should().dependOnClassesThat().resideInAPackage("com.tmp.capability..")
                    .because("Document Engine must remain independent from Capability Engine");

    @ArchTest
    static final ArchRule uiShellDoesNotDependOnCapabilityEngine =
            noClasses()
                    .that().resideInAPackage("com.tmp.ui..")
                    .should().dependOnClassesThat().resideInAPackage("com.tmp.capability..")
                    .because("UI shell must remain independent from Capability Engine; bootstrap bridges status text");

    @ArchTest
    static final ArchRule sampleCapabilityUsesOnlyPublicApis =
            noClasses()
                    .that().resideInAPackage("com.tmp.capability.sample..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.core..")
                                    .and(resideOutsideOfPackage("com.tmp.core.api..")))
                    .orShould().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.document..")
                                    .and(resideOutsideOfPackage("com.tmp.document.api..")))
                    .because("Sample capabilities must use only public Platform Core and Document Engine APIs");
}
