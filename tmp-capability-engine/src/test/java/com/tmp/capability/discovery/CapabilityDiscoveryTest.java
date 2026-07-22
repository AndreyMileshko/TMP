package com.tmp.capability.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityDiscoveryTest {

    private static Capability capabilityFor(String id) {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Test capability " + id)
                .build();
        return new Capability() {
            @Override
            public CapabilityDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void onInitialize() {
                // test double: no-op
            }

            @Override
            public void onActivate() {
                // test double: no-op
            }

            @Override
            public void onDeactivate() {
                // test double: no-op
            }

            @Override
            public void onStop() {
                // test double: no-op
            }
        };
    }

    @Test
    void zeroCapabilitiesDiscoveredYieldsEmptyList() {
        CapabilityDiscovery discovery = new CapabilityDiscovery(List.of());

        assertTrue(discovery.discover().isEmpty());
    }

    @Test
    void oneCapabilityDiscoveredYieldsSingletonList() {
        Capability capability = capabilityFor("sample.capability");
        CapabilityDiscovery discovery = new CapabilityDiscovery(List.of(capability));

        assertEquals(List.of(capability), discovery.discover());
    }

    @Test
    void multipleCapabilitiesDiscoveredYieldDeterministicSortedListRegardlessOfInputOrder() {
        Capability alpha = capabilityFor("alpha.capability");
        Capability bravo = capabilityFor("bravo.capability");
        Capability charlie = capabilityFor("charlie.capability");

        CapabilityDiscovery firstOrder = new CapabilityDiscovery(List.of(charlie, alpha, bravo));
        CapabilityDiscovery secondOrder = new CapabilityDiscovery(List.of(bravo, charlie, alpha));

        List<Capability> expected = List.of(alpha, bravo, charlie);
        assertEquals(expected, firstOrder.discover());
        assertEquals(expected, secondOrder.discover());
    }

    @Test
    void duplicateDiscoveredIdThrowsIllegalStateExceptionNamingBothCapabilities() {
        Capability first = capabilityFor("dup.capability");
        Capability second = capabilityFor("dup.capability");

        CapabilityDiscovery discovery = new CapabilityDiscovery(List.of(first, second));

        IllegalStateException failure = assertThrows(IllegalStateException.class, discovery::discover);
        assertTrue(failure.getMessage().contains("dup.capability"));
        assertTrue(failure.getMessage().contains(first.getClass().getName()));
        assertTrue(failure.getMessage().contains(second.getClass().getName()));
    }

    @Test
    void repeatedCallsToDiscoverReturnEqualDeterministicResult() {
        Capability alpha = capabilityFor("alpha.capability");
        Capability bravo = capabilityFor("bravo.capability");
        CapabilityDiscovery discovery = new CapabilityDiscovery(List.of(bravo, alpha));

        List<Capability> firstCall = discovery.discover();
        List<Capability> secondCall = discovery.discover();

        assertEquals(firstCall, secondCall);
        assertEquals(List.of(alpha, bravo), firstCall);
    }
}
