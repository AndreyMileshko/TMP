package com.tmp.capability.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DependencyDescriptorTest {

    @Test
    void validConstructionExposesGivenValues() {
        CapabilityId dependencyId = CapabilityId.of("other.capability");
        CapabilityVersion minimumVersion = CapabilityVersion.of("1.0.0");

        DependencyDescriptor descriptor = DependencyDescriptor.of(dependencyId, minimumVersion);

        assertEquals(dependencyId, descriptor.dependencyId());
        assertEquals(minimumVersion, descriptor.minimumVersion());
    }

    @Test
    void nullDependencyIdRejected() {
        assertThrows(
                NullPointerException.class,
                () -> DependencyDescriptor.of(null, CapabilityVersion.of("1.0.0")));
    }

    @Test
    void nullMinimumVersionRejected() {
        assertThrows(
                NullPointerException.class,
                () -> DependencyDescriptor.of(CapabilityId.of("other.capability"), null));
    }

    @Test
    void equalsAndHashCodeByIdAndVersion() {
        DependencyDescriptor first =
                DependencyDescriptor.of(CapabilityId.of("other.capability"), CapabilityVersion.of("1.0.0"));
        DependencyDescriptor second =
                DependencyDescriptor.of(CapabilityId.of("other.capability"), CapabilityVersion.of("1.0.0"));
        DependencyDescriptor differentId =
                DependencyDescriptor.of(CapabilityId.of("another.capability"), CapabilityVersion.of("1.0.0"));
        DependencyDescriptor differentVersion =
                DependencyDescriptor.of(CapabilityId.of("other.capability"), CapabilityVersion.of("2.0.0"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, differentId);
        assertNotEquals(first, differentVersion);
    }
}
