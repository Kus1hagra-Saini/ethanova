package com.ethanova.backend.masterdata;

import com.ethanova.backend.common.exception.ResourceNotFoundException;
import com.ethanova.backend.masterdata.dto.SupplierRequest;
import com.ethanova.backend.masterdata.dto.SupplierResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for the {@link Supplier} aggregate.
 *
 * <p>Owns transaction boundaries and translation between the persistence model
 * ({@link Supplier}) and the HTTP contract ({@link SupplierRequest} /
 * {@link SupplierResponse}). Controllers must never bypass this service to
 * reach the repository directly.
 *
 * <p>Read methods inherit the class-level {@code readOnly = true}. Write
 * methods override with a plain {@link Transactional} so Hibernate opens a
 * writable transaction and flushes on commit.
 */
@Service
@Transactional(readOnly = true)
public class SupplierService {

    private static final String RESOURCE_NAME = "Supplier";

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    // ---------------------------------------------------------------------
    // Query methods
    // ---------------------------------------------------------------------

    public List<SupplierResponse> findAll() {
        return supplierRepository.findAll().stream()
                .map(SupplierService::toResponse)
                .toList();
    }

    public SupplierResponse findByCode(String supplierCode) {
        Supplier supplier = supplierRepository.findBySupplierCode(supplierCode)
                .orElseThrow(() -> ResourceNotFoundException.forResource(RESOURCE_NAME, supplierCode));
        return toResponse(supplier);
    }

    // ---------------------------------------------------------------------
    // Command methods
    // ---------------------------------------------------------------------

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        if (supplierRepository.existsBySupplierCode(request.supplierCode())) {
            throw new DataIntegrityViolationException(
                    "supplierCode already exists: " + request.supplierCode());
        }

        Supplier supplier = new Supplier();
        applyRequest(supplier, request);
        // Explicit default: nullable in request means "client did not specify" — new suppliers are active by default.
        if (request.active() == null) {
            supplier.setActive(Boolean.TRUE);
        }

        Supplier saved = supplierRepository.save(supplier);
        return toResponse(saved);
    }

    @Transactional
    public SupplierResponse update(String supplierCode, SupplierRequest request) {
        Supplier supplier = supplierRepository.findBySupplierCode(supplierCode)
                .orElseThrow(() -> ResourceNotFoundException.forResource(RESOURCE_NAME, supplierCode));

        // supplierCode is the URL-path identifier and immutable via this endpoint.
        // Any value in the request body is ignored.
        applyRequestPreservingCode(supplier, request);
        // For updates, a null `active` in the payload is left unchanged — clients
        // must explicitly send true/false to change activation status.

        // JPA dirty checking flushes on transaction commit; explicit save() is
        // included for clarity and to make the write intent obvious to reviewers.
        Supplier saved = supplierRepository.save(supplier);
        return toResponse(saved);
    }

    @Transactional
    public void delete(String supplierCode) {
        Supplier supplier = supplierRepository.findBySupplierCode(supplierCode)
                .orElseThrow(() -> ResourceNotFoundException.forResource(RESOURCE_NAME, supplierCode));

        // Hard delete. If child rows (e.g., production_plants) reference this
        // supplier, PostgreSQL raises a foreign-key violation which surfaces
        // as DataIntegrityViolationException and is translated to HTTP 409
        // by GlobalExceptionHandler.
        supplierRepository.delete(supplier);
    }

    // ---------------------------------------------------------------------
    // Mapping (kept private and static — inlined per project convention)
    // ---------------------------------------------------------------------

    private static SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(
                s.getId(),
                s.getSupplierCode(),
                s.getSupplierName(),
                s.getSupplierType(),
                s.getStateCode(),
                s.getContactEmail(),
                s.getContactPhone(),
                s.getReliabilityScore(),
                s.getActive(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }

    /**
     * Full write of request fields including supplierCode. Used on create.
     */
    private static void applyRequest(Supplier target, SupplierRequest req) {
        target.setSupplierCode(req.supplierCode());
        target.setSupplierName(req.supplierName());
        target.setSupplierType(req.supplierType());
        target.setStateCode(req.stateCode());
        target.setContactEmail(req.contactEmail());
        target.setContactPhone(req.contactPhone());
        target.setReliabilityScore(req.reliabilityScore());
        if (req.active() != null) {
            target.setActive(req.active());
        }
    }

    /**
     * Write of request fields excluding supplierCode. Used on update, where the
     * URL path is the authoritative identifier.
     */
    private static void applyRequestPreservingCode(Supplier target, SupplierRequest req) {
        target.setSupplierName(req.supplierName());
        target.setSupplierType(req.supplierType());
        target.setStateCode(req.stateCode());
        target.setContactEmail(req.contactEmail());
        target.setContactPhone(req.contactPhone());
        target.setReliabilityScore(req.reliabilityScore());
        if (req.active() != null) {
            target.setActive(req.active());
        }
    }
}