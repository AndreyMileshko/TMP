package com.tmp.security.application;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.repository.PermissionDefinitionRepository;
import java.util.List;
import java.util.Objects;

public final class DefaultRoleAdministrationService implements RoleAdministrationService {

    private final RoleAdministrationApplicationService roles;
    private final RoleAssignmentApplicationService assignments;
    private final PermissionOverrideApplicationService overrides;
    private final PermissionDefinitionRepository permissionDefinitions;

    public DefaultRoleAdministrationService(
            RoleAdministrationApplicationService roles,
            RoleAssignmentApplicationService assignments,
            PermissionOverrideApplicationService overrides,
            PermissionDefinitionRepository permissionDefinitions) {
        this.roles = Objects.requireNonNull(roles, "roles");
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.permissionDefinitions = Objects.requireNonNull(permissionDefinitions, "permissionDefinitions");
    }

    @Override
    public RoleSummary createRole(String name, String description) {
        return SecurityApiMapper.toSummary(roles.createRole(name, description));
    }

    @Override
    public RoleSummary updateRole(RoleId roleId, String name, String description) {
        return SecurityApiMapper.toSummary(roles.updateRole(roleId, name, description));
    }

    @Override
    public RoleSummary grantPermissionToRole(RoleId roleId, PermissionId permissionId) {
        return SecurityApiMapper.toSummary(roles.grantPermissionToRole(roleId, permissionId));
    }

    @Override
    public RoleSummary revokePermissionFromRole(RoleId roleId, PermissionId permissionId) {
        return SecurityApiMapper.toSummary(roles.revokePermissionFromRole(roleId, permissionId));
    }

    @Override
    public void deleteRole(RoleId roleId) {
        roles.deleteRole(roleId);
    }

    @Override
    public List<RoleSummary> listRoles() {
        return roles.listRoles().stream().map(SecurityApiMapper::toSummary).toList();
    }

    @Override
    public void assignRole(UserId userId, RoleId roleId) {
        assignments.assignRole(userId, roleId);
    }

    @Override
    public void revokeRole(UserId userId, RoleId roleId) {
        assignments.revokeRole(userId, roleId);
    }

    @Override
    public void grantIndividualPermission(UserId userId, PermissionId permissionId) {
        overrides.grantIndividualPermission(userId, permissionId);
    }

    @Override
    public void revokeIndividualPermission(UserId userId, PermissionId permissionId) {
        overrides.revokeIndividualPermission(userId, permissionId);
    }

    @Override
    public void removeOverride(UserId userId, PermissionId permissionId) {
        overrides.removeOverride(userId, permissionId);
    }

    @Override
    public List<PermissionSummary> listAllPermissionDefinitions() {
        return permissionDefinitions.findAll().stream().map(SecurityApiMapper::toSummary).toList();
    }
}
