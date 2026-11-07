package com.umis.common.exception;

/**
 * Thrown when client provides an invalid request payload or parameters.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
