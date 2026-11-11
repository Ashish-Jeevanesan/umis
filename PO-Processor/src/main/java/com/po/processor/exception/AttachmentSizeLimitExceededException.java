package com.po.processor.exception;

/**
 * Exception thrown when an attachment exceeds individual or aggregate memory safety thresholds.
 */
public class AttachmentSizeLimitExceededException extends RuntimeException {

    private final long actualBytes;
    private final long maxAllowedBytes;

    public AttachmentSizeLimitExceededException(String strMessage, long actualBytes, long maxAllowedBytes) {
        super(strMessage);
        this.actualBytes = actualBytes;
        this.maxAllowedBytes = maxAllowedBytes;
    }

    public long getActualBytes() {
        return actualBytes;
    }

    public long getMaxAllowedBytes() {
        return maxAllowedBytes;
    }
}
