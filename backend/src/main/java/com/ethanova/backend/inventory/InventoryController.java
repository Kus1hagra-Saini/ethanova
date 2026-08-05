package com.ethanova.backend.inventory;

import com.ethanova.backend.dispatch.EthanolGrade;
import com.ethanova.backend.inventory.dto.InventoryResponse;
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
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryResponse> list() {
        return inventoryService.findAll();
    }

    @GetMapping("/depot/{depotCode}")
    public List<InventoryResponse> listByDepot(@PathVariable String depotCode) {
        return inventoryService.findByDepotCode(depotCode);
    }

    @GetMapping("/depot/{depotCode}/grade/{grade}")
    public InventoryResponse getByDepotAndGrade(
            @PathVariable String depotCode,
            @PathVariable EthanolGrade grade) {
        return inventoryService.findByDepotCodeAndGrade(depotCode, grade);
    }
}