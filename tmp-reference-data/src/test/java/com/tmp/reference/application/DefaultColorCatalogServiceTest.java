package com.tmp.reference.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tmp.reference.api.ColorDto;
import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.ColorRepository;
import com.tmp.reference.domain.So7ColorMappingRepository;
import com.tmp.reference.security.ReferenceDataPermissions;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class DefaultColorCatalogServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T06:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock private AuthorizationService authorization;
    @Mock private AuthenticationService authentication;
    @Mock private ColorRepository colors;
    @Mock private So7ColorMappingRepository mappings;
    @Mock private PlatformTransactionManager transactionManager;

    private DefaultColorCatalogService service;

    @BeforeEach
    void setUp() {
        lenient()
                .when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        service =
                new DefaultColorCatalogService(
                        authorization,
                        authentication,
                        colors,
                        mappings,
                        transactionManager,
                        CLOCK);
    }

    @Test
    void listColorsRequiresViewPermissionBeforeRepositoryAccess() {
        doThrow(new AccessDeniedException("denied"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_VIEW);

        assertThrows(
                AccessDeniedException.class,
                () -> service.listColors(ColorDto.ActiveFilter.ALL, null));
        verifyNoInteractions(colors);
    }

    @Test
    void getArchiveImpactRequiresViewPermissionBeforeRepositoryAccess() {
        doThrow(new AccessDeniedException("denied"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_VIEW);

        assertThrows(
                AccessDeniedException.class, () -> service.getArchiveImpact(UUID.randomUUID()));
        verifyNoInteractions(colors, mappings);
    }

    @Test
    void createColorRequiresEditPermissionBeforeRepositoryAccess() {
        doThrow(new AccessDeniedException("denied"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_EDIT);

        assertThrows(AccessDeniedException.class, () -> service.createColor("X"));
        verifyNoInteractions(colors);
    }

    @Test
    void archiveAndRestoreRequireEditPermissionBeforeRepositoryAccess() {
        doThrow(new AccessDeniedException("denied"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_EDIT);
        UUID id = UUID.randomUUID();

        assertThrows(AccessDeniedException.class, () -> service.archiveColor(id, 0L));
        assertThrows(AccessDeniedException.class, () -> service.restoreColor(id, 0L));
        verifyNoInteractions(colors, mappings);
    }

    @Test
    void listColorsMapsRepositoryResultsAndActiveFilter() {
        Color active = Color.create("ListActive", ACTOR, CLOCK);
        when(colors.search(eq(Boolean.TRUE), eq("List"))).thenReturn(List.of(active));

        List<ColorDto> result = service.listColors(ColorDto.ActiveFilter.ACTIVE, "List");

        verify(authorization).requirePermission(ReferenceDataPermissions.COLOR_VIEW);
        assertEquals(1, result.size());
        assertEquals(active.id().value(), result.get(0).id());
        assertEquals("ListActive", result.get(0).code());
        assertTrue(result.get(0).active());
    }

    @Test
    void createColorPerservesCodeAndReturnsDto() {
        openSession();
        when(colors.insert(any(Color.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Color.class));

        ColorDto dto = service.createColor("NewCode|30");

        verify(authorization).requirePermission(ReferenceDataPermissions.COLOR_EDIT);
        assertEquals("NewCode|30", dto.code());
        assertTrue(dto.active());
        assertEquals(0L, dto.version());
    }

    @Test
    void getArchiveImpactCountsActiveMappingsForExistingColor() {
        Color color = Color.create("Impact", ACTOR, CLOCK);
        when(colors.findById(color.id())).thenReturn(Optional.of(color));
        when(mappings.countActiveByColorId(color.id())).thenReturn(3L);

        assertEquals(3L, service.getArchiveImpact(color.id().value()));
        verify(authorization).requirePermission(ReferenceDataPermissions.COLOR_VIEW);
    }

    @Test
    void archiveColorUsesExpectedVersionAndDeactivatesMappings() {
        openSession();
        Color color = Color.create("ArchiveMe", ACTOR, CLOCK);
        when(colors.findById(color.id())).thenReturn(Optional.of(color));
        when(colors.update(any(Color.class)))
                .thenAnswer(
                        invocation -> {
                            Color archived = invocation.getArgument(0, Color.class);
                            return Color.rehydrate(
                                    archived.id(),
                                    archived.code(),
                                    archived.codeKey(),
                                    archived.active(),
                                    archived.version() + 1,
                                    archived.createdAt(),
                                    archived.updatedAt(),
                                    archived.createdBy(),
                                    archived.updatedBy());
                        });
        when(mappings.deactivateByColorId(eq(color.id()), eq(ACTOR), any(Instant.class)))
                .thenReturn(2);

        int deactivated = service.archiveColor(color.id().value(), 0L);

        assertEquals(2, deactivated);
        verify(authorization).requirePermission(ReferenceDataPermissions.COLOR_EDIT);
        verify(mappings).deactivateByColorId(eq(color.id()), eq(ACTOR), any(Instant.class));
    }

    @Test
    void archiveColorRejectsVersionMismatchWithoutWriting() {
        openSession();
        Color color = Color.create("Stale", ACTOR, CLOCK);
        when(colors.findById(color.id())).thenReturn(Optional.of(color));

        assertThrows(
                Color.OptimisticLockConflictException.class,
                () -> service.archiveColor(color.id().value(), 5L));
        verify(colors, never()).update(any());
        verify(mappings, never()).deactivateByColorId(any(), any(), any());
    }

    @Test
    void restoreColorDoesNotTouchMappings() {
        openSession();
        Color archived =
                Color.create("RestoreMe", ACTOR, CLOCK).archive(ACTOR, CLOCK);
        when(colors.findById(archived.id())).thenReturn(Optional.of(archived));
        when(colors.update(any(Color.class)))
                .thenAnswer(
                        invocation -> {
                            Color restored = invocation.getArgument(0, Color.class);
                            return Color.rehydrate(
                                    restored.id(),
                                    restored.code(),
                                    restored.codeKey(),
                                    restored.active(),
                                    restored.version() + 1,
                                    restored.createdAt(),
                                    restored.updatedAt(),
                                    restored.createdBy(),
                                    restored.updatedBy());
                        });

        ColorDto dto = service.restoreColor(archived.id().value(), archived.version());

        assertTrue(dto.active());
        verifyNoInteractions(mappings);
        verify(mappings, never()).deactivateByColorId(any(), any(), any());
    }

    private void openSession() {
        when(authentication.currentSession())
                .thenReturn(
                        Optional.of(
                                new SessionSummary(
                                        SessionId.of(UUID.randomUUID()),
                                        UserId.of(ACTOR),
                                        Login.of("tester"),
                                        CLOCK.instant())));
    }
}
