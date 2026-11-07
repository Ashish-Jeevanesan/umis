package com.umis.common.exception;

/**
 * Thrown when an unauthenticated or unauthorized access occurs.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
