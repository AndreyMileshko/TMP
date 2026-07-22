package com.tmp.document.support;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigurableDocumentProcessor implements DocumentProcessor {

    public enum FailurePoint {
        VALIDATE_CREATE,
        VALIDATE_UPDATE,
        ON_POST,
        ON_UNPOST,
        ON_CLOSE,
        ON_DELETE
    }

    private final String typeId;
    private final AtomicReference<FailurePoint> failurePoint = new AtomicReference<>();

    public ConfigurableDocumentProcessor(String typeId) {
        this.typeId = typeId;
    }

    public void failOn(FailurePoint point) {
        failurePoint.set(point);
    }

    public void clearFailures() {
        failurePoint.set(null);
    }

    @Override
    public String documentTypeId() {
        return typeId;
    }

    @Override
    public void validateCreate(DocumentOperationContext context) {
        throwIf(FailurePoint.VALIDATE_CREATE);
    }

    @Override
    public void validateUpdate(DocumentOperationContext context) {
        throwIf(FailurePoint.VALIDATE_UPDATE);
    }

    @Override
    public void onPost(DocumentOperationContext context) {
        throwIf(FailurePoint.ON_POST);
    }

    @Override
    public void onUnpost(DocumentOperationContext context) {
        throwIf(FailurePoint.ON_UNPOST);
    }

    @Override
    public void onClose(DocumentOperationContext context) {
        throwIf(FailurePoint.ON_CLOSE);
    }

    @Override
    public void onDelete(DocumentOperationContext context) {
        throwIf(FailurePoint.ON_DELETE);
    }

    private void throwIf(FailurePoint point) {
        if (failurePoint.get() == point) {
            throw new IllegalStateException("Simulated processor failure at " + point);
        }
    }
}
