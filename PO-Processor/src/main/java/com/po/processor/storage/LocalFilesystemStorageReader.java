package com.po.processor.storage;

import com.po.processor.exception.StorageException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Local filesystem implementation of StorageReader accessing UMIS storage files.
 */
@Component
@ConditionalOnProperty(prefix = "po.storage", name = "type", havingValue = "LOCAL", matchIfMissing = true)
public class LocalFilesystemStorageReader implements StorageReader {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemStorageReader.class);

    private final Path basePath;

    public LocalFilesystemStorageReader(@Value("${po.storage.local-base-path:./storage}") String storageBasePath) {
        this.basePath = Paths.get(storageBasePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        log.info("PO-Processor LocalFilesystemStorageReader initialized with base path: {}", basePath);
        if (!Files.exists(basePath)) {
            log.warn("Storage base directory does not exist yet: {}. Will be accessed when available.", basePath);
        }
    }

    @Override
    public boolean exists(String storageReference) {
        if (storageReference == null || storageReference.isBlank()) {
            return false;
        }
        try {
            Path targetPath = resolveSafePath(storageReference);
            return Files.exists(targetPath) && Files.isRegularFile(targetPath);
        } catch (IllegalArgumentException | StorageException ex) {
            log.debug("Path rejected or inaccessible: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public StorageObject getObject(String storageReference) {
        Path targetPath = resolveSafePath(storageReference);
        try {
            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
                throw new StorageException("Storage object not found: " + storageReference);
            }
            String contentType = Files.probeContentType(targetPath);
            long contentLength = Files.size(targetPath);
            InputStream stream = Files.newInputStream(targetPath);
            return new StorageObject(
                    stream,
                    contentType != null ? contentType : "application/octet-stream",
                    contentLength
            );
        } catch (IOException ioException) {
            throw new StorageException("Failed to open storage object stream: " + storageReference, ioException);
        }
    }

    @Override
    public String readText(String storageReference) {
        Path targetPath = resolveSafePath(storageReference);
        try {
            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
                throw new StorageException("Text object not found: " + storageReference);
            }
            byte[] bytes = Files.readAllBytes(targetPath);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            throw new StorageException("Failed to read text from storage: " + storageReference, ioException);
        }
    }

    /**
     * Resolves a relative storage key against the base path while strictly preventing path traversal.
     */
    public Path resolveSafePath(String storageReference) {
        if (storageReference == null || storageReference.isBlank()) {
            throw new StorageException("Storage reference must not be blank");
        }

        // Normalize separators
        String normalizedKey = storageReference.replace('\\', '/').trim();
        while (normalizedKey.startsWith("/")) {
            normalizedKey = normalizedKey.substring(1);
        }

        Path resolvedPath = basePath.resolve(normalizedKey).normalize();

        if (!resolvedPath.startsWith(basePath)) {
            log.error("Path traversal detected: input '{}' resolved to '{}' outside base '{}'",
                    storageReference, resolvedPath, basePath);
            throw new StorageException("Access denied: path traversal attempt detected for key: " + storageReference);
        }

        return resolvedPath;
    }

    public Path getBasePath() {
        return basePath;
    }
}
