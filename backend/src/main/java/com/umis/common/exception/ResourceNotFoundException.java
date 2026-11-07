package com.umis.common.exception;

/**
 * Thrown when a requested entity does not exist.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
