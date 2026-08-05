package com.ethanova.backend.dispatch.dto;

import com.ethanova.backend.dispatch.DispatchStatus;
import com.ethanova.backend.dispatch.EthanolGrade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Outbound representation of a {@link com.ethanova.backend.dispatch.DispatchOrder}.
 *
 * <p>Foreign associations are referenced by business code
 * ({@code supplierCode}, {@code sourcePlantCode}, {@code destinationDepotCode})
 * to keep the payload flat and avoid cross-aggregate loading.
 *
 * <p>Timestamps use the project convention:
 * {@link LocalDate} for business dates that have no time-of-day meaning
 * ({@code orderDate}, {@code expectedArrivalDate}); {@link OffsetDateTime}
 * for exact instants ({@code actualArrivalAt}, audit fields).
 */
public record DispatchOrderResponse(
        Long id,
        String orderNumber,
        String supplierCode,
        String sourcePlantCode,
        String destinationDepotCode,
        EthanolGrade ethanolGrade,
        BigDecimal quantityKl,
        BigDecimal unitPriceInrPerL,
        BigDecimal totalAmountInr,
        LocalDate orderDate,
        LocalDate expectedArrivalDate,
        OffsetDateTime actualArrivalAt,
        DispatchStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}