package com.tmp.order.application.imports.stxt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.document.api.DocumentEngine;
import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.application.imports.DefaultOrderImportService;
import com.tmp.order.application.imports.OrderImportValidator;
import com.tmp.order.application.payload.DraftPayloadApplicationService;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.security.api.AuthorizationService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * STXT → OrderImportBatch → Import Core preview without persistence writes.
 */
class StxtImportPreviewIntegrationTest {

    private CustomerOrderRepository customerOrderRepository;
    private OrderItemRepository orderItemRepository;
    private DocumentEngine documentEngine;
    private OrderImportService importService;
    private final StxtFileAdapter adapter = new StxtFileAdapter();

    @BeforeEach
    void setUp() {
        customerOrderRepository = mock(CustomerOrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        documentEngine = mock(DocumentEngine.class);
        DraftPayloadApplicationService draftPayloadApplicationService =
                mock(DraftPayloadApplicationService.class);
        ProcessingRecordPort processingRecordPort = mock(ProcessingRecordPort.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        when(customerOrderRepository.existsByOrderNumber(any(OrderNumber.class))).thenReturn(false);

        importService =
                new DefaultOrderImportService(
                        new OrderImportValidator(),
                        customerOrderRepository,
                        orderItemRepository,
                        documentEngine,
                        draftPayloadApplicationService,
                        processingRecordPort,
                        authorizationService,
                        transactionManager,
                        Clock.systemUTC());
    }

    @Test
    void fixtureParsesToBatchAndPreviewWithoutDbWrites() throws IOException {
        byte[] content = readFixture("stxt/sample-utf8.stxt");
        StxtParseResult parseResult = adapter.parse(content, "sample-utf8.stxt");

        assertTrue(parseResult.isSuccessful(), () -> parseResult.errors().toString());
        OrderImportBatch batch = parseResult.batch().orElseThrow();
        assertEquals("26062891", batch.orderNumber());
        assertEquals(LocalDate.of(2026, 6, 25), batch.orderDate());
        assertEquals("Альпы ООО", batch.customerName());
        assertEquals(2, batch.positionCount());
        assertEquals(4, batch.specificationLineCount());
        assertEquals("WHS HALO WHS_60 ActivPilot", batch.positions().get(0).name());
        assertEquals(
                "Штапик черный 8 мм/38.39.40",
                batch.positions().get(0).specificationLines().get(0).materialName());
        assertEquals(
                0,
                new BigDecimal("2066.0")
                        .compareTo(batch.positions().get(0).specificationLines().get(0).lengthMm()));
        assertNull(batch.positions().get(1).specificationLines().get(0).lengthMm());
        OrderImportSpecificationLine sqmLine =
                batch.positions().get(0).specificationLines().stream()
                        .filter(line -> "200.001".equals(line.materialCode()))
                        .findFirst()
                        .orElseThrow();
        assertNull(sqmLine.lengthMm());
        assertEquals("шт.", sqmLine.unitOfMeasure());

        OrderImportPreview preview = importService.preview(batch);
        assertTrue(preview.errors().isEmpty(), () -> preview.errors().toString());
        assertTrue(preview.canConfirm());
        assertEquals("26062891", preview.orderNumber());
        assertEquals(2, preview.positionCount());
        assertEquals(4, preview.specificationLineCount());
        assertEquals(0, new BigDecimal("10").compareTo(preview.totalProductQuantity()));

        verify(documentEngine, never()).postDocument(any());
        verify(documentEngine, never()).createDocument(any());
        verify(orderItemRepository, never()).save(any());
        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void finalExportFormatPreviewShowsOrdersPositionsProductsAndZeroErrors() throws IOException {
        byte[] content = readFixture("stxt/final-export-format.stxt");
        StxtParseResult parseResult = adapter.parse(content, "final-export-format.stxt");
        assertTrue(parseResult.isSuccessful(), () -> parseResult.errors().toString());
        assertTrue(parseResult.batches().size() > 0);

        OrderImportPreview preview = importService.preview(parseResult.batches());
        assertEquals(0, preview.errorCount(), () -> preview.errors().toString());
        assertTrue(preview.canConfirm());
        assertTrue(preview.orderCount() > 0, "orders > 0");
        assertTrue(preview.positionCount() > 0, "positions > 0");
        assertTrue(preview.totalProductQuantity().signum() > 0, "product qty > 0");

        verify(documentEngine, never()).postDocument(any());
        verify(orderItemRepository, never()).save(any());
        verify(customerOrderRepository, never()).save(any());
    }

    private static byte[] readFixture(String classpath) throws IOException {
        try (InputStream in =
                StxtImportPreviewIntegrationTest.class
                        .getClassLoader()
                        .getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IOException("Missing fixture: " + classpath);
            }
            return in.readAllBytes();
        }
    }
}
