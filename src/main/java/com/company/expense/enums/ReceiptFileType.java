package com.company.expense.enums;

import java.util.Arrays;
import java.util.Optional;

public enum ReceiptFileType {
    PDF("application/pdf", ".pdf", new byte[] {'%', 'P', 'D', 'F'}),
    PNG("image/png", ".png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}),
    JPEG("image/jpeg", ".jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    public static final int SIGNATURE_LENGTH = 8;

    private final String mediaType;
    private final String extension;
    private final byte[] signature;

    ReceiptFileType(String mediaType, String extension, byte[] signature) {
        this.mediaType = mediaType;
        this.extension = extension;
        this.signature = signature;
    }

    // Detected from the file's leading bytes, because the client-supplied content type can be anything.
    public static Optional<ReceiptFileType> detect(byte[] header) {
        return Arrays.stream(values())
                .filter(type -> type.matches(header))
                .findFirst();
    }

    private boolean matches(byte[] header) {
        if (header.length < signature.length) {
            return false;
        }
        return Arrays.equals(signature, Arrays.copyOf(header, signature.length));
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }
}
