package com.tmp.order.application.imports.stxt;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Detects and decodes STXT bytes as Windows-1251, UTF-8 or UTF-8 with BOM (Specification §27.5).
 */
final class StxtEncodingDetector {

    static final String NAME_UTF8_BOM = "UTF-8 BOM";
    static final String NAME_UTF8 = "UTF-8";
    static final String NAME_WINDOWS_1251 = "Windows-1251";

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Charset WINDOWS_1251 = Charset.forName("Windows-1251");

    private StxtEncodingDetector() {}

    static Optional<DecodedText> decode(byte[] content) {
        Objects.requireNonNull(content, "content");
        if (content.length == 0) {
            return Optional.empty();
        }
        if (startsWithBom(content)) {
            byte[] withoutBom = Arrays.copyOfRange(content, UTF8_BOM.length, content.length);
            Optional<String> text = decodeStrict(withoutBom, StandardCharsets.UTF_8);
            return text.map(value -> new DecodedText(value, NAME_UTF8_BOM));
        }
        Optional<String> utf8 = decodeStrict(content, StandardCharsets.UTF_8);
        if (utf8.isPresent()) {
            return Optional.of(new DecodedText(utf8.get(), NAME_UTF8));
        }
        Optional<String> cp1251 = decodeStrict(content, WINDOWS_1251);
        return cp1251.map(value -> new DecodedText(value, NAME_WINDOWS_1251));
    }

    private static boolean startsWithBom(byte[] content) {
        if (content.length < UTF8_BOM.length) {
            return false;
        }
        return content[0] == UTF8_BOM[0]
                && content[1] == UTF8_BOM[1]
                && content[2] == UTF8_BOM[2];
    }

    private static Optional<String> decodeStrict(byte[] content, Charset charset) {
        CharsetDecoder decoder =
                charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return Optional.of(decoder.decode(ByteBuffer.wrap(content)).toString());
        } catch (CharacterCodingException ex) {
            return Optional.empty();
        }
    }

    record DecodedText(String text, String encodingName) {}
}
