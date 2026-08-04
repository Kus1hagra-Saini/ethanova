package com.ethanova.backend.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standard error response envelope for all API errors.
 * Immutable record — no builder, no setters, no ambiguity.
 *
 * <p>Uses {@link OffsetDateTime} for the timestamp to stay consistent with
 * entity audit fields and other response DTOs across the API surface.
 *
 * <p>{@code fieldErrors} is omitted from JSON when null (only present
 * for validation failures) via {@link JsonInclude.Include#NON_NULL}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path, null);
    }

    public static ApiError withFieldErrors(
            int status, String error, String message, String path, List<FieldError> fieldErrors) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path, fieldErrors);
    }

    /**
     * Single field-level validation failure.
     */
    public record FieldError(String field, Object rejectedValue, String message) {}
}