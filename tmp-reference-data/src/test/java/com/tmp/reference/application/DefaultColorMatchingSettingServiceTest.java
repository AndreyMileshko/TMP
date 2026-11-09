package com.tmp.reference.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tmp.reference.api.ColorMatchingSettingApi.ColorMatchingSettingDto;
import com.tmp.reference.domain.ColorMatchingSettingRepository;
import com.tmp.reference.domain.ColorMatchingSettingRepository.ColorMatchingSetting;
import com.tmp.reference.domain.ColorMatchingSettingRepository.OptimisticLockConflictException;
import com.tmp.reference.security.ReferenceDataPermissions;
import com.tmp.reference.security.ReferenceImportAccessPolicy;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultColorMatchingSettingServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T06:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock private AuthorizationService authorization;
    @Mock private AuthenticationService authentication;
    @Mock private ReferenceImportAccessPolicy importAccessPolicy;
    @Mock private ColorMatchingSettingRepository settings;

    private DefaultColorMatchingSettingService service;

    @BeforeEach
    void setUp() {
        service =
                new DefaultColorMatchingSettingService(
                        authorization, authentication, importAccessPolicy, settings, CLOCK);
    }

    @Test
    void getRequiresSettingReadAccessBeforeRepository() {
        doThrow(new AccessDeniedException("denied"))
                .when(importAccessPolicy)
                .requireSettingReadAccess();

        assertThrows(AccessDeniedException.class, service::getAutomaticMatchingSetting);
        verifyNoInteractions(settings);
    }

    @Test
    void getReturnsEnabledAndVersion() {
        when(settings.get()).thenReturn(setting(false, 0L));

        ColorMatchingSettingDto dto = service.getAutomaticMatchingSetting();

        verify(importAccessPolicy).requireSettingReadAccess();
        assertFalse(dto.enabled());
        assertEquals(0L, dto.version());
    }

    @Test
    void setRequiresColorSettingEditBeforeMutation() {
        doThrow(new AccessDeniedException("denied"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_SETTING_EDIT);

        assertThrows(
                AccessDeniedException.class, () -> service.setAutomaticMatchingEnabled(true, 0L));
        verify(settings, never()).get();
        verify(settings, never()).update(any());
    }

    @Test
    void setRejectsVersionMismatchWithoutWriting() {
        openSession();
        when(settings.get()).thenReturn(setting(false, 2L));

        assertThrows(
                OptimisticLockConflictException.class,
                () -> service.setAutomaticMatchingEnabled(true, 0L));
        verify(settings, never()).update(any());
    }

    @Test
    void setUpdatesEnabledAndReturnsNewVersion() {
        openSession();
        when(settings.get()).thenReturn(setting(false, 0L));
        when(settings.update(any(ColorMatchingSetting.class)))
                .thenAnswer(
                        invocation -> {
                            ColorMatchingSetting next = invocation.getArgument(0);
                            return ColorMatchingSetting.rehydrate(
                                    next.automaticMatchingEnabled(),
                                    next.version() + 1,
                                    next.createdAt(),
                                    next.updatedAt(),
                                    next.createdBy(),
                                    next.updatedBy());
                        });

        ColorMatchingSettingDto dto = service.setAutomaticMatchingEnabled(true, 0L);

        ArgumentCaptor<ColorMatchingSetting> captor =
                ArgumentCaptor.forClass(ColorMatchingSetting.class);
        verify(authorization).requirePermission(ReferenceDataPermissions.COLOR_SETTING_EDIT);
        verify(settings).update(captor.capture());
        assertTrue(captor.getValue().automaticMatchingEnabled());
        assertEquals(ACTOR, captor.getValue().updatedBy());
        assertTrue(dto.enabled());
        assertEquals(1L, dto.version());
    }

    private static ColorMatchingSetting setting(boolean enabled, long version) {
        Instant now = CLOCK.instant();
        return ColorMatchingSetting.rehydrate(enabled, version, now, now, null, null);
    }

    private void openSession() {
        when(authentication.currentSession())
                .thenReturn(
                        Optional.of(
                                new SessionSummary(
                                        SessionId.of(UUID.randomUUID()),
                                        UserId.of(ACTOR),
                                        Login.of("setting-editor"),
                                        CLOCK.instant())));
    }
}