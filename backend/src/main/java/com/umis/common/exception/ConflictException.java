package com.umis.common.exception;

/**
 * Thrown when a unique constraint or conflicting state violation occurs.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
