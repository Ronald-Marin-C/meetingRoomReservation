package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.dtos.CreateRoomRequest;
import fr.emse.ismin.reservation.dtos.UpdateRoomRequest;
import fr.emse.ismin.reservation.exceptions.ConflictException;
import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.InvalidFieldException;
import fr.emse.ismin.reservation.exceptions.InvalidReservationPeriodException;
import fr.emse.ismin.reservation.exceptions.ResourceNotFoundException;
import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;
import fr.emse.ismin.reservation.repositories.ReservationRepository;
import fr.emse.ismin.reservation.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Business logic of the rooms: creation, update, status, equipment and search
 * of the rooms available over a period.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final BuildingService buildingService;
    private final EquipmentService equipmentService;
    private final ReservationPeriodValidator periodValidator;
    private final RoomCompatibility roomCompatibility;

    /**
     * Creates a room. Its initial status is {@code AVAILABLE}.
     *
     * @param request name, location, capacity and equipment of the room
     * @return the saved room
     * @throws ResourceNotFoundException {@code BUILDING_NOT_FOUND} or {@code EQUIPMENT_NOT_FOUND}
     * @throws InvalidFieldException     {@code VALIDATION_ERROR} if the floor does not exist in the building
     * @throws ConflictException         {@code RESOURCE_ALREADY_EXISTS} if the name is already used, ignoring case
     */
    @Transactional
    public Room create(CreateRoomRequest request) {
        Building building = buildingService.findLocation(request.buildingId(), request.floor());
        Set<Equipment> equipment = equipmentService.findAllByCodes(request.equipmentCodes());
        if (roomRepository.existsByNameIgnoreCase(request.name())) {
            throw nameAlreadyUsed(request.name());
        }
        Room room = new Room();
        room.setName(request.name());
        room.setBuilding(building);
        room.setFloor(request.floor());
        room.setCapacity(request.capacity());
        room.setEquipment(new HashSet<>(equipment));
        return roomRepository.save(room);
    }

    /**
     * @return all rooms, sorted by name ignoring case, then by id
     */
    @Transactional(readOnly = true)
    public List<Room> findAll() {
        return roomRepository.findAllSortedByName();
    }

    /**
     * @param roomId id of the room
     * @return the room with its building and equipment
     * @throws ResourceNotFoundException {@code ROOM_NOT_FOUND} if it does not exist
     */
    @Transactional(readOnly = true)
    public Room findById(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> ResourceNotFoundException.room(roomId));
    }

    /**
     * Replaces the name, the location and the capacity of a room. The status and
     * the equipment are kept.
     *
     * @param roomId  id of the room
     * @param request new name, location and capacity
     * @return the updated room
     * @throws ResourceNotFoundException {@code ROOM_NOT_FOUND} or {@code BUILDING_NOT_FOUND}
     * @throws InvalidFieldException     {@code VALIDATION_ERROR} if the floor does not exist in the building
     * @throws ConflictException         {@code RESOURCE_ALREADY_EXISTS} if another room uses the name
     */
    @Transactional
    public Room update(Long roomId, UpdateRoomRequest request) {
        Room room = findById(roomId);
        Building building = buildingService.findLocation(request.buildingId(), request.floor());
        if (roomRepository.existsByNameIgnoreCaseAndIdNot(request.name(), roomId)) {
            throw nameAlreadyUsed(request.name());
        }
        room.setName(request.name());
        room.setBuilding(building);
        room.setFloor(request.floor());
        room.setCapacity(request.capacity());
        return room;
    }

    /**
     * Puts a room in {@code AVAILABLE} or {@code MAINTENANCE}. A room in
     * maintenance is no longer proposed nor bookable, but its existing
     * reservations are kept.
     *
     * @param roomId id of the room
     * @param status the new status
     * @return the updated room
     * @throws ResourceNotFoundException {@code ROOM_NOT_FOUND} if the room does not exist
     */
    @Transactional
    public Room updateStatus(Long roomId, RoomStatus status) {
        Room room = findById(roomId);
        room.setStatus(status);
        return room;
    }

    /**
     * Replaces all the equipment of a room. An empty list removes everything.
     *
     * @param roomId         id of the room
     * @param equipmentCodes codes of the new equipment
     * @return the updated room
     * @throws ResourceNotFoundException {@code ROOM_NOT_FOUND} or {@code EQUIPMENT_NOT_FOUND}
     */
    @Transactional
    public Room replaceEquipment(Long roomId, Collection<String> equipmentCodes) {
        Room room = findById(roomId);
        Set<Equipment> equipment = equipmentService.findAllByCodes(equipmentCodes);
        room.getEquipment().clear();
        room.getEquipment().addAll(equipment);
        return room;
    }

    /**
     * Finds the rooms that can host a reservation over {@code [start, end[}:
     * available status, enough seats, all the requested equipment and no
     * confirmed reservation overlapping the period. Nothing is booked.
     * <p>
     * The rooms are sorted by unused seats, then by name ignoring case, then by id.
     *
     * @param start          start of the period
     * @param end            end of the period
     * @param capacity       number of people to host
     * @param equipmentCodes codes of the requested equipment, may be {@code null}
     * @return the compatible rooms, empty if none matches
     * @throws InvalidReservationPeriodException if the period is invalid
     */
    @Transactional(readOnly = true)
    public List<Room> findAvailable(Instant start, Instant end, int capacity, Collection<String> equipmentCodes) {
        periodValidator.validate(start, end);
        Collection<String> requiredCodes = equipmentCodes == null ? List.of() : equipmentCodes;
        Set<Long> busyRoomIds = reservationRepository.findBusyRoomIds(start, end);

        return roomRepository.findByStatus(RoomStatus.AVAILABLE).stream()
                .filter(room -> roomCompatibility.isCompatible(room, capacity, requiredCodes, busyRoomIds))
                .sorted(Comparator.comparingInt((Room room) -> roomCompatibility.unusedCapacity(room, capacity))
                        .thenComparing(Room::getNormalizedName)
                        .thenComparing(Room::getId))
                .toList();
    }

    private ConflictException nameAlreadyUsed(String name) {
        return new ConflictException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                "Une salle utilise déjà ce nom", Map.of("name", name));
    }
}
