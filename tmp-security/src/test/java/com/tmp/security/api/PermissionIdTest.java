package com.tmp.security.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PermissionIdTest {

    private static final List<String> CATALOGUE = List.of(
            "security.users.view",
            "security.users.create",
            "security.users.update",
            "security.users.delete",
            "security.users.reset-password",
            "security.roles.view",
            "security.roles.create",
            "security.roles.update",
            "security.roles.delete",
            "security.roles.assign",
            "security.permissions.assign",
            "security.audit.view");

    @Test
    void acceptsAllCatalogueIds() {
        for (String id : CATALOGUE) {
            PermissionId permissionId = PermissionId.of(id);
            assertEquals(id, permissionId.value());
            assertEquals(id, permissionId.toString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "security",
            "security.users",
            "security.users.view.extra",
            "Security.users.view",
            "security.Users.view",
            "1security.users.view",
            "security.1users.view",
            "security.users.1view",
            " ",
            ""
    })
    void rejectsMalformed(String raw) {
        assertThrows(IllegalArgumentException.class, () -> PermissionId.of(raw));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> PermissionId.of(null));
    }

    @Test
    void equalsAndHashCode() {
        PermissionId a = PermissionId.of("security.users.view");
        PermissionId b = PermissionId.of("security.users.view");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
