package com.tmp.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tmp.architecture.stage5.negative.Stage5ArchitectureViolators;
import com.tmp.core.api.EventBus;
import com.tmp.order.application.payload.GenericMapPayloadViolator;
import com.tmp.order.domain.DomainDependsOnSpringViolator;
import org.junit.jupiter.api.Test;

/**
 * Verifies that key Stage 5 ArchUnit rules fail on intentional test-only violators.
 */
class Stage5OrderManagementArchitectureNegativeTest {

    private static final JavaClasses VIOLATORS =
            new ClassFileImporter()
                    .importClasses(
                            Stage5ArchitectureViolators.OtherCapabilityUsesOrderUiApi.class,
                            Stage5ArchitectureViolators.ProcessorUsesEventBus.class,
                            Stage5ArchitectureViolators.MethodOnPostReturnsString.class,
                            Stage5ArchitectureViolators.OrderUsesInternalDocumentEngine.class,
                            Stage5ArchitectureViolators.UiUsesOrderPersistence.class,
                            Stage5ArchitectureViolators.OrderUsesJackson.class);

    @Test
    void otherCapabilitiesMustNotUseOrderUiApiRuleDetectsViolation() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackages(
                                "com.tmp.order..", "com.tmp.ui.shell..", "com.tmp.bootstrap..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("com.tmp.order.api.ui..");

        assertThrows(AssertionError.class, () -> rule.check(VIOLATORS));
    }

    @Test
    void documentProcessorOnPostVoidRuleDetectsNonVoidReturn() {
        ArchRule rule =
                methods()
                        .that()
                        .areDeclaredIn(Stage5ArchitectureViolators.MethodOnPostReturnsString.class)
                        .and()
                        .haveName("onPost")
                        .should()
                        .haveRawReturnType(void.class);

        assertThrows(AssertionError.class, () -> rule.check(VIOLATORS));
    }

    @Test
    void processorMustNotDependOnEventBusRuleDetectsDependency() {
        ArchRule rule =
                noClasses()
                        .that()
                        .areAssignableTo(com.tmp.document.api.DocumentProcessor.class)
                        .should()
                        .dependOnClassesThat()
                        .areAssignableTo(EventBus.class);

        assertThrows(AssertionError.class, () -> rule.check(VIOLATORS));
    }

    @Test
    void orderMustNotImportInternalDocumentEngineRuleDetectsViolation() {
        ArchRule rule =
                noClasses()
                        .that()
                        .areAssignableTo(Stage5ArchitectureViolators.OrderUsesInternalDocumentEngine.class)
                        .should()
                        .dependOnClassesThat(
                                resideInAnyPackage("com.tmp.document..")
                                        .and(resideOutsideOfPackage("com.tmp.document.api..")));

        assertThrows(AssertionError.class, () -> rule.check(VIOLATORS));
    }

    @Test
    void uiMustNotDependOnOrderPersistenceRuleDetectsViolation() {
        ArchRule rule =
                noClasses()
                        .that()
                        .areAssignableTo(Stage5ArchitectureViolators.UiUsesOrderPersistence.class)
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("com.tmp.order.persistence..");

        assertThrows(AssertionError.class, () -> rule.check(VIOLATORS));
    }

    @Test
    void orderMustNotUseJacksonRuleDetectsViolation() {
        ArchRule rule =
                noClasses()
                        .that()
                        .areAssignableTo(Stage5ArchitectureViolators.OrderUsesJackson.class)
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("com.fasterxml.jackson..");

        assertThrows(AssertionError.class, () -> rule.check(VIOLATORS));
    }

    @Test
    void orderDomainFrameworkFreeRuleDetectsSpringDependency() {
        JavaClasses violator =
                new ClassFileImporter().importClasses(DomainDependsOnSpringViolator.class);

        AssertionError error =
                assertThrows(
                        AssertionError.class,
                        () -> Stage5OrderManagementArchitectureTest.orderDomainIsFrameworkFree
                                .check(violator));

        String message = error.getMessage().toLowerCase();
        assertTrue(
                message.contains("springframework") || message.contains("spring"),
                () -> "Expected Spring dependency in violation message, got: " + error.getMessage());
    }

    @Test
    void orderTypedPayloadRuleDetectsMapObjectViolation() {
        JavaClasses violator =
                new ClassFileImporter().importClasses(GenericMapPayloadViolator.class);

        AssertionError error =
                assertThrows(
                        AssertionError.class,
                        () -> Stage5OrderManagementArchitectureTest
                                .orderTypedPayloadDoesNotUseObjectOrMap
                                .check(violator));

        String message = error.getMessage();
        assertTrue(
                message.contains("Map") || message.contains("Object"),
                () -> "Expected Map or Object in violation message, got: " + message);
    }

    @Test
    void productionTypedPayloadPassesObjectOrMapRule() {
        JavaClasses production =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.tmp.order.application.payload");

        Stage5OrderManagementArchitectureTest.orderTypedPayloadDoesNotUseObjectOrMap.check(
                production);
    }
}
