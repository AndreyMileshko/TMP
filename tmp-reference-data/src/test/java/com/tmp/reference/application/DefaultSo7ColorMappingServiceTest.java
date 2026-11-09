package com.tmp.reference.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.tmp.reference.api.ImportMappingSaveReport;
import com.tmp.reference.api.So7ColorMappingDto;
import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.ColorRepository;
import com.tmp.reference.domain.So7ColorMapping;
import com.tmp.reference.domain.So7ColorMappingRepository;
import com.tmp.reference.domain.So7SourceKey;
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
class DefaultSo7ColorMappingServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T08:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock private AuthorizationService authorization;
    @Mock private AuthenticationService authentication;
    @Mock private ColorRepository colors;
    @Mock private So7ColorMappingRepository mappings;
    @Mock private PlatformTransactionManager transactionManager;

    private DefaultSo7ColorMappingService service;

    @BeforeEach
    void setUp() {
        lenient()
                .when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        service =
                new DefaultSo7ColorMappingService(
                        authorization,
                        authentication,
                        colors,
                        mappings,
                        transactionManager,
                        CLOCK);
    }

    @Test
    void listMappingsRequiresViewPermissionBeforeRepositoryAccess() {
        doThrow(new AccessDeniedException("denied"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_VIEW);

        assertThrows(
                AccessDeniedException.class,
                () -> service.listMappings(So7ColorMappingDto.ActiveFilter.ALL, null));
        verifyNoInteractions(mappings, colors);
    }

    @Test
    void mutationsRequireMappingEditPermissionBeforeRepositoryAccess() {
        doThrow(new AccessDeniedException("denied"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT);
        UUID id = UUID.randomUUID();

        assertThrows(
                AccessDeniedException.class,
                () -> service.createMapping("X", id, id));
        assertThrows(
                AccessDeniedException.class,
                () -> service.updateMappingPair(id, id, id, 0L));
        assertThrows(
                AccessDeniedException.class, () -> service.setMappingActive(id, false, 0L));
        assertThrows(AccessDeniedException.class, () -> service.deleteMapping(id, 0L));
        assertThrows(
                AccessDeniedException.class,
                () -> service.saveMappingsFromImport(List.of()));
        verifyNoInteractions(mappings, colors);
    }

    @Test
    void createMappingRejectsDuplicateNormalizedKeyWithSpecMessage() {
        openSession();
        Color color = Color.create("PairA", ACTOR, CLOCK);
        when(colors.findById(color.id())).thenReturn(Optional.of(color));
        when(mappings.findBySourceKey(So7SourceKey.of("Dup Key")))
                .thenReturn(
                        Optional.of(
                                So7ColorMapping.create(
                                        "Dup Key", color.id(), color.id(), ACTOR, CLOCK)));

        So7ColorMapping.DuplicateSourceKeyException ex =
                assertThrows(
                        So7ColorMapping.DuplicateSourceKeyException.class,
                        () ->
                                service.createMapping(
                                        "dup key", color.id().value(), color.id().value()));
        assertEquals(DefaultSo7ColorMappingService.DUPLICATE_SOURCE_MESSAGE, ex.getMessage());
        verify(mappings, never()).insert(any());
    }

    @Test
    void createMappingPersistsAndComposesDisplayColor() {
        openSession();
        Color inside = Color.create("White", ACTOR, CLOCK);
        Color outside = Color.create("RAL7016", ACTOR, CLOCK);
        when(colors.findById(inside.id())).thenReturn(Optional.of(inside));
        when(colors.findById(outside.id())).thenReturn(Optional.of(outside));
        when(mappings.findBySourceKey(any())).thenReturn(Optional.empty());
        when(mappings.insert(any(So7ColorMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, So7ColorMapping.class));

        So7ColorMappingDto dto =
                service.createMapping("SO7 White/RAL", inside.id().value(), outside.id().value());

        verify(authorization).requirePermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT);
        assertEquals("SO7 White/RAL", dto.sourceText());
        assertEquals("White", dto.insideCode());
        assertEquals("RAL7016", dto.outsideCode());
        assertEquals("White/RAL7016", dto.displayColor());
        assertTrue(dto.active());
    }

    @Test
    void createMappingRejectsInactiveReferencedColor() {
        openSession();
        Color archived = Color.create("Arch", ACTOR, CLOCK).archive(ACTOR, CLOCK);
        when(colors.findById(archived.id())).thenReturn(Optional.of(archived));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.createMapping(
                                "SO7 Arch", archived.id().value(), archived.id().value()));
        verify(mappings, never()).insert(any());
    }

    @Test
    void updateMappingPairChecksOptimisticLockBeforeWrite() {
        openSession();
        Color color = Color.create("Upd", ACTOR, CLOCK);
        So7ColorMapping mapping =
                So7ColorMapping.create("SO7 Upd", color.id(), color.id(), ACTOR, CLOCK);
        when(colors.findById(color.id())).thenReturn(Optional.of(color));
        when(mappings.findById(mapping.id())).thenReturn(Optional.of(mapping));

        assertThrows(
                So7ColorMapping.OptimisticLockConflictException.class,
                () ->
                        service.updateMappingPair(
                                mapping.id(),
                                color.id().value(),
                                color.id().value(),
                                5L));
        verify(mappings, never()).update(any());
    }

    @Test
    void saveMappingsFromImportMapsDuplicateInsertToExistsResult() {
        openSession();
        Color color = Color.create("Imp", ACTOR, CLOCK);
        when(colors.findById(color.id())).thenReturn(Optional.of(color));
        when(mappings.findBySourceKey(any())).thenReturn(Optional.empty());
        when(mappings.insert(any(So7ColorMapping.class)))
                .thenThrow(
                        new So7ColorMapping.DuplicateSourceKeyException(
                                "race", new RuntimeException("unique")));

        ImportMappingSaveReport report =
                service.saveMappingsFromImport(
                        List.of(
                                new ImportMappingSaveReport.Candidate(
                                        "SO7 Imp", color.id().value(), color.id().value())));

        assertEquals(0, report.savedCount());
        assertEquals(1, report.entries().size());
        assertEquals(
                ImportMappingSaveReport.Result.NOT_SAVED_MAPPING_EXISTS,
                report.entries().get(0).result());
    }

    @Test
    void saveMappingsFromImportDetectsConflictInImport() {
        openSession();
        Color a = Color.create("A1", ACTOR, CLOCK);
        Color b = Color.create("B1", ACTOR, CLOCK);

        ImportMappingSaveReport report =
                service.saveMappingsFromImport(
                        List.of(
                                new ImportMappingSaveReport.Candidate(
                                        "Same", a.id().value(), a.id().value()),
                                new ImportMappingSaveReport.Candidate(
                                        "same", a.id().value(), b.id().value())));

        assertEquals(
                ImportMappingSaveReport.Result.NOT_SAVED_CONFLICT_IN_IMPORT,
                report.entries().get(0).result());
        verify(mappings, never()).insert(any());
    }

    @Test
    void listMappingsMapsRepositoryRowsToDto() {
        Color color = Color.create("ListMap", ACTOR, CLOCK);
        So7ColorMapping mapping =
                So7ColorMapping.create("SO7 List", color.id(), color.id(), ACTOR, CLOCK);
        when(mappings.search(eq(Boolean.TRUE), eq("List"))).thenReturn(List.of(mapping));
        when(colors.findById(color.id())).thenReturn(Optional.of(color));

        List<So7ColorMappingDto> result =
                service.listMappings(So7ColorMappingDto.ActiveFilter.ACTIVE, "List");

        verify(authorization).requirePermission(ReferenceDataPermissions.COLOR_VIEW);
        assertEquals(1, result.size());
        assertEquals("SO7 List", result.get(0).sourceText());
        assertEquals("ListMap", result.get(0).displayColor());
        assertFalse(result.get(0).insideActive() && !color.active());
    }

    private void openSession() {
        when(authentication.currentSession())
                .thenReturn(
                        Optional.of(
                                new SessionSummary(
                                        SessionId.of(UUID.randomUUID()),
                                        UserId.of(ACTOR),
                                        Login.of("mapper"),
                                        CLOCK.instant())));
    }
}