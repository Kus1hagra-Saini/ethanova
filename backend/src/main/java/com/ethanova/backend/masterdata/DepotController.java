package com.ethanova.backend.masterdata;

import com.ethanova.backend.masterdata.dto.DepotRequest;
import com.ethanova.backend.masterdata.dto.DepotResponse;
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
public class DepotController {

    private final DepotService depotService;

    public DepotController(DepotService depotService) {
        this.depotService = depotService;
    }

    @GetMapping
    public List<DepotResponse> list() {
        return depotService.findAll();
    }

    @GetMapping("/{depotCode}")
    public DepotResponse getByCode(@PathVariable String depotCode) {
        return depotService.findByCode(depotCode);
    }

    @PostMapping
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
    public DepotResponse update(
            @PathVariable String depotCode,
            @Valid @RequestBody DepotRequest request) {
        return depotService.update(depotCode, request);
    }

    @DeleteMapping("/{depotCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String depotCode) {
        depotService.delete(depotCode);
    }
}