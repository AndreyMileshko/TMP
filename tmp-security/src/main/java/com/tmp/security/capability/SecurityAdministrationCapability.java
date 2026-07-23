package com.tmp.security.capability;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.security.api.SecurityPermissions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

/**
 * Security Administration Capability: declares Security's own permission catalogue and
 * navigation/view/command metadata for admin screens. Lifecycle hooks are no-ops.
 */
public final class SecurityAdministrationCapability implements Capability {

    public static final CapabilityId ID = CapabilityId.of("security-administration");
    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

    public static final String NAV_USERS = "security.nav.users";
    public static final String NAV_ROLES = "security.nav.roles";
    public static final String NAV_AUDIT = "security.nav.audit";
    public static final String VIEW_USERS = "security.view.users";
    public static final String VIEW_ROLES = "security.view.roles";
    public static final String VIEW_AUDIT = "security.view.audit";

    private final CapabilityDescriptor descriptor;

    public SecurityAdministrationCapability() {
        this.descriptor = CapabilityDescriptor.builder()
                .id(ID)
                .name("Security Administration")
                .version(VERSION)
                .description("Administration of users, roles, permissions, and security audit")
                .permissions(List.of(
                        PermissionDescriptor.of(
                                SecurityPermissions.USERS_VIEW.value(),
                                "View users",
                                "View Security users"),
                        PermissionDescriptor.of(
                                SecurityPermissions.USERS_CREATE.value(),
                                "Create users",
                                "Create Security users"),
                        PermissionDescriptor.of(
                                SecurityPermissions.USERS_UPDATE.value(),
                                "Update users",
                                "Update Security users"),
                        PermissionDescriptor.of(
                                SecurityPermissions.USERS_DELETE.value(),
                                "Delete users",
                                "Logically delete Security users"),
                        PermissionDescriptor.of(
                                SecurityPermissions.USERS_RESET_PASSWORD.value(),
                                "Reset passwords",
                                "Administratively reset user passwords"),
                        PermissionDescriptor.of(
                                SecurityPermissions.ROLES_VIEW.value(),
                                "View roles",
                                "View Security roles"),
                        PermissionDescriptor.of(
                                SecurityPermissions.ROLES_CREATE.value(),
                                "Create roles",
                                "Create Security roles"),
                        PermissionDescriptor.of(
                                SecurityPermissions.ROLES_UPDATE.value(),
                                "Update roles",
                                "Update Security roles and their permissions"),
                        PermissionDescriptor.of(
                                SecurityPermissions.ROLES_DELETE.value(),
                                "Delete roles",
                                "Delete Security roles"),
                        PermissionDescriptor.of(
                                SecurityPermissions.ROLES_ASSIGN.value(),
                                "Assign roles",
                                "Assign and revoke roles for users"),
                        PermissionDescriptor.of(
                                SecurityPermissions.PERMISSIONS_ASSIGN.value(),
                                "Assign permissions",
                                "Grant or revoke individual user permissions"),
                        PermissionDescriptor.of(
                                SecurityPermissions.AUDIT_VIEW.value(),
                                "View security audit",
                                "View Security audit events")))
                .commands(List.of(
                        CommandDescriptor.of(
                                NAV_USERS, "Users administration", List.of(SecurityPermissions.USERS_VIEW.value())),
                        CommandDescriptor.of(
                                NAV_ROLES, "Roles administration", List.of(SecurityPermissions.ROLES_VIEW.value())),
                        CommandDescriptor.of(
                                NAV_AUDIT, "Security audit", List.of(SecurityPermissions.AUDIT_VIEW.value()))))
                .views(List.of(
                        ViewDescriptor.of(VIEW_USERS, "Users", NAV_USERS),
                        ViewDescriptor.of(VIEW_ROLES, "Roles", NAV_ROLES),
                        ViewDescriptor.of(VIEW_AUDIT, "Security audit", NAV_AUDIT)))
                .navigationContributions(List.of(
                        NavigationContribution.of(NAV_USERS, "Users", VIEW_USERS, 10),
                        NavigationContribution.of(NAV_ROLES, "Roles", VIEW_ROLES, 20),
                        NavigationContribution.of(NAV_AUDIT, "Security audit", VIEW_AUDIT, 30)))
                .build();
    }

    @Override
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "CapabilityDescriptor is an immutable value type; returning it directly is safe.")
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void onInitialize() {
        // no-op: contributions only
    }

    @Override
    public void onActivate() {
        // no-op: contributions only
    }

    @Override
    public void onDeactivate() {
        // no-op: contributions only
    }

    @Override
    public void onStop() {
        // no-op: contributions only
    }
}
