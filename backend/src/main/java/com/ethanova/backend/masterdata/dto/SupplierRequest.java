package com.ethanova.backend.masterdata.dto;

import com.ethanova.backend.masterdata.SupplierType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inbound payload for creating or updating a {@link com.ethanova.backend.masterdata.Supplier}.
 *
 * <p>On POST, all required fields (marked non-blank / non-null) must be supplied.
 * On PUT, the {@code supplierCode} in the URL path is authoritative — any value
 * present in the body is ignored by the service.
 *
 * <p>Nullable fields (contactEmail, contactPhone, reliabilityScore, active) are
 * validated only when present; nulls are treated as "no value provided".
 */
public record SupplierRequest(

        @NotBlank(message = "supplierCode is required")
        @Size(max = 50, message = "supplierCode must be at most 50 characters")
        @Pattern(
                regexp = "^[A-Z0-9_-]+$",
                message = "supplierCode must contain only uppercase letters, digits, hyphens, and underscores"
        )
        String supplierCode,

        @NotBlank(message = "supplierName is required")
        @Size(max = 200, message = "supplierName must be at most 200 characters")
        String supplierName,

        @NotNull(message = "supplierType is required")
        SupplierType supplierType,

        @NotBlank(message = "stateCode is required")
        @Size(max = 10, message = "stateCode must be at most 10 characters")
        @Pattern(
                regexp = "^[A-Z]{2,10}$",
                message = "stateCode must be 2-10 uppercase letters"
        )
        String stateCode,

        @Email(message = "contactEmail must be a valid email address")
        @Size(max = 255, message = "contactEmail must be at most 255 characters")
        String contactEmail,

        @Size(max = 20, message = "contactPhone must be at most 20 characters")
        @Pattern(
                regexp = "^[+0-9 -]{7,20}$",
                message = "contactPhone must contain 7-20 characters of digits, spaces, plus, or hyphens"
        )
        String contactPhone,

        @DecimalMin(value = "0.00", message = "reliabilityScore must be at least 0.00")
        @DecimalMax(value = "100.00", message = "reliabilityScore must be at most 100.00")
        BigDecimal reliabilityScore,

        Boolean active
) {
}