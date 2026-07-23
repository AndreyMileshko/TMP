package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RoleAssignmentTest {

    @Test
    void equalityOnNaturalKey() {
        UserId user = UserId.generate();
        RoleId role = RoleId.generate();
        RoleAssignment a = RoleAssignment.of(user, role, Instant.parse("2026-07-23T03:00:00Z"));
        RoleAssignment b = RoleAssignment.of(user, role, Instant.parse("2026-07-23T04:00:00Z"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, RoleAssignment.of(user, RoleId.generate(), a.assignedAt()));
    }
}
