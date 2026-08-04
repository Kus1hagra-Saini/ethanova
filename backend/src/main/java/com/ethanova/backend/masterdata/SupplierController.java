package com.ethanova.backend.masterdata;

import com.ethanova.backend.masterdata.dto.SupplierRequest;
import com.ethanova.backend.masterdata.dto.SupplierResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST endpoints for the {@link Supplier} master-data resource.
 *
 * <p>URL identifier is {@code supplierCode} (business key), not the surrogate
 * database {@code id}. This keeps URLs stable, human-readable, and safe to
 * share across integrations.
 *
 * <p>All non-happy paths are handled by
 * {@link com.ethanova.backend.common.exception.GlobalExceptionHandler}; no
 * try/catch blocks in controllers.
 */
@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public List<SupplierResponse> list() {
        return supplierService.findAll();
    }

    @GetMapping("/{supplierCode}")
    public SupplierResponse getByCode(@PathVariable String supplierCode) {
        return supplierService.findByCode(supplierCode);
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        SupplierResponse created = supplierService.create(request);

        // Location header points at the canonical GET for the new resource.
        // Standard REST practice on POST-that-creates.
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{supplierCode}")
                .buildAndExpand(created.supplierCode())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{supplierCode}")
    public SupplierResponse update(
            @PathVariable String supplierCode,
            @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(supplierCode, request);
    }

    @DeleteMapping("/{supplierCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String supplierCode) {
        supplierService.delete(supplierCode);
    }
}