package com.tmp.capability.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.DependencyDescriptor;
import com.tmp.capability.api.DependencyValidationException;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DependencyGraphValidatorTest {

    private static Capability capabilityFor(String id, String version, DependencyDescriptor... dependencies) {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of(version))
                .description("Test capability " + id)
                .dependencies(List.of(dependencies))
                .build();
        return wrap(descriptor);
    }

    private static Capability wrap(CapabilityDescriptor descriptor) {
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

    /**
     * Builds a {@link CapabilityDescriptor} whose dependency list contains a duplicate
     * target id, bypassing {@link CapabilityDescriptor.Builder#build()}'s own defensive
     * duplicate check (STAGE3-006). This is only reachable this way because a descriptor
     * built through the normal public API can never carry a duplicate; the reflection here
     * exists solely to exercise {@link DependencyGraphValidator}'s independent, defensive
     * re-check of the same invariant against the actual discovered graph.
     */
    private static Capability capabilityWithUnvalidatedDuplicateDependency(String id, DependencyDescriptor duplicated)
            throws ReflectiveOperationException {
        CapabilityDescriptor.Builder builder = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Test capability " + id)
                .dependencies(List.of(duplicated, duplicated));
        Constructor<CapabilityDescriptor> constructor =
                CapabilityDescriptor.class.getDeclaredConstructor(CapabilityDescriptor.Builder.class);
        constructor.setAccessible(true);
        CapabilityDescriptor descriptor = constructor.newInstance(builder);
        return wrap(descriptor);
    }

    private static List<String> ids(List<Capability> capabilities) {
        return capabilities.stream().map(c -> c.descriptor().id().value()).collect(Collectors.toList());
    }

    @Test
    void validLinearGraphProducesDependenciesBeforeDependents() {
        Capability c = capabilityFor("c.capability", "1.0.0");
        Capability b = capabilityFor(
                "b.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("c.capability"), CapabilityVersion.of("1.0.0")));
        Capability a = capabilityFor(
                "a.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("b.capability"), CapabilityVersion.of("1.0.0")));

        List<Capability> order = DependencyGraphValidator.validate(List.of(a, b, c));

        assertEquals(List.of("c.capability", "b.capability", "a.capability"), ids(order));
    }

    @Test
    void validDiamondGraphProducesDependenciesBeforeDependents() {
        Capability a = capabilityFor("a.capability", "1.0.0");
        Capability b = capabilityFor(
                "b.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("a.capability"), CapabilityVersion.of("1.0.0")));
        Capability c = capabilityFor(
                "c.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("a.capability"), CapabilityVersion.of("1.0.0")));
        Capability d = capabilityFor(
                "d.capability",
                "1.0.0",
                DependencyDescriptor.of(CapabilityId.of("b.capability"), CapabilityVersion.of("1.0.0")),
                DependencyDescriptor.of(CapabilityId.of("c.capability"), CapabilityVersion.of("1.0.0")));

        List<Capability> order = DependencyGraphValidator.validate(List.of(d, c, b, a));

        assertEquals(List.of("a.capability", "b.capability", "c.capability", "d.capability"), ids(order));
    }

    @Test
    void missingDependencyDetected() {
        Capability a = capabilityFor(
                "a.capability",
                "1.0.0",
                DependencyDescriptor.of(CapabilityId.of("missing.capability"), CapabilityVersion.of("1.0.0")));

        DependencyValidationException failure =
                assertThrows(DependencyValidationException.class, () -> DependencyGraphValidator.validate(List.of(a)));
        assertEquals(DependencyValidationException.DependencyValidationReason.MISSING_DEPENDENCY, failure.reason());
    }

    @Test
    void selfDependencyDetected() {
        Capability a = capabilityFor(
                "a.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("a.capability"), CapabilityVersion.of("1.0.0")));

        DependencyValidationException failure =
                assertThrows(DependencyValidationException.class, () -> DependencyGraphValidator.validate(List.of(a)));
        assertEquals(DependencyValidationException.DependencyValidationReason.SELF_DEPENDENCY, failure.reason());
    }

    @Test
    void duplicateDependencyDetected() throws ReflectiveOperationException {
        Capability b = capabilityFor("b.capability", "1.0.0");
        Capability a = capabilityWithUnvalidatedDuplicateDependency(
                "a.capability", DependencyDescriptor.of(CapabilityId.of("b.capability"), CapabilityVersion.of("1.0.0")));

        DependencyValidationException failure = assertThrows(
                DependencyValidationException.class, () -> DependencyGraphValidator.validate(List.of(a, b)));
        assertEquals(DependencyValidationException.DependencyValidationReason.DUPLICATE_DEPENDENCY, failure.reason());
    }

    @Test
    void incompatibleVersionDetected() {
        Capability b = capabilityFor("b.capability", "1.0.0");
        Capability a = capabilityFor(
                "a.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("b.capability"), CapabilityVersion.of("2.0.0")));

        DependencyValidationException failure = assertThrows(
                DependencyValidationException.class, () -> DependencyGraphValidator.validate(List.of(a, b)));
        assertEquals(DependencyValidationException.DependencyValidationReason.INCOMPATIBLE_VERSION, failure.reason());
    }

    @Test
    void directCycleDetected() {
        Capability a = capabilityFor(
                "a.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("b.capability"), CapabilityVersion.of("1.0.0")));
        Capability b = capabilityFor(
                "b.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("a.capability"), CapabilityVersion.of("1.0.0")));

        DependencyValidationException failure = assertThrows(
                DependencyValidationException.class, () -> DependencyGraphValidator.validate(List.of(a, b)));
        assertEquals(DependencyValidationException.DependencyValidationReason.CYCLIC_DEPENDENCY, failure.reason());
    }

    @Test
    void indirectCycleDetected() {
        Capability a = capabilityFor(
                "a.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("b.capability"), CapabilityVersion.of("1.0.0")));
        Capability b = capabilityFor(
                "b.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("c.capability"), CapabilityVersion.of("1.0.0")));
        Capability c = capabilityFor(
                "c.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("a.capability"), CapabilityVersion.of("1.0.0")));

        DependencyValidationException failure = assertThrows(
                DependencyValidationException.class, () -> DependencyGraphValidator.validate(List.of(a, b, c)));
        assertEquals(DependencyValidationException.DependencyValidationReason.CYCLIC_DEPENDENCY, failure.reason());
    }

    @Test
    void topologicalOrderIsDeterministicAcrossRepeatedRunsAndInputOrder() {
        Capability a = capabilityFor("a.capability", "1.0.0");
        Capability b = capabilityFor(
                "b.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("a.capability"), CapabilityVersion.of("1.0.0")));
        Capability c = capabilityFor(
                "c.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("a.capability"), CapabilityVersion.of("1.0.0")));

        List<Capability> firstInputOrder = List.of(c, b, a);
        List<Capability> secondInputOrder = List.of(a, c, b);

        List<Capability> firstResult = DependencyGraphValidator.validate(firstInputOrder);
        List<Capability> secondResult = DependencyGraphValidator.validate(secondInputOrder);
        List<Capability> repeatResult = DependencyGraphValidator.validate(firstInputOrder);

        assertEquals(List.of("a.capability", "b.capability", "c.capability"), ids(firstResult));
        assertEquals(ids(firstResult), ids(secondResult));
        assertEquals(ids(firstResult), ids(repeatResult));
    }

    @Test
    void reverseProducesExactReverseOfForwardOrder() {
        Capability c = capabilityFor("c.capability", "1.0.0");
        Capability b = capabilityFor(
                "b.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("c.capability"), CapabilityVersion.of("1.0.0")));
        Capability a = capabilityFor(
                "a.capability", "1.0.0", DependencyDescriptor.of(CapabilityId.of("b.capability"), CapabilityVersion.of("1.0.0")));

        List<Capability> forward = DependencyGraphValidator.validate(List.of(a, b, c));
        List<Capability> reversed = DependencyGraphValidator.reverse(forward);

        assertEquals(List.of("a.capability", "b.capability", "c.capability"), ids(reversed));
    }
}
