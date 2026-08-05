package com.ethanova.backend.inventory;

import com.ethanova.backend.common.exception.ResourceNotFoundException;
import com.ethanova.backend.dispatch.EthanolGrade;
import com.ethanova.backend.inventory.dto.InventoryResponse;
import com.ethanova.backend.masterdata.DepotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for the {@link Inventory} aggregate — read-only surface.
 *
 * <p>Inventory is <b>not</b> mutable via the REST API. Stock changes are the
 * consequence of dispatch-order side effects (implemented in Milestone 3.6
 * inside {@code DispatchOrderService}). Exposing writes here would create a
 * second write path for the same data and open the door to state drift.
 *
 * <p>All read methods use fetch variants that JOIN the depot in a single
 * query, avoiding N+1 when serialising the {@code depotCode} field.
 *
 * <p>The {@link DepotRepository} is injected to distinguish two 404-worthy
 * cases from the empty-result case: a request for a non-existent depot yields
 * a real 404, while a valid depot with no inventory yields an empty list.
 */
@Service
@Transactional(readOnly = true)
public class InventoryService {

    private static final String INVENTORY_RESOURCE = "Inventory";
    private static final String DEPOT_RESOURCE = "Depot";

    private final InventoryRepository inventoryRepository;
    private final DepotRepository depotRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            DepotRepository depotRepository) {
        this.inventoryRepository = inventoryRepository;
        this.depotRepository = depotRepository;
    }

    public List<InventoryResponse> findAll() {
        return inventoryRepository.findAllWithDepot().stream()
                .map(InventoryService::toResponse)
                .toList();
    }

    public List<InventoryResponse> findByDepotCode(String depotCode) {
        // Distinguish "depot doesn't exist" (404) from "depot exists but has no inventory" ([]).
        // The existence check is a cheap indexed lookup (unique constraint on depot_code).
        if (!depotRepository.existsByDepotCode(depotCode)) {
            throw ResourceNotFoundException.forResource(DEPOT_RESOURCE, depotCode);
        }

        return inventoryRepository.findByDepotCodeWithDepot(depotCode).stream()
                .map(InventoryService::toResponse)
                .toList();
    }

    public InventoryResponse findByDepotCodeAndGrade(String depotCode, EthanolGrade grade) {
        // Same distinction for the single-row endpoint: a missing depot is a
        // Depot 404; a present depot with no matching grade is an Inventory 404.
        if (!depotRepository.existsByDepotCode(depotCode)) {
            throw ResourceNotFoundException.forResource(DEPOT_RESOURCE, depotCode);
        }

        Inventory row = inventoryRepository.findByDepotCodeAndGradeWithDepot(depotCode, grade)
                .orElseThrow(() -> ResourceNotFoundException.forResource(
                        INVENTORY_RESOURCE,
                        "depotCode=%s, grade=%s".formatted(depotCode, grade)));
        return toResponse(row);
    }

    // ---------------------------------------------------------------------
    // Mapping — depot is already fetched, so getDepotCode() is safe here.
    // ---------------------------------------------------------------------

    private static InventoryResponse toResponse(Inventory i) {
        return new InventoryResponse(
                i.getId(),
                i.getDepot().getDepotCode(),
                i.getEthanolGrade(),
                i.getCurrentStockKl(),
                i.getMaxCapacityKl(),
                i.getReorderLevelKl(),
                i.getLastUpdatedAt(),
                i.getCreatedAt(),
                i.getUpdatedAt()
        );
    }
}