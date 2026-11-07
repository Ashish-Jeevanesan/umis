package com.umis.common.api;

import com.umis.common.exception.BadRequestException;
import com.umis.common.exception.ConflictException;
import com.umis.common.exception.MailboxIntegrationException;
import com.umis.common.exception.ResourceNotFoundException;
import com.umis.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralized exception handler providing consistent RFC-friendly API responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException exception) {
        log.warn("Resource not found: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(exception.getMessage(), "RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException exception) {
        log.warn("Unauthorized access: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(exception.getMessage(), "ACCESS_DENIED"));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException exception) {
        log.warn("Conflict detected: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(exception.getMessage(), "CONFLICT"));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException exception) {
        log.warn("Bad request: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(exception.getMessage(), "BAD_REQUEST"));
    }

    @ExceptionHandler(MailboxIntegrationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMailboxError(MailboxIntegrationException exception) {
        log.error("Mailbox integration failure: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(exception.getMessage(), "MAILBOX_PROVIDER_ERROR"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String validationErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation error: {}", validationErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(validationErrors, "VALIDATION_FAILED"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception exception) {
        log.error("Unhandled server exception: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected internal error occurred", "INTERNAL_ERROR"));
    }
}
