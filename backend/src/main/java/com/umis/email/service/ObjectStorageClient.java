package com.umis.email.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Backend-agnostic object storage operations for email bodies and attachments.
 * Selected implementation (S3/MinIO or local filesystem) is chosen via umis.storage.type.
 */
public interface ObjectStorageClient {

    /**
     * Stores content from a stream without requiring the caller to buffer it into a byte[]
     * first. Implementations must write to a temporary location and atomically publish it at
     * the final key only on full success, so a failed or interrupted write never leaves a
     * partial object visible at that key.
     */
    void putObject(String key, InputStream content, long contentLength, String contentType);

    /**
     * Convenience overload for callers that already have the full content in memory.
     */
    default void putObject(String key, byte[] bytes, String contentType) {
        putObject(key, new ByteArrayInputStream(bytes), bytes.length, contentType);
    }

    DownloadedObject getObject(String key);

    record DownloadedObject(InputStream inputStream, String contentType, long contentLength) {}
}
