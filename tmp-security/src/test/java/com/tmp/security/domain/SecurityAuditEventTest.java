package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.UserId;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SecurityAuditEventTest {

    @Test
    void constructionSuccessWithNullableActorAndTarget() {
        SecurityAuditEvent event = SecurityAuditEvent.record(
                AuditEventId.generate(),
                Instant.parse("2026-07-23T03:00:00Z"),
                null,
                "unknown",
                AuditOperation.LOGIN_FAILURE,
                "USER",
                null,
                "Login failed",
                AuditResult.FAILURE);
        assertNull(event.actorUserId());
        assertNull(event.targetIdentifier());
        assertEquals(AuditOperation.LOGIN_FAILURE, event.operation());
        assertEquals(AuditResult.FAILURE, event.result());
    }

    @Test
    void rejectsNullRequiredFields() {
        AuditEventId id = AuditEventId.generate();
        Instant now = Instant.parse("2026-07-23T03:00:00Z");
        assertThrows(NullPointerException.class, () -> SecurityAuditEvent.record(
                id, now, null, "a", null, "USER", null, "d", AuditResult.SUCCESS));
        assertThrows(NullPointerException.class, () -> SecurityAuditEvent.record(
                id, now, null, "a", AuditOperation.LOGOUT, null, null, "d", AuditResult.SUCCESS));
        assertThrows(NullPointerException.class, () -> SecurityAuditEvent.record(
                id, now, null, "a", AuditOperation.LOGOUT, "USER", null, null, AuditResult.SUCCESS));
        assertThrows(NullPointerException.class, () -> SecurityAuditEvent.record(
                id, now, null, "a", AuditOperation.LOGOUT, "USER", null, "d", null));
    }

    @Test
    void allFieldsAreFinal() {
        for (Field field : SecurityAuditEvent.class.getDeclaredFields()) {
            assertTrue(Modifier.isFinal(field.getModifiers()), field.getName());
        }
    }

    @Test
    void withActorUserId() {
        UserId actor = UserId.generate();
        SecurityAuditEvent event = SecurityAuditEvent.record(
                AuditEventId.generate(),
                Instant.parse("2026-07-23T03:00:00Z"),
                actor,
                "admin",
                AuditOperation.USER_CREATED,
                "USER",
                "target-id",
                "User created",
                AuditResult.SUCCESS);
        assertEquals(actor, event.actorUserId());
        assertEquals("target-id", event.targetIdentifier());
    }
}
