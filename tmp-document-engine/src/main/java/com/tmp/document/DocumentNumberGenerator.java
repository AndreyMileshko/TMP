package com.tmp.document;

import java.util.UUID;

final class DocumentNumberGenerator {

    private DocumentNumberGenerator() {
    }

    static String nextNumber(String documentTypeId) {
        String typePrefix = documentTypeId.replace('.', '-').toUpperCase();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return typePrefix + "-" + uniqueSuffix;
    }
}
