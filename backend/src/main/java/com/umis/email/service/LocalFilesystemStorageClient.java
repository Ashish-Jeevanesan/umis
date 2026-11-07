package com.umis.email.service;

import com.umis.common.exception.MailboxIntegrationException;
import com.umis.config.UmisProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local-disk object storage client - a zero-dependency alternative to S3/MinIO for local
 * development, mirroring the same key layout (emails/..., attachments/...) as plain files
 * under umis.storage.local-base-path (UMIS_STORAGE_LOCAL_BASE_PATH).
 *
 * Writes are atomic: content is written to a temporary file in the same target directory (so
 * the final rename is a same-filesystem move, not a copy) and only moved into place once fully
 * written, so a crash or failed write mid-copy never leaves a partial file visible at the real
 * key. Reads stream directly from disk rather than buffering the whole file into memory, so
 * large attachments don't need to fit in the heap to be served.
 */
@Component
@ConditionalOnProperty(prefix = "umis.storage", name = "type", havingValue = "LOCAL", matchIfMissing = true)
public class LocalFilesystemStorageClient implements ObjectStorageClient {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemStorageClient.class);

    private final Path basePath;

    public LocalFilesystemStorageClient(UmisProperties properties) {
        this.basePath = Paths.get(properties.getStorage().getLocalBasePath()).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void ensureBaseDirectoryExists() {
        try {
            Files.createDirectories(basePath);
            log.info("Local filesystem object storage ready at: {}", basePath);
        } catch (IOException exception) {
            log.error("Could not create local storage directory {}: {}", basePath, exception.getMessage());
        }
    }

    @Override
    public void putObject(String key, InputStream content, long contentLength, String contentType) {
        Path target = resolveSafePath(key);
        Path parentDir = target.getParent();
        Path tempFile = null;
        try {
            Files.createDirectories(parentDir);
            tempFile = Files.createTempFile(parentDir, ".upload-", ".tmp");
            Files.copy(content, tempFile, StandardCopyOption.REPLACE_EXISTING);
            // Atomic within the same filesystem (guaranteed here, since tempFile and target share
            // the same parent directory) - the object at `key` either doesn't exist yet or is
            // fully written; it's never observable half-written.
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            tempFile = null;
        } catch (Exception exception) {
            log.error("Failed to write local storage object for key {}: {}", key, exception.getMessage());
            // Do not fail entire ingestion if local disk write fails, matching S3 backend behavior.
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupException) {
                    log.warn("Failed to clean up temp file {} after failed write: {}", tempFile, cleanupException.getMessage());
                }
            }
        }
    }

    @Override
    public DownloadedObject getObject(String key) {
        Path source = resolveSafePath(key);
        try {
            if (!Files.exists(source)) {
                throw new MailboxIntegrationException("Object not found in local storage: " + key);
            }
            String contentType = Files.probeContentType(source);
            long contentLength = Files.size(source);
            InputStream stream = Files.newInputStream(source);
            return new DownloadedObject(stream, contentType != null ? contentType : "application/octet-stream", contentLength);
        } catch (MailboxIntegrationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MailboxIntegrationException("Failed to retrieve file from local storage: " + key, exception);
        }
    }

    /**
     * Resolves a storage key to a path under the configured base directory, rejecting any key
     * that would escape it. Keys are always built internally from UUIDs/tenant IDs, never taken
     * verbatim from untrusted input, but this is a defense-in-depth guard against path traversal
     * regardless, and the only thing standing between a crafted key and the host filesystem.
     */
    private Path resolveSafePath(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        Path resolved;
        try {
            resolved = basePath.resolve(key).normalize();
        } catch (UncheckedIOException | SecurityException exception) {
            throw new IllegalArgumentException("Invalid storage key: " + key, exception);
        }
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return resolved;
    }
}
