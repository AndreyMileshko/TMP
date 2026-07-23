package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectivePermissionCalculatorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final PermissionId VIEW = PermissionId.of("security.users.view");
    private static final PermissionId CREATE = PermissionId.of("security.users.create");
    private static final PermissionId DELETE = PermissionId.of("security.users.delete");
    private static final UserId USER = UserId.generate();

    @Test
    void individualRevokeWinsOverRoleGrant() {
        Role role = Role.create(RoleId.generate(), "R", "", CLOCK).grantPermission(VIEW, CLOCK);
        IndividualPermissionOverride revoke =
                IndividualPermissionOverride.of(USER, VIEW, PermissionOverrideDecision.REVOKE, CLOCK);
        assertFalse(EffectivePermissionCalculator.isGranted(VIEW, Set.of(revoke), Set.of(role)));
    }

    @Test
    void individualGrantWinsWithoutRole() {
        IndividualPermissionOverride grant =
                IndividualPermissionOverride.of(USER, VIEW, PermissionOverrideDecision.GRANT, CLOCK);
        assertTrue(EffectivePermissionCalculator.isGranted(VIEW, Set.of(grant), Set.of()));
    }

    @Test
    void unionOfRolesGrantsIfAnyRoleHasPermission() {
        Role a = Role.create(RoleId.generate(), "A", "", CLOCK).grantPermission(VIEW, CLOCK);
        Role b = Role.create(RoleId.generate(), "B", "", CLOCK).grantPermission(CREATE, CLOCK);
        assertTrue(EffectivePermissionCalculator.isGranted(VIEW, Set.of(), Set.of(a, b)));
        assertTrue(EffectivePermissionCalculator.isGranted(CREATE, Set.of(), Set.of(a, b)));
        assertFalse(EffectivePermissionCalculator.isGranted(DELETE, Set.of(), Set.of(a, b)));
    }

    @Test
    void noOverrideAndNoRoleDenies() {
        assertFalse(EffectivePermissionCalculator.isGranted(VIEW, Set.of(), Set.of()));
    }

    @Test
    void inactiveDeclaredPermissionExcludedEvenWithGrant() {
        IndividualPermissionOverride grant =
                IndividualPermissionOverride.of(USER, DELETE, PermissionOverrideDecision.GRANT, CLOCK);
        Set<PermissionId> effective = EffectivePermissionCalculator.effectivePermissions(
                Set.of(VIEW), Set.of(grant), Set.of());
        assertFalse(effective.contains(DELETE));
        assertEquals(Set.of(), effective);
    }

    @Test
    void combinedMultiRoleMultiPermission() {
        Role a = Role.create(RoleId.generate(), "A", "", CLOCK).grantPermission(VIEW, CLOCK);
        Role b = Role.create(RoleId.generate(), "B", "", CLOCK).grantPermission(CREATE, CLOCK);
        IndividualPermissionOverride revoke =
                IndividualPermissionOverride.of(USER, VIEW, PermissionOverrideDecision.REVOKE, CLOCK);
        IndividualPermissionOverride grant =
                IndividualPermissionOverride.of(USER, DELETE, PermissionOverrideDecision.GRANT, CLOCK);
        Set<PermissionId> effective = EffectivePermissionCalculator.effectivePermissions(
                Set.of(VIEW, CREATE, DELETE), Set.of(revoke, grant), Set.of(a, b));
        assertEquals(Set.of(CREATE, DELETE), effective);
    }
}
