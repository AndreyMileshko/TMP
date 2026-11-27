package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.reference.security.ReferenceImportAccessPolicy;
import com.tmp.security.api.PermissionId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * COL-022B: composition proof that Reference Data's duplicated OM import permission strings match
 * the permissions required by {@code DefaultOrderImportService} preview and confirm paths.
 *
 * <p>Keeps {@code tmp-reference-data} free of a compile-time Order Management dependency while
 * still detecting drift of the five-permission import set.
 */
class ReferenceImportPermissionSetConsistencyTest {

    @Test
    void referenceImportPermissionSetMatchesOrderImportConfirmRequirements() {
        List<String> expectedFromOrderManagement =
                OrderManagementPermissions.ORDER_IMPORT_REQUIRED.stream()
                        .map(PermissionId::value)
                        .toList();

        assertEquals(
                expectedFromOrderManagement,
                ReferenceImportAccessPolicy.ORDER_IMPORT_PERMISSION_IDS);

        assertEquals(
                expectedFromOrderManagement.stream().map(PermissionId::of).toList(),
                ReferenceImportAccessPolicy.ORDER_IMPORT_PERMISSION_IDS.stream()
                        .map(PermissionId::of)
                        .toList());
    }
}