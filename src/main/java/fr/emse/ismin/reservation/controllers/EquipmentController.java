package fr.emse.ismin.reservation.controllers;

import fr.emse.ismin.reservation.dtos.EquipmentRequest;
import fr.emse.ismin.reservation.dtos.EquipmentResponse;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.services.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * HTTP endpoints of the equipment ({@code /api/equipment}).
 */
@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    /**
     * Creates a piece of equipment.
     *
     * @param request code and label
     * @return 201 with the created equipment and its URI in the {@code Location} header
     */
    @PostMapping
    public ResponseEntity<EquipmentResponse> create(@Valid @RequestBody EquipmentRequest request) {
        Equipment equipment = equipmentService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{equipmentId}")
                .buildAndExpand(equipment.getId())
                .toUri();
        return ResponseEntity.created(location).body(EquipmentResponse.from(equipment));
    }

    /**
     * @return all equipment, sorted by code
     */
    @GetMapping
    public List<EquipmentResponse> findAll() {
        return equipmentService.findAll().stream()
                .map(EquipmentResponse::from)
                .toList();
    }
}
