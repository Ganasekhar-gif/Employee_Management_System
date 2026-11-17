package com.company.expense.service.impl;

import com.company.expense.config.ReceiptStorageProperties;
import com.company.expense.service.ReceiptStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class LocalReceiptStorage implements ReceiptStorage {

    private final Path rootDirectory;

    public LocalReceiptStorage(ReceiptStorageProperties properties) {
        this.rootDirectory = properties.storageDirectory().toAbsolutePath().normalize();
    }

    // The client's file name never becomes part of the path, so it cannot escape the root directory.
    @Override
    public String store(Long expenseId, String extension, InputStream content) {
        String storagePath = expenseId + "/" + UUID.randomUUID() + extension;
        Path target = resolve(storagePath);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
            return storagePath;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not store receipt", exception);
        }
    }

    @Override
    public Resource load(String storagePath) {
        return new PathResource(resolve(storagePath));
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not delete receipt", exception);
        }
    }

    private Path resolve(String storagePath) {
        Path path = rootDirectory.resolve(storagePath).normalize();
        if (!path.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("Storage path is outside the receipt directory");
        }
        return path;
    }
}
