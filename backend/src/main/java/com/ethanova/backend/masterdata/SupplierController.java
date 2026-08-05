package com.ethanova.backend.masterdata;

import com.ethanova.backend.common.exception.ApiError;
import com.ethanova.backend.masterdata.dto.SupplierRequest;
import com.ethanova.backend.masterdata.dto.SupplierResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Suppliers", description = "Ethanol suppliers (sugar mills, distilleries, dual-feed producers)")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @Operation(summary = "List all suppliers")
    public List<SupplierResponse> list() {
        return supplierService.findAll();
    }

    @GetMapping("/{supplierCode}")
    @Operation(summary = "Get a supplier by its business code")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SupplierResponse getByCode(@PathVariable String supplierCode) {
        return supplierService.findByCode(supplierCode);
    }

    @PostMapping
    @Operation(summary = "Create a new supplier")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation failure on request body",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "supplierCode already exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        SupplierResponse created = supplierService.create(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{supplierCode}")
                .buildAndExpand(created.supplierCode())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{supplierCode}")
    @Operation(summary = "Update an existing supplier (full replacement)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation failure on request body",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SupplierResponse update(
            @PathVariable String supplierCode,
            @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(supplierCode, request);
    }

    @DeleteMapping("/{supplierCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a supplier")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Supplier is referenced by other records (e.g. production plants)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void delete(@PathVariable String supplierCode) {
        supplierService.delete(supplierCode);
    }
}