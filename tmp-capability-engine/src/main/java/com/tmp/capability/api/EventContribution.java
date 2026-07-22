package com.tmp.capability.api;

import java.util.Objects;

/**
 * Immutable, pure-data descriptor of a domain event type that a Capability declares it
 * publishes through Platform Core's public {@code EventBus}. The Capability Engine does
 * not interpret event payloads: this descriptor carries only the event type identifier
 * and a human-readable description, never the event's data shape or contents.
 */
public final class EventContribution {

    private final String eventTypeId;
    private final String description;

    private EventContribution(String eventTypeId, String description) {
        this.eventTypeId = eventTypeId;
        this.description = description;
    }

    public static EventContribution of(String eventTypeId, String description) {
        requireNonBlank(eventTypeId, "eventTypeId");
        Objects.requireNonNull(description, "description");
        return new EventContribution(eventTypeId, description);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public String eventTypeId() {
        return eventTypeId;
    }

    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventContribution that)) {
            return false;
        }
        return eventTypeId.equals(that.eventTypeId);
    }

    @Override
    public int hashCode() {
        return eventTypeId.hashCode();
    }

    @Override
    public String toString() {
        return eventTypeId;
    }
}
