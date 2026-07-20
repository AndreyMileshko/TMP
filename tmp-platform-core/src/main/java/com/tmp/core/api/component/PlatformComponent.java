package com.tmp.core.api.component;

import com.tmp.core.api.PlatformCore;

/**
 * Contract for components registered in Platform Core.
 * Implementations must not contain business logic.
 */
public interface PlatformComponent {

    PlatformComponentMetadata metadata();

    void initialize(PlatformCore platformCore);

    void start();

    void stop();
}
