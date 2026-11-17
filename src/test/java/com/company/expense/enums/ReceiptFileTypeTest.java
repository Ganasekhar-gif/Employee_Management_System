package com.company.expense.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReceiptFileTypeTest {

    @Test
    void detectsTypesFromLeadingBytes() {
        assertThat(ReceiptFileType.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                .contains(ReceiptFileType.PDF);
        assertThat(ReceiptFileType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}))
                .contains(ReceiptFileType.PNG);
        assertThat(ReceiptFileType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}))
                .contains(ReceiptFileType.JPEG);
    }

    @Test
    void rejectsUnknownOrTruncatedContent() {
        assertThat(ReceiptFileType.detect("<html>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ReceiptFileType.detect(new byte[] {'%', 'P'})).isEmpty();
        assertThat(ReceiptFileType.detect(new byte[0])).isEmpty();
    }
}
