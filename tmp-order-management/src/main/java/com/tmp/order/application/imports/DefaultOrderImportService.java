package com.tmp.order.application.imports;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportConflictException;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportProblemSeverity;
import com.tmp.order.api.imports.OrderImportProcessingException;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.api.imports.OrderImportValidationException;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import com.tmp.order.application.document.OrderCreateDocumentProcessor;
import com.tmp.order.application.order.DuplicateOrderNumberException;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.DraftPayloadApplicationService;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderItemCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionPayloadLine;
import com.tmp.order.application.payload.OrderItemRevisionUpdatePayload;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecord;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.ProductCode;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.security.api.AuthorizationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default Import Core: source-neutral preview without persistence and atomic confirm through
 * Document Engine business documents, landing Order/Item/Revision/Specification as ACTIVE
 * (ADR-031 / Final STXT Contract). Multi-order files are confirmed in one transaction.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class DefaultOrderImportService implements OrderImportService {

    private final OrderImportValidator validator;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DocumentEngine documentEngine;
    private final DraftPayloadApplicationService draftPayloads;
    private final ProcessingRecordPort processingRecords;
    private final AuthorizationService authorizationService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DefaultOrderImportService(
            OrderImportValidator validator,
            CustomerOrderRepository customerOrderRepository,
            OrderItemRepository orderItemRepository,
            DocumentEngine documentEngine,
            DraftPayloadApplicationService draftPayloads,
            ProcessingRecordPort processingRecords,
            AuthorizationService authorizationService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.draftPayloads = Objects.requireNonNull(draftPayloads, "draftPayloads");
        this.processingRecords = Objects.requireNonNull(processingRecords, "processingRecords");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        Objects.requireNonNull(transactionManager, "transactionManager");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OrderImportPreview preview(OrderImportBatch batch) {
        Objects.requireNonNull(batch, "batch");
        return preview(List.of(batch));
    }

    @Override
    public OrderImportPreview preview(List<OrderImportBatch> batches) {
        Objects.requireNonNull(batches, "batches");
        authorizationService.requirePermission(OrderManagementPermissions.ORDER_CREATE);

        List<OrderImportProblem> structural = validator.validateAll(batches);
        List<OrderImportProblem> errors = new ArrayList<>();
        List<OrderImportProblem> warnings = new ArrayList<>();
        for (OrderImportProblem problem : structural) {
            if (problem.severity() == OrderImportProblemSeverity.ERROR) {
                errors.add(problem);
            } else {
                warnings.add(problem);
            }
        }

        if (errors.isEmpty()) {
            assertOrderNumbersAvailable(batches);
        }

        int positionCount = 0;
        int specCount = 0;
        BigDecimal totalQty = BigDecimal.ZERO;
        for (OrderImportBatch batch : batches) {
            positionCount += batch.positionCount();
            specCount += batch.specificationLineCount();
            totalQty = totalQty.add(batch.totalProductQuantity());
        }
        String orderNumbers =
                batches.stream()
                        .map(OrderImportBatch::orderNumber)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(", "));
        String sourceReference =
                batches.isEmpty() ? null : batches.get(0).sourceReference();

        PreparedOrderImportPlan plan =
                errors.isEmpty() ? new DefaultPreparedOrderImportPlan(batches) : null;

        return OrderImportPreview.of(
                sourceReference,
                orderNumbers,
                batches.size(),
                positionCount,
                totalQty,
                specCount,
                errors,
                warnings,
                plan);
    }

    @Override
    public OrderImportConfirmResult confirm(PreparedOrderImportPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (!(plan instanceof DefaultPreparedOrderImportPlan)) {
            throw new OrderImportValidationException(
                    List.of(
                            OrderImportProblem.error(
                                    "IMPORT_PLAN_NOT_FROM_PREVIEW",
                                    "plan",
                                    null,
                                    null,
                                    "preparedPlan",
                                    null,
                                    "План импорта недействителен. Выполните предварительный просмотр.")));
        }
        authorizationService.requirePermission(OrderManagementPermissions.ORDER_CREATE);
        authorizationService.requirePermission(OrderManagementPermissions.ITEM_CREATE);
        authorizationService.requirePermission(OrderManagementPermissions.REVISION_EDIT);

        List<OrderImportBatch> batches = plan.batches();
        List<OrderImportProblem> structural = validator.validateAll(batches);
        List<OrderImportProblem> errors =
                structural.stream()
                        .filter(problem -> problem.severity() == OrderImportProblemSeverity.ERROR)
                        .toList();
        if (!errors.isEmpty()) {
            throw new OrderImportValidationException(errors);
        }

        try {
            return transactionTemplate.execute(status -> executeConfirm(batches));
        } catch (OrderImportConflictException | OrderImportValidationException controlled) {
            throw controlled;
        } catch (RuntimeException ex) {
            throw mapUnexpected(ex);
        }
    }

    private OrderImportConfirmResult executeConfirm(List<OrderImportBatch> batches) {
        assertOrderNumbersAvailable(batches);

        Instant now = clock.instant();
        List<OrderImportConfirmResult.ImportedOrder> imported = new ArrayList<>();
        int totalPositions = 0;
        int totalLines = 0;

        for (OrderImportBatch batch : batches) {
            OrderNumber orderNumber = OrderNumber.of(batch.orderNumber().trim());
            OrderId orderId;
            try {
                orderId = postOrderCreate(orderNumber, batch.customerName(), now);
            } catch (DuplicateOrderNumberException duplicate) {
                throw new OrderImportConflictException();
            } catch (DataIntegrityViolationException integrity) {
                if (isOrderNumberUniqueViolation(integrity)) {
                    throw new OrderImportConflictException();
                }
                throw integrity;
            }

            List<OrderItemId> createdItemIds = new ArrayList<>();
            for (OrderImportPosition position : batch.positions()) {
                totalLines += position.specificationLines().size();
                createdItemIds.add(postItemWithSpecification(orderId, position, now));
            }
            totalPositions += batch.positionCount();

            for (OrderItemId itemId : createdItemIds) {
                OrderItem item =
                        orderItemRepository
                                .findById(itemId)
                                .orElseThrow(
                                        () ->
                                                new OrderImportProcessingException(
                                                        new IllegalStateException(
                                                                "Imported order item missing: "
                                                                        + itemId)));
                orderItemRepository.save(item.activateDraftRevisionForImport(clock));
            }

            CustomerOrder order =
                    customerOrderRepository
                            .findById(orderId)
                            .orElseThrow(
                                    () ->
                                            new OrderImportProcessingException(
                                                    new IllegalStateException(
                                                            "Imported order missing: " + orderId)));
            customerOrderRepository.save(order.activateFromImport(clock));
            imported.add(
                    OrderImportConfirmResult.ImportedOrder.of(orderId, orderNumber.value()));
        }

        return OrderImportConfirmResult.of(imported, totalPositions, totalLines);
    }

    private void assertOrderNumbersAvailable(List<OrderImportBatch> batches) {
        for (OrderImportBatch batch : batches) {
            if (batch.orderNumber() != null && !batch.orderNumber().trim().isEmpty()) {
                OrderNumber orderNumber = OrderNumber.of(batch.orderNumber().trim());
                if (customerOrderRepository.existsByOrderNumber(orderNumber)) {
                    throw new OrderImportConflictException();
                }
            }
        }
    }

    private OrderId postOrderCreate(OrderNumber orderNumber, String customerName, Instant now) {
        UUID documentId = createDocument(DocumentTypeCode.ORDER_CREATE, "import-order-create");
        OrderCommercialData commercial =
                OrderCommercialData.of(null, customerName, null, null, null, null, null);
        draftPayloads.createDraft(
                OrderCreatePayload.create(
                        DocumentId.of(documentId), orderNumber, commercial, now));
        documentEngine.postDocument(documentId);
        return resolveCreatedOrderId(DocumentId.of(documentId));
    }

    private OrderItemId postItemWithSpecification(
            OrderId orderId, OrderImportPosition position, Instant now) {
        OrderItemId orderItemId = OrderItemId.generate();
        OrderedQuantity quantity = OrderedQuantity.of(position.quantity().longValue());
        ItemCommercialData commercial =
                ItemCommercialData.of(
                        ProductCode.of(position.productCode().trim()),
                        position.name().trim(),
                        null,
                        position.externalPositionNumber().trim());

        UUID createDocId = createDocument(DocumentTypeCode.ORDER_ITEM_CREATE, "import-item-create");
        draftPayloads.createDraft(
                OrderItemCreatePayload.create(
                        DocumentId.of(createDocId),
                        orderId,
                        orderItemId,
                        commercial,
                        quantity,
                        now));
        documentEngine.postDocument(createDocId);

        List<OrderItemRevisionPayloadLine> lines = toPayloadLines(position.specificationLines());
        UUID updateDocId =
                createDocument(DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE, "import-revision-update");
        draftPayloads.createDraft(
                OrderItemRevisionUpdatePayload.create(
                        DocumentId.of(updateDocId),
                        orderItemId,
                        RevisionNumber.first(),
                        quantity,
                        lines,
                        now));
        documentEngine.postDocument(updateDocId);
        return orderItemId;
    }

    private static List<OrderItemRevisionPayloadLine> toPayloadLines(
            List<OrderImportSpecificationLine> lines) {
        List<OrderItemRevisionPayloadLine> result = new ArrayList<>(lines.size());
        int lineNumber = 1;
        for (OrderImportSpecificationLine line : lines) {
            String unit =
                    line.unitOfMeasure() == null || line.unitOfMeasure().isBlank()
                            ? OrderImportDefaults.UNIT_OF_MEASURE
                            : line.unitOfMeasure().trim();
            result.add(
                    OrderItemRevisionPayloadLine.of(
                            lineNumber++,
                            line.materialCode().trim(),
                            line.materialName().trim(),
                            line.color(),
                            line.length(),
                            line.quantity(),
                            unit));
        }
        return result;
    }

    private UUID createDocument(DocumentTypeCode type, String title) {
        DocumentMetadata created =
                documentEngine.createDocument(new CreateDocumentCommand(type.name(), title));
        return created.id();
    }

    private OrderId resolveCreatedOrderId(DocumentId documentId) {
        ProcessingRecord record =
                processingRecords
                        .findByDocumentIdAndOperation(documentId, ProcessingOperation.POST)
                        .orElseThrow(
                                () ->
                                        new OrderImportProcessingException(
                                                new IllegalStateException(
                                                        "ORDER_CREATE processing record missing")));
        ResultReference result =
                record.resultReference()
                        .orElseThrow(
                                () ->
                                        new OrderImportProcessingException(
                                                new IllegalStateException(
                                                        "ORDER_CREATE result missing")));
        return OrderCreateDocumentProcessor.orderIdFrom(result);
    }

    private static RuntimeException mapUnexpected(RuntimeException ex) {
        if (ex instanceof DuplicateOrderNumberException) {
            return new OrderImportConflictException();
        }
        if (ex instanceof DataIntegrityViolationException integrity) {
            if (isOrderNumberUniqueViolation(integrity)) {
                return new OrderImportConflictException();
            }
        }
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof DuplicateOrderNumberException) {
                return new OrderImportConflictException();
            }
            cause = cause.getCause();
        }
        return new OrderImportProcessingException(ex);
    }

    private static boolean isOrderNumberUniqueViolation(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase();
        return message.contains("uk_orders_order_number") || message.contains("order_number");
    }
}
