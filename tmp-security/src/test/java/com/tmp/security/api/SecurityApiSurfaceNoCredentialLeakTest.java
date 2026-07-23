package com.tmp.security.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecurityApiSurfaceNoCredentialLeakTest {

    private static final Set<String> DENYLIST = Set.of(
            "PasswordHash",
            "char[]",
            "char",
            "PasswordHasher");

    @Test
    void publicApiTypesDoNotExposeCredentialCarriers() throws Exception {
        Class<?>[] types = {
                UserId.class,
                RoleId.class,
                PermissionId.class,
                AuditEventId.class,
                SessionId.class,
                Login.class,
                DisplayName.class,
                UserSummary.class,
                RoleSummary.class,
                PermissionSummary.class,
                AuditEventSummary.class,
                SessionSummary.class,
                AuthenticationService.class,
                AuthorizationService.class,
                UserAdministrationService.class,
                RoleAdministrationService.class,
                AuditQueryService.class,
                AccessDeniedException.class,
                AuthenticationFailedException.class,
                InvalidCurrentPasswordException.class
        };
        for (Class<?> type : types) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String fieldType = field.getType().getSimpleName();
                assertFalse(
                        DENYLIST.contains(fieldType) || fieldType.equals("PasswordHash"),
                        type.getName() + "." + field.getName() + " type=" + fieldType);
                String name = field.getName().toLowerCase();
                assertFalse(
                        name.contains("password") || name.contains("hash"),
                        type.getName() + " field name hints credential: " + field.getName());
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                if (returnType == char[].class) {
                    // char[] is allowed only as input parameters for password operations
                    if (method.getParameterCount() == 0) {
                        fail(type.getName() + "." + method.getName() + " returns char[]");
                    }
                }
                if (returnType.getSimpleName().equals("PasswordHash")) {
                    fail(type.getName() + "." + method.getName() + " returns PasswordHash");
                }
            }
        }
    }
}
