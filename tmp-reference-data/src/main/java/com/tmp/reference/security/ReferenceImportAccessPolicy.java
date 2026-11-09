package com.tmp.reference.security;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import java.util.List;
import java.util.Objects;

/**
 * Access rules for Reference Data operations used during Order Management import (TMP-SPEC-015
 * section 15.2).
 *
 * <p>Import permission identifiers are duplicated as plain strings so {@code tmp-reference-data}
 * has no Maven dependency on Order Management. COL-022B adds a bootstrap composition test that the
 * set matches {@code DefaultOrderImportService}.
 */
public final class ReferenceImportAccessPolicy {

    /**
     * Full Order Management import permission set required by {@code DefaultOrderImportService}
     * confirm path, in declaration order.
     */
    public static final List<String> ORDER_IMPORT_PERMISSION_IDS =
            List.of(
                    "order.order.create",
                    "order.item.create",
                    "order.revision.edit",
                    "order.item.approve",
                    "order.order.approve");

    private static final List<PermissionId> ORDER_IMPORT_PERMISSIONS =
            ORDER_IMPORT_PERMISSION_IDS.stream().map(PermissionId::of).toList();

    private final AuthorizationService authorization;

    public ReferenceImportAccessPolicy(AuthorizationService authorization) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    /**
     * Allows reading the automatic-matching switch (and later import-resolution reads) when the
     * caller has {@code reference.color.view} <strong>or</strong> the full import permission set.
     * A partial import subset (for example only {@code order.order.create}) is denied.
     */
    public void requireSettingReadAccess() {
        if (authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)) {
            return;
        }
        if (hasFullOrderImportPermissionSet()) {
            return;
        }
        throw new AccessDeniedException(
                "Access denied: reference.color.view or full order import permission set required");
    }

    /** True when the current session holds every permission in {@link #ORDER_IMPORT_PERMISSION_IDS}. */
    public boolean hasFullOrderImportPermissionSet() {
        for (PermissionId permission : ORDER_IMPORT_PERMISSIONS) {
            if (!authorization.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }
}