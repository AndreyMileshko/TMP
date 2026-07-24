package com.tmp.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@AnalyzeClasses(
        packages = "com.tmp",
        importOptions = ImportOption.DoNotIncludeTests.class)
class Stage4SecurityArchitectureTest {

    @ArchTest
    static final ArchRule securityDoesNotDependOnBusinessOrUiModules =
            noClasses()
                    .that().resideInAPackage("com.tmp.security..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.tmp.document..",
                            "com.tmp.ui..",
                            "com.tmp.bootstrap..",
                            "com.tmp.order..",
                            "com.tmp.warehouse..",
                            "com.tmp.production..",
                            "com.tmp.cutting..",
                            "com.tmp.analytics..")
                    .because("Security may depend only on core.api / capability.api, not UI or business modules");

    @ArchTest
    static final ArchRule securityInternalsUseOnlyAllowedDependencies =
            noClasses()
                    .that().resideInAPackage("com.tmp.security..")
                    .and().resideOutsideOfPackage("com.tmp.security.api..")
                    .should().dependOnClassesThat(
                            resideOutsideOfPackages(
                                    "com.tmp.security..",
                                    "com.tmp.core.api..",
                                    "com.tmp.capability.api..",
                                    "java..",
                                    "javax..",
                                    "jakarta..",
                                    "org.springframework..",
                                    "org.postgresql..",
                                    "org.slf4j..",
                                    "edu.umd.cs.findbugs.."))
                    .because("Security internals may depend only on approved platform/API/JDK/Spring/JDBC packages");

    @ArchTest
    static final ArchRule externalModulesUseOnlySecurityPublicApi =
            noClasses()
                    .that().resideOutsideOfPackage("com.tmp.security..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage("com.tmp.security..")
                                    .and(resideOutsideOfPackage("com.tmp.security.api..")))
                    .because("External modules may depend on Security only through com.tmp.security.api..");

    @ArchTest
    static final ArchRule securityDomainIsFrameworkFree =
            noClasses()
                    .that().resideInAPackage("com.tmp.security.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "org.hibernate..",
                            "javafx..")
                    .because("Security domain must stay free of Spring / JPA / Hibernate / JavaFX");

    @ArchTest
    static final ArchRule uiControllersDoNotDependOnSpring =
            noClasses()
                    .that().resideInAPackage("com.tmp.ui.shell..")
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .because("UI Controllers must not depend on Spring");

    @ArchTest
    static final ArchRule uiControllersAreNotSpringStereotypes =
            classes()
                    .that().resideInAPackage("com.tmp.ui.shell..")
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().notBeAnnotatedWith(Component.class)
                    .andShould().notBeAnnotatedWith(Service.class)
                    .andShould().notBeAnnotatedWith(Repository.class)
                    .andShould().notBeAnnotatedWith(Controller.class)
                    .because("UI Controllers must not carry Spring stereotypes");

    @ArchTest
    static final ArchRule uiControllersDoNotReachSecurityInternals =
            noClasses()
                    .that().resideInAPackage("com.tmp.ui.shell..")
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.tmp.security.persistence..",
                            "com.tmp.security.application..",
                            "com.tmp.security.domain..",
                            "com.tmp.security.infrastructure..",
                            "com.tmp.security.capability..")
                    .because("UI Controllers must not reach Security persistence/application/domain packages");

    @ArchTest
    static final ArchRule uiControllersDoNotDependOnRepositories =
            noClasses()
                    .that().resideInAPackage("com.tmp.ui.shell..")
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .because("UI Controllers must not depend on Repository types");

    @ArchTest
    static final ArchRule securityApiDoesNotExposeCredentialCarriers =
            classes()
                    .that().resideInAPackage("com.tmp.security.api..")
                    .should(notExposeCredentialCarriers())
                    .because("Security public API must not expose PasswordHash / credential carriers");

    @ArchTest
    static final ArchRule stage6PlusBusinessPackagesDoNotExist =
            noClasses()
                    .should().resideInAnyPackage(
                            "com.tmp.warehouse..",
                            "com.tmp.production..",
                            "com.tmp.cutting..",
                            "com.tmp.analytics..")
                    .because("Stage 6+ business packages must not exist yet "
                            + "(com.tmp.order.. is introduced by Stage 5)");

    @Test
    void reactorPomsForbidSpringSecurityWebStackAndIdentityProtocols() throws IOException {
        Path root = locateReactorRoot();
        try (Stream<Path> poms = Files.walk(root)) {
            poms.filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !path.toString().contains(Path.of("target").toString()))
                    .forEach(Stage4SecurityArchitectureTest::assertPomAllowsOnlyCryptoSecurity);
        }
    }

    private static void assertPomAllowsOnlyCryptoSecurity(Path pom) {
        String text;
        try {
            text = Files.readString(pom).toLowerCase(Locale.ROOT);
        } catch (IOException ex) {
            fail("Unable to read " + pom + ": " + ex.getMessage());
            return;
        }
        assertFalse(
                text.contains("spring-security-web")
                        || text.contains("spring-security-config")
                        || text.contains("spring-security-oauth")
                        || text.contains("spring-security-ldap"),
                () -> "Forbidden Spring Security stack dependency in " + pom);
        assertFalse(
                text.contains("jwt") || text.contains("oauth") || text.contains("ldap") || text.contains("saml"),
                () -> "Forbidden identity-protocol dependency token in " + pom);
        if (text.contains("org.springframework.security")) {
            assertTrue(
                    text.contains("spring-security-crypto"),
                    () -> "org.springframework.security groupId only allowed via spring-security-crypto in " + pom);
        }
    }

    private static Path locateReactorRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path candidate = cwd;
        for (int i = 0; i < 6; i++) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("tmp-security"))) {
                return candidate;
            }
            candidate = candidate.getParent();
            if (candidate == null) {
                break;
            }
        }
        // surefire cwd is typically the module directory
        Path fromModule = cwd.getParent();
        if (fromModule != null
                && Files.isRegularFile(fromModule.resolve("pom.xml"))
                && Files.isDirectory(fromModule.resolve("tmp-security"))) {
            return fromModule;
        }
        fail("Could not locate reactor root from " + cwd);
        return cwd;
    }

    private static ArchCondition<JavaClass> notExposeCredentialCarriers() {
        return new ArchCondition<>("not expose PasswordHash or credential-shaped carriers") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaField field : javaClass.getAllFields()) {
                    if (field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC)) {
                        continue;
                    }
                    String typeName = field.getRawType().getSimpleName();
                    String fieldName = field.getName().toLowerCase(Locale.ROOT);
                    if ("PasswordHash".equals(typeName)
                            || "char[]".equals(typeName)
                            || fieldName.contains("password")
                            || fieldName.contains("hash")) {
                        events.add(SimpleConditionEvent.violated(
                                field,
                                javaClass.getName() + "." + field.getName() + " exposes credential carrier type="
                                        + typeName));
                    }
                }
                for (JavaMethod method : javaClass.getMethods()) {
                    String returnName = method.getRawReturnType().getSimpleName();
                    if ("PasswordHash".equals(returnName)) {
                        events.add(SimpleConditionEvent.violated(
                                method, javaClass.getName() + "." + method.getName() + " returns PasswordHash"));
                    }
                    if ("char[]".equals(returnName) && method.getParameters().isEmpty()) {
                        events.add(SimpleConditionEvent.violated(
                                method, javaClass.getName() + "." + method.getName() + " returns char[]"));
                    }
                }
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
