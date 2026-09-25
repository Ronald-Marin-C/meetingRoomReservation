package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.dtos.OrganizerRequest;
import fr.emse.ismin.reservation.exceptions.ConflictException;
import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.InvalidFieldException;
import fr.emse.ismin.reservation.exceptions.ResourceNotFoundException;
import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.repositories.OrganizerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Business logic of the organizers: creation and lookup. The location of an
 * organizer is later used to find the closest room.
 */
@Service
@RequiredArgsConstructor
public class OrganizerService {

    private final OrganizerRepository organizerRepository;
    private final BuildingService buildingService;

    /**
     * Creates an organizer located on a floor of an existing building.
     *
     * @param request identity, email and location of the organizer
     * @return the saved organizer
     * @throws ResourceNotFoundException {@code BUILDING_NOT_FOUND} if the building does not exist
     * @throws InvalidFieldException     {@code VALIDATION_ERROR} if the floor does not exist in the building
     * @throws ConflictException         {@code RESOURCE_ALREADY_EXISTS} if the email is already used, ignoring case
     */
    @Transactional
    public Organizer create(OrganizerRequest request) {
        Building building = buildingService.findLocation(request.buildingId(), request.floor());
        if (organizerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Un organisateur utilise déjà cette adresse e-mail", Map.of("email", request.email()));
        }
        Organizer organizer = new Organizer();
        organizer.setName(request.name());
        organizer.setEmail(request.email());
        organizer.setBuilding(building);
        organizer.setFloor(request.floor());
        return organizerRepository.save(organizer);
    }

    /**
     * @return all organizers, sorted by name then id
     */
    @Transactional(readOnly = true)
    public List<Organizer> findAll() {
        return organizerRepository.findAllSortedByName();
    }

    /**
     * @param organizerId id of the organizer
     * @return the organizer
     * @throws ResourceNotFoundException {@code ORGANIZER_NOT_FOUND} if it does not exist
     */
    @Transactional(readOnly = true)
    public Organizer findById(Long organizerId) {
        return organizerRepository.findById(organizerId)
                .orElseThrow(() -> ResourceNotFoundException.organizer(organizerId));
    }
}
