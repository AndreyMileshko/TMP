package com.tmp.security.application.securedfixture;

import com.tmp.security.api.PermissionId;
import com.tmp.security.application.AuthorizationApplicationService;
import java.util.Objects;

/**
 * Technical fixture: direct invocation always checks authorization, independent of UI hiding.
 */
public final class SecuredOperationFixture {

    private final AuthorizationApplicationService authorization;

    public SecuredOperationFixture(AuthorizationApplicationService authorization) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public String performSecuredOperation(PermissionId required) {
        authorization.requirePermission(required);
        return "OK";
    }
}
