package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.reference.security.ReferenceImportAccessPolicy;
import com.tmp.security.api.PermissionId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * COL-022B: composition proof that Reference Data's duplicated OM import permission strings match
 * the permissions required by {@code DefaultOrderImportService#confirm}.
 *
 * <p>Keeps {@code tmp-reference-data} free of a compile-time Order Management dependency while
 * still detecting drift of the five-permission import set.
 */
class ReferenceImportPermissionSetConsistencyTest {

    @Test
    void referenceImportPermissionSetMatchesOrderImportConfirmRequirements() {
        List<String> expectedFromOrderManagement =
                List.of(
                        OrderManagementPermissions.ORDER_CREATE.value(),
                        OrderManagementPermissions.ITEM_CREATE.value(),
                        OrderManagementPermissions.REVISION_EDIT.value(),
                        OrderManagementPermissions.ITEM_APPROVE.value(),
                        OrderManagementPermissions.ORDER_APPROVE.value());

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