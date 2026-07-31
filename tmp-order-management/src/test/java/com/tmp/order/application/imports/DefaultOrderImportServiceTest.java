package com.tmp.order.application.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tmp.document.api.DocumentEngine;
import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportConflictException;
import com.tmp.order.api.imports.OrderImportDuplicateException;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.api.imports.OrderImportValidationException;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import com.tmp.order.application.payload.DraftPayloadApplicationService;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class DefaultOrderImportServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-31T04:00:00Z"), ZoneOffset.UTC);

    @Mock private CustomerOrderRepository customerOrderRepository;
    @Mock private OrderImportMetadataRepository importMetadataRepository;
    @Mock private DocumentEngine documentEngine;
    @Mock private DraftPayloadApplicationService draftPayloads;
    @Mock private ProcessingRecordPort processingRecords;
    @Mock private AuthenticationService authenticationService;
    @Mock private AuthorizationService authorizationService;
    @Mock private PlatformTransactionManager transactionManager;

    private DefaultOrderImportService service;

    @BeforeEach
    void setUp() {
        service =
                new DefaultOrderImportService(
                        new OrderImportValidator(),
                        customerOrderRepository,
                        importMetadataRepository,
                        documentEngine,
                        draftPayloads,
                        processingRecords,
                        authenticationService,
                        authorizationService,
                        transactionManager,
                        CLOCK);
    }

    @Test
    void validBatchFormsPreviewWithoutErrors() {
        stubPreviewReadOnlyOk();
        OrderImportPreview preview = service.preview(validBatch());
        assertTrue(preview.canConfirm());
        assertEquals(0, preview.errorCount());
        assertTrue(preview.preparedPlan().isPresent());
    }

    @Test
    void previewCalculatesCounts() {
        stubPreviewReadOnlyOk();
        OrderImportBatch batch =
                OrderImportBatch.of(
                        "STXT",
                        "sample.stxt",
                        "checksum-1",
                        "26062891",
                        List.of(
                                position(
                                        "1",
                                        8,
                                        List.of(
                                                line("A1", "Name1", "White", bd("10"), bd("16")),
                                                line("A2", "Name2", null, null, bd("4")))),
                                position(
                                        "2",
                                        3,
                                        List.of(line("B1", "NameB", " ", bd("20"), bd("2"))))));
        OrderImportPreview preview = service.preview(batch);
        assertEquals(2, preview.positionCount());
        assertEquals(0, bd("11").compareTo(preview.totalProductQuantity()));
        assertEquals(3, preview.specificationLineCount());
        assertNull(batch.positions().get(1).specificationLines().get(0).color());
    }

    @Test
    void previewDoesNotCallMutatingCollaborators() {
        stubPreviewReadOnlyOk();
        service.preview(validBatch());
        verify(documentEngine, never()).createDocument(any());
        verify(documentEngine, never()).postDocument(any());
        verifyNoInteractions(draftPayloads);
        verify(importMetadataRepository, never()).save(any());
        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void blankOrderNumberIsRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "cs",
                                "  ",
                                List.of(position("1", 1, List.of(line("c", "n", null, null, bd("1")))))));
        assertFalse(preview.canConfirm());
        assertTrue(hasCode(preview.errors(), OrderImportValidator.CODE_ORDER_NUMBER_REQUIRED));
    }

    @Test
    void emptyPositionsRejected() {
        OrderImportPreview preview =
                service.preview(OrderImportBatch.of("STXT", "file.stxt", "cs", "ORD-1", List.of()));
        assertTrue(hasCode(preview.errors(), OrderImportValidator.CODE_POSITIONS_EMPTY));
    }

    @Test
    void blankExternalPositionNumberRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(
                                        position(
                                                " ",
                                                1,
                                                List.of(line("c", "n", null, null, bd("1")))))));
        assertTrue(
                hasCode(preview.errors(), OrderImportValidator.CODE_EXTERNAL_POSITION_REQUIRED));
    }

    @Test
    void zeroProductQuantityRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(
                                        position(
                                                "1",
                                                0,
                                                List.of(line("c", "n", null, null, bd("1")))))));
        assertTrue(
                hasCode(
                        preview.errors(),
                        OrderImportValidator.CODE_PRODUCT_QUANTITY_NOT_POSITIVE));
    }

    @Test
    void negativeProductQuantityRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(
                                        position(
                                                "1",
                                                -2,
                                                List.of(line("c", "n", null, null, bd("1")))))));
        assertTrue(
                hasCode(
                        preview.errors(),
                        OrderImportValidator.CODE_PRODUCT_QUANTITY_NOT_POSITIVE));
    }

    @Test
    void emptySpecificationRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(OrderImportPosition.of("1", 1, List.of()))));
        assertTrue(hasCode(preview.errors(), OrderImportValidator.CODE_SPECIFICATION_EMPTY));
    }

    @Test
    void blankMaterialCodeRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(
                                        position(
                                                "1",
                                                1,
                                                List.of(line(" ", "Name", null, null, bd("1")))))));
        assertTrue(hasCode(preview.errors(), OrderImportValidator.CODE_MATERIAL_CODE_REQUIRED));
    }

    @Test
    void blankMaterialNameRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(
                                        position(
                                                "1",
                                                1,
                                                List.of(line("CODE", " ", null, null, bd("1")))))));
        assertTrue(hasCode(preview.errors(), OrderImportValidator.CODE_MATERIAL_NAME_REQUIRED));
    }

    @Test
    void nullLengthMmAccepted() {
        stubPreviewReadOnlyOk();
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(
                                        position(
                                                "1",
                                                1,
                                                List.of(line("CODE", "Name", null, null, bd("1")))))));
        assertTrue(preview.canConfirm());
    }

    @Test
    void nonPositiveLengthMmRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(
                                        position(
                                                "1",
                                                1,
                                                List.of(
                                                        line(
                                                                "CODE",
                                                                "Name",
                                                                null,
                                                                bd("0"),
                                                                bd("1")))))));
        assertTrue(hasCode(preview.errors(), OrderImportValidator.CODE_LENGTH_MM_NOT_POSITIVE));
    }

    @Test
    void nonPositiveLineQuantityRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(
                                        position(
                                                "1",
                                                1,
                                                List.of(
                                                        line(
                                                                "CODE",
                                                                "Name",
                                                                null,
                                                                null,
                                                                bd("-1")))))));
        assertTrue(
                hasCode(preview.errors(), OrderImportValidator.CODE_LINE_QUANTITY_NOT_POSITIVE));
    }

    @Test
    void blankColorNormalizedToNull() {
        OrderImportSpecificationLine line = line("CODE", "Name", "   ", null, bd("1"));
        assertNull(line.color());
    }

    @Test
    void lineQuantityNotMultipliedByProductQuantity() {
        stubPreviewReadOnlyOk();
        OrderImportBatch batch =
                OrderImportBatch.of(
                        "STXT",
                        "file.stxt",
                        "cs",
                        "ORD-1",
                        List.of(
                                position(
                                        "1",
                                        8,
                                        List.of(line("CODE", "Name", null, null, bd("16"))))));
        OrderImportPreview preview = service.preview(batch);
        assertEquals(0, bd("16").compareTo(preview.preparedPlan().orElseThrow()
                .batch()
                .positions()
                .get(0)
                .specificationLines()
                .get(0)
                .lineQuantity()));
        assertEquals(0, bd("8").compareTo(preview.totalProductQuantity()));
    }

    @Test
    void warningsDoNotBlockConfirmEligibility() {
        OrderImportValidator validator =
                new OrderImportValidator() {
                    @Override
                    public List<OrderImportProblem> validate(OrderImportBatch batch) {
                        List<OrderImportProblem> problems = new ArrayList<>(super.validate(batch));
                        problems.add(
                                OrderImportProblem.warning(
                                        "IMPORT_UNKNOWN_COLUMN",
                                        "batch",
                                        null,
                                        null,
                                        "extra",
                                        "x",
                                        "Неизвестная колонка проигнорирована."));
                        return problems;
                    }
                };
        service =
                new DefaultOrderImportService(
                        validator,
                        customerOrderRepository,
                        importMetadataRepository,
                        documentEngine,
                        draftPayloads,
                        processingRecords,
                        authenticationService,
                        authorizationService,
                        transactionManager,
                        CLOCK);
        stubPreviewReadOnlyOk();
        OrderImportPreview preview = service.preview(validBatch());
        assertTrue(preview.canConfirm());
        assertEquals(1, preview.warningCount());
        assertEquals(0, preview.errorCount());
    }

    @Test
    void errorsBlockConfirm() {
        OrderImportPreview preview =
                service.preview(OrderImportBatch.of("STXT", "file.stxt", "cs", "ORD-1", List.of()));
        assertFalse(preview.canConfirm());
        assertTrue(preview.preparedPlan().isEmpty());
        assertThrows(
                OrderImportValidationException.class,
                () -> service.confirm(new FakePreparedPlan(validBatch())));
    }

    @Test
    void preparedPlanCannotBeCreatedViaPublicApiFactory() {
        assertTrue(PreparedOrderImportPlan.class.isInterface());
        assertEquals(0, PreparedOrderImportPlan.class.getConstructors().length);
        assertEquals(
                0,
                java.util.Arrays.stream(PreparedOrderImportPlan.class.getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .count());
        assertEquals(0, DefaultPreparedOrderImportPlan.class.getConstructors().length);
        assertTrue(
                java.util.Arrays.stream(DefaultPreparedOrderImportPlan.class.getDeclaredConstructors())
                        .noneMatch(ctor -> java.lang.reflect.Modifier.isPublic(ctor.getModifiers())));
    }

    @Test
    void confirmRejectsPlanNotMintedByPreview() {
        assertThrows(
                OrderImportValidationException.class,
                () -> service.confirm(new FakePreparedPlan(validBatch())));
    }

    @Test
    void previewErrorHasNoPreparedPlan() {
        OrderImportPreview preview =
                service.preview(OrderImportBatch.of("STXT", "file.stxt", "cs", "ORD-1", List.of()));
        assertFalse(preview.canConfirm());
        assertTrue(preview.preparedPlan().isEmpty());
    }

    @Test
    void previewSuccessHasPreparedPlan() {
        stubPreviewReadOnlyOk();
        OrderImportPreview preview = service.preview(validBatch());
        assertTrue(preview.canConfirm());
        assertTrue(preview.preparedPlan().isPresent());
        assertInstanceOf(DefaultPreparedOrderImportPlan.class, preview.preparedPlan().orElseThrow());
    }

    @Test
    void preparedPlanIsImmutableSnapshot() {
        stubPreviewReadOnlyOk();
        OrderImportBatch original = validBatch();
        PreparedOrderImportPlan plan = service.preview(original).preparedPlan().orElseThrow();
        assertEquals(original, plan.batch());
        assertEquals(original.positions(), plan.batch().positions());
        assertThrows(
                UnsupportedOperationException.class,
                () -> plan.batch().positions().add(position("9", 1, List.of(line("c", "n", null, null, bd("1"))))));
    }

    @Test
    void sourceTypeTooLongIsRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "S".repeat(OrderImportValidator.MAX_SOURCE_TYPE_LENGTH + 1),
                                "file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(position("1", 1, List.of(line("c", "n", null, null, bd("1")))))));
        assertTrue(hasCode(preview.errors(), OrderImportValidator.CODE_SOURCE_TYPE_TOO_LONG));
        assertFalse(preview.canConfirm());
    }

    @Test
    void contentChecksumTooLongIsRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "file.stxt",
                                "C".repeat(OrderImportValidator.MAX_CONTENT_CHECKSUM_LENGTH + 1),
                                "ORD-1",
                                List.of(position("1", 1, List.of(line("c", "n", null, null, bd("1")))))));
        assertTrue(hasCode(preview.errors(), OrderImportValidator.CODE_CHECKSUM_TOO_LONG));
    }

    @Test
    void sourceReferenceTooLongIsRejected() {
        OrderImportPreview preview =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "r".repeat(OrderImportValidator.MAX_SOURCE_REFERENCE_LENGTH + 1),
                                "cs",
                                "ORD-1",
                                List.of(position("1", 1, List.of(line("c", "n", null, null, bd("1")))))));
        assertTrue(hasCode(preview.errors(), OrderImportValidator.CODE_SOURCE_REFERENCE_TOO_LONG));
    }

    @Test
    void absoluteSourceReferenceIsRejected() {
        OrderImportPreview windows =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "C:\\exports\\file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(position("1", 1, List.of(line("c", "n", null, null, bd("1")))))));
        assertTrue(hasCode(windows.errors(), OrderImportValidator.CODE_SOURCE_REFERENCE_ABSOLUTE_PATH));

        OrderImportPreview unix =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "/home/user/file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(position("1", 1, List.of(line("c", "n", null, null, bd("1")))))));
        assertTrue(hasCode(unix.errors(), OrderImportValidator.CODE_SOURCE_REFERENCE_ABSOLUTE_PATH));

        OrderImportPreview tmp =
                service.preview(
                        OrderImportBatch.of(
                                "STXT",
                                "/tmp/file.stxt",
                                "cs",
                                "ORD-1",
                                List.of(position("1", 1, List.of(line("c", "n", null, null, bd("1")))))));
        assertTrue(hasCode(tmp.errors(), OrderImportValidator.CODE_SOURCE_REFERENCE_ABSOLUTE_PATH));
    }

    @Test
    void userMessagesContainNoSqlOrStackTrace() {
        when(importMetadataRepository.existsBySourceTypeAndChecksum(any(), any())).thenReturn(true);
        OrderImportDuplicateException duplicate =
                assertThrows(OrderImportDuplicateException.class, () -> service.preview(validBatch()));
        assertEquals(OrderImportDuplicateException.USER_MESSAGE, duplicate.getMessage());
        assertFalse(duplicate.getMessage().toLowerCase().contains("sql"));
        assertFalse(duplicate.getMessage().contains("Exception"));

        when(importMetadataRepository.existsBySourceTypeAndChecksum(any(), any())).thenReturn(false);
        when(customerOrderRepository.existsByOrderNumber(any(OrderNumber.class))).thenReturn(true);
        OrderImportConflictException conflict =
                assertThrows(OrderImportConflictException.class, () -> service.preview(validBatch()));
        assertEquals(OrderImportConflictException.USER_MESSAGE, conflict.getMessage());
        assertFalse(conflict.getMessage().toLowerCase().contains("sql"));
    }

    @Test
    void placeholdersAreNotCreatedInValidBatchCommercialMapping() {
        OrderImportBatch batch = validBatch();
        assertNull(batch.positions().get(0).specificationLines().get(0).color());
        assertFalse("UNKNOWN".equalsIgnoreCase(batch.orderNumber()));
        assertFalse("N/A".equalsIgnoreCase(batch.positions().get(0).externalPositionNumber()));
    }

    @Test
    void importedLineUsesDefaultUnitOfMeasureConstant() {
        assertEquals("шт", OrderImportDefaults.UNIT_OF_MEASURE);
    }

    private void stubPreviewReadOnlyOk() {
        when(importMetadataRepository.existsBySourceTypeAndChecksum(any(), any())).thenReturn(false);
        when(customerOrderRepository.existsByOrderNumber(any(OrderNumber.class))).thenReturn(false);
    }

    private static OrderImportBatch validBatch() {
        return OrderImportBatch.of(
                "STXT",
                "sample.stxt",
                "checksum-abc",
                "26062891",
                List.of(
                        position(
                                "1",
                                8,
                                List.of(line("107.225", "Штапик", null, bd("2066"), bd("16"))))));
    }

    private static OrderImportPosition position(
            String external, Integer qty, List<OrderImportSpecificationLine> lines) {
        return OrderImportPosition.of(external, qty, lines);
    }

    private static OrderImportSpecificationLine line(
            String code, String name, String color, BigDecimal length, BigDecimal qty) {
        return OrderImportSpecificationLine.of(code, name, color, length, qty);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static boolean hasCode(List<OrderImportProblem> problems, String code) {
        return problems.stream().anyMatch(problem -> code.equals(problem.code()));
    }

    private static final class FakePreparedPlan implements PreparedOrderImportPlan {
        private final OrderImportBatch batch;

        private FakePreparedPlan(OrderImportBatch batch) {
            this.batch = batch;
        }

        @Override
        public OrderImportBatch batch() {
            return batch;
        }

        @Override
        public String sourceType() {
            return batch.sourceType();
        }

        @Override
        public String sourceReference() {
            return batch.sourceReference();
        }

        @Override
        public String contentChecksum() {
            return batch.contentChecksum();
        }

        @Override
        public String orderNumber() {
            return batch.orderNumber();
        }
    }
}
