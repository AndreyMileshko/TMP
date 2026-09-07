package com.tmp.security.api;

import java.util.List;
import java.util.Set;

/**
 * Public role and permission-assignment administration API.
 */
public interface RoleAdministrationService {

    RoleSummary createRole(String name, String description);

    RoleSummary updateRole(RoleId roleId, String name, String description);

    RoleSummary grantPermissionToRole(RoleId roleId, PermissionId permissionId);

    RoleSummary revokePermissionFromRole(RoleId roleId, PermissionId permissionId);

    /**
     * Transactionally replaces role permissions with exactly {@code targetPermissions}.
     * Requires {@link SecurityPermissions#PERMISSIONS_ASSIGN}.
     */
    RoleSummary setRolePermissions(RoleId roleId, Set<PermissionId> targetPermissions);

    void deleteRole(RoleId roleId);

    List<RoleSummary> listRoles();

    void assignRole(UserId userId, RoleId roleId);

    void revokeRole(UserId userId, RoleId roleId);

    /**
     * Roles currently assigned to the user. Requires {@link SecurityPermissions#ROLES_ASSIGN}
     * (assignment read used by the Roles UX).
     */
    Set<RoleId> listRolesForUser(UserId userId);

    void grantIndividualPermission(UserId userId, PermissionId permissionId);

    void revokeIndividualPermission(UserId userId, PermissionId permissionId);

    void removeOverride(UserId userId, PermissionId permissionId);

    List<PermissionSummary> listAllPermissionDefinitions();
}
