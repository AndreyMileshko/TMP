package com.tmp.security.domain;

/**
 * Security audit operations. Descriptions must never contain passwords or hashes.
 */
public enum AuditOperation {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_DELETED,
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    ROLE_PERMISSIONS_CHANGED,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    PERMISSION_OVERRIDE_REMOVED,
    PERMISSION_DEFINITION_REGISTERED
}
