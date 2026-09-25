package fr.emse.ismin.reservation.controllers;

import fr.emse.ismin.reservation.dtos.OrganizerRequest;
import fr.emse.ismin.reservation.dtos.OrganizerResponse;
import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.services.OrganizerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * HTTP endpoints of the organizers ({@code /api/organizers}).
 */
@RestController
@RequestMapping("/api/organizers")
@RequiredArgsConstructor
public class OrganizerController {

    private final OrganizerService organizerService;

    /**
     * Creates an organizer.
     *
     * @param request identity, email and location
     * @return 201 with the created organizer and its URI in the {@code Location} header
     */
    @PostMapping
    public ResponseEntity<OrganizerResponse> create(@Valid @RequestBody OrganizerRequest request) {
        Organizer organizer = organizerService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{organizerId}")
                .buildAndExpand(organizer.getId())
                .toUri();
        return ResponseEntity.created(location).body(OrganizerResponse.from(organizer));
    }

    /**
     * @return all organizers, sorted by name then id
     */
    @GetMapping
    public List<OrganizerResponse> findAll() {
        return organizerService.findAll().stream()
                .map(OrganizerResponse::from)
                .toList();
    }

    /**
     * @param organizerId id of the organizer
     * @return the organizer
     */
    @GetMapping("/{organizerId}")
    public OrganizerResponse findById(@PathVariable Long organizerId) {
        return OrganizerResponse.from(organizerService.findById(organizerId));
    }
}
