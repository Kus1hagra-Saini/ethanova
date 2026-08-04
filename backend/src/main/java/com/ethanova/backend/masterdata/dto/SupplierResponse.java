package com.ethanova.backend.masterdata.dto;

import com.ethanova.backend.masterdata.SupplierType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Outbound representation of a {@link com.ethanova.backend.masterdata.Supplier}.
 *
 * <p>Deliberately flat and JSON-friendly. Includes surrogate {@code id} (useful
 * for internal joins in later phases) and business-key {@code supplierCode}
 * (used as the URL identifier).
 *
 * <p>Audit fields ({@code createdAt}, {@code updatedAt}) are exposed to help
 * clients reason about staleness in the UI and to support optimistic-refresh
 * patterns downstream. Uses {@link OffsetDateTime} to preserve timezone offset
 * in the serialised JSON, matching {@code BaseAuditableEntity}.
 */
public record SupplierResponse(
        Long id,
        String supplierCode,
        String supplierName,
        SupplierType supplierType,
        String stateCode,
        String contactEmail,
        String contactPhone,
        BigDecimal reliabilityScore,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}