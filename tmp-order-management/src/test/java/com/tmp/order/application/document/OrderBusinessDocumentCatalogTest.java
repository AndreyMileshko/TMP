package com.tmp.order.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderApprovePayload;
import com.tmp.order.application.payload.OrderCancelPayload;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderItemCancelPayload;
import com.tmp.order.application.payload.OrderItemCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionApprovePayload;
import com.tmp.order.application.payload.OrderItemRevisionCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionUpdatePayload;
import com.tmp.order.application.payload.OrderItemUpdatePayload;
import com.tmp.order.application.payload.OrderUpdatePayload;
import com.tmp.order.domain.PayloadSchemaVersion;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderBusinessDocumentCatalogTest {

    @Test
    void catalogContainsExactlyTenImmutableDescriptors() {
        List<OrderDocumentTypeDescriptor> all = OrderBusinessDocumentCatalog.all();
        assertEquals(10, all.size());
        assertThrows(UnsupportedOperationException.class, () -> all.add(all.getFirst()));

        Set<DocumentTypeCode> codes = new HashSet<>();
        for (OrderDocumentTypeDescriptor descriptor : all) {
            assertTrue(codes.add(descriptor.documentTypeCode()), "duplicate type code");
            assertTrue(descriptor.payloadClass() != null);
            assertTrue(descriptor.requiredCapability().startsWith("order."));
            assertEquals(PayloadSchemaVersion.initial(), descriptor.payloadSchemaVersion());
        }
        assertEquals(10, codes.size());
    }

    @Test
    void mappingsMatchSpecification() {
        assertEquals(
                OrderCreatePayload.class,
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_CREATE)
                        .payloadClass());
        assertEquals(
                "order.order.create",
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_CREATE)
                        .requiredCapability());
        assertEquals(
                OrderUpdatePayload.class,
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_UPDATE)
                        .payloadClass());
        assertEquals(
                "order.order.edit",
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_UPDATE)
                        .requiredCapability());
        assertEquals(
                OrderApprovePayload.class,
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_APPROVE)
                        .payloadClass());
        assertEquals(
                "order.order.approve",
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_APPROVE)
                        .requiredCapability());
        assertEquals(
                OrderCancelPayload.class,
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_CANCEL)
                        .payloadClass());
        assertEquals(
                "order.order.cancel",
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_CANCEL)
                        .requiredCapability());
        assertEquals(
                OrderItemCreatePayload.class,
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_ITEM_CREATE)
                        .payloadClass());
        assertEquals(
                "order.item.create",
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_ITEM_CREATE)
                        .requiredCapability());
        assertEquals(
                OrderItemUpdatePayload.class,
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_ITEM_UPDATE)
                        .payloadClass());
        assertEquals(
                "order.item.edit",
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_ITEM_UPDATE)
                        .requiredCapability());
        assertEquals(
                OrderItemCancelPayload.class,
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_ITEM_CANCEL)
                        .payloadClass());
        assertEquals(
                "order.item.cancel",
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_ITEM_CANCEL)
                        .requiredCapability());
        assertEquals(
                OrderItemRevisionCreatePayload.class,
                OrderBusinessDocumentCatalog.requireByCode(
                                DocumentTypeCode.ORDER_ITEM_REVISION_CREATE)
                        .payloadClass());
        assertEquals(
                "order.revision.create",
                OrderBusinessDocumentCatalog.requireByCode(
                                DocumentTypeCode.ORDER_ITEM_REVISION_CREATE)
                        .requiredCapability());
        assertEquals(
                OrderItemRevisionUpdatePayload.class,
                OrderBusinessDocumentCatalog.requireByCode(
                                DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE)
                        .payloadClass());
        assertEquals(
                "order.revision.edit",
                OrderBusinessDocumentCatalog.requireByCode(
                                DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE)
                        .requiredCapability());
        assertEquals(
                OrderItemRevisionApprovePayload.class,
                OrderBusinessDocumentCatalog.requireByCode(
                                DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE)
                        .payloadClass());
        assertEquals(
                "order.item.approve",
                OrderBusinessDocumentCatalog.requireByCode(
                                DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE)
                        .requiredCapability());
    }
}
