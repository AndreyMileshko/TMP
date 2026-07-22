package com.tmp.capability.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityDescriptorTest {

    private static DocumentProcessor processorFor(String documentTypeId) {
        return new DocumentProcessor() {
            @Override
            public String documentTypeId() {
                return documentTypeId;
            }

            @Override
            public void validateCreate(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void validateUpdate(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onPost(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onUnpost(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onClose(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onDelete(DocumentOperationContext context) {
                // test double: no-op
            }
        };
    }

    private static CapabilityDescriptor.Builder validBuilder() {
        return CapabilityDescriptor.builder()
                .id(CapabilityId.of("sample.capability"))
                .name("Sample Capability")
                .version(CapabilityVersion.of("1.0.0"))
                .description("A sample technical capability")
                .dependencies(List.of(
                        DependencyDescriptor.of(CapabilityId.of("other.capability"), CapabilityVersion.of("1.0.0"))))
                .permissions(List.of(PermissionDescriptor.of("perm.sample.view", "View sample", "desc")))
                .commands(List.of(CommandDescriptor.of("cmd.sample.run", "Run sample", List.of("perm.sample.view"))))
                .views(List.of(ViewDescriptor.of("view.sample", "Sample view", "nav.sample")))
                .navigationContributions(
                        List.of(NavigationContribution.of("nav.sample", "Sample", "view.sample", 0)))
                .documents(List.of(DocumentContribution.of(
                        "sample.document", "Sample document", "desc", processorFor("sample.document"))))
                .events(List.of(EventContribution.of("sample.event", "desc")))
                .settings(List.of(SettingsContribution.of("sample.setting", "Sample setting", "desc", "0")));
    }

    @Test
    void validDescriptorBuildsSuccessfullyWithAllContributionTypesPopulated() {
        CapabilityDescriptor descriptor = validBuilder().build();

        assertEquals(CapabilityId.of("sample.capability"), descriptor.id());
        assertEquals("Sample Capability", descriptor.name());
        assertEquals(CapabilityVersion.of("1.0.0"), descriptor.version());
        assertEquals(1, descriptor.dependencies().size());
        assertEquals(1, descriptor.permissions().size());
        assertEquals(1, descriptor.commands().size());
        assertEquals(1, descriptor.views().size());
        assertEquals(1, descriptor.navigationContributions().size());
        assertEquals(1, descriptor.documents().size());
        assertEquals(1, descriptor.events().size());
        assertEquals(1, descriptor.settings().size());
    }

    @Test
    void missingIdRejected() {
        assertThrows(
                NullPointerException.class,
                () -> CapabilityDescriptor.builder()
                        .name("Sample Capability")
                        .version(CapabilityVersion.of("1.0.0"))
                        .description("desc")
                        .build());
    }

    @Test
    void duplicatePermissionIdRejected() {
        CapabilityDescriptor.Builder builder = validBuilder()
                .permissions(List.of(
                        PermissionDescriptor.of("perm.sample", "A", "desc"),
                        PermissionDescriptor.of("perm.sample", "B", "desc")));

        assertThrows(CapabilityDescriptor.DuplicateContributionException.class, builder::build);
    }

    @Test
    void duplicateCommandIdRejected() {
        CapabilityDescriptor.Builder builder = validBuilder()
                .commands(List.of(
                        CommandDescriptor.of("cmd.sample", "A", List.of()),
                        CommandDescriptor.of("cmd.sample", "B", List.of())));

        assertThrows(CapabilityDescriptor.DuplicateContributionException.class, builder::build);
    }

    @Test
    void duplicateViewIdRejected() {
        CapabilityDescriptor.Builder builder = validBuilder()
                .views(List.of(
                        ViewDescriptor.of("view.sample", "A", "nav.a"),
                        ViewDescriptor.of("view.sample", "B", "nav.b")));

        assertThrows(CapabilityDescriptor.DuplicateContributionException.class, builder::build);
    }

    @Test
    void duplicateNavigationIdRejected() {
        CapabilityDescriptor.Builder builder = validBuilder()
                .navigationContributions(List.of(
                        NavigationContribution.of("nav.sample", "A", "view.a", 0),
                        NavigationContribution.of("nav.sample", "B", "view.b", 1)));

        assertThrows(CapabilityDescriptor.DuplicateContributionException.class, builder::build);
    }

    @Test
    void duplicateEventIdRejected() {
        CapabilityDescriptor.Builder builder = validBuilder()
                .events(List.of(EventContribution.of("event.sample", "A"), EventContribution.of("event.sample", "B")));

        assertThrows(CapabilityDescriptor.DuplicateContributionException.class, builder::build);
    }

    @Test
    void duplicateSettingsKeyRejected() {
        CapabilityDescriptor.Builder builder = validBuilder()
                .settings(List.of(
                        SettingsContribution.of("setting.sample", "A", "desc", "0"),
                        SettingsContribution.of("setting.sample", "B", "desc", "1")));

        assertThrows(CapabilityDescriptor.DuplicateContributionException.class, builder::build);
    }

    @Test
    void duplicateDocumentTypeIdRejected() {
        CapabilityDescriptor.Builder builder = validBuilder()
                .documents(List.of(
                        DocumentContribution.of("doc.sample", "A", "desc", processorFor("doc.sample")),
                        DocumentContribution.of("doc.sample", "B", "desc", processorFor("doc.sample"))));

        assertThrows(CapabilityDescriptor.DuplicateContributionException.class, builder::build);
    }

    @Test
    void duplicateDependencyRejected() {
        CapabilityDescriptor.Builder builder = validBuilder()
                .dependencies(List.of(
                        DependencyDescriptor.of(CapabilityId.of("dep.capability"), CapabilityVersion.of("1.0.0")),
                        DependencyDescriptor.of(CapabilityId.of("dep.capability"), CapabilityVersion.of("2.0.0"))));

        DependencyValidationException exception =
                assertThrows(DependencyValidationException.class, builder::build);
        assertEquals(DependencyValidationException.DependencyValidationReason.DUPLICATE_DEPENDENCY, exception.reason());
    }

    @Test
    void dependenciesListIsImmutable() {
        CapabilityDescriptor descriptor = validBuilder().build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.dependencies().add(
                        DependencyDescriptor.of(CapabilityId.of("x"), CapabilityVersion.of("1.0.0"))));
    }

    @Test
    void permissionsListIsImmutable() {
        CapabilityDescriptor descriptor = validBuilder().build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.permissions().add(PermissionDescriptor.of("perm.x", "X", "desc")));
    }

    @Test
    void commandsListIsImmutable() {
        CapabilityDescriptor descriptor = validBuilder().build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.commands().add(CommandDescriptor.of("cmd.x", "X", List.of())));
    }

    @Test
    void viewsListIsImmutable() {
        CapabilityDescriptor descriptor = validBuilder().build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.views().add(ViewDescriptor.of("view.x", "X", "nav.x")));
    }

    @Test
    void navigationContributionsListIsImmutable() {
        CapabilityDescriptor descriptor = validBuilder().build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.navigationContributions().add(
                        NavigationContribution.of("nav.x", "X", "view.x", 0)));
    }

    @Test
    void documentsListIsImmutable() {
        CapabilityDescriptor descriptor = validBuilder().build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.documents().add(
                        DocumentContribution.of("doc.x", "X", "desc", processorFor("doc.x"))));
    }

    @Test
    void publicServicesListIsImmutable() {
        CapabilityDescriptor descriptor = validBuilder().build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.publicServices().add(
                        PublicServiceContribution.of(Runnable.class, (Runnable) () -> { })));
    }

    @Test
    void eventsListIsImmutable() {
        CapabilityDescriptor descriptor = validBuilder().build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.events().add(EventContribution.of("event.x", "desc")));
    }

    @Test
    void settingsListIsImmutable() {
        CapabilityDescriptor descriptor = validBuilder().build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.settings().add(SettingsContribution.of("setting.x", "X", "desc", "0")));
    }

    @Test
    void capabilityInterfaceHasNoUnexpectedFrameworkDependency() {
        assertTrue(Capability.class.isInterface());
        for (var method : Capability.class.getMethods()) {
            for (var paramType : method.getParameterTypes()) {
                assertTrue(!paramType.getName().startsWith("com.tmp.core") && !paramType.getName().startsWith("com.tmp.document"));
            }
        }
    }
}
