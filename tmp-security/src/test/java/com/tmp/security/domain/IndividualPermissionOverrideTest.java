package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class IndividualPermissionOverrideTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void withDecisionReturnsNewSnapshot() {
        UserId user = UserId.generate();
        PermissionId permission = PermissionId.of("security.users.view");
        IndividualPermissionOverride grant =
                IndividualPermissionOverride.of(user, permission, PermissionOverrideDecision.GRANT, CLOCK);
        IndividualPermissionOverride revoke =
                grant.withDecision(PermissionOverrideDecision.REVOKE, CLOCK);
        assertEquals(PermissionOverrideDecision.GRANT, grant.decision());
        assertEquals(PermissionOverrideDecision.REVOKE, revoke.decision());
        assertNotSame(grant, revoke);
        assertEquals(grant, revoke);
        assertEquals(grant.hashCode(), revoke.hashCode());
        assertNotEquals(
                grant,
                IndividualPermissionOverride.of(
                        user,
                        PermissionId.of("security.users.create"),
                        PermissionOverrideDecision.GRANT,
                        CLOCK));
    }
}
