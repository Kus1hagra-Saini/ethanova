package com.ethanova.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centralised exception handling for all REST controllers.
 *
 * <p>Every unhandled exception thrown from a controller or downstream
 * service is caught here and translated into a consistent {@link ApiError}
 * response. Controllers therefore never contain try/catch blocks for
 * expected error paths.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 404 — Domain resource not found.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        ApiError body = ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * 400 — Bean-validation failure on request body (@Valid).
     * Aggregates every field error into the response.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(
                        fe.getField(),
                        fe.getRejectedValue(),
                        fe.getDefaultMessage()))
                .toList();

        ApiError body = ApiError.withFieldErrors(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for one or more fields",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }
    /**
     * 400 — Request body could not be read or parsed (typically malformed JSON).
     * Common cause: syntax errors, missing quotes, or a body that doesn't match
     * the expected content type. Distinct from field-level validation failures,
     * which are handled by {@link #handleValidation}.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        // Root cause message is more useful than the wrapper's, but be defensive.
        Throwable root = ex.getMostSpecificCause();
        String detail = (root != null && root.getMessage() != null)
                ? root.getMessage()
                : "Request body is not readable";

        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Malformed request body: " + detail,
                request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }
    /**
     * 409 — Database integrity violation (FK constraint, unique constraint).
     * Common when deleting a master-data row that has child references.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());

        ApiError body = ApiError.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "Operation violates data integrity constraints (likely a foreign-key or unique constraint)",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * 500 — Catch-all for unexpected exceptions.
     * Message is sanitised: the raw exception text is logged, never returned.
     */
    /**
     * Handles {@link org.springframework.web.server.ResponseStatusException}
     * thrown by services to signal a specific HTTP status with a domain message
     * (e.g. invalid status transition → 409, integrity rule failure → 400).
     *
     * <p>Placed above the generic {@link Exception} handler because
     * {@code @RestControllerAdvice} handlers are matched most-specific-first
     * only when each candidate is declared. Without this method, the generic
     * handler swallows {@code ResponseStatusException} and returns 500,
     * discarding the intended status.
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            org.springframework.web.server.ResponseStatusException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        // ex.getReason() is the message the service passed in; fall back to the
        // status's reason phrase (e.g. "Bad Request") if none was supplied.
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();

        ApiError body = ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);

        ApiError body = ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred. Please contact support if the problem persists.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}