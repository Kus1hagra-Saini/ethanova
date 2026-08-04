package com.ethanova.backend.masterdata.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Outbound representation of a {@link com.ethanova.backend.masterdata.Depot}.
 *
 * <p>Deliberately flat and JSON-friendly. Includes surrogate {@code id} (useful
 * for internal joins in later phases) and business-key {@code depotCode}
 * (used as the URL identifier).
 *
 * <p>Audit fields ({@code createdAt}, {@code updatedAt}) exposed as
 * {@link OffsetDateTime} to match {@code BaseAuditableEntity} and preserve
 * timezone offset in the serialised JSON.
 */
public record DepotResponse(
        Long id,
        String depotCode,
        String depotName,
        String omcCode,
        String stateCode,
        BigDecimal storageCapacityKl,
        BigDecimal reorderThresholdKl,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}