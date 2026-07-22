package com.tmp.document;

import java.util.UUID;

final class DocumentNumberGenerator {

    private static final int MAX_NUMBER_LENGTH = 64;
    private static final int SUFFIX_LENGTH = 8;

    private DocumentNumberGenerator() {
    }

    static String nextNumber(String documentTypeId) {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, SUFFIX_LENGTH).toUpperCase();
        String typePrefix = documentTypeId.replace('.', '-').toUpperCase();
        int maxPrefixLength = MAX_NUMBER_LENGTH - uniqueSuffix.length() - 1;
        if (typePrefix.length() > maxPrefixLength) {
            typePrefix = typePrefix.substring(0, maxPrefixLength);
        }
        return typePrefix + "-" + uniqueSuffix;
    }
}
