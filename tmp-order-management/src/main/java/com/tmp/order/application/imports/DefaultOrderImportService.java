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
import com.tmp.order.api.imports.OrderImportDuplicateException;
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
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default Import Core: source-neutral preview without persistence and atomic confirm through
 * Document Engine business documents plus capability-owned metadata.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class DefaultOrderImportService implements OrderImportService {

    private final OrderImportValidator validator;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderImportMetadataRepository importMetadataRepository;
    private final DocumentEngine documentEngine;
    private final DraftPayloadApplicationService draftPayloads;
    private final ProcessingRecordPort processingRecords;
    private final AuthenticationService authenticationService;
    private final AuthorizationService authorizationService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DefaultOrderImportService(
            OrderImportValidator validator,
            CustomerOrderRepository customerOrderRepository,
            OrderImportMetadataRepository importMetadataRepository,
            DocumentEngine documentEngine,
            DraftPayloadApplicationService draftPayloads,
            ProcessingRecordPort processingRecords,
            AuthenticationService authenticationService,
            AuthorizationService authorizationService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.importMetadataRepository =
                Objects.requireNonNull(importMetadataRepository, "importMetadataRepository");
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.draftPayloads = Objects.requireNonNull(draftPayloads, "draftPayloads");
        this.processingRecords = Objects.requireNonNull(processingRecords, "processingRecords");
        this.authenticationService =
                Objects.requireNonNull(authenticationService, "authenticationService");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        Objects.requireNonNull(transactionManager, "transactionManager");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OrderImportPreview preview(OrderImportBatch batch) {
        Objects.requireNonNull(batch, "batch");
        authorizationService.requirePermission(OrderManagementPermissions.ORDER_CREATE);

        List<OrderImportProblem> structural = validator.validate(batch);
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
            assertNoDuplicateOrConflict(batch);
        }

        PreparedOrderImportPlan plan =
                errors.isEmpty() ? new DefaultPreparedOrderImportPlan(batch) : null;

        return OrderImportPreview.of(
                batch.sourceReference(),
                batch.orderNumber(),
                batch.positionCount(),
                batch.totalProductQuantity(),
                batch.specificationLineCount(),
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

        OrderImportBatch batch = plan.batch();
        List<OrderImportProblem> structural = validator.validate(batch);
        List<OrderImportProblem> errors =
                structural.stream()
                        .filter(problem -> problem.severity() == OrderImportProblemSeverity.ERROR)
                        .toList();
        if (!errors.isEmpty()) {
            throw new OrderImportValidationException(errors);
        }

        UserId importedBy = requireCurrentUserId();
        Instant importedAt = clock.instant();

        try {
            return transactionTemplate.execute(
                    status -> executeConfirm(batch, importedBy, importedAt));
        } catch (OrderImportDuplicateException
                | OrderImportConflictException
                | OrderImportValidationException controlled) {
            throw controlled;
        } catch (RuntimeException ex) {
            throw mapUnexpected(ex);
        }
    }

    private OrderImportConfirmResult executeConfirm(
            OrderImportBatch batch, UserId importedBy, Instant importedAt) {
        assertNoDuplicateOrConflict(batch);

        OrderNumber orderNumber = OrderNumber.of(batch.orderNumber().trim());
        OrderId orderId;
        try {
            orderId = postOrderCreate(orderNumber, importedAt);
        } catch (DuplicateOrderNumberException duplicate) {
            throw new OrderImportConflictException();
        } catch (DataIntegrityViolationException integrity) {
            if (isOrderNumberUniqueViolation(integrity)) {
                throw new OrderImportConflictException();
            }
            throw integrity;
        }

        int lineCount = 0;
        for (OrderImportPosition position : batch.positions()) {
            lineCount += position.specificationLines().size();
            postItemWithSpecification(orderId, position, importedAt);
        }

        OrderImportMetadata metadata;
        try {
            metadata =
                    importMetadataRepository.save(
                            OrderImportMetadata.of(
                                    UUID.randomUUID(),
                                    batch.sourceType().trim(),
                                    batch.sourceReference().trim(),
                                    batch.contentChecksum().trim(),
                                    importedAt,
                                    importedBy,
                                    orderId));
        } catch (DuplicateKeyException duplicate) {
            throw new OrderImportDuplicateException();
        } catch (DataIntegrityViolationException integrity) {
            if (isImportChecksumUniqueViolation(integrity)) {
                throw new OrderImportDuplicateException();
            }
            if (isOrderNumberUniqueViolation(integrity)) {
                throw new OrderImportConflictException();
            }
            throw integrity;
        }

        return OrderImportConfirmResult.of(
                orderId,
                orderNumber.value(),
                metadata.importId(),
                batch.positionCount(),
                lineCount,
                importedAt);
    }

    private void assertNoDuplicateOrConflict(OrderImportBatch batch) {
        String sourceType = batch.sourceType() == null ? null : batch.sourceType().trim();
        String checksum = batch.contentChecksum() == null ? null : batch.contentChecksum().trim();
        if (sourceType != null
                && !sourceType.isEmpty()
                && checksum != null
                && !checksum.isEmpty()
                && importMetadataRepository.existsBySourceTypeAndChecksum(sourceType, checksum)) {
            throw new OrderImportDuplicateException();
        }
        if (batch.orderNumber() != null && !batch.orderNumber().trim().isEmpty()) {
            OrderNumber orderNumber = OrderNumber.of(batch.orderNumber().trim());
            if (customerOrderRepository.existsByOrderNumber(orderNumber)) {
                throw new OrderImportConflictException();
            }
        }
    }

    private OrderId postOrderCreate(OrderNumber orderNumber, Instant now) {
        UUID documentId = createDocument(DocumentTypeCode.ORDER_CREATE, "import-order-create");
        OrderCommercialData commercial =
                OrderCommercialData.of(null, null, null, null, null, null, null);
        draftPayloads.createDraft(
                OrderCreatePayload.create(
                        DocumentId.of(documentId), orderNumber, commercial, now));
        documentEngine.postDocument(documentId);
        return resolveCreatedOrderId(DocumentId.of(documentId));
    }

    private void postItemWithSpecification(
            OrderId orderId, OrderImportPosition position, Instant now) {
        OrderItemId orderItemId = OrderItemId.generate();
        OrderedQuantity quantity = OrderedQuantity.of(position.productQuantity().longValue());
        ItemCommercialData commercial =
                ItemCommercialData.of(
                        null, null, null, position.externalPositionNumber().trim());

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
    }

    private static List<OrderItemRevisionPayloadLine> toPayloadLines(
            List<OrderImportSpecificationLine> lines) {
        List<OrderItemRevisionPayloadLine> result = new ArrayList<>(lines.size());
        int lineNumber = 1;
        for (OrderImportSpecificationLine line : lines) {
            result.add(
                    OrderItemRevisionPayloadLine.of(
                            lineNumber++,
                            line.materialCode().trim(),
                            line.materialName().trim(),
                            line.color(),
                            line.lengthMm(),
                            line.lineQuantity(),
                            OrderImportDefaults.UNIT_OF_MEASURE));
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

    private UserId requireCurrentUserId() {
        SessionSummary session =
                authenticationService
                        .currentSession()
                        .orElseThrow(
                                () ->
                                        new OrderImportProcessingException(
                                                new IllegalStateException(
                                                        "Authenticated session required for import")));
        return session.userId();
    }

    private static RuntimeException mapUnexpected(RuntimeException ex) {
        if (ex instanceof DuplicateOrderNumberException) {
            return new OrderImportConflictException();
        }
        if (ex instanceof DuplicateKeyException) {
            return new OrderImportDuplicateException();
        }
        if (ex instanceof DataIntegrityViolationException integrity) {
            if (isImportChecksumUniqueViolation(integrity)) {
                return new OrderImportDuplicateException();
            }
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

    private static boolean isImportChecksumUniqueViolation(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase();
        return message.contains("uk_order_import_metadata_source_checksum")
                || message.contains("content_checksum");
    }
}
