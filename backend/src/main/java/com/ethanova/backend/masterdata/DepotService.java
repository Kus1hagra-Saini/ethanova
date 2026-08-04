package com.ethanova.backend.masterdata;

import com.ethanova.backend.common.exception.ResourceNotFoundException;
import com.ethanova.backend.masterdata.dto.DepotRequest;
import com.ethanova.backend.masterdata.dto.DepotResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for the {@link Depot} aggregate.
 *
 * <p>Owns transaction boundaries and translation between the persistence model
 * ({@link Depot}) and the HTTP contract ({@link DepotRequest} /
 * {@link DepotResponse}). Controllers must never bypass this service to reach
 * the repository directly.
 *
 * <p>Read methods inherit the class-level {@code readOnly = true}. Write
 * methods override with a plain {@link Transactional} so Hibernate opens a
 * writable transaction and flushes on commit.
 */
@Service
@Transactional(readOnly = true)
public class DepotService {

    private static final String RESOURCE_NAME = "Depot";

    private final DepotRepository depotRepository;

    public DepotService(DepotRepository depotRepository) {
        this.depotRepository = depotRepository;
    }

    // ---------------------------------------------------------------------
    // Query methods
    // ---------------------------------------------------------------------

    public List<DepotResponse> findAll() {
        return depotRepository.findAll().stream()
                .map(DepotService::toResponse)
                .toList();
    }

    public DepotResponse findByCode(String depotCode) {
        Depot depot = depotRepository.findByDepotCode(depotCode)
                .orElseThrow(() -> ResourceNotFoundException.forResource(RESOURCE_NAME, depotCode));
        return toResponse(depot);
    }

    // ---------------------------------------------------------------------
    // Command methods
    // ---------------------------------------------------------------------

    @Transactional
    public DepotResponse create(DepotRequest request) {
        if (depotRepository.existsByDepotCode(request.depotCode())) {
            throw new DataIntegrityViolationException(
                    "depotCode already exists: " + request.depotCode());
        }

        Depot depot = new Depot();
        applyRequest(depot, request);
        // Explicit default: nullable in request means "client did not specify" — new depots are active by default.
        if (request.active() == null) {
            depot.setActive(Boolean.TRUE);
        }

        Depot saved = depotRepository.save(depot);
        return toResponse(saved);
    }

    @Transactional
    public DepotResponse update(String depotCode, DepotRequest request) {
        Depot depot = depotRepository.findByDepotCode(depotCode)
                .orElseThrow(() -> ResourceNotFoundException.forResource(RESOURCE_NAME, depotCode));

        // depotCode is the URL-path identifier and immutable via this endpoint.
        // Any value in the request body is ignored.
        applyRequestPreservingCode(depot, request);

        // JPA dirty checking flushes on transaction commit; explicit save() is
        // included for clarity and to make the write intent obvious to reviewers.
        Depot saved = depotRepository.save(depot);
        return toResponse(saved);
    }

    @Transactional
    public void delete(String depotCode) {
        Depot depot = depotRepository.findByDepotCode(depotCode)
                .orElseThrow(() -> ResourceNotFoundException.forResource(RESOURCE_NAME, depotCode));

        // Hard delete. If child rows (inventory, dispatch_orders) reference this
        // depot, PostgreSQL raises a foreign-key violation which surfaces as
        // DataIntegrityViolationException and is translated to HTTP 409 by
        // GlobalExceptionHandler.
        depotRepository.delete(depot);
    }

    // ---------------------------------------------------------------------
    // Mapping (kept private and static — inlined per project convention)
    // ---------------------------------------------------------------------

    private static DepotResponse toResponse(Depot d) {
        return new DepotResponse(
                d.getId(),
                d.getDepotCode(),
                d.getDepotName(),
                d.getOmcCode(),
                d.getStateCode(),
                d.getStorageCapacityKl(),
                d.getReorderThresholdKl(),
                d.getActive(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    /**
     * Full write of request fields including depotCode. Used on create.
     */
    private static void applyRequest(Depot target, DepotRequest req) {
        target.setDepotCode(req.depotCode());
        target.setDepotName(req.depotName());
        target.setOmcCode(req.omcCode());
        target.setStateCode(req.stateCode());
        target.setStorageCapacityKl(req.storageCapacityKl());
        target.setReorderThresholdKl(req.reorderThresholdKl());
        if (req.active() != null) {
            target.setActive(req.active());
        }
    }

    /**
     * Write of request fields excluding depotCode. Used on update, where the
     * URL path is the authoritative identifier.
     */
    private static void applyRequestPreservingCode(Depot target, DepotRequest req) {
        target.setDepotName(req.depotName());
        target.setOmcCode(req.omcCode());
        target.setStateCode(req.stateCode());
        target.setStorageCapacityKl(req.storageCapacityKl());
        target.setReorderThresholdKl(req.reorderThresholdKl());
        if (req.active() != null) {
            target.setActive(req.active());
        }
    }
}