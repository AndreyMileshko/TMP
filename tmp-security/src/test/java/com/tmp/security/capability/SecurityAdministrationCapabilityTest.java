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
    void permissionDisplayNamesAreRussianAndIdsUnchanged() {
        SecurityAdministrationCapability capability = new SecurityAdministrationCapability();
        var byId = capability.descriptor().permissions().stream()
                .collect(Collectors.toMap(PermissionDescriptor::permissionId, PermissionDescriptor::displayName));

        assertEquals("Просмотр пользователей", byId.get(SecurityPermissions.USERS_VIEW.value()));
        assertEquals("Создание пользователей", byId.get(SecurityPermissions.USERS_CREATE.value()));
        assertEquals("Изменение пользователей", byId.get(SecurityPermissions.USERS_UPDATE.value()));
        assertEquals("Удаление пользователей", byId.get(SecurityPermissions.USERS_DELETE.value()));
        assertEquals("Сброс паролей пользователей", byId.get(SecurityPermissions.USERS_RESET_PASSWORD.value()));
        assertEquals("Просмотр ролей", byId.get(SecurityPermissions.ROLES_VIEW.value()));
        assertEquals("Создание ролей", byId.get(SecurityPermissions.ROLES_CREATE.value()));
        assertEquals("Изменение ролей", byId.get(SecurityPermissions.ROLES_UPDATE.value()));
        assertEquals("Удаление ролей", byId.get(SecurityPermissions.ROLES_DELETE.value()));
        assertEquals("Назначение и отзыв ролей", byId.get(SecurityPermissions.ROLES_ASSIGN.value()));
        assertEquals("Управление разрешениями ролей", byId.get(SecurityPermissions.PERMISSIONS_ASSIGN.value()));
        assertEquals("Просмотр журнала безопасности", byId.get(SecurityPermissions.AUDIT_VIEW.value()));

        for (String displayName : byId.values()) {
            assertTrue(containsCyrillic(displayName), () -> "Expected Russian display name: " + displayName);
        }
    }

    @Test
    void securityPermissionsConstantsMatchCatalogue() {
        assertEquals(PermissionId.of("security.users.view"), SecurityPermissions.USERS_VIEW);
        assertEquals(PermissionId.of("security.audit.view"), SecurityPermissions.AUDIT_VIEW);
    }

    @Test
    void auditNavigationCaptionIsRussian() {
        SecurityAdministrationCapability capability = new SecurityAdministrationCapability();
        String caption = capability.descriptor().navigationContributions().stream()
                .filter(nav -> SecurityAdministrationCapability.NAV_AUDIT.equals(nav.navigationId()))
                .map(NavigationContribution::displayName)
                .findFirst()
                .orElseThrow();
        assertEquals("Аудит безопасности", caption);
        assertEquals(SecurityAdministrationCapability.NAV_AUDIT, capability.descriptor()
                .navigationContributions().stream()
                .filter(nav -> "Аудит безопасности".equals(nav.displayName()))
                .map(NavigationContribution::navigationId)
                .findFirst()
                .orElseThrow());
    }

    private static boolean containsCyrillic(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 'А' && ch <= 'я' || ch == 'ё' || ch == 'Ё') {
                return true;
            }
        }
        return false;
    }
}
