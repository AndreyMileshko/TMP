package com.tmp.reference.application;

import com.tmp.reference.api.ImportMappingSaveReport;
import com.tmp.reference.api.So7ColorMappingCommandApi;
import com.tmp.reference.api.So7ColorMappingDto;
import com.tmp.reference.api.So7ColorMappingQueryApi;
import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.ColorPairComposition;
import com.tmp.reference.domain.ColorRepository;
import com.tmp.reference.domain.So7ColorMapping;
import com.tmp.reference.domain.So7ColorMappingRepository;
import com.tmp.reference.domain.So7SourceKey;
import com.tmp.reference.security.ReferenceDataPermissions;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.SessionSummary;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SO7 color mapping application service (TMP-SPEC-015 sections 6.4, 11, 15.1, 17 / COL-021B).
 *
 * <p>Enforces {@code reference.color.view} / {@code reference.color-mapping.edit} on the server
 * before repository access. Physical mapping DELETE frees the source key; catalog colors are never
 * deleted here.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class DefaultSo7ColorMappingService
        implements So7ColorMappingQueryApi, So7ColorMappingCommandApi {

    static final String DUPLICATE_SOURCE_MESSAGE =
            "Сопоставление для этого текста уже существует";

    private final AuthorizationService authorization;
    private final AuthenticationService authentication;
    private final ColorRepository colors;
    private final So7ColorMappingRepository mappings;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DefaultSo7ColorMappingService(
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
    public List<So7ColorMappingDto> listMappings(
            So7ColorMappingDto.ActiveFilter filter, String search) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_VIEW);
        Objects.requireNonNull(filter, "filter");
        Boolean activeFilter =
                switch (filter) {
                    case ACTIVE -> Boolean.TRUE;
                    case INACTIVE -> Boolean.FALSE;
                    case ALL -> null;
                };
        return mappings.search(activeFilter, search).stream().map(this::toDto).toList();
    }

    @Override
    public So7ColorMappingDto createMapping(
            String sourceText, UUID insideColorId, UUID outsideColorId) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT);
        UUID actor = requireActorUserId();
        ColorId insideId = ColorId.of(Objects.requireNonNull(insideColorId, "insideColorId"));
        ColorId outsideId = ColorId.of(Objects.requireNonNull(outsideColorId, "outsideColorId"));
        Color inside = requireActiveColor(insideId);
        Color outside = requireActiveColor(outsideId);

        So7ColorMapping created;
        try {
            created = So7ColorMapping.create(sourceText, insideId, outsideId, actor, clock);
        } catch (So7ColorMapping.InvalidSourceException ex) {
            throw ex;
        }

        if (mappings.findBySourceKey(created.sourceKey()).isPresent()) {
            throw new So7ColorMapping.DuplicateSourceKeyException(DUPLICATE_SOURCE_MESSAGE);
        }

        try {
            return toDto(mappings.insert(created), inside, outside);
        } catch (So7ColorMapping.DuplicateSourceKeyException ex) {
            throw new So7ColorMapping.DuplicateSourceKeyException(DUPLICATE_SOURCE_MESSAGE, ex);
        }
    }

    @Override
    public So7ColorMappingDto updateMappingPair(
            UUID mappingId, UUID insideColorId, UUID outsideColorId, long expectedVersion) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT);
        UUID actor = requireActorUserId();
        UUID id = Objects.requireNonNull(mappingId, "mappingId");
        ColorId insideId = ColorId.of(Objects.requireNonNull(insideColorId, "insideColorId"));
        ColorId outsideId = ColorId.of(Objects.requireNonNull(outsideColorId, "outsideColorId"));
        Color inside = requireActiveColor(insideId);
        Color outside = requireActiveColor(outsideId);

        So7ColorMapping existing = requireMappingExists(id);
        requireExpectedVersion(existing, expectedVersion);
        So7ColorMapping updated = existing.updatePair(insideId, outsideId, actor, clock);
        return toDto(mappings.update(updated), inside, outside);
    }

    @Override
    public So7ColorMappingDto setMappingActive(
            UUID mappingId, boolean active, long expectedVersion) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT);
        UUID actor = requireActorUserId();
        UUID id = Objects.requireNonNull(mappingId, "mappingId");

        So7ColorMapping existing = requireMappingExists(id);
        requireExpectedVersion(existing, expectedVersion);

        So7ColorMapping next;
        if (active) {
            Color inside = requireActiveColor(existing.insideColorId());
            Color outside = requireActiveColor(existing.outsideColorId());
            next = existing.activate(actor, clock);
            return toDto(mappings.update(next), inside, outside);
        }
        next = existing.deactivate(actor, clock);
        return toDto(mappings.update(next));
    }

    @Override
    public void deleteMapping(UUID mappingId, long expectedVersion) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT);
        UUID id = Objects.requireNonNull(mappingId, "mappingId");
        So7ColorMapping existing = requireMappingExists(id);
        requireExpectedVersion(existing, expectedVersion);
        mappings.delete(id, expectedVersion);
    }

    @Override
    public ImportMappingSaveReport saveMappingsFromImport(
            List<ImportMappingSaveReport.Candidate> candidates) {
        authorization.requirePermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT);
        Objects.requireNonNull(candidates, "candidates");
        UUID actor = requireActorUserId();

        ImportMappingSaveReport report =
                transactionTemplate.execute(
                        status -> saveMappingsFromImportInTransaction(candidates, actor));
        if (report == null) {
            throw new IllegalStateException("saveMappingsFromImport transaction returned no report");
        }
        return report;
    }

    private ImportMappingSaveReport saveMappingsFromImportInTransaction(
            List<ImportMappingSaveReport.Candidate> candidates, UUID actor) {
        ImportMappingSaveReport.Builder report = ImportMappingSaveReport.builder();
        Map<String, List<ParsedCandidate>> byKey = new LinkedHashMap<>();

        for (ImportMappingSaveReport.Candidate candidate : candidates) {
            ParsedCandidate parsed = parseCandidate(candidate);
            if (parsed.invalid) {
                report.add(parsed.sourceText, parsed.sourceKey, parsed.invalidResult);
                continue;
            }
            byKey.computeIfAbsent(parsed.sourceKey, ignored -> new ArrayList<>()).add(parsed);
        }

        for (Map.Entry<String, List<ParsedCandidate>> entry : byKey.entrySet()) {
            String sourceKey = entry.getKey();
            List<ParsedCandidate> group = entry.getValue();
            ParsedCandidate first = group.get(0);

            if (hasConflictingPairs(group)) {
                report.add(
                        first.sourceText,
                        sourceKey,
                        ImportMappingSaveReport.Result.NOT_SAVED_CONFLICT_IN_IMPORT);
                continue;
            }

            if (mappings.findBySourceKey(So7SourceKey.of(first.sourceText)).isPresent()) {
                report.add(
                        first.sourceText,
                        sourceKey,
                        ImportMappingSaveReport.Result.NOT_SAVED_MAPPING_EXISTS);
                continue;
            }

            Color inside = colors.findById(first.insideColorId).orElse(null);
            Color outside = colors.findById(first.outsideColorId).orElse(null);
            if (inside == null
                    || outside == null
                    || !inside.active()
                    || !outside.active()) {
                report.add(
                        first.sourceText,
                        sourceKey,
                        ImportMappingSaveReport.Result.NOT_SAVED_COLOR_INACTIVE);
                continue;
            }

            So7ColorMapping created =
                    So7ColorMapping.create(
                            first.sourceText,
                            first.insideColorId,
                            first.outsideColorId,
                            actor,
                            clock);
            try {
                mappings.insert(created);
                report.add(first.sourceText, sourceKey, ImportMappingSaveReport.Result.SAVED);
            } catch (So7ColorMapping.DuplicateSourceKeyException ex) {
                report.add(
                        first.sourceText,
                        sourceKey,
                        ImportMappingSaveReport.Result.NOT_SAVED_MAPPING_EXISTS);
            }
        }

        return report.build();
    }

    private static boolean hasConflictingPairs(List<ParsedCandidate> group) {
        ParsedCandidate first = group.get(0);
        for (int i = 1; i < group.size(); i++) {
            ParsedCandidate other = group.get(i);
            if (!first.insideColorId.equals(other.insideColorId)
                    || !first.outsideColorId.equals(other.outsideColorId)) {
                return true;
            }
        }
        return false;
    }

    private ParsedCandidate parseCandidate(ImportMappingSaveReport.Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        String raw = candidate.sourceText();
        UUID inside = candidate.insideColorId();
        UUID outside = candidate.outsideColorId();
        if (inside == null || outside == null) {
            return ParsedCandidate.invalid(
                    raw,
                    null,
                    ImportMappingSaveReport.Result.NOT_SAVED_COLOR_INACTIVE);
        }
        try {
            So7ColorMapping created =
                    So7ColorMapping.create(
                            raw, ColorId.of(inside), ColorId.of(outside), null, clock);
            return ParsedCandidate.valid(
                    created.sourceText(),
                    created.sourceKey().value(),
                    created.insideColorId(),
                    created.outsideColorId());
        } catch (So7ColorMapping.InvalidSourceException | IllegalArgumentException ex) {
            String key = null;
            try {
                if (raw != null) {
                    key = So7SourceKey.of(raw).value();
                }
            } catch (RuntimeException ignored) {
                // keep null key for totally invalid source
            }
            return ParsedCandidate.invalid(
                    raw, key, ImportMappingSaveReport.Result.NOT_SAVED_INVALID_SOURCE);
        }
    }

    private Color requireActiveColor(ColorId id) {
        Color color =
                colors.findById(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Color not found: " + id.value()));
        if (!color.active()) {
            throw new IllegalArgumentException("Referenced color is inactive: " + color.code());
        }
        return color;
    }

    private So7ColorMapping requireMappingExists(UUID id) {
        return mappings
                .findById(id)
                .orElseThrow(
                        () -> new IllegalArgumentException("SO7 mapping not found: " + id));
    }

    private static void requireExpectedVersion(So7ColorMapping mapping, long expectedVersion) {
        if (mapping.version() != expectedVersion) {
            throw new So7ColorMapping.OptimisticLockConflictException(
                    "SO7 mapping version conflict: " + mapping.id());
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

    private So7ColorMappingDto toDto(So7ColorMapping mapping) {
        Color inside =
                colors.findById(mapping.insideColorId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Inside color missing for mapping "
                                                        + mapping.id()));
        Color outside =
                colors.findById(mapping.outsideColorId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Outside color missing for mapping "
                                                        + mapping.id()));
        return toDto(mapping, inside, outside);
    }

    private static So7ColorMappingDto toDto(
            So7ColorMapping mapping, Color inside, Color outside) {
        return new So7ColorMappingDto(
                mapping.id(),
                mapping.sourceText(),
                mapping.sourceKey().value(),
                mapping.insideColorId().value(),
                mapping.outsideColorId().value(),
                inside.code(),
                outside.code(),
                inside.active(),
                outside.active(),
                ColorPairComposition.compose(inside.code(), outside.code()),
                mapping.active(),
                mapping.version());
    }

    private static final class ParsedCandidate {
        private final String sourceText;
        private final String sourceKey;
        private final ColorId insideColorId;
        private final ColorId outsideColorId;
        private final boolean invalid;
        private final ImportMappingSaveReport.Result invalidResult;

        private ParsedCandidate(
                String sourceText,
                String sourceKey,
                ColorId insideColorId,
                ColorId outsideColorId,
                boolean invalid,
                ImportMappingSaveReport.Result invalidResult) {
            this.sourceText = sourceText;
            this.sourceKey = sourceKey;
            this.insideColorId = insideColorId;
            this.outsideColorId = outsideColorId;
            this.invalid = invalid;
            this.invalidResult = invalidResult;
        }

        static ParsedCandidate valid(
                String sourceText, String sourceKey, ColorId insideColorId, ColorId outsideColorId) {
            return new ParsedCandidate(
                    sourceText, sourceKey, insideColorId, outsideColorId, false, null);
        }

        static ParsedCandidate invalid(
                String sourceText, String sourceKey, ImportMappingSaveReport.Result result) {
            return new ParsedCandidate(sourceText, sourceKey, null, null, true, result);
        }
    }
}