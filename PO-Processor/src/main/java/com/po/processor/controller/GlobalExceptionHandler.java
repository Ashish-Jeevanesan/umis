package com.po.processor.controller;

import com.po.processor.exception.EventProcessingException;
import com.po.processor.exception.ResourceNotFoundException;
import com.po.processor.exception.StorageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Controller advice translating exceptions into standard JSON error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorageException(StorageException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "STORAGE_ERROR",
                "message", ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(EventProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleEventProcessing(EventProcessingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "EVENT_PROCESSING_ERROR",
                "message", ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_SERVER_ERROR",
                "message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
                "timestamp", Instant.now()
        ));
    }
}
