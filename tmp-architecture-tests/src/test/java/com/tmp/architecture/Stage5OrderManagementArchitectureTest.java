package com.tmp.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;

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
                            "org.springframework.jdbc..",
                            "jakarta.persistence..",
                            "javax.persistence..",
                            "org.hibernate..",
                            "javafx..",
                            "java.sql..")
                    .because(
                            "Order domain must stay free of Spring / JDBC / JPA / Hibernate / "
                                    + "JavaFX / java.sql");

    @ArchTest
    static final ArchRule orderModuleHasNoJavaFxDependency =
            noClasses()
                    .that().resideInAPackage("com.tmp.order..")
                    .should().dependOnClassesThat().resideInAnyPackage("javafx..")
                    .because("Order Management must not depend on JavaFX; UI lives in tmp-ui-shell");

    @ArchTest
    static final ArchRule uiShellDoesNotImportOrderOrDocumentInternals =
            noClasses()
                    .that().resideInAPackage("com.tmp.ui.shell..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage(
                                            "com.tmp.order.application..",
                                            "com.tmp.order.persistence..",
                                            "com.tmp.order.domain..")
                                    .or(
                                            resideInAnyPackage("com.tmp.document..")
                                                    .and(
                                                            resideOutsideOfPackage(
                                                                    "com.tmp.document.api.."))))
                    .because("UI shell and OrderUiErrorMapper may use only public Order/Document "
                            + "APIs; no application/persistence/domain or Document Engine internals");

    @ArchTest
    static final ArchRule otherCapabilitiesDoNotUseOrderUiApi =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(
                            "com.tmp.order..", "com.tmp.ui.shell..", "com.tmp.bootstrap..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.tmp.order.api.ui..")
                    .because(
                            "com.tmp.order.api.ui is allowed only for tmp-ui-shell, bootstrap wiring, "
                                    + "and Order Management itself");

    @ArchTest
    static final ArchRule orderDocumentProcessorsDoNotUseEventBusDirectly =
            noClasses()
                    .that().resideInAPackage("com.tmp.order.application.document..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(com.tmp.core.api.EventBus.class)
                    .because(
                            "Order document processors must publish through public "
                                    + "TransactionalEventPublisher, not EventBus");

    @ArchTest
    static final ArchRule orderDocumentProcessorsOnPostReturnsVoid =
            methods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage("com.tmp.order.application.document..")
                    .and()
                    .haveName("onPost")
                    .should()
                    .haveRawReturnType(void.class)
                    .because("DocumentProcessor.onPost() must remain void");

    @ArchTest
    static final ArchRule orderNoJacksonOrGenericJsonPayload =
            noClasses()
                    .that().resideInAPackage("com.tmp.order..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.fasterxml.jackson..")
                    .because("Order Management typed payload must not use Jackson/generic JSON");

    @ArchTest
    static final ArchRule orderTypedPayloadDoesNotUseObjectOrMap =
            classes()
                    .that()
                    .resideInAPackage("com.tmp.order.application.payload..")
                    .should(notUseGenericObjectOrMapInTypedPayload())
                    .because(
                            "Typed payload contracts must not use java.lang.Object or java.util.Map "
                                    + "(including Map<String, Object>)");

    @ArchTest
    static final ArchRule orderNoJpaOrHibernate =
            noClasses()
                    .that().resideInAPackage("com.tmp.order..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("Order Management persistence uses JDBC only");

    @ArchTest
    static final ArchRule jdbcTemplateUsedOnlyInOrderPersistence =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.order..")
                    .and(resideOutsideOfPackage("com.tmp.order.persistence.."))
                    .and()
                    .haveSimpleNameNotContaining("AutoConfiguration")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(org.springframework.jdbc.core.JdbcTemplate.class)
                    .because(
                            "JDBC access belongs in com.tmp.order.persistence..; wiring may reference "
                                    + "JdbcTemplate only in AutoConfiguration");

    @ArchTest
    static final ArchRule orderPublicItemDtoDoesNotExposeDraftRevision =
            fields()
                    .that()
                    .areDeclaredIn(com.tmp.order.api.OrderItemDto.class)
                    .should()
                    .haveNameNotContaining("draft")
                    .because("Public Query API must not expose Draft Revision pointers");

    @ArchTest
    static final ArchRule orderDoesNotOwnForeignBusinessStateClasses =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.order..")
                    .and(
                            resideInAnyPackage(
                                    "com.tmp.order.domain..",
                                    "com.tmp.order.application..",
                                    "com.tmp.order.persistence..",
                                    "com.tmp.order.api.."))
                    .and(forbiddenForeignBusinessModelClass())
                    .should()
                    .beInterfaces()
                    .allowEmptyShould(true)
                    .because(
                            "Order Management must not own Production/Warehouse/Cutting state models");

    @ArchTest
    static final ArchRule orderDoesNotOwnForeignBusinessStateFields =
            noFields()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage("com.tmp.order..")
                    .and(forbiddenForeignBusinessField())
                    .should()
                    .beStatic()
                    .allowEmptyShould(true)
                    .because(
                            "Order Management must not own foreign capability quantity/state fields");

    @ArchTest
    static final ArchRule platformCoreDoesNotOwnOrderPayload =
            noClasses()
                    .that().resideInAPackage("com.tmp.core..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.tmp.order.application.payload..", "com.tmp.order.persistence..")
                    .because("Typed document payload ownership stays in Order Management");

    @ArchTest
    static final ArchRule publicQueryApiDoesNotDependOnUiApi =
            noClasses()
                    .that()
                    .resideInAnyPackage("com.tmp.order.api..")
                    .and(resideOutsideOfPackage("com.tmp.order.api.ui.."))
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.tmp.order.api.ui..")
                    .because("Public Query API must stay isolated from ui-facing mutating contracts");

    @ArchTest
    static final ArchRule orderQueryServiceIsReadOnly =
            methods()
                    .that()
                    .areDeclaredIn(com.tmp.order.api.OrderQueryService.class)
                    .should()
                    .haveNameNotStartingWith("save")
                    .andShould()
                    .haveNameNotStartingWith("create")
                    .andShould()
                    .haveNameNotStartingWith("update")
                    .andShould()
                    .haveNameNotStartingWith("delete")
                    .andShould()
                    .haveNameNotStartingWith("post")
                    .andShould()
                    .haveNameNotStartingWith("approve")
                    .andShould()
                    .haveNameNotStartingWith("cancel")
                    .because("Public Query API is read-only");

    private static ArchCondition<JavaClass> notUseGenericObjectOrMapInTypedPayload() {
        return new ArchCondition<>("not use Object or Map in typed payload contracts") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaField field : javaClass.getFields()) {
                    reportIfForbidden(
                            field.getRawType(),
                            javaClass.getName() + "." + field.getName() + " field type",
                            field,
                            events);
                }
                for (JavaConstructor constructor : javaClass.getConstructors()) {
                    for (JavaParameter parameter : constructor.getParameters()) {
                        reportIfForbidden(
                                parameter.getRawType(),
                                javaClass.getName()
                                        + " constructor parameter "
                                        + parameter.getIndex(),
                                parameter,
                                events);
                    }
                }
                for (JavaMethod method : javaClass.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                        continue;
                    }
                    if (isStandardEquals(method)) {
                        continue;
                    }
                    reportIfForbidden(
                            method.getRawReturnType(),
                            javaClass.getName() + "." + method.getName() + "() return type",
                            method,
                            events);
                    for (JavaParameter parameter : method.getParameters()) {
                        reportIfForbidden(
                                parameter.getRawType(),
                                javaClass.getName()
                                        + "."
                                        + method.getName()
                                        + "() parameter "
                                        + parameter.getIndex(),
                                parameter,
                                events);
                    }
                }
            }

            private static boolean isStandardEquals(JavaMethod method) {
                return "equals".equals(method.getName())
                        && method.getRawParameterTypes().size() == 1
                        && method.getRawParameterTypes().get(0).isEquivalentTo(Object.class);
            }

            private static void reportIfForbidden(
                    JavaClass type,
                    String location,
                    Object owner,
                    ConditionEvents events) {
                if (type.isEquivalentTo(Object.class)) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    owner,
                                    location + " uses forbidden Object"));
                    return;
                }
                if (type.isEquivalentTo(Map.class) || "java.util.Map".equals(type.getName())) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    owner, location + " uses forbidden Map"));
                }
            }
        };
    }

    @ArchTest
    static final ArchRule orderImportCoreDoesNotDependOnUiOrFirebirdOrStxtParser =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "com.tmp.order.application.imports..", "com.tmp.order.api.imports..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "javafx..",
                            "com.tmp.ui..",
                            "org.firebirdsql..",
                            "java.sql..",
                            "com.tmp.order.application.imports.stxt..")
                    .because(
                            "Import Core is source-neutral (ADR-029): no JavaFX, UI shell, Firebird, "
                                    + "JDBC or STXT parser dependencies");

    @ArchTest
    static final ArchRule orderImportCoreDoesNotUseDocumentOrSecurityInternals =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.order.application.imports..")
                    .should()
                    .dependOnClassesThat(
                            resideInAnyPackage("com.tmp.document..", "com.tmp.security..")
                                    .and(
                                            resideOutsideOfPackages(
                                                    "com.tmp.document.api..",
                                                    "com.tmp.security.api..")))
                    .because(
                            "Import Core may use only public Document Engine and Security APIs");

    @ArchTest
    static final ArchRule onlyImportMetadataJdbcAdapterTouchesImportMetadataPersistence =
            noClasses()
                    .that()
                    .resideInAPackage("com.tmp.order.application.imports..")
                    .and()
                    .haveSimpleNameNotEndingWith("Repository")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(org.springframework.jdbc.core.JdbcTemplate.class)
                    .because(
                            "Only the import metadata persistence adapter may use JDBC; Import Core "
                                    + "orchestrates business documents through application services");

    private static DescribedPredicate<JavaClass> forbiddenForeignBusinessModelClass() {
        return new DescribedPredicate<>("forbidden foreign business model class") {
            @Override
            public boolean test(JavaClass input) {
                return switch (input.getSimpleName()) {
                    case "ProductionStatus",
                            "ProductionState",
                            "StockPosition",
                            "Reservation",
                            "WarehouseMovement",
                            "CuttingPlan" -> true;
                    default -> false;
                };
            }
        };
    }

    private static DescribedPredicate<JavaField> forbiddenForeignBusinessField() {
        return new DescribedPredicate<>("forbidden foreign business field") {
            @Override
            public boolean test(JavaField input) {
                return switch (input.getName()) {
                    case "launchedQuantity", "releasedQuantity", "producedQuantity" -> true;
                    default -> false;
                };
            }
        };
    }

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
