package com.tmp.order.application.imports.stxt;

import com.tmp.order.api.imports.OrderImportProblem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * STXT file adapter (Final STXT Contract / ADR-029).
 *
 * <p>Reads SuperOkna export bytes, detects encoding, parses ORDER/ITEM/SPECIFICATION blocks, and
 * builds source-neutral {@link com.tmp.order.api.imports.OrderImportBatch} list (one batch per
 * order). Does not write to the database, create orders, post documents, call Confirm, or use
 * JavaFX / Firebird. Checksum is ephemeral only (not persisted).
 */
public final class StxtFileAdapter {

    public static final String SOURCE_TYPE = StxtBlockParser.SOURCE_TYPE;

    /**
     * Parses STXT file bytes.
     *
     * @param content raw file bytes (may include UTF-8 BOM)
     * @param sourceReference non-absolute reference for Import Core (typically file name only)
     */
    public StxtParseResult parse(byte[] content, String sourceReference) {
        Objects.requireNonNull(content, "content");
        String reference =
                sourceReference == null || sourceReference.isBlank()
                        ? "stxt"
                        : sourceReference.trim();

        if (content.length == 0) {
            return StxtParseResult.of(
                    List.of(),
                    List.of(
                            OrderImportProblem.error(
                                    StxtBlockParser.CODE_FILE_EMPTY,
                                    "file",
                                    null,
                                    null,
                                    null,
                                    null,
                                    "Файл выгрузки пуст.")),
                    List.of(),
                    null);
        }

        var decoded = StxtEncodingDetector.decode(content);
        if (decoded.isEmpty()) {
            return StxtParseResult.of(
                    List.of(),
                    List.of(
                            OrderImportProblem.error(
                                    StxtBlockParser.CODE_ENCODING,
                                    "file",
                                    null,
                                    null,
                                    "encoding",
                                    null,
                                    "Не удалось определить кодировку файла. "
                                            + "Поддерживаются Windows-1251, UTF-8 и UTF-8 с BOM.")),
                    List.of(),
                    null);
        }

        String checksum = sha256Hex(content);
        StxtBlockParser.ParsedFile parsed =
                StxtBlockParser.parse(decoded.get().text(), reference, checksum);
        return StxtParseResult.of(
                parsed.batches(),
                parsed.errors(),
                parsed.warnings(),
                decoded.get().encodingName());
    }

    /**
     * Reads a file from disk and parses it. {@code sourceReference} is the file name only (not the
     * absolute path).
     */
    public StxtParseResult parseFile(Path file) {
        Objects.requireNonNull(file, "file");
        Path namePath = file.getFileName();
        String fileName = namePath == null ? "stxt" : namePath.toString();
        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (IOException ex) {
            return StxtParseResult.of(
                    List.of(),
                    List.of(
                            OrderImportProblem.error(
                                    StxtBlockParser.CODE_ENCODING,
                                    "file",
                                    null,
                                    null,
                                    null,
                                    fileName,
                                    "Не удалось прочитать файл выгрузки.")),
                    List.of(),
                    null);
        }
        return parse(content, fileName);
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
