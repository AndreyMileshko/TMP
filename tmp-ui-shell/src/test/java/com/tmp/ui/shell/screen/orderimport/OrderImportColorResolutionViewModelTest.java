package com.tmp.ui.shell.screen.orderimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportFileParseResult;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import com.tmp.order.api.imports.SpecificationColorConstraint;
import com.tmp.order.api.imports.StxtOrderFileParser;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ColorImportResolutionApi;
import com.tmp.reference.api.ImportMappingSaveReport;
import com.tmp.reference.api.ResolvedColorPair;
import com.tmp.reference.api.So7ColorMappingCommandApi;
import com.tmp.reference.api.So7ColorMappingDto;
import com.tmp.reference.api.SourceColorResolution;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.screen.orderimport.colors.ColorResolutionCoordinator;
import com.tmp.ui.shell.screen.orderimport.colors.ColorResolutionDialog;
import com.tmp.ui.shell.screen.orderimport.colors.ColorResolutionSession;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OrderImportColorResolutionViewModelTest {

    private static final UUID INSIDE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OUTSIDE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void offPathLegacyConstructorSkipsCoordinatorAndMappingApi() {
        TrackingImportService imports = new TrackingImportService(successPreview("ORD-1"));
        TrackingStxtParser stxt = new TrackingStxtParser(batchWithColor("ORD-1", "Цвет"));
        OrderImportViewModel viewModel = new OrderImportViewModel(imports, stxt, createAuth());

        viewModel.selectFile(Path.of("a.stxt"));

        assertEquals(1, imports.previewCalls.get());
        assertFalse(viewModel.checkColorsButtonVisibleProperty().get());
        assertNull(viewModel.appliedColorSessionForTest());
    }

    @Test
    void onKnownColorsPreviewsWithoutDialog() {
        FakeColorApi api = matchedApi("Белый", pair("Белый", "RAL7016"));
        TrackingImportService imports = new TrackingImportService(successPreview("ORD-1"));
        TrackingStxtParser stxt = new TrackingStxtParser(batchWithColor("ORD-1", "Белый"));
        AtomicInteger dialogCalls = new AtomicInteger();
        OrderImportViewModel viewModel = viewModel(api, imports, stxt, dialogCalls, new TrackingMappingApi());

        viewModel.selectFile(Path.of("known.stxt"));

        assertEquals(0, dialogCalls.get());
        assertTrue(viewModel.previewVisibleProperty().get());
        assertNotNull(viewModel.appliedColorSessionForTest());
        assertTrue(viewModel.checkColorsButtonVisibleProperty().get());
    }

    @Test
    void unknownColorOpensDialogBeforePreview() {
        FakeColorApi api = unmatchedApi("Цвет");
        TrackingImportService imports = new TrackingImportService(successPreview("ORD-1"));
        TrackingStxtParser stxt = new TrackingStxtParser(batchWithColor("ORD-1", "Цвет"));
        AtomicInteger dialogCalls = new AtomicInteger();
        ColorResolutionSession[] opened = new ColorResolutionSession[1];
        OrderImportViewModel viewModel =
                viewModel(
                        api,
                        imports,
                        stxt,
                        dialogCalls,
                        new TrackingMappingApi(),
                        session -> {
                            opened[0] = session;
                            ColorResolutionSession resolved =
                                    session.applyCatalogPair(
                                            List.of(session.rows().get(0).id()), INSIDE, OUTSIDE);
                            return Optional.of(
                                    new ColorResolutionDialog.Result(
                                            true, resolved, resolved.rebuild()));
                        });
        viewModel.selectFile(Path.of("unknown.stxt"));
        assertEquals(1, dialogCalls.get());
        assertEquals(1, imports.previewCalls.get());
        assertTrue(viewModel.canImportProperty().get());
    }

    @Test
    void autoDialogCancelLeavesImportUnprepared() {
        FakeColorApi api = unmatchedApi("Цвет");
        TrackingImportService imports = new TrackingImportService(successPreview("ORD-1"));
        TrackingStxtParser stxt = new TrackingStxtParser(batchWithColor("ORD-1", "Цвет"));
        OrderImportViewModel viewModel =
                viewModel(
                        api,
                        imports,
                        stxt,
                        new AtomicInteger(),
                        new TrackingMappingApi(),
                        session -> Optional.empty());
        viewModel.selectFile(Path.of("cancel.stxt"));
        assertEquals(0, imports.previewCalls.get());
        assertFalse(viewModel.previewVisibleProperty().get());
        assertNull(viewModel.preparedPlanForTest());
        assertTrue(viewModel.fileSelectedProperty().get());
    }

    @Test
    void reviewColorsCancelKeepsExistingPreview() {
        FakeColorApi api = matchedApi("Белый", pair("Белый", "RAL7016"));
        TrackingImportService imports = new TrackingImportService(successPreview("ORD-OLD"));
        TrackingStxtParser stxt = new TrackingStxtParser(batchWithColor("ORD-OLD", "Белый"));
        AtomicInteger dialogCalls = new AtomicInteger();
        OrderImportViewModel viewModel =
                viewModel(
                        api,
                        imports,
                        stxt,
                        dialogCalls,
                        new TrackingMappingApi(),
                        session -> Optional.empty());
        viewModel.selectFile(Path.of("old.stxt"));
        PreparedOrderImportPlan planBefore = viewModel.preparedPlanForTest();
        viewModel.reviewColors();
        assertEquals(planBefore, viewModel.preparedPlanForTest());
        assertEquals(1, imports.previewCalls.get());
    }

    @Test
    void failedReviewPreviewKeepsPreparedPlan() {
        FakeColorApi api = matchedApi("Белый", pair("Белый", "RAL7016"));
        TrackingImportService imports = new TrackingImportService(successPreview("ORD-1"));
        TrackingStxtParser stxt = new TrackingStxtParser(batchWithColor("ORD-1", "Белый"));
        OrderImportViewModel viewModel =
                viewModel(
                        api,
                        imports,
                        stxt,
                        new AtomicInteger(),
                        new TrackingMappingApi(),
                        session -> {
                            ColorResolutionSession resolved =
                                    session.applyCatalogPair(
                                            List.of(session.rows().get(0).id()), INSIDE, OUTSIDE);
                            return Optional.of(
                                    new ColorResolutionDialog.Result(
                                            true, resolved, resolved.rebuild()));
                        });
        viewModel.selectFile(Path.of("ok.stxt"));
        PreparedOrderImportPlan before = viewModel.preparedPlanForTest();
        imports.previewException = new IllegalStateException("boom");
        viewModel.reviewColors();
        assertEquals(before, viewModel.preparedPlanForTest());
        assertEquals(
                OrderImportViewModel.MSG_REVIEW_PREVIEW_FAILED,
                viewModel.errorMessageProperty().get());
    }

    @Test
    void successfulConfirmRunsMappingSaveAfterOrderImport() {
        FakeColorApi api = unmatchedApi("Цвет");
        TrackingImportService imports = new TrackingImportService(successPreview("ORD-1"));
        TrackingStxtParser stxt = new TrackingStxtParser(batchWithColor("ORD-1", "Цвет"));
        TrackingMappingApi mappingApi = new TrackingMappingApi();
        OrderImportViewModel viewModel =
                viewModel(
                        api,
                        imports,
                        stxt,
                        new AtomicInteger(),
                        mappingApi,
                        session -> {
                            ColorResolutionSession resolved =
                                    session.applyCatalogPair(
                                                    List.of(session.rows().get(0).id()), INSIDE, OUTSIDE)
                                            .withSelection(java.util.Set.of(session.rows().get(0).id()))
                                            .setSaveMappingForAvailableHighlighted(true);
                            return Optional.of(
                                    new ColorResolutionDialog.Result(
                                            true, resolved, resolved.rebuild()));
                        });
        viewModel.selectFile(Path.of("save.stxt"));
        viewModel.confirmImport();
        assertEquals(1, imports.confirmCalls.get());
        assertEquals(1, mappingApi.saveCalls.get());
        assertTrue(viewModel.successVisibleProperty().get());
        assertTrue(
                viewModel.successMessageProperty().get().contains("Сопоставлений сохранено: 1"));
    }

    @Test
    void failedConfirmDoesNotSaveMappings() {
        FakeColorApi api = matchedApi("Белый", pair("Белый", "RAL7016"));
        TrackingImportService imports = new TrackingImportService(successPreview("ORD-1"));
        imports.confirmException = new RuntimeException("confirm failed");
        TrackingStxtParser stxt = new TrackingStxtParser(batchWithColor("ORD-1", "Белый"));
        TrackingMappingApi mappingApi = new TrackingMappingApi();
        OrderImportViewModel viewModel = viewModel(api, imports, stxt, new AtomicInteger(), mappingApi);
        viewModel.selectFile(Path.of("fail.stxt"));
        viewModel.confirmImport();
        assertEquals(1, imports.confirmCalls.get());
        assertEquals(0, mappingApi.saveCalls.get());
        assertNull(viewModel.appliedColorSessionForTest());
    }

    @Test
    void mappingSaveTechnicalErrorDoesNotRevertSuccess() {
        FakeColorApi api = unmatchedApi("НовыйЦвет");
        TrackingImportService imports = new TrackingImportService(successPreview("ORD-1"));
        TrackingStxtParser stxt = new TrackingStxtParser(batchWithColor("ORD-1", "НовыйЦвет"));
        TrackingMappingApi mappingApi = new TrackingMappingApi();
        mappingApi.throwOnSave = true;
        OrderImportViewModel viewModel =
                viewModel(
                        api,
                        imports,
                        stxt,
                        new AtomicInteger(),
                        mappingApi,
                        session -> {
                            ColorResolutionSession resolved =
                                    session.applyCatalogPair(
                                                    List.of(session.rows().get(0).id()), INSIDE, OUTSIDE)
                                            .withSelection(java.util.Set.of(session.rows().get(0).id()))
                                            .setSaveMappingForAvailableHighlighted(true);
                            return Optional.of(
                                    new ColorResolutionDialog.Result(
                                            true, resolved, resolved.rebuild()));
                        });
        viewModel.selectFile(Path.of("tech.stxt"));
        viewModel.confirmImport();
        assertTrue(viewModel.successVisibleProperty().get());
        assertTrue(viewModel.successMessageProperty().get().contains("Сопоставления цветов не сохранены"));
    }

    @Test
    void noSavePermissionSkipsMappingApiEvenWithCandidates() {
        FakeColorApi api = unmatchedApi("Цвет");
        api.canSave = false;
        TrackingImportService imports = new TrackingImportService(successPreview("ORD-1"));
        TrackingStxtParser stxt = new TrackingStxtParser(batchWithColor("ORD-1", "Цвет"));
        TrackingMappingApi mappingApi = new TrackingMappingApi();
        OrderImportViewModel viewModel =
                viewModel(
                        api,
                        imports,
                        stxt,
                        new AtomicInteger(),
                        mappingApi,
                        session -> {
                            ColorResolutionSession resolved =
                                    session.applyCatalogPair(
                                            List.of(session.rows().get(0).id()), INSIDE, OUTSIDE);
                            return Optional.of(
                                    new ColorResolutionDialog.Result(
                                            true, resolved, resolved.rebuild()));
                        });
        viewModel.selectFile(Path.of("noperm.stxt"));
        viewModel.confirmImport();
        assertEquals(0, mappingApi.saveCalls.get());
    }

  @Test
  void fileWithoutColorsHidesReviewButton() {
    FakeColorApi api = matchedApi("Белый", pair("Белый", "RAL7016"));
    TrackingImportService imports = new TrackingImportService(successPreview("ORD-1"));
    TrackingStxtParser stxt = new TrackingStxtParser(batchWithoutColor("ORD-1"));
    OrderImportViewModel viewModel = viewModel(api, imports, stxt, new AtomicInteger(), new TrackingMappingApi());
    viewModel.selectFile(Path.of("plain.stxt"));
    assertFalse(viewModel.checkColorsButtonVisibleProperty().get());
  }

    private static OrderImportViewModel viewModel(
            FakeColorApi api,
            TrackingImportService imports,
            TrackingStxtParser stxt,
            AtomicInteger dialogCalls,
            TrackingMappingApi mappingApi) {
        return viewModel(api, imports, stxt, dialogCalls, mappingApi, session -> Optional.empty());
    }

    private static OrderImportViewModel viewModel(
            FakeColorApi api,
            TrackingImportService imports,
            TrackingStxtParser stxt,
            AtomicInteger dialogCalls,
            TrackingMappingApi mappingApi,
            java.util.function.Function<ColorResolutionSession, Optional<ColorResolutionDialog.Result>> opener) {
        ColorResolutionCoordinator coordinator =
                new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportViewModel viewModel =
                new OrderImportViewModel(
                        imports, stxt, createAuth(), coordinator, mappingApi);
        viewModel.setColorResolutionDialogOpener(
                session -> {
                    dialogCalls.incrementAndGet();
                    return opener.apply(session);
                });
        return viewModel;
    }

    private static FakeAuthorization createAuth() {
        return new FakeAuthorization(
                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION),
                PermissionId.of(UiShellScreens.ORDER_ITEM_CREATE_PERMISSION),
                PermissionId.of(UiShellScreens.ORDER_REVISION_EDIT_PERMISSION),
                PermissionId.of(UiShellScreens.ORDER_ITEM_APPROVE_PERMISSION),
                PermissionId.of(UiShellScreens.ORDER_APPROVE_PERMISSION));
    }

    private static OrderImportPreview successPreview(String orderNumber) {
        return OrderImportPreview.of(
                "file.stxt",
                orderNumber,
                1,
                1,
                BigDecimal.ONE,
                1,
                List.of(),
                List.of(),
                new FakePlan(orderNumber));
    }

    private static OrderImportBatch batchWithColor(String orderNumber, String color) {
        return OrderImportBatch.of(
                "STXT",
                "f.stxt",
                "chk",
                orderNumber,
                LocalDate.of(2026, 1, 1),
                null,
                "Client",
                List.of(
                        OrderImportPosition.of(
                                "1",
                                "P1",
                                "Item",
                                1,
                                List.of(OrderImportSpecificationLine.of(
                                        "MAT", "Material", color, BigDecimal.ONE, "шт", BigDecimal.ONE)))));
    }

    private static OrderImportBatch batchWithoutColor(String orderNumber) {
        return OrderImportBatch.of(
                "STXT",
                "f.stxt",
                "chk",
                orderNumber,
                LocalDate.of(2026, 1, 1),
                null,
                "Client",
                List.of(
                        OrderImportPosition.of(
                                "1",
                                "P1",
                                "Item",
                                1,
                                List.of(OrderImportSpecificationLine.of(
                                        "MAT", "Material", " ", BigDecimal.ONE, "шт", BigDecimal.ONE)))));
    }

    private static ResolvedColorPair pair(String inside, String outside) {
        return new ResolvedColorPair(INSIDE, OUTSIDE, inside, outside, inside + "/" + outside);
    }

    private static FakeColorApi matchedApi(String text, ResolvedColorPair pair) {
        FakeColorApi api = new FakeColorApi(true);
        api.resolutions.put(
                text, SourceColorResolution.matched(text, text.toLowerCase(), pair));
        return api;
    }

    private static FakeColorApi unmatchedApi(String text) {
        FakeColorApi api = new FakeColorApi(true);
        api.resolutions.put(text, SourceColorResolution.noMapping(text, text.toLowerCase()));
        return api;
    }

    private static final class FakePlan implements PreparedOrderImportPlan {
        private final String orderNumber;

        private FakePlan(String orderNumber) {
            this.orderNumber = orderNumber;
        }

        @Override
        public List<OrderImportBatch> batches() {
            return List.of(batchWithColor(orderNumber, "Цвет"));
        }

        @Override
        public OrderImportBatch batch() {
            return batchWithColor(orderNumber, "Цвет");
        }

        @Override
        public String sourceType() {
            return "STXT";
        }

        @Override
        public String sourceReference() {
            return "f.stxt";
        }

        @Override
        public String contentChecksum() {
            return "chk";
        }

        @Override
        public String orderNumber() {
            return orderNumber;
        }
    }

    private static final class TrackingImportService implements OrderImportService {
        private final AtomicInteger previewCalls = new AtomicInteger();
        private final AtomicInteger confirmCalls = new AtomicInteger();
        private final AtomicReference<PreparedOrderImportPlan> lastConfirmedPlan = new AtomicReference<>();
        private OrderImportPreview preview;
        private RuntimeException previewException;
        private RuntimeException confirmException;
        private OrderImportConfirmResult confirmResult =
                OrderImportConfirmResult.of(OrderId.generate(), "ORD-1", 1, 1);

        private TrackingImportService(OrderImportPreview preview) {
            this.preview = preview;
        }

        @Override
        public OrderImportPreview preview(OrderImportBatch batch) {
            return preview(List.of(batch));
        }

        @Override
        public OrderImportPreview preview(List<OrderImportBatch> batches) {
            previewCalls.incrementAndGet();
            if (previewException != null) {
                throw previewException;
            }
            return Objects.requireNonNull(preview);
        }

        @Override
        public OrderImportConfirmResult confirm(PreparedOrderImportPlan plan) {
            confirmCalls.incrementAndGet();
            lastConfirmedPlan.set(plan);
            if (confirmException != null) {
                throw confirmException;
            }
            return confirmResult;
        }
    }

    private static final class TrackingStxtParser implements StxtOrderFileParser {
        private final OrderImportBatch batch;

        private TrackingStxtParser(OrderImportBatch batch) {
            this.batch = batch;
        }

        @Override
        public OrderImportFileParseResult parseFile(Path file) {
            return OrderImportFileParseResult.of(List.of(batch), List.of(), List.of(), "UTF-8");
        }

        @Override
        public OrderImportFileParseResult parse(byte[] content, String sourceReference) {
            return parseFile(Path.of("x.stxt"));
        }
    }

    private static final class TrackingMappingApi implements So7ColorMappingCommandApi {
        private final AtomicInteger saveCalls = new AtomicInteger();
        private boolean throwOnSave;

        @Override
        public ImportMappingSaveReport saveMappingsFromImport(
                List<ImportMappingSaveReport.Candidate> candidates) {
            saveCalls.incrementAndGet();
            if (throwOnSave) {
                throw new IllegalStateException("db down");
            }
            return ImportMappingSaveReport.builder()
                    .add(candidates.get(0).sourceText(), "key", ImportMappingSaveReport.Result.SAVED)
                    .build();
        }

        @Override
        public So7ColorMappingDto createMapping(
                String sourceText, UUID insideColorId, UUID outsideColorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public So7ColorMappingDto updateMappingPair(
                UUID mappingId, UUID insideColorId, UUID outsideColorId, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public So7ColorMappingDto setMappingActive(UUID mappingId, boolean active, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteMapping(UUID mappingId, long expectedVersion) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeColorApi implements ColorImportResolutionApi {
        private final boolean matching;
        private boolean canSave = true;
        private final Map<String, SourceColorResolution> resolutions = new HashMap<>();

        private FakeColorApi(boolean matching) {
            this.matching = matching;
        }

        @Override
        public boolean isAutomaticMatchingEnabled() {
            return matching;
        }

        @Override
        public List<SourceColorResolution> resolveSourceTexts(List<String> sourceTexts) {
            List<SourceColorResolution> result = new ArrayList<>();
            for (String text : sourceTexts) {
                result.add(
                        resolutions.getOrDefault(
                                text, SourceColorResolution.noMapping(text, text.toLowerCase())));
            }
            return result;
        }

        @Override
        public List<ColorDto> listActiveColors() {
            return List.of(new ColorDto(INSIDE, "Белый", true, 1L));
        }

        @Override
        public ResolvedColorPair composePair(UUID insideColorId, UUID outsideColorId) {
            return pair("Белый", "RAL7016");
        }

        @Override
        public boolean canSaveGlobalMappings() {
            return canSave;
        }
    }

    private static final class FakeConstraint implements SpecificationColorConstraint {
        @Override
        public Result check(String colorValue) {
            return Result.allowed();
        }
    }
}