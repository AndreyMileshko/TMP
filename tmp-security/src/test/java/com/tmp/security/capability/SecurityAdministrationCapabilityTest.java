package com.tmp.security.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SecurityPermissions;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SecurityAdministrationCapabilityTest {

    @Test
    void descriptorHasExactlyTwelvePermissionsAndMatchingNavCommands() {
        SecurityAdministrationCapability capability = new SecurityAdministrationCapability();
        CapabilityDescriptor descriptor = capability.descriptor();

        Set<String> permissionIds = descriptor.permissions().stream()
                .map(PermissionDescriptor::permissionId)
                .collect(Collectors.toSet());
        Set<String> expected = Set.of(
                SecurityPermissions.USERS_VIEW.value(),
                SecurityPermissions.USERS_CREATE.value(),
                SecurityPermissions.USERS_UPDATE.value(),
                SecurityPermissions.USERS_DELETE.value(),
                SecurityPermissions.USERS_RESET_PASSWORD.value(),
                SecurityPermissions.ROLES_VIEW.value(),
                SecurityPermissions.ROLES_CREATE.value(),
                SecurityPermissions.ROLES_UPDATE.value(),
                SecurityPermissions.ROLES_DELETE.value(),
                SecurityPermissions.ROLES_ASSIGN.value(),
                SecurityPermissions.PERMISSIONS_ASSIGN.value(),
                SecurityPermissions.AUDIT_VIEW.value());
        assertEquals(expected, permissionIds);
        assertEquals(12, permissionIds.size());
        assertTrue(descriptor.dependencies().isEmpty());

        Set<String> commandIds = descriptor.commands().stream()
                .map(CommandDescriptor::commandId)
                .collect(Collectors.toSet());
        Set<String> navigationIds = descriptor.navigationContributions().stream()
                .map(NavigationContribution::navigationId)
                .collect(Collectors.toSet());
        assertEquals(commandIds, navigationIds);

        capability.onInitialize();
        capability.onActivate();
        capability.onDeactivate();
        capability.onStop();
    }

    @Test
    void securityPermissionsConstantsMatchCatalogue() {
        assertEquals(PermissionId.of("security.users.view"), SecurityPermissions.USERS_VIEW);
        assertEquals(PermissionId.of("security.audit.view"), SecurityPermissions.AUDIT_VIEW);
    }
}
