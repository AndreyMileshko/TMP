package com.tmp.security.api;

/**
 * Public catalogue of Security Administration permission identifiers.
 */
public final class SecurityPermissions {

    public static final PermissionId USERS_VIEW = PermissionId.of("security.users.view");
    public static final PermissionId USERS_CREATE = PermissionId.of("security.users.create");
    public static final PermissionId USERS_UPDATE = PermissionId.of("security.users.update");
    public static final PermissionId USERS_DELETE = PermissionId.of("security.users.delete");
    public static final PermissionId USERS_RESET_PASSWORD = PermissionId.of("security.users.reset-password");
    public static final PermissionId ROLES_VIEW = PermissionId.of("security.roles.view");
    public static final PermissionId ROLES_CREATE = PermissionId.of("security.roles.create");
    public static final PermissionId ROLES_UPDATE = PermissionId.of("security.roles.update");
    public static final PermissionId ROLES_DELETE = PermissionId.of("security.roles.delete");
    public static final PermissionId ROLES_ASSIGN = PermissionId.of("security.roles.assign");
    public static final PermissionId PERMISSIONS_ASSIGN = PermissionId.of("security.permissions.assign");
    public static final PermissionId AUDIT_VIEW = PermissionId.of("security.audit.view");

    private SecurityPermissions() {
    }
}
