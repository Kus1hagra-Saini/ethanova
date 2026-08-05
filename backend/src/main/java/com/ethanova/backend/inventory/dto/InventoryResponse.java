package com.ethanova.backend.inventory.dto;

import com.ethanova.backend.dispatch.EthanolGrade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Outbound representation of an {@link com.ethanova.backend.inventory.Inventory} row.
 *
 * <p>The parent {@link com.ethanova.backend.masterdata.Depot} is referenced by
 * business key ({@code depotCode}) rather than nested, to keep the payload
 * flat and avoid tempting clients to treat inventory as a source of truth for
 * depot master data.
 *
 * <p>Two timestamps are exposed with distinct semantics:
 * <ul>
 *   <li>{@code lastUpdatedAt} — business-level: when the stock quantity last changed</li>
 *   <li>{@code updatedAt} — technical: when the row was last flushed by JPA</li>
 * </ul>
 * They are usually identical but can diverge (e.g., a non-stock field is updated).
 */
public record InventoryResponse(
        Long id,
        String depotCode,
        EthanolGrade ethanolGrade,
        BigDecimal currentStockKl,
        BigDecimal maxCapacityKl,
        BigDecimal reorderLevelKl,
        OffsetDateTime lastUpdatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}