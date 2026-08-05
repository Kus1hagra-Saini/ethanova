package com.ethanova.backend.dispatch;

import com.ethanova.backend.common.exception.ApiError;
import com.ethanova.backend.dispatch.dto.DispatchOrderCreateRequest;
import com.ethanova.backend.dispatch.dto.DispatchOrderResponse;
import com.ethanova.backend.dispatch.dto.DispatchOrderStatusUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST endpoints for the {@link DispatchOrder} aggregate.
 *
 * <p>URL identifier is {@code orderNumber} (business key), matching the
 * project-wide convention. Order numbers are generated server-side in the
 * format {@code DO-YYYY-MM-NNNN}.
 *
 * <p>Status changes use {@code PUT /{orderNumber}/status} with a minimal body
 * carrying only the target status. The transition itself is validated by
 * {@link DispatchStatus#canTransitionTo(DispatchStatus)}; invalid transitions
 * return 409 Conflict.
 *
 * <p>No PUT for full-order update and no DELETE by design: dispatch orders
 * are records of intent that get cancelled (via status), not destroyed.
 */
@RestController
@RequestMapping("/api/v1/dispatch-orders")
@Tag(name = "Dispatch Orders", description = "Ethanol dispatch orders — creation, retrieval, and lifecycle transitions")
public class DispatchOrderController {

    private final DispatchOrderService dispatchOrderService;

    public DispatchOrderController(DispatchOrderService dispatchOrderService) {
        this.dispatchOrderService = dispatchOrderService;
    }

    @GetMapping
    @Operation(summary = "List all dispatch orders (newest first)")
    public List<DispatchOrderResponse> list() {
        return dispatchOrderService.findAll();
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "Get a dispatch order by its server-generated order number")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "Dispatch order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DispatchOrderResponse getByOrderNumber(@PathVariable String orderNumber) {
        return dispatchOrderService.findByOrderNumber(orderNumber);
    }

    @PostMapping
    @Operation(summary = "Create a new dispatch order in DRAFT status")
    @ApiResponses({
            @ApiResponse(responseCode = "400",
                    description = "Validation failure, invalid date range, or plant does not belong to supplier",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404",
                    description = "Supplier, source plant, or destination depot not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<DispatchOrderResponse> create(
            @Valid @RequestBody DispatchOrderCreateRequest request) {

        DispatchOrderResponse created = dispatchOrderService.create(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{orderNumber}")
                .buildAndExpand(created.orderNumber())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{orderNumber}/status")
    @Operation(summary = "Transition a dispatch order to a new status")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation failure on request body",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Dispatch order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Transition is not permitted from the order's current status",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DispatchOrderResponse updateStatus(
            @PathVariable String orderNumber,
            @Valid @RequestBody DispatchOrderStatusUpdateRequest request) {
        return dispatchOrderService.updateStatus(orderNumber, request);
    }
}