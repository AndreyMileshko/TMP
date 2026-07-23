package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PermissionDefinitionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final PermissionId ID = PermissionId.of("security.users.view");

    @Test
    void registerStartsActive() {
        PermissionDefinition def = PermissionDefinition.register(ID, "View users", "desc", CLOCK);
        assertTrue(def.active());
        assertEquals(ID, def.permissionId());
    }

    @Test
    void activateDeactivateIdempotent() {
        PermissionDefinition active = PermissionDefinition.register(ID, "View", "", CLOCK);
        PermissionDefinition inactive = active.deactivated();
        assertFalse(inactive.active());
        assertEquals(ID, inactive.permissionId());
        assertSame(inactive, inactive.deactivated());
        assertSame(active, active.activated());
        assertTrue(inactive.activated().active());
    }

    @Test
    void metadataChangeKeepsPermissionId() {
        PermissionDefinition original = PermissionDefinition.register(ID, "View", "a", CLOCK);
        PermissionDefinition renamed = original.withDisplayName("View users").withDescription("b");
        assertEquals(ID, renamed.permissionId());
        assertEquals("View users", renamed.displayName());
        assertEquals("b", renamed.description());
        assertEquals("View", original.displayName());
    }
}
