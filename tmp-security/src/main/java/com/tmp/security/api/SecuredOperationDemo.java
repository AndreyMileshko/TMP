package com.tmp.security.api;

import java.util.Objects;

/**
 * Public entry point proving that UI hiding never replaces authorization:
 * a direct call still enforces {@link AuthorizationService#requirePermission}.
 *
 * <p>Delegates to the same authorization check used by the internal secured-operation fixture.
 */
public final class SecuredOperationDemo {

    private final AuthorizationService authorization;

    public SecuredOperationDemo(AuthorizationService authorization) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    /**
     * Performs a secured no-op operation after checking {@code required}.
     *
     * @return {@code "OK"} when permitted
     * @throws AccessDeniedException when the current session lacks the permission
     */
    public String performSecuredOperation(PermissionId required) {
        authorization.requirePermission(required);
        return "OK";
    }
}
