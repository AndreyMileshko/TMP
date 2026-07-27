package com.tmp.ui.shell.order.error;

/** UI operation context for mapping domain/document failures to user messages. */
public enum OrderUiOperation {
    LOAD,
    SAVE_DRAFT,
    POST_DOCUMENT,
    CREATE,
    UPDATE,
    APPROVE,
    CANCEL,
    DELETE,
    UNPOST,
    VALIDATE
}
