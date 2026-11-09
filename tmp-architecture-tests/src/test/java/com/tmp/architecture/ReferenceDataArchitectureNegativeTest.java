package com.tmp.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tmp.order.architecture.OrderUsesReferenceInternalsViolator;
import com.tmp.reference.architecture.ReferenceUsesOrderInternalsViolator;
import com.tmp.reference.domain.ReferenceDataDomainSpringViolator;
import org.junit.jupiter.api.Test;

/**
 * Verifies COL-010 ArchUnit rules fail on intentional test-only violators.
 */
class ReferenceDataArchitectureNegativeTest {

    @Test
    void externalModulesMustUseOnlyPublicApiRuleDetectsViolation() {
        JavaClasses violators =
                new ClassFileImporter().importClasses(OrderUsesReferenceInternalsViolator.class);

        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackages("com.tmp.reference..")
                        .should()
                        .dependOnClassesThat(
                                resideInAnyPackage("com.tmp.reference..")
                                        .and(resideOutsideOfPackage("com.tmp.reference.api..")));

        assertThrows(AssertionError.class, () -> rule.check(violators));
    }

    @Test
    void referenceDataMustNotDependOnOrderRuleDetectsViolation() {
        JavaClasses violators =
                new ClassFileImporter().importClasses(ReferenceUsesOrderInternalsViolator.class);

        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("com.tmp.reference..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("com.tmp.order..");

        assertThrows(AssertionError.class, () -> rule.check(violators));
    }

    @Test
    void orderMustNotDependOnReferenceDataRuleDetectsViolation() {
        JavaClasses violators =
                new ClassFileImporter().importClasses(OrderUsesReferenceInternalsViolator.class);

        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("com.tmp.order..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("com.tmp.reference..");

        assertThrows(AssertionError.class, () -> rule.check(violators));
    }

    @Test
    void referenceDataDomainFrameworkFreeRuleDetectsSpringDependency() {
        JavaClasses violator =
                new ClassFileImporter().importClasses(ReferenceDataDomainSpringViolator.class);

        assertThrows(
                AssertionError.class,
                () -> ReferenceDataArchitectureTest.referenceDataDomainIsFrameworkFree.check(
                        violator));
    }
}
