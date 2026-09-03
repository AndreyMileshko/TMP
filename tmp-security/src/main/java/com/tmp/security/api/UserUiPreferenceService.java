package com.tmp.security.api;

import java.util.Optional;

/**
 * Minimal generic per-user UI preference store. Values are opaque strings keyed by immutable
 * {@link UserId}, namespace and preference key. Not machine-local.
 */
public interface UserUiPreferenceService {

    Optional<String> load(UserId userId, String namespace, String preferenceKey);

    void save(UserId userId, String namespace, String preferenceKey, int preferenceVersion, String value);
}
