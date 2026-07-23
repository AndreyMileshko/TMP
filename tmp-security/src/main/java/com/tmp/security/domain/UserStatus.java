package com.tmp.security.domain;

/**
 * Lifecycle status of a Security user. Physical deletion is forbidden; {@link #DELETED}
 * is logical deletion only.
 */
public enum UserStatus {
    ACTIVE,
    DELETED
}
