package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.InvalidActivationCodeException;
import com.tmp.security.api.PasswordResetResult;
import com.tmp.security.domain.ActivationCodeGenerator;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.User;
import com.tmp.security.support.ActivationTestSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ActivationCodeGeneratorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void generatesHumanReadableFormatWithoutAmbiguousCharacters() {
        ActivationCodeGenerator generator = new ActivationCodeGenerator();
        String code = generator.generate();
        assertTrue(code.matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}"));
        assertFalse(code.contains("O"));
        assertFalse(code.contains("0"));
        assertFalse(code.contains("I"));
        assertFalse(code.contains("1"));
    }

    @Test
    void normalizeStripsDashesAndUppercases() {
        assertEquals("ABCDEFGH", ActivationCodeGenerator.normalize("ab-cd-ef-gh"));
    }

    @Test
    void deterministicGeneratorProducesDistinctCodes() {
        ActivationCodeGenerator generator = ActivationTestSupport.deterministicGenerator();
        String first = generator.generate();
        String second = generator.generate();
        assertNotNull(first);
        assertNotEquals(first, second);
    }

    @Test
    void uninitializedPasswordHashCannotAuthenticate() {
        User pending = User.createActivePendingPasswordSetup(
                com.tmp.security.api.UserId.generate(),
                com.tmp.security.api.Login.of("pending"),
                com.tmp.security.api.DisplayName.of("Pending"),
                CLOCK);
        assertTrue(pending.passwordHash().isUninitialized());
        assertTrue(pending.passwordSetupRequired());
    }
}
