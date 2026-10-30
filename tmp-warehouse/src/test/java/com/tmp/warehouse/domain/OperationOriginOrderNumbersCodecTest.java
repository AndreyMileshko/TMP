package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class OperationOriginOrderNumbersCodecTest {

    @Test
    void roundTripSingleAndMultipleOrderNumbers() {
        assertEquals(
                List.of("26106082"),
                OperationOriginOrderNumbersCodec.decode(
                        OperationOriginOrderNumbersCodec.encode(List.of("26106082"))));
        assertEquals(
                List.of("26106082", "26106083"),
                OperationOriginOrderNumbersCodec.decode(
                        OperationOriginOrderNumbersCodec.encode(
                                List.of("26106082", "26106083"))));
    }

    @Test
    void escapesQuotesAndBackslashes() {
        List<String> original = List.of("A\"B", "C\\D");
        assertEquals(
                original,
                OperationOriginOrderNumbersCodec.decode(
                        OperationOriginOrderNumbersCodec.encode(original)));
    }
}
