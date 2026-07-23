package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionTest {

    @Test
    void construction() {
        Session session = Session.of(
                SessionId.generate(),
                UserId.generate(),
                Login.of("admin"),
                Instant.parse("2026-07-23T03:00:00Z"));
        assertEquals("admin", session.login().value());
        assertFalse(session.toString().toLowerCase().contains("password"));
    }
}
