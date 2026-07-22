package com.tmp.capability.lifecycle;

import com.tmp.capability.api.CapabilityLifecycleState;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure, stateless technical contract for the allowed transitions between
 * {@link CapabilityLifecycleState} values. This class performs no orchestration and no
 * I/O; it only answers whether a given {@code from -> to} transition is legal, so that the
 * lifecycle manager (a later task) can enforce it rather than re-derive it.
 *
 * <p>Fixed transition table:
 *
 * <pre>
 * DISCOVERED  -> VALIDATED, FAILED
 * VALIDATED   -> REGISTERED, FAILED
 * REGISTERED  -> INITIALIZED, FAILED
 * INITIALIZED -> ACTIVE, FAILED
 * ACTIVE      -> STOPPED, FAILED
 * STOPPED     -> INITIALIZED (restart), DEACTIVATED
 * DEACTIVATED -> (terminal, no outgoing transition)
 * FAILED      -> (terminal, no outgoing transition)
 * </pre>
 *
 * <p>{@code DEACTIVATED} and {@code FAILED} are terminal for this Stage: re-activation of
 * a deactivated capability is out of scope without a specification-defined re-activation
 * rule, and this is a deliberate, documented scope limitation rather than an oversight.
 * Deactivation from {@code ACTIVE} must pass through {@code STOPPED} first; this table
 * intentionally does not allow {@code ACTIVE -> DEACTIVATED} directly, leaving that
 * ordering to be enforced by the lifecycle manager's orchestration.
 */
public final class CapabilityStateTransition {

    private static final Map<CapabilityLifecycleState, Set<CapabilityLifecycleState>> ALLOWED_TRANSITIONS =
            buildAllowedTransitions();

    private CapabilityStateTransition() {
    }

    public static boolean isAllowed(CapabilityLifecycleState from, CapabilityLifecycleState to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return ALLOWED_TRANSITIONS.get(from).contains(to);
    }

    private static Map<CapabilityLifecycleState, Set<CapabilityLifecycleState>> buildAllowedTransitions() {
        Map<CapabilityLifecycleState, Set<CapabilityLifecycleState>> transitions =
                new EnumMap<>(CapabilityLifecycleState.class);
        transitions.put(
                CapabilityLifecycleState.DISCOVERED,
                EnumSet.of(CapabilityLifecycleState.VALIDATED, CapabilityLifecycleState.FAILED));
        transitions.put(
                CapabilityLifecycleState.VALIDATED,
                EnumSet.of(CapabilityLifecycleState.REGISTERED, CapabilityLifecycleState.FAILED));
        transitions.put(
                CapabilityLifecycleState.REGISTERED,
                EnumSet.of(CapabilityLifecycleState.INITIALIZED, CapabilityLifecycleState.FAILED));
        transitions.put(
                CapabilityLifecycleState.INITIALIZED,
                EnumSet.of(CapabilityLifecycleState.ACTIVE, CapabilityLifecycleState.FAILED));
        transitions.put(
                CapabilityLifecycleState.ACTIVE,
                EnumSet.of(CapabilityLifecycleState.STOPPED, CapabilityLifecycleState.FAILED));
        transitions.put(
                CapabilityLifecycleState.STOPPED,
                EnumSet.of(CapabilityLifecycleState.INITIALIZED, CapabilityLifecycleState.DEACTIVATED));
        transitions.put(CapabilityLifecycleState.DEACTIVATED, EnumSet.noneOf(CapabilityLifecycleState.class));
        transitions.put(CapabilityLifecycleState.FAILED, EnumSet.noneOf(CapabilityLifecycleState.class));
        return Map.copyOf(transitions);
    }
}
