package com.ethanova.backend.dispatch.dto;

import com.ethanova.backend.dispatch.EthanolGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Payload for creating a new dispatch order. " +
        "The orderNumber and totalAmountInr are computed by the server; " +
        "status always starts as DRAFT.")
public record DispatchOrderCreateRequest(

        @Schema(description = "Business code of the supplying entity", example = "SUP-UP-001")
        @NotBlank(message = "supplierCode is required")
        @Size(max = 50, message = "supplierCode must be at most 50 characters")
        String supplierCode,

        @Schema(description = "Business code of the source production plant. " +
                "Must belong to the specified supplier.", example = "PLT-UP-001")
        @NotBlank(message = "sourcePlantCode is required")
        @Size(max = 50, message = "sourcePlantCode must be at most 50 characters")
        String sourcePlantCode,

        @Schema(description = "Business code of the destination OMC depot", example = "DEP-UP-KNP")
        @NotBlank(message = "destinationDepotCode is required")
        @Size(max = 50, message = "destinationDepotCode must be at most 50 characters")
        String destinationDepotCode,

        @NotNull(message = "ethanolGrade is required")
        EthanolGrade ethanolGrade,

        @Schema(description = "Order quantity in kilolitres", example = "50.000")
        @NotNull(message = "quantityKl is required")
        @DecimalMin(value = "0.001", message = "quantityKl must be greater than zero")
        @DecimalMax(value = "1000000.000", message = "quantityKl must be at most 1,000,000 KL")
        BigDecimal quantityKl,

        @Schema(description = "Unit price in INR per litre", example = "65.500")
        @NotNull(message = "unitPriceInrPerL is required")
        @DecimalMin(value = "0.001", message = "unitPriceInrPerL must be greater than zero")
        @DecimalMax(value = "9999.999", message = "unitPriceInrPerL must be at most 9999.999 INR/L")
        BigDecimal unitPriceInrPerL,

        @Schema(description = "Date the order was placed. Defaults to today if omitted.",
                example = "2026-08-05")
        LocalDate orderDate,

        @Schema(description = "Expected arrival date at the destination depot. Must be >= orderDate.",
                example = "2026-08-15")
        @NotNull(message = "expectedArrivalDate is required")
        LocalDate expectedArrivalDate
) {
}