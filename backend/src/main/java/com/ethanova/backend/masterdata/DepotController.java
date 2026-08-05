package com.ethanova.backend.masterdata;

import com.ethanova.backend.common.exception.ApiError;
import com.ethanova.backend.masterdata.dto.DepotRequest;
import com.ethanova.backend.masterdata.dto.DepotResponse;
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
 * REST endpoints for the {@link Depot} master-data resource.
 *
 * <p>URL identifier is {@code depotCode} (business key), not the surrogate
 * database {@code id}. This keeps URLs stable, human-readable, and safe to
 * share across integrations.
 *
 * <p>All non-happy paths are handled by
 * {@link com.ethanova.backend.common.exception.GlobalExceptionHandler}; no
 * try/catch blocks in controllers.
 */
@RestController
@RequestMapping("/api/v1/depots")
@Tag(name = "Depots", description = "OMC ethanol storage depots (IOCL, BPCL, HPCL)")
public class DepotController {

    private final DepotService depotService;

    public DepotController(DepotService depotService) {
        this.depotService = depotService;
    }

    @GetMapping
    @Operation(summary = "List all depots")
    public List<DepotResponse> list() {
        return depotService.findAll();
    }

    @GetMapping("/{depotCode}")
    @Operation(summary = "Get a depot by its business code")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "Depot not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DepotResponse getByCode(@PathVariable String depotCode) {
        return depotService.findByCode(depotCode);
    }

    @PostMapping
    @Operation(summary = "Create a new depot")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation failure on request body",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "depotCode already exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<DepotResponse> create(@Valid @RequestBody DepotRequest request) {
        DepotResponse created = depotService.create(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{depotCode}")
                .buildAndExpand(created.depotCode())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{depotCode}")
    @Operation(summary = "Update an existing depot (full replacement)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation failure on request body",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Depot not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DepotResponse update(
            @PathVariable String depotCode,
            @Valid @RequestBody DepotRequest request) {
        return depotService.update(depotCode, request);
    }

    @DeleteMapping("/{depotCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a depot")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "Depot not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Depot is referenced by other records (e.g. inventory, dispatch orders)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void delete(@PathVariable String depotCode) {
        depotService.delete(depotCode);
    }
}