package com.tmp.warehouse.application;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.WarehouseUserResponsibilityRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves the authenticated user via Security public API and checks Warehouse-owned
 * responsibility. No admin bypass.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected Security and responsibility collaborators.")
public final class DefaultWarehouseResponsibilityGuard implements WarehouseResponsibilityGuard {

    private final AuthenticationService authentication;
    private final WarehouseUserResponsibilityRepository responsibilities;

    public DefaultWarehouseResponsibilityGuard(
            AuthenticationService authentication,
            WarehouseUserResponsibilityRepository responsibilities) {
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.responsibilities = Objects.requireNonNull(responsibilities, "responsibilities");
    }

    @Override
    public void requireResponsible(WarehouseId warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        UUID userId = currentUserId().value();
        if (!responsibilities.isResponsible(userId, warehouseId)) {
            throw new AccessDeniedException(
                    "Access denied: no warehouse responsibility for warehouse "
                            + warehouseId.value());
        }
    }

    UserId currentUserId() {
        SessionSummary session =
                authentication
                        .currentSession()
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Access denied: authentication required"));
        return session.userId();
    }
}
