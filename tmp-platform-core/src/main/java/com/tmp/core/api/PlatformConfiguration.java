package com.tmp.core.api;

import java.util.Optional;

/**
 * Read-only access to platform configuration properties.
 */
public interface PlatformConfiguration {

    Optional<String> getString(String key);

    String getString(String key, String defaultValue);

    boolean getBoolean(String key, boolean defaultValue);
}
