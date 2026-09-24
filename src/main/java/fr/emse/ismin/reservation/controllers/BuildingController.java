package fr.emse.ismin.reservation.controllers;

import fr.emse.ismin.reservation.dtos.BuildingRequest;
import fr.emse.ismin.reservation.dtos.BuildingResponse;
import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.services.BuildingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * HTTP endpoints of the buildings ({@code /api/buildings}).
 */
@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    /**
     * Creates a building.
     *
     * @param request name and number of floors
     * @return 201 with the created building and its URI in the {@code Location} header
     */
    @PostMapping
    public ResponseEntity<BuildingResponse> create(@Valid @RequestBody BuildingRequest request) {
        Building building = buildingService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{buildingId}")
                .buildAndExpand(building.getId())
                .toUri();
        return ResponseEntity.created(location).body(BuildingResponse.from(building));
    }

    /**
     * @return all buildings, sorted by name ignoring case
     */
    @GetMapping
    public List<BuildingResponse> findAll() {
        return buildingService.findAll().stream()
                .map(BuildingResponse::from)
                .toList();
    }

    /**
     * @param buildingId id of the building
     * @return the building
     */
    @GetMapping("/{buildingId}")
    public BuildingResponse findById(@PathVariable Long buildingId) {
        return BuildingResponse.from(buildingService.findById(buildingId));
    }

    /**
     * Replaces the name and the number of floors of a building.
     *
     * @param buildingId id of the building
     * @param request    new name and number of floors
     * @return the updated building
     */
    @PutMapping("/{buildingId}")
    public BuildingResponse update(@PathVariable Long buildingId, @Valid @RequestBody BuildingRequest request) {
        return BuildingResponse.from(buildingService.update(buildingId, request));
    }
}
