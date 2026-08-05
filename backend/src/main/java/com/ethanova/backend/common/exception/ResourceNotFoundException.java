package com.ethanova.backend.common.exception;

/**
 * Thrown by services when a requested entity does not exist.
 * Translated to HTTP 404 by {@link GlobalExceptionHandler}.
 *
 * <p>Extends {@link RuntimeException} so callers are not forced to declare
 * or catch it — the exception handler catches it at the web boundary.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException forResource(String resource, String identifier) {
        return new ResourceNotFoundException(
                "%s not found with identifier: %s".formatted(resource, identifier));
    }
}