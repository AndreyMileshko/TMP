-- STAGE5-058 FIX: register ORDER_ACTIVATE in typed payload / processing check constraints.
-- Required for Document Engine approve flow: ORDER_APPROVE → ORDER_ACTIVATE → ACTIVE.

ALTER TABLE order_management.order_document_payload
    DROP CONSTRAINT chk_order_document_payload_type;

ALTER TABLE order_management.order_document_payload
    ADD CONSTRAINT chk_order_document_payload_type CHECK (document_type_code IN (
        'ORDER_CREATE',
        'ORDER_UPDATE',
        'ORDER_APPROVE',
        'ORDER_ACTIVATE',
        'ORDER_CANCEL',
        'ORDER_ITEM_CREATE',
        'ORDER_ITEM_UPDATE',
        'ORDER_ITEM_CANCEL',
        'ORDER_ITEM_REVISION_CREATE',
        'ORDER_ITEM_REVISION_UPDATE',
        'ORDER_ITEM_REVISION_APPROVE'
    ));

ALTER TABLE order_management.order_document_processing
    DROP CONSTRAINT chk_order_document_processing_type;

ALTER TABLE order_management.order_document_processing
    ADD CONSTRAINT chk_order_document_processing_type CHECK (document_type_code IN (
        'ORDER_CREATE',
        'ORDER_UPDATE',
        'ORDER_APPROVE',
        'ORDER_ACTIVATE',
        'ORDER_CANCEL',
        'ORDER_ITEM_CREATE',
        'ORDER_ITEM_UPDATE',
        'ORDER_ITEM_CANCEL',
        'ORDER_ITEM_REVISION_CREATE',
        'ORDER_ITEM_REVISION_UPDATE',
        'ORDER_ITEM_REVISION_APPROVE'
    ));
