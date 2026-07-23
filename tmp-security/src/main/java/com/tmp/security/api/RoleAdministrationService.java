package com.tmp.security.api;

import java.util.List;

/**
 * Public role and permission-assignment administration API.
 */
public interface RoleAdministrationService {

    RoleSummary createRole(String name, String description);

    RoleSummary updateRole(RoleId roleId, String name, String description);

    RoleSummary grantPermissionToRole(RoleId roleId, PermissionId permissionId);

    RoleSummary revokePermissionFromRole(RoleId roleId, PermissionId permissionId);

    void deleteRole(RoleId roleId);

    List<RoleSummary> listRoles();

    void assignRole(UserId userId, RoleId roleId);

    void revokeRole(UserId userId, RoleId roleId);

    void grantIndividualPermission(UserId userId, PermissionId permissionId);

    void revokeIndividualPermission(UserId userId, PermissionId permissionId);

    void removeOverride(UserId userId, PermissionId permissionId);

    List<PermissionSummary> listAllPermissionDefinitions();
}
