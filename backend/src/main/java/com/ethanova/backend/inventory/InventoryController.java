package com.ethanova.backend.inventory;

import com.ethanova.backend.common.exception.ApiError;
import com.ethanova.backend.dispatch.EthanolGrade;
import com.ethanova.backend.inventory.dto.InventoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only REST endpoints for {@link Inventory}.
 *
 * <p>Three access patterns are exposed, all keyed by business identifiers:
 * <ul>
 *   <li>{@code GET /api/v1/inventory} — every row</li>
 *   <li>{@code GET /api/v1/inventory/depot/{depotCode}} — all grades at a depot</li>
 *   <li>{@code GET /api/v1/inventory/depot/{depotCode}/grade/{grade}} — one row</li>
 * </ul>
 *
 * <p>Writes are intentionally absent. See {@link InventoryService} for
 * rationale.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Read-only depot inventory (stock levels by ethanol grade)")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @Operation(summary = "List every inventory row across all depots")
    public List<InventoryResponse> list() {
        return inventoryService.findAll();
    }

    @GetMapping("/depot/{depotCode}")
    @Operation(summary = "List inventory for a specific depot (all grades)")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "Depot not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<InventoryResponse> listByDepot(@PathVariable String depotCode) {
        return inventoryService.findByDepotCode(depotCode);
    }

    @GetMapping("/depot/{depotCode}/grade/{grade}")
    @Operation(summary = "Get the inventory row for a specific depot and ethanol grade")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Invalid ethanol grade in path",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Depot not found, or no inventory for that grade at the depot",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public InventoryResponse getByDepotAndGrade(
            @PathVariable String depotCode,
            @PathVariable EthanolGrade grade) {
        return inventoryService.findByDepotCodeAndGrade(depotCode, grade);
    }
}