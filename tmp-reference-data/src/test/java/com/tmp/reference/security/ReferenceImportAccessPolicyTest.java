package com.tmp.reference.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceImportAccessPolicyTest {

    @Mock private AuthorizationService authorization;

    private ReferenceImportAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ReferenceImportAccessPolicy(authorization);
    }

    @Test
    void requireSettingReadAccessAllowsColorViewWithoutImportPermissions() {
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(true);

        assertDoesNotThrow(policy::requireSettingReadAccess);
    }

    @Test
    void requireSettingReadAccessAllowsFullImportPermissionSetWithoutColorView() {
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(false);
        grantFullImportSet(true);

        assertDoesNotThrow(policy::requireSettingReadAccess);
        assertTrue(policy.hasFullOrderImportPermissionSet());
    }

    @Test
    void requireSettingReadAccessRejectsOnlyOrderCreate() {
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(false);
        when(authorization.hasPermission(PermissionId.of("order.order.create"))).thenReturn(true);
        when(authorization.hasPermission(PermissionId.of("order.item.create"))).thenReturn(false);

        assertThrows(AccessDeniedException.class, policy::requireSettingReadAccess);
        assertFalse(policy.hasFullOrderImportPermissionSet());
    }

    @Test
    void requireSettingReadAccessRejectsIncompleteImportSubset() {
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(false);
        when(authorization.hasPermission(PermissionId.of("order.order.create"))).thenReturn(true);
        when(authorization.hasPermission(PermissionId.of("order.item.create"))).thenReturn(true);
        when(authorization.hasPermission(PermissionId.of("order.revision.edit"))).thenReturn(true);
        when(authorization.hasPermission(PermissionId.of("order.item.approve"))).thenReturn(true);
        when(authorization.hasPermission(PermissionId.of("order.order.approve"))).thenReturn(false);

        assertThrows(AccessDeniedException.class, policy::requireSettingReadAccess);
    }

    @Test
    void orderImportPermissionIdsMatchSpecFifteenPointTwo() {
        assertTrue(
                ReferenceImportAccessPolicy.ORDER_IMPORT_PERMISSION_IDS.containsAll(
                        java.util.List.of(
                                "order.order.create",
                                "order.item.create",
                                "order.revision.edit",
                                "order.item.approve",
                                "order.order.approve")));
        assertEqualsFive();
    }

    private void assertEqualsFive() {
        org.junit.jupiter.api.Assertions.assertEquals(
                5, ReferenceImportAccessPolicy.ORDER_IMPORT_PERMISSION_IDS.size());
    }

    private void grantFullImportSet(boolean granted) {
        for (String id : ReferenceImportAccessPolicy.ORDER_IMPORT_PERMISSION_IDS) {
            when(authorization.hasPermission(PermissionId.of(id))).thenReturn(granted);
        }
    }
}