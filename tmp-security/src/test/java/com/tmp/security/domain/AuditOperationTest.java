package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class AuditOperationTest {

    @Test
    void allRequiredConstantsPresent() {
        assertEquals(18, AuditOperation.values().length);
        assertEquals(
                EnumSet.of(
                        AuditOperation.LOGIN_SUCCESS,
                        AuditOperation.LOGIN_FAILURE,
                        AuditOperation.LOGOUT,
                        AuditOperation.USER_CREATED,
                        AuditOperation.USER_UPDATED,
                        AuditOperation.USER_DELETED,
                        AuditOperation.PASSWORD_CHANGED,
                        AuditOperation.PASSWORD_RESET,
                        AuditOperation.ROLE_CREATED,
                        AuditOperation.ROLE_UPDATED,
                        AuditOperation.ROLE_DELETED,
                        AuditOperation.ROLE_ASSIGNED,
                        AuditOperation.ROLE_REVOKED,
                        AuditOperation.ROLE_PERMISSIONS_CHANGED,
                        AuditOperation.PERMISSION_GRANTED,
                        AuditOperation.PERMISSION_REVOKED,
                        AuditOperation.PERMISSION_OVERRIDE_REMOVED,
                        AuditOperation.PERMISSION_DEFINITION_REGISTERED),
                EnumSet.allOf(AuditOperation.class));
    }
}
