package com.tmp.capability.contribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.EventContribution;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.SettingsContribution;
import com.tmp.capability.api.ViewDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityContributionCatalogsTest {

    private static CapabilityDescriptor descriptorFor(String id) {
        return CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Test capability " + id)
                .permissions(List.of(PermissionDescriptor.of(id + ".perm", "Perm", "desc")))
                .commands(List.of(CommandDescriptor.of(id + ".cmd", "Cmd", List.of())))
                .views(List.of(ViewDescriptor.of(id + ".view", "View", id + ".nav")))
                .navigationContributions(List.of(NavigationContribution.of(id + ".nav", "Nav", id + ".view", 0)))
                .settings(List.of(SettingsContribution.of(id + ".setting", "Setting", "desc", "0")))
                .events(List.of(EventContribution.of(id + ".event", "Event")))
                .build();
    }

    @Test
    void registerInternalContributionsPopulatesAllSixCatalogsForOneOwner() {
        CapabilityContributionCatalogs catalogs = new CapabilityContributionCatalogs();
        CapabilityDescriptor descriptor = descriptorFor("cap.a");

        catalogs.registerInternalContributions(descriptor);

        assertEquals(1, catalogs.activePermissions().size());
        assertEquals(1, catalogs.activeCommands().size());
        assertEquals(1, catalogs.activeViews().size());
        assertEquals(1, catalogs.activeNavigation().size());
        assertEquals(1, catalogs.activeSettings().size());
        assertEquals(1, catalogs.activeEvents().size());
        assertEquals(
                CapabilityId.of("cap.a"),
                catalogs.permissions().ownerOf("cap.a.perm").orElseThrow());
        assertEquals(
                CapabilityId.of("cap.a"), catalogs.commands().ownerOf("cap.a.cmd").orElseThrow());
        assertEquals(CapabilityId.of("cap.a"), catalogs.views().ownerOf("cap.a.view").orElseThrow());
        assertEquals(
                CapabilityId.of("cap.a"),
                catalogs.navigation().ownerOf("cap.a.nav").orElseThrow());
        assertEquals(
                CapabilityId.of("cap.a"),
                catalogs.settings().ownerOf("cap.a.setting").orElseThrow());
        assertEquals(CapabilityId.of("cap.a"), catalogs.events().ownerOf("cap.a.event").orElseThrow());
    }

    @Test
    void bulkRollbackRemovesEntriesFromAllSixCatalogsAtomically() {
        CapabilityContributionCatalogs catalogs = new CapabilityContributionCatalogs();
        catalogs.registerInternalContributions(descriptorFor("cap.a"));
        catalogs.registerInternalContributions(descriptorFor("cap.b"));

        catalogs.removeAllForOwner(CapabilityId.of("cap.a"));

        assertEquals(1, catalogs.activePermissions().size());
        assertEquals(1, catalogs.activeCommands().size());
        assertEquals(1, catalogs.activeViews().size());
        assertEquals(1, catalogs.activeNavigation().size());
        assertEquals(1, catalogs.activeSettings().size());
        assertEquals(1, catalogs.activeEvents().size());
        assertTrue(catalogs.permissions().ownerOf("cap.a.perm").isEmpty());
        assertEquals(
                CapabilityId.of("cap.b"),
                catalogs.permissions().ownerOf("cap.b.perm").orElseThrow());
    }

    @Test
    void conflictDuringRegistrationRollsBackAllCatalogsForOwner() {
        CapabilityContributionCatalogs catalogs = new CapabilityContributionCatalogs();
        catalogs.registerInternalContributions(descriptorFor("cap.a"));

        CapabilityDescriptor conflicting = CapabilityDescriptor.builder()
                .id(CapabilityId.of("cap.b"))
                .name("Capability cap.b")
                .version(CapabilityVersion.of("1.0.0"))
                .description("Conflicts on permission id with cap.a")
                .permissions(List.of(PermissionDescriptor.of("cap.a.perm", "Conflict", "desc")))
                .commands(List.of(CommandDescriptor.of("cap.b.cmd", "Cmd", List.of())))
                .build();

        assertThrows(IllegalStateException.class, () -> catalogs.registerInternalContributions(conflicting));

        assertEquals(1, catalogs.activePermissions().size());
        assertEquals(1, catalogs.activeCommands().size());
        assertTrue(catalogs.commands().ownerOf("cap.b.cmd").isEmpty());
        assertEquals(
                CapabilityId.of("cap.a"),
                catalogs.permissions().ownerOf("cap.a.perm").orElseThrow());
    }
}
