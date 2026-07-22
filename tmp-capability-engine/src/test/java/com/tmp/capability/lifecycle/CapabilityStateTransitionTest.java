package com.tmp.capability.lifecycle;

import static com.tmp.capability.api.CapabilityLifecycleState.ACTIVE;
import static com.tmp.capability.api.CapabilityLifecycleState.DEACTIVATED;
import static com.tmp.capability.api.CapabilityLifecycleState.DISCOVERED;
import static com.tmp.capability.api.CapabilityLifecycleState.FAILED;
import static com.tmp.capability.api.CapabilityLifecycleState.INITIALIZED;
import static com.tmp.capability.api.CapabilityLifecycleState.REGISTERED;
import static com.tmp.capability.api.CapabilityLifecycleState.STOPPED;
import static com.tmp.capability.api.CapabilityLifecycleState.VALIDATED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityLifecycleState;
import java.util.EnumSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CapabilityStateTransitionTest {

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(DISCOVERED, VALIDATED),
                Arguments.of(VALIDATED, REGISTERED),
                Arguments.of(REGISTERED, INITIALIZED),
                Arguments.of(INITIALIZED, ACTIVE),
                Arguments.of(ACTIVE, STOPPED),
                Arguments.of(STOPPED, INITIALIZED),
                Arguments.of(STOPPED, DEACTIVATED),
                Arguments.of(DISCOVERED, FAILED),
                Arguments.of(VALIDATED, FAILED),
                Arguments.of(REGISTERED, FAILED),
                Arguments.of(INITIALIZED, FAILED),
                Arguments.of(ACTIVE, FAILED));
    }

    static Stream<Arguments> disallowedTransitions() {
        return Stream.of(
                Arguments.of(DISCOVERED, ACTIVE),
                Arguments.of(DISCOVERED, REGISTERED),
                Arguments.of(DISCOVERED, DEACTIVATED),
                Arguments.of(ACTIVE, DISCOVERED),
                Arguments.of(ACTIVE, VALIDATED),
                Arguments.of(ACTIVE, DEACTIVATED),
                Arguments.of(STOPPED, ACTIVE),
                Arguments.of(STOPPED, DISCOVERED),
                Arguments.of(VALIDATED, DISCOVERED),
                Arguments.of(REGISTERED, VALIDATED),
                Arguments.of(INITIALIZED, REGISTERED),
                Arguments.of(DEACTIVATED, DISCOVERED),
                Arguments.of(DEACTIVATED, VALIDATED),
                Arguments.of(DEACTIVATED, REGISTERED),
                Arguments.of(DEACTIVATED, INITIALIZED),
                Arguments.of(DEACTIVATED, ACTIVE),
                Arguments.of(DEACTIVATED, STOPPED),
                Arguments.of(DEACTIVATED, FAILED),
                Arguments.of(DEACTIVATED, DEACTIVATED),
                Arguments.of(FAILED, DISCOVERED),
                Arguments.of(FAILED, VALIDATED),
                Arguments.of(FAILED, REGISTERED),
                Arguments.of(FAILED, INITIALIZED),
                Arguments.of(FAILED, ACTIVE),
                Arguments.of(FAILED, STOPPED),
                Arguments.of(FAILED, DEACTIVATED),
                Arguments.of(FAILED, FAILED),
                Arguments.of(DISCOVERED, DISCOVERED),
                Arguments.of(VALIDATED, VALIDATED),
                Arguments.of(REGISTERED, REGISTERED),
                Arguments.of(INITIALIZED, INITIALIZED),
                Arguments.of(ACTIVE, ACTIVE),
                Arguments.of(STOPPED, STOPPED));
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void allowedTransitionsReturnTrue(CapabilityLifecycleState from, CapabilityLifecycleState to) {
        assertTrue(CapabilityStateTransition.isAllowed(from, to));
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    void disallowedTransitionsReturnFalse(CapabilityLifecycleState from, CapabilityLifecycleState to) {
        assertFalse(CapabilityStateTransition.isAllowed(from, to));
    }

    @Test
    void noTransitionAllowedFromDeactivatedOrFailed() {
        for (CapabilityLifecycleState target : EnumSet.allOf(CapabilityLifecycleState.class)) {
            assertFalse(CapabilityStateTransition.isAllowed(DEACTIVATED, target));
            assertFalse(CapabilityStateTransition.isAllowed(FAILED, target));
        }
    }

    @Test
    void nullFromRejected() {
        assertThrows(NullPointerException.class, () -> CapabilityStateTransition.isAllowed(null, ACTIVE));
    }

    @Test
    void nullToRejected() {
        assertThrows(NullPointerException.class, () -> CapabilityStateTransition.isAllowed(ACTIVE, null));
    }
}
