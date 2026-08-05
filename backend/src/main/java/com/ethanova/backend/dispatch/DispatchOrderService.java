package com.ethanova.backend.dispatch;

import com.ethanova.backend.common.exception.ResourceNotFoundException;
import com.ethanova.backend.dispatch.dto.DispatchOrderCreateRequest;
import com.ethanova.backend.dispatch.dto.DispatchOrderResponse;
import com.ethanova.backend.dispatch.dto.DispatchOrderStatusUpdateRequest;
import com.ethanova.backend.masterdata.Depot;
import com.ethanova.backend.masterdata.DepotRepository;
import com.ethanova.backend.masterdata.ProductionPlant;
import com.ethanova.backend.masterdata.ProductionPlantRepository;
import com.ethanova.backend.masterdata.Supplier;
import com.ethanova.backend.masterdata.SupplierRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Application service for the {@link DispatchOrder} aggregate.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Resolve supplier / plant / depot business codes to entities on create</li>
 *   <li>Enforce the plant-belongs-to-supplier integrity rule</li>
 *   <li>Generate server-side order numbers in {@code DO-YYYY-MM-<seq>} format</li>
 *   <li>Compute {@code totalAmountInr} from quantity × unit price (KL → L)</li>
 *   <li>Validate status transitions via {@link DispatchStatus#canTransitionTo}</li>
 *   <li>Stamp {@code actualArrivalAt} on transition to {@link DispatchStatus#DELIVERED}</li>
 * </ul>
 *
 * <p><b>Inventory side-effect (deferred).</b> A real E20 platform would
 * increment {@code Inventory.currentStockKl} on transition to
 * {@code DELIVERED}. This is intentionally left out of Review 1 — the write
 * path requires idempotency, retry semantics, and a proper transaction
 * boundary spanning inventory and dispatch. See the marker in
 * {@link #updateStatus} for the extension point.
 */
@Service
@Transactional(readOnly = true)
public class DispatchOrderService {

    private static final String RESOURCE_NAME = "DispatchOrder";
    private static final String SUPPLIER_RESOURCE = "Supplier";
    private static final String PLANT_RESOURCE = "ProductionPlant";
    private static final String DEPOT_RESOURCE = "Depot";

    private static final DateTimeFormatter ORDER_NUMBER_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal LITRES_PER_KL = new BigDecimal("1000");

    private final DispatchOrderRepository dispatchOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductionPlantRepository plantRepository;
    private final DepotRepository depotRepository;

    public DispatchOrderService(
            DispatchOrderRepository dispatchOrderRepository,
            SupplierRepository supplierRepository,
            ProductionPlantRepository plantRepository,
            DepotRepository depotRepository) {
        this.dispatchOrderRepository = dispatchOrderRepository;
        this.supplierRepository = supplierRepository;
        this.plantRepository = plantRepository;
        this.depotRepository = depotRepository;
    }

    // ---------------------------------------------------------------------
    // Query methods
    // ---------------------------------------------------------------------

    public List<DispatchOrderResponse> findAll() {
        return dispatchOrderRepository.findAllWithAssociations().stream()
                .map(DispatchOrderService::toResponse)
                .toList();
    }

    public DispatchOrderResponse findByOrderNumber(String orderNumber) {
        DispatchOrder order = dispatchOrderRepository
                .findByOrderNumberWithAssociations(orderNumber)
                .orElseThrow(() -> ResourceNotFoundException.forResource(RESOURCE_NAME, orderNumber));
        return toResponse(order);
    }

    // ---------------------------------------------------------------------
    // Command methods
    // ---------------------------------------------------------------------

    @Transactional
    public DispatchOrderResponse create(DispatchOrderCreateRequest request) {
        // 1. Resolve business codes to entities (each throws 404 if missing).
        Supplier supplier = supplierRepository.findBySupplierCode(request.supplierCode())
                .orElseThrow(() -> ResourceNotFoundException.forResource(SUPPLIER_RESOURCE, request.supplierCode()));

        ProductionPlant plant = plantRepository.findByPlantCode(request.sourcePlantCode())
                .orElseThrow(() -> ResourceNotFoundException.forResource(PLANT_RESOURCE, request.sourcePlantCode()));

        Depot depot = depotRepository.findByDepotCode(request.destinationDepotCode())
                .orElseThrow(() -> ResourceNotFoundException.forResource(DEPOT_RESOURCE, request.destinationDepotCode()));

        // 2. Integrity: the source plant must belong to the specified supplier.
        //    (Accessing plant.supplier.id is safe — lazy FK, but we are inside the transaction.)
        if (!plant.getSupplier().getId().equals(supplier.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sourcePlantCode '%s' does not belong to supplierCode '%s'"
                            .formatted(request.sourcePlantCode(), request.supplierCode()));
        }

        // 3. Cross-field date rule.
        LocalDate orderDate = request.orderDate() != null ? request.orderDate() : LocalDate.now();
        if (request.expectedArrivalDate().isBefore(orderDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "expectedArrivalDate must not be before orderDate");
        }

        // 4. Generate order number: DO-YYYY-MM-<seq> where seq is 1-based per month.
        String orderNumber = generateOrderNumber(orderDate);

        // 5. Server-computed total: quantity (KL) × 1000 L/KL × unit price (INR/L).
        //    Rounded HALF_UP to 2dp to match the NUMERIC(15,2) column.
        BigDecimal totalAmount = request.quantityKl()
                .multiply(LITRES_PER_KL)
                .multiply(request.unitPriceInrPerL())
                .setScale(2, RoundingMode.HALF_UP);

        // 6. Build and persist.
        DispatchOrder order = DispatchOrder.builder()
                .orderNumber(orderNumber)
                .supplier(supplier)
                .sourcePlant(plant)
                .destinationDepot(depot)
                .ethanolGrade(request.ethanolGrade())
                .quantityKl(request.quantityKl())
                .unitPriceInrPerL(request.unitPriceInrPerL())
                .totalAmountInr(totalAmount)
                .orderDate(orderDate)
                .expectedArrivalDate(request.expectedArrivalDate())
                .status(DispatchStatus.DRAFT)
                .build();

        DispatchOrder saved = dispatchOrderRepository.save(order);
        return toResponse(saved);
    }

    @Transactional
    public DispatchOrderResponse updateStatus(String orderNumber, DispatchOrderStatusUpdateRequest request) {
        DispatchOrder order = dispatchOrderRepository
                .findByOrderNumberWithAssociations(orderNumber)
                .orElseThrow(() -> ResourceNotFoundException.forResource(RESOURCE_NAME, orderNumber));

        DispatchStatus current = order.getStatus();
        DispatchStatus target = request.status();

        if (!current.canTransitionTo(target)) {
            // 409 — the request is well-formed but conflicts with the resource's current state.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid status transition from %s to %s for order %s"
                            .formatted(current, target, orderNumber));
        }

        order.setStatus(target);

        if (target == DispatchStatus.DELIVERED) {
            order.setActualArrivalAt(OffsetDateTime.now(ZoneOffset.UTC));

            // === EXTENSION POINT — deferred to a future milestone ===
            // On transition to DELIVERED, increment inventory:
            //   Inventory inv = inventoryRepository.findByDepotIdAndEthanolGrade(
            //       order.getDestinationDepot().getId(),
            //       order.getEthanolGrade()
            //   ).orElseThrow(...);
            //   inv.setCurrentStockKl(inv.getCurrentStockKl().add(order.getQuantityKl()));
            //   inv.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            //
            // Not implemented here: requires idempotency, capacity checks, and a
            // separate transaction-management review. Out of scope for Review 1.
        }

        DispatchOrder saved = dispatchOrderRepository.save(order);
        return toResponse(saved);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Generates {@code DO-YYYY-MM-NNNN} where NNNN is the 1-based sequence of
     * the current calendar month.
     *
     * <p>Race note: two concurrent creates within the same second could produce
     * duplicate sequences. The unique constraint on {@code order_number}
     * ensures at most one succeeds; the other surfaces as 409 via
     * {@link DataIntegrityViolationException}. Acceptable for Review 1 volumes
     * (single planner, low frequency). A DB sequence or advisory lock is the
     * proper fix and can be introduced later without changing the API.
     */
    private String generateOrderNumber(LocalDate orderDate) {
        String monthPrefix = "DO-" + orderDate.format(ORDER_NUMBER_MONTH) + "-";
        long existing = dispatchOrderRepository.countByOrderNumberStartingWith(monthPrefix);
        long seq = existing + 1;
        return "%s%04d".formatted(monthPrefix, seq);
    }

    // ---------------------------------------------------------------------
    // Mapping — associations are already fetched inside the transaction.
    // ---------------------------------------------------------------------

    private static DispatchOrderResponse toResponse(DispatchOrder o) {
        return new DispatchOrderResponse(
                o.getId(),
                o.getOrderNumber(),
                o.getSupplier().getSupplierCode(),
                o.getSourcePlant().getPlantCode(),
                o.getDestinationDepot().getDepotCode(),
                o.getEthanolGrade(),
                o.getQuantityKl(),
                o.getUnitPriceInrPerL(),
                o.getTotalAmountInr(),
                o.getOrderDate(),
                o.getExpectedArrivalDate(),
                o.getActualArrivalAt(),
                o.getStatus(),
                o.getCreatedAt(),
                o.getUpdatedAt()
        );
    }
}