package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String SAMPLE_HASH = "$2a$10$SampleHashValueForUserTestXX";

    @Test
    void createActiveUser() {
        User user = sampleUser();
        assertTrue(user.isActive());
        assertFalse(user.isDeleted());
        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(0L, user.version());
    }

    @Test
    void deletedTransitionsOnce() {
        User deleted = sampleUser().deleted(CLOCK);
        assertTrue(deleted.isDeleted());
        assertThrows(UserAlreadyDeletedException.class, () -> deleted.deleted(CLOCK));
        assertThrows(UserAlreadyDeletedException.class,
                () -> deleted.withDisplayName(DisplayName.of("X"), CLOCK));
    }

    @Test
    void mutationsReturnNewInstances() {
        User original = sampleUser();
        User renamed = original.withDisplayName(DisplayName.of("Bob"), CLOCK);
        assertEquals("Alice", original.displayName().value());
        assertEquals("Bob", renamed.displayName().value());
        assertEquals(original.id(), renamed.id());
    }

    @Test
    void toStringNeverContainsHash() {
        User user = sampleUser();
        assertFalse(user.toString().contains(SAMPLE_HASH));
        assertTrue(user.toString().contains("REDACTED"));
    }

    private static User sampleUser() {
        return User.createActive(
                UserId.generate(),
                Login.of("alice"),
                DisplayName.of("Alice"),
                PasswordHash.of(SAMPLE_HASH),
                CLOCK);
    }
}
