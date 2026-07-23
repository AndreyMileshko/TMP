package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UserStatusTest {

    @Test
    void exhaustiveValues() {
        assertArrayEquals(
                new UserStatus[] {UserStatus.ACTIVE, UserStatus.DELETED},
                UserStatus.values());
        assertEquals(2, UserStatus.values().length);
    }
}
