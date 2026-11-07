package com.umis.email.service;

import com.umis.common.exception.BadRequestException;
import com.umis.config.UmisProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Service managing object storage operations for email bodies and attachments, backed by
 * whichever ObjectStorageClient is active (S3/MinIO or local filesystem - see umis.storage.type).
 */
@Service
public class EmailStorageService {

    private final ObjectStorageClient storageClient;
    private final UmisProperties properties;

    public EmailStorageService(ObjectStorageClient storageClient, UmisProperties properties) {
        this.storageClient = storageClient;
        this.properties = properties;
    }

    public String storeEmailBody(UUID tenantId, UUID mailboxId, UUID emailId, String content, String extension) {
        if (content == null || content.isBlank()) {
            return null;
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String key = String.format("emails/%s/%s/%s/body.%s", tenantId, mailboxId, emailId, extension);
        String contentType = extension.equalsIgnoreCase("html") ? "text/html; charset=UTF-8" : "text/plain; charset=UTF-8";
        storageClient.putObject(key, bytes, contentType);
        return key;
    }

    public String storeAttachment(UUID tenantId, UUID emailId, UUID attachmentId, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        long maxBytes = properties.getStorage().getMaxFileSizeBytes();
        if (bytes.length > maxBytes) {
            throw new BadRequestException("Attachment exceeds maximum allowed size of " + (maxBytes / 1024 / 1024) + "MB");
        }
        // The storage key never incorporates the original file name - even sanitized, a
        // caller-supplied name is an unnecessary part of the storage key/path surface. The
        // original name is preserved purely as metadata (EmailAttachment.fileName in the DB)
        // and reattached only when serving the download's Content-Disposition header.
        String key = String.format("attachments/%s/%s/%s/content", tenantId, emailId, attachmentId);
        storageClient.putObject(key, bytes, contentType != null ? contentType : "application/octet-stream");
        return key;
    }

    public StorageDownload getObject(String storageReference) {
        ObjectStorageClient.DownloadedObject object = storageClient.getObject(storageReference);
        return new StorageDownload(object.inputStream(), object.contentType(), object.contentLength());
    }

    public record StorageDownload(InputStream inputStream, String contentType, long contentLength) {}
}
