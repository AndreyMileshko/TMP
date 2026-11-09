package com.tmp.reference.application;

import com.tmp.reference.api.ColorMatchingSettingApi;
import com.tmp.reference.domain.ColorMatchingSettingRepository;
import com.tmp.reference.domain.ColorMatchingSettingRepository.ColorMatchingSetting;
import com.tmp.reference.domain.ColorMatchingSettingRepository.OptimisticLockConflictException;
import com.tmp.reference.security.ReferenceDataPermissions;
import com.tmp.reference.security.ReferenceImportAccessPolicy;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.SessionSummary;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for the system-wide automatic color-matching switch (TMP-SPEC-015 sections 7
 * / 15 / 17 / COL-022A).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class DefaultColorMatchingSettingService implements ColorMatchingSettingApi {

    private final AuthorizationService authorization;
    private final AuthenticationService authentication;
    private final ReferenceImportAccessPolicy importAccessPolicy;
    private final ColorMatchingSettingRepository settings;
    private final Clock clock;

    public DefaultColorMatchingSettingService(
            AuthorizationService authorization,
            AuthenticationService authentication,
            ReferenceImportAccessPolicy importAccessPolicy,
            ColorMatchingSettingRepository settings,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.importAccessPolicy = Objects.requireNonNull(importAccessPolicy, "importAccessPolicy");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ColorMatchingSettingDto getAutomaticMatchingSetting() {
        importAccessPolicy.requireSettingReadAccess();
        return toDto(settings.get());
    }

    @Override
    public ColorMatchingSettingDto setAutomaticMatchingEnabled(
            boolean enabled, long expectedVersion) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_SETTING_EDIT);
        UUID actor = requireActorUserId();
        ColorMatchingSetting existing = settings.get();
        if (existing.version() != expectedVersion) {
            throw new OptimisticLockConflictException(
                    "Color matching setting version conflict: expected "
                            + expectedVersion
                            + ", actual "
                            + existing.version());
        }
        ColorMatchingSetting updated =
                settings.update(existing.withEnabled(enabled, actor, clock));
        return toDto(updated);
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

    private static ColorMatchingSettingDto toDto(ColorMatchingSetting setting) {
        return new ColorMatchingSettingDto(
                setting.automaticMatchingEnabled(), setting.version());
    }
}