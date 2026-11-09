package com.tmp.reference.application;

import com.tmp.reference.api.ColorCatalogCommandApi;
import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.ColorRepository;
import com.tmp.reference.domain.So7ColorMappingRepository;
import com.tmp.reference.security.ReferenceDataPermissions;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.SessionSummary;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Color catalog application service (TMP-SPEC-015 sections 5.3, 15.1, 17 / COL-020B).
 *
 * <p>Enforces {@code reference.color.view} / {@code reference.color.edit} on the server before any
 * repository access. Archive + SO7 mapping deactivation run in one database transaction.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class DefaultColorCatalogService
        implements ColorCatalogQueryApi, ColorCatalogCommandApi {

    private final AuthorizationService authorization;
    private final AuthenticationService authentication;
    private final ColorRepository colors;
    private final So7ColorMappingRepository mappings;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DefaultColorCatalogService(
            AuthorizationService authorization,
            AuthenticationService authentication,
            ColorRepository colors,
            So7ColorMappingRepository mappings,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.colors = Objects.requireNonNull(colors, "colors");
        this.mappings = Objects.requireNonNull(mappings, "mappings");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public List<ColorDto> listColors(ColorDto.ActiveFilter filter, String search) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_VIEW);
        Objects.requireNonNull(filter, "filter");
        Boolean activeFilter =
                switch (filter) {
                    case ACTIVE -> Boolean.TRUE;
                    case ARCHIVED -> Boolean.FALSE;
                    case ALL -> null;
                };
        return colors.search(activeFilter, search).stream().map(DefaultColorCatalogService::toDto).toList();
    }

    @Override
    public long getArchiveImpact(UUID colorId) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_VIEW);
        ColorId id = ColorId.of(Objects.requireNonNull(colorId, "colorId"));
        requireColorExists(id);
        return mappings.countActiveByColorId(id);
    }

    @Override
    public ColorDto createColor(String code) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_EDIT);
        UUID actor = requireActorUserId();
        Color created = Color.create(code, actor, clock);
        return toDto(colors.insert(created));
    }

    @Override
    public int archiveColor(UUID colorId, long expectedVersion) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_EDIT);
        ColorId id = ColorId.of(Objects.requireNonNull(colorId, "colorId"));
        UUID actor = requireActorUserId();
        Integer deactivated =
                transactionTemplate.execute(
                        status -> {
                            Color existing = requireColorExists(id);
                            requireExpectedVersion(existing, expectedVersion);
                            Color archived = existing.archive(actor, clock);
                            colors.update(archived);
                            return mappings.deactivateByColorId(id, actor, clock.instant());
                        });
        return deactivated == null ? 0 : deactivated;
    }

    @Override
    public ColorDto restoreColor(UUID colorId, long expectedVersion) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_EDIT);
        ColorId id = ColorId.of(Objects.requireNonNull(colorId, "colorId"));
        UUID actor = requireActorUserId();
        Color restored =
                transactionTemplate.execute(
                        status -> {
                            Color existing = requireColorExists(id);
                            requireExpectedVersion(existing, expectedVersion);
                            Color next = existing.restore(actor, clock);
                            return colors.update(next);
                        });
        if (restored == null) {
            throw new IllegalStateException("restoreColor transaction returned no color");
        }
        return toDto(restored);
    }

    private Color requireColorExists(ColorId id) {
        return colors
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Color not found: " + id.value()));
    }

    private static void requireExpectedVersion(Color color, long expectedVersion) {
        if (color.version() != expectedVersion) {
            throw new Color.OptimisticLockConflictException(
                    "Color version conflict: " + color.id().value());
        }
    }

    private UUID requireActorUserId() {
        SessionSummary session =
                authentication
                        .currentSession()
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Access denied: authentication required"));
        return session.userId().value();
    }

    private static ColorDto toDto(Color color) {
        return new ColorDto(color.id().value(), color.code(), color.active(), color.version());
    }
}
