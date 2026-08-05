package com.ethanova.backend.dispatch.dto;

import com.ethanova.backend.dispatch.DispatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload for updating a dispatch order's status. " +
        "The transition is validated against DispatchStatus.canTransitionTo(); " +
        "invalid transitions return 409 Conflict.")
public record DispatchOrderStatusUpdateRequest(

        @Schema(description = "Target status for the order", example = "CONFIRMED")
        @NotNull(message = "status is required")
        DispatchStatus status
) {
}