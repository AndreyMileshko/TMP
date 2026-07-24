package com.tmp.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Stage 5 architecture boundaries for the Order Management Capability ({@code com.tmp.order..}).
 *
 * <p>These rules protect the module boundaries defined by the Stage 5 Manifest (§3/§16) and
 * ADR-003/004/019/028: external access only through the public {@code com.tmp.order.api} package,
 * dependency on other Capabilities only through their public {@code *.api} packages (in particular
 * no imports of internal Document Engine classes), a framework-free domain, no JavaFX, and no
 * coupling to future business modules. The rules hold on the empty bootstrap module and stay in
 * force as aggregates are added in later Stage 5 tasks.
 */
@AnalyzeClasses(
        packages = "com.tmp",
        importOptions = ImportOption.DoNotIncludeTests.class)
class Stage5OrderManagementArchitectureTest {

    @ArchTest
    static final ArchRule externalModulesUseOnlyOrderPublicApi =
            noClasses()
                    .that().resideOutsideOfPackage("com.tmp.order..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.order..")
                                    .and(resideOutsideOfPackage("com.tmp.order.api..")))
                    .because("External modules may depend on Order Management only through "
                            + "com.tmp.order.api.. (read-only Query API); no external mutating API");

    @ArchTest
    static final ArchRule orderPublicApiDoesNotDependOnInternals =
            noClasses()
                    .that().resideInAPackage("com.tmp.order.api..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.tmp.order.domain..",
                            "com.tmp.order.application..",
                            "com.tmp.order.persistence..",
                            "com.tmp.order.capability..")
                    .because("Order public API must not depend on domain / application / "
                            + "persistence / capability internals");

    @ArchTest
    static final ArchRule orderUsesOnlyDocumentEnginePublicApi =
            noClasses()
                    .that().resideInAPackage("com.tmp.order..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.document..")
                                    .and(resideOutsideOfPackage("com.tmp.document.api..")))
                    .because("Order Management may depend on Document Engine only through "
                            + "com.tmp.document.api.. (no internal Document Engine imports)");

    @ArchTest
    static final ArchRule orderUsesOnlyCapabilityEnginePublicApi =
            noClasses()
                    .that().resideInAPackage("com.tmp.order..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.capability..")
                                    .and(resideOutsideOfPackage("com.tmp.capability.api..")))
                    .because("Order Management may depend on Capability Engine only through "
                            + "com.tmp.capability.api..");

    @ArchTest
    static final ArchRule orderUsesOnlyPlatformCorePublicApi =
            noClasses()
                    .that().resideInAPackage("com.tmp.order..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.core..")
                                    .and(resideOutsideOfPackage("com.tmp.core.api..")))
                    .because("Order Management may depend on Platform Core only through "
                            + "com.tmp.core.api..");

    @ArchTest
    static final ArchRule orderUsesOnlySecurityPublicApi =
            noClasses()
                    .that().resideInAPackage("com.tmp.order..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.security..")
                                    .and(resideOutsideOfPackage("com.tmp.security.api..")))
                    .because("Order Management may depend on Security only through "
                            + "com.tmp.security.api..");

    @ArchTest
    static final ArchRule orderInternalsUseOnlyAllowedDependencies =
            noClasses()
                    .that().resideInAPackage("com.tmp.order..")
                    .should().dependOnClassesThat(
                            resideOutsideOfPackages(
                                    "com.tmp.order..",
                                    "com.tmp.core.api..",
                                    "com.tmp.capability.api..",
                                    "com.tmp.document.api..",
                                    "com.tmp.security.api..",
                                    "java..",
                                    "javax..",
                                    "jakarta..",
                                    "org.springframework..",
                                    "org.postgresql..",
                                    "org.slf4j..",
                                    "edu.umd.cs.findbugs.."))
                    .because("Order Management may depend only on approved public APIs "
                            + "(core/capability/document/security .api) plus JDK / Spring / JDBC");

    @ArchTest
    static final ArchRule orderDoesNotDependOnOtherBusinessOrUiModules =
            noClasses()
                    .that().resideInAPackage("com.tmp.order..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.tmp.warehouse..",
                            "com.tmp.production..",
                            "com.tmp.cutting..",
                            "com.tmp.analytics..",
                            "com.tmp.ui..",
                            "com.tmp.bootstrap..")
                    .because("Order Management must not depend on other business modules, UI or "
                            + "bootstrap, and must not hold production-owned data");

    @ArchTest
    static final ArchRule orderDomainIsFrameworkFree =
            noClasses()
                    .that().resideInAPackage("com.tmp.order.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "org.hibernate..",
                            "javafx..")
                    .because("Order domain must stay free of Spring / JPA / Hibernate / JavaFX");

    @ArchTest
    static final ArchRule orderModuleHasNoJavaFxDependency =
            noClasses()
                    .that().resideInAPackage("com.tmp.order..")
                    .should().dependOnClassesThat().resideInAnyPackage("javafx..")
                    .because("Order Management must not depend on JavaFX; UI lives in tmp-ui-shell");

    @SafeVarargs
    private static com.tngtech.archunit.base.DescribedPredicate<JavaClass> resideOutsideOfPackages(
            String... packages) {
        com.tngtech.archunit.base.DescribedPredicate<JavaClass> predicate =
                resideOutsideOfPackage(packages[0]);
        for (int i = 1; i < packages.length; i++) {
            predicate = predicate.and(resideOutsideOfPackage(packages[i]));
        }
        return predicate;
    }
}
