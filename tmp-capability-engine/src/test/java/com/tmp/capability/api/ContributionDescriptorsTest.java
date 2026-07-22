package com.tmp.capability.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContributionDescriptorsTest {

    @Nested
    class PermissionDescriptorTests {

        @Test
        void validConstructionExposesGivenValues() {
            PermissionDescriptor descriptor =
                    PermissionDescriptor.of("perm.sample.view", "View sample", "Allows viewing sample data");

            assertEquals("perm.sample.view", descriptor.permissionId());
            assertEquals("View sample", descriptor.displayName());
            assertEquals("Allows viewing sample data", descriptor.description());
        }

        @Test
        void blankPermissionIdRejected() {
            assertThrows(
                    IllegalArgumentException.class, () -> PermissionDescriptor.of("   ", "View sample", "desc"));
        }

        @Test
        void nullPermissionIdRejected() {
            assertThrows(
                    NullPointerException.class, () -> PermissionDescriptor.of(null, "View sample", "desc"));
        }

        @Test
        void equalsAndHashCodeById() {
            PermissionDescriptor first = PermissionDescriptor.of("perm.sample", "A", "desc-a");
            PermissionDescriptor second = PermissionDescriptor.of("perm.sample", "B", "desc-b");
            PermissionDescriptor different = PermissionDescriptor.of("perm.other", "A", "desc-a");

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertNotEquals(first, different);
        }
    }

    @Nested
    class CommandDescriptorTests {

        @Test
        void validConstructionExposesGivenValues() {
            CommandDescriptor descriptor =
                    CommandDescriptor.of("cmd.sample.run", "Run sample", List.of("perm.sample.run"));

            assertEquals("cmd.sample.run", descriptor.commandId());
            assertEquals("Run sample", descriptor.displayName());
            assertEquals(List.of("perm.sample.run"), descriptor.requiredPermissionIds());
        }

        @Test
        void blankCommandIdRejected() {
            assertThrows(
                    IllegalArgumentException.class, () -> CommandDescriptor.of(" ", "Run sample", List.of()));
        }

        @Test
        void nullCommandIdRejected() {
            assertThrows(NullPointerException.class, () -> CommandDescriptor.of(null, "Run sample", List.of()));
        }

        @Test
        void requiredPermissionIdsIsUnmodifiable() {
            CommandDescriptor descriptor = CommandDescriptor.of("cmd.sample", "Sample", List.of("perm.a"));

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> descriptor.requiredPermissionIds().add("perm.b"));
        }

        @Test
        void requiredPermissionIdsIsDefensivelyCopied() {
            List<String> mutableSource = new ArrayList<>(List.of("perm.a"));
            CommandDescriptor descriptor = CommandDescriptor.of("cmd.sample", "Sample", mutableSource);
            mutableSource.add("perm.b");

            assertEquals(List.of("perm.a"), descriptor.requiredPermissionIds());
        }

        @Test
        void equalsAndHashCodeById() {
            CommandDescriptor first = CommandDescriptor.of("cmd.sample", "A", List.of());
            CommandDescriptor second = CommandDescriptor.of("cmd.sample", "B", List.of("perm.x"));
            CommandDescriptor different = CommandDescriptor.of("cmd.other", "A", List.of());

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertNotEquals(first, different);
        }
    }

    @Nested
    class ViewDescriptorTests {

        @Test
        void validConstructionExposesGivenValues() {
            ViewDescriptor descriptor = ViewDescriptor.of("view.sample", "Sample view", "nav.sample");

            assertEquals("view.sample", descriptor.viewId());
            assertEquals("Sample view", descriptor.displayName());
            assertEquals("nav.sample", descriptor.navigationTargetId());
        }

        @Test
        void blankViewIdRejected() {
            assertThrows(
                    IllegalArgumentException.class, () -> ViewDescriptor.of("", "Sample view", "nav.sample"));
        }

        @Test
        void nullViewIdRejected() {
            assertThrows(NullPointerException.class, () -> ViewDescriptor.of(null, "Sample view", "nav.sample"));
        }

        @Test
        void equalsAndHashCodeById() {
            ViewDescriptor first = ViewDescriptor.of("view.sample", "A", "nav.a");
            ViewDescriptor second = ViewDescriptor.of("view.sample", "B", "nav.b");
            ViewDescriptor different = ViewDescriptor.of("view.other", "A", "nav.a");

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertNotEquals(first, different);
        }
    }

    @Nested
    class NavigationContributionTests {

        @Test
        void validConstructionExposesGivenValues() {
            NavigationContribution contribution =
                    NavigationContribution.of("nav.sample", "Sample", "view.sample", 10);

            assertEquals("nav.sample", contribution.navigationId());
            assertEquals("Sample", contribution.displayName());
            assertEquals("view.sample", contribution.viewId());
            assertEquals(10, contribution.order());
        }

        @Test
        void blankNavigationIdRejected() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> NavigationContribution.of("  ", "Sample", "view.sample", 0));
        }

        @Test
        void nullNavigationIdRejected() {
            assertThrows(
                    NullPointerException.class,
                    () -> NavigationContribution.of(null, "Sample", "view.sample", 0));
        }

        @Test
        void equalsAndHashCodeById() {
            NavigationContribution first = NavigationContribution.of("nav.sample", "A", "view.a", 1);
            NavigationContribution second = NavigationContribution.of("nav.sample", "B", "view.b", 2);
            NavigationContribution different = NavigationContribution.of("nav.other", "A", "view.a", 1);

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertNotEquals(first, different);
        }
    }

    @Test
    void allDescriptorsAreFinalPureDataTypes() {
        assertTrue(Modifier.isFinal(PermissionDescriptor.class.getModifiers()));
        assertTrue(Modifier.isFinal(CommandDescriptor.class.getModifiers()));
        assertTrue(Modifier.isFinal(ViewDescriptor.class.getModifiers()));
        assertTrue(Modifier.isFinal(NavigationContribution.class.getModifiers()));
    }
}
