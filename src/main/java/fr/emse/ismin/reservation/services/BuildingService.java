package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.dtos.BuildingRequest;
import fr.emse.ismin.reservation.exceptions.ConflictException;
import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.ResourceNotFoundException;
import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.repositories.BuildingRepository;
import fr.emse.ismin.reservation.repositories.OrganizerRepository;
import fr.emse.ismin.reservation.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Business logic of the buildings: creation, lookup and update.
 */
@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final OrganizerRepository organizerRepository;

    /**
     * Creates a building.
     *
     * @param request name and number of floors of the new building
     * @return the saved building
     * @throws ConflictException {@code RESOURCE_ALREADY_EXISTS} if the name is already used, ignoring case
     */
    @Transactional
    public Building create(BuildingRequest request) {
        if (buildingRepository.existsByNameIgnoreCase(request.name())) {
            throw nameAlreadyUsed(request.name());
        }
        Building building = new Building();
        building.setName(request.name());
        building.setNumberOfFloors(request.numberOfFloors());
        return buildingRepository.save(building);
    }

    /**
     * @return all buildings, sorted by name ignoring case
     */
    @Transactional(readOnly = true)
    public List<Building> findAll() {
        return buildingRepository.findAllSortedByName();
    }

    /**
     * @param buildingId id of the building
     * @return the building
     * @throws ResourceNotFoundException {@code BUILDING_NOT_FOUND} if it does not exist
     */
    @Transactional(readOnly = true)
    public Building findById(Long buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> ResourceNotFoundException.building(buildingId));
    }

    /**
     * Replaces the name and the number of floors of a building. Reducing the
     * number of floors is refused when a room or an organizer is located on a
     * floor that would disappear.
     *
     * @param buildingId id of the building to update
     * @param request    new name and number of floors
     * @return the updated building
     * @throws ResourceNotFoundException {@code BUILDING_NOT_FOUND} if the building does not exist
     * @throws ConflictException         {@code RESOURCE_ALREADY_EXISTS} if another building uses the name,
     *                                   {@code BUILDING_FLOOR_COUNT_CONFLICT} if an occupied floor would disappear
     */
    @Transactional
    public Building update(Long buildingId, BuildingRequest request) {
        Building building = findById(buildingId);
        if (buildingRepository.existsByNameIgnoreCaseAndIdNot(request.name(), buildingId)) {
            throw nameAlreadyUsed(request.name());
        }

        // Floors go from 0 to numberOfFloors - 1, so an occupied floor must stay below the new count
        Optional<Integer> highestOccupiedFloor = findHighestOccupiedFloor(buildingId);
        if (highestOccupiedFloor.isPresent() && highestOccupiedFloor.get() >= request.numberOfFloors()) {
            throw new ConflictException(ErrorCode.BUILDING_FLOOR_COUNT_CONFLICT,
                    "Une salle ou un organisateur occupe un étage qui serait supprimé",
                    Map.of("highestOccupiedFloor", highestOccupiedFloor.get()));
        }

        building.setName(request.name());
        building.setNumberOfFloors(request.numberOfFloors());
        return building;
    }

    /**
     * Returns the highest floor of the building occupied by a room or an organizer.
     */
    private Optional<Integer> findHighestOccupiedFloor(Long buildingId) {
        return Stream.of(roomRepository.findHighestFloorInBuilding(buildingId),
                        organizerRepository.findHighestFloorInBuilding(buildingId))
                .flatMap(Optional::stream)
                .max(Integer::compare);
    }

    private ConflictException nameAlreadyUsed(String name) {
        return new ConflictException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                "Un bâtiment utilise déjà ce nom", Map.of("name", name));
    }
}
