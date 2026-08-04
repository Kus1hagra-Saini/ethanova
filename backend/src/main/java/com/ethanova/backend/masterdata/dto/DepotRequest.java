package com.ethanova.backend.masterdata.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inbound payload for creating or updating a {@link com.ethanova.backend.masterdata.Depot}.
 *
 * <p>On POST, all non-blank / non-null fields must be supplied.
 * On PUT, the {@code depotCode} in the URL path is authoritative — any value
 * present in the body is ignored by the service.
 *
 * <p>Nullable fields ({@code reorderThresholdKl}, {@code active}) are validated
 * only when present; nulls indicate "no value provided".
 *
 * <p>Cross-field rule ({@code reorderThresholdKl <= storageCapacityKl}) is
 * intentionally not enforced here — deferred to a future business-rules
 * layer to keep validation declarative and single-field for Review 1.
 */
public record DepotRequest(

        @NotBlank(message = "depotCode is required")
        @Size(max = 50, message = "depotCode must be at most 50 characters")
        @Pattern(
                regexp = "^[A-Z0-9_-]+$",
                message = "depotCode must contain only uppercase letters, digits, hyphens, and underscores"
        )
        String depotCode,

        @NotBlank(message = "depotName is required")
        @Size(max = 200, message = "depotName must be at most 200 characters")
        String depotName,

        @NotBlank(message = "omcCode is required")
        @Size(max = 20, message = "omcCode must be at most 20 characters")
        @Pattern(
                regexp = "^[A-Z]{2,20}$",
                message = "omcCode must be 2-20 uppercase letters (e.g. IOCL, BPCL, HPCL)"
        )
        String omcCode,

        @NotBlank(message = "stateCode is required")
        @Size(max = 10, message = "stateCode must be at most 10 characters")
        @Pattern(
                regexp = "^[A-Z]{2,10}$",
                message = "stateCode must be 2-10 uppercase letters"
        )
        String stateCode,

        @NotNull(message = "storageCapacityKl is required")
        @DecimalMin(value = "0.001", message = "storageCapacityKl must be greater than zero")
        @DecimalMax(value = "1000000.000", message = "storageCapacityKl must be at most 1,000,000 KL")
        BigDecimal storageCapacityKl,

        @DecimalMin(value = "0.000", message = "reorderThresholdKl must be non-negative")
        @DecimalMax(value = "1000000.000", message = "reorderThresholdKl must be at most 1,000,000 KL")
        BigDecimal reorderThresholdKl,

        Boolean active
) {
}