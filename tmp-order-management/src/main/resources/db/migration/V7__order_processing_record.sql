-- Stage 5 Order Management: document processing record (Specification §16).
CREATE TABLE order_management.order_document_processing (
    document_id UUID NOT NULL,
    document_type_code VARCHAR(64) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    payload_revision BIGINT NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result_reference VARCHAR(255),
    CONSTRAINT pk_order_document_processing PRIMARY KEY (document_id, operation),
    CONSTRAINT chk_order_document_processing_operation CHECK (operation IN ('POST')),
    CONSTRAINT chk_order_document_processing_status CHECK (processing_status IN ('COMPLETED')),
    CONSTRAINT chk_order_document_processing_type CHECK (document_type_code IN (
        'ORDER_CREATE',
        'ORDER_UPDATE',
        'ORDER_APPROVE',
        'ORDER_CANCEL',
        'ORDER_ITEM_CREATE',
        'ORDER_ITEM_UPDATE',
        'ORDER_ITEM_CANCEL',
        'ORDER_ITEM_REVISION_CREATE',
        'ORDER_ITEM_REVISION_UPDATE',
        'ORDER_ITEM_REVISION_APPROVE'
    )),
    CONSTRAINT chk_order_document_processing_revision CHECK (payload_revision >= 0)
);
