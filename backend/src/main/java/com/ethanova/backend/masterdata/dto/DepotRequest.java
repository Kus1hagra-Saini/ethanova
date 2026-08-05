package com.ethanova.backend.masterdata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Payload for creating or updating an OMC ethanol depot")
public record DepotRequest(

        @Schema(description = "Unique business identifier for the depot", example = "DEP-UP-KNP")
        @NotBlank(message = "depotCode is required")
        @Size(max = 50, message = "depotCode must be at most 50 characters")
        @Pattern(regexp = "^[A-Z0-9_-]+$",
                message = "depotCode must contain only uppercase letters, digits, hyphens, and underscores")
        String depotCode,

        @Schema(example = "IOCL Kanpur Depot")
        @NotBlank(message = "depotName is required")
        @Size(max = 200, message = "depotName must be at most 200 characters")
        String depotName,

        @Schema(description = "Oil Marketing Company code", example = "IOCL")
        @NotBlank(message = "omcCode is required")
        @Size(max = 20, message = "omcCode must be at most 20 characters")
        @Pattern(regexp = "^[A-Z]{2,20}$",
                message = "omcCode must be 2-20 uppercase letters (e.g. IOCL, BPCL, HPCL)")
        String omcCode,

        @Schema(description = "ISO 3166-2 state code", example = "UP")
        @NotBlank(message = "stateCode is required")
        @Size(max = 10, message = "stateCode must be at most 10 characters")
        @Pattern(regexp = "^[A-Z]{2,10}$", message = "stateCode must be 2-10 uppercase letters")
        String stateCode,

        @Schema(description = "Maximum storage capacity in kilolitres", example = "5000.000")
        @NotNull(message = "storageCapacityKl is required")
        @DecimalMin(value = "0.001", message = "storageCapacityKl must be greater than zero")
        @DecimalMax(value = "1000000.000", message = "storageCapacityKl must be at most 1,000,000 KL")
        BigDecimal storageCapacityKl,

        @Schema(description = "Stock level below which the depot triggers a reorder alert", example = "1000.000")
        @DecimalMin(value = "0.000", message = "reorderThresholdKl must be non-negative")
        @DecimalMax(value = "1000000.000", message = "reorderThresholdKl must be at most 1,000,000 KL")
        BigDecimal reorderThresholdKl,

        Boolean active
) {
}