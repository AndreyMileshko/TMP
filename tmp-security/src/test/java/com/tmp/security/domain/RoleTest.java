package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RoleTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final PermissionId VIEW = PermissionId.of("security.users.view");
    private static final PermissionId CREATE = PermissionId.of("security.users.create");

    @Test
    void createEmptyPermissionSet() {
        Role role = Role.create(RoleId.generate(), "Admin", "desc", CLOCK);
        assertTrue(role.permissions().isEmpty());
        assertEquals("Admin", role.name());
        assertEquals("desc", role.description());
    }

    @Test
    void grantAndRevokeAreIdempotent() {
        Role base = Role.create(RoleId.generate(), "Admin", "", CLOCK);
        Role granted = base.grantPermission(VIEW, CLOCK);
        assertEquals(1, granted.permissions().size());
        assertTrue(granted.permissions().contains(VIEW));
        assertSame(granted, granted.grantPermission(VIEW, CLOCK));

        Role withTwo = granted.grantPermission(CREATE, CLOCK);
        assertEquals(2, withTwo.permissions().size());

        Role revoked = withTwo.revokePermission(VIEW, CLOCK);
        assertEquals(1, revoked.permissions().size());
        assertTrue(revoked.permissions().contains(CREATE));
        assertSame(revoked, revoked.revokePermission(VIEW, CLOCK));
    }

    @Test
    void nameChangeDoesNotMutatePermissions() {
        Role base = Role.create(RoleId.generate(), "Admin", "", CLOCK).grantPermission(VIEW, CLOCK);
        Role renamed = base.withName("Security Admin", CLOCK);
        assertEquals("Admin", base.name());
        assertEquals("Security Admin", renamed.name());
        assertEquals(base.permissions(), renamed.permissions());
        assertNotSame(base, renamed);
    }

    @Test
    void originalUnaffectedByDerivedMutation() {
        Role original = Role.create(RoleId.generate(), "R", "", CLOCK);
        Role derived = original.grantPermission(VIEW, CLOCK);
        assertTrue(original.permissions().isEmpty());
        assertEquals(1, derived.permissions().size());
    }
}
