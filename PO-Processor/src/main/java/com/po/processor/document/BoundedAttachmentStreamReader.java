package com.po.processor.document;

import com.po.processor.exception.AttachmentSizeLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Bounded stream reader enforcing per-attachment and aggregate memory safety limits.
 */
@Component
public class BoundedAttachmentStreamReader {

    private static final int BUFFER_SIZE = 8192;

    private final long maxFileSizeBytes;
    private final long maxTotalJobBytes;

    public BoundedAttachmentStreamReader(
            @Value("${po.attachment.max-file-size-bytes:26214400}") long maxFileSizeBytes,
            @Value("${po.attachment.max-total-size-bytes:104857600}") long maxTotalJobBytes
    ) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxTotalJobBytes = maxTotalJobBytes;
    }

    /**
     * Value record holding bounded byte content, computed SHA-256 hash, and actual byte count.
     */
    public record BoundedReadResult(
            byte[] byteData,
            String sha256Hex,
            long bytesRead
    ) {}

    /**
     * Reads an input stream enforcing individual file and cumulative job size limits.
     * Computes the SHA-256 hash in a single streaming pass without double-buffering.
     *
     * @param inputStream Source input stream
     * @param declaredSize Declared size from metadata (-1 if unknown)
     * @param cumulativeJobBytes Bytes already read for other attachments in this job
     * @return BoundedReadResult
     * @throws AttachmentSizeLimitExceededException if size limits are violated
     * @throws IOException on I/O error
     */
    public BoundedReadResult readBounded(
            InputStream inputStream,
            long declaredSize,
            long cumulativeJobBytes
    ) throws IOException {
        if (declaredSize > maxFileSizeBytes) {
            throw new AttachmentSizeLimitExceededException(
                    "Declared attachment size (" + declaredSize + " bytes) exceeds maximum limit of "
                            + maxFileSizeBytes + " bytes",
                    declaredSize, maxFileSizeBytes
            );
        }

        if (declaredSize > 0 && (cumulativeJobBytes + declaredSize) > maxTotalJobBytes) {
            throw new AttachmentSizeLimitExceededException(
                    "Total job attachments would exceed cumulative limit of " + maxTotalJobBytes + " bytes",
                    cumulativeJobBytes + declaredSize, maxTotalJobBytes
            );
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", exception);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        long totalBytesRead = 0;
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            totalBytesRead += bytesRead;

            if (totalBytesRead > maxFileSizeBytes) {
                throw new AttachmentSizeLimitExceededException(
                        "Attachment stream exceeded maximum allowed limit of " + maxFileSizeBytes + " bytes",
                        totalBytesRead, maxFileSizeBytes
                );
            }

            if ((cumulativeJobBytes + totalBytesRead) > maxTotalJobBytes) {
                throw new AttachmentSizeLimitExceededException(
                        "Cumulative job attachments exceeded limit of " + maxTotalJobBytes + " bytes",
                        cumulativeJobBytes + totalBytesRead, maxTotalJobBytes
                );
            }

            baos.write(buffer, 0, bytesRead);
            digest.update(buffer, 0, bytesRead);
        }

        String strHash = HexFormat.of().formatHex(digest.digest());
        return new BoundedReadResult(baos.toByteArray(), strHash, totalBytesRead);
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public long getMaxTotalJobBytes() {
        return maxTotalJobBytes;
    }
}
