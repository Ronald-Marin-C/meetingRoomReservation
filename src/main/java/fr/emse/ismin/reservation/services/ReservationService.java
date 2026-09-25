package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.dtos.AutomaticReservationRequest;
import fr.emse.ismin.reservation.dtos.CreateReservationRequest;
import fr.emse.ismin.reservation.exceptions.ConflictException;
import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.InvalidReservationPeriodException;
import fr.emse.ismin.reservation.exceptions.ResourceNotFoundException;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.models.Reservation;
import fr.emse.ismin.reservation.models.ReservationStatus;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.repositories.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * Business logic of the reservations: booking of a chosen room, automatic
 * booking of the most suitable room, listing with filters, lookup and cancellation.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomService roomService;
    private final OrganizerService organizerService;
    private final EquipmentService equipmentService;
    private final ReservationPeriodValidator periodValidator;
    private final RoomCompatibility roomCompatibility;
    private final RoomAllocator roomAllocator;
    private final Clock clock;

    /**
     * Books the room chosen by the client. The reservation is refused as soon as
     * one rule fails; the room is never silently replaced by another one.
     * <p>
     * Checks are made in this order: period, room, organizer, equipment codes,
     * room status, capacity, room equipment and overlapping reservations.
     *
     * @param request the reservation to create
     * @return the confirmed reservation
     * @throws InvalidReservationPeriodException if the period is invalid
     * @throws ResourceNotFoundException         if the room, the organizer or an equipment code does not exist
     * @throws ConflictException                 if the chosen room cannot host the reservation
     */
    @Transactional
    public Reservation create(CreateReservationRequest request) {
        Instant start = request.start().toInstant();
        Instant end = request.end().toInstant();
        periodValidator.validate(start, end);

        Room room = roomService.findById(request.roomId());
        Organizer organizer = organizerService.findById(request.organizerId());
        Set<Equipment> requiredEquipment = equipmentService.findAllByCodes(request.requiredEquipmentCodes());

        checkRoomCanHost(room, request.numberOfParticipants(), codesOf(requiredEquipment), start, end);
        return save(request.title(), room, organizer, start, end, request.numberOfParticipants(), requiredEquipment);
    }

    /**
     * Books the most suitable room, chosen by {@link RoomAllocator}. The choice
     * and the booking happen in the same transaction, so the room cannot be
     * taken by another reservation in between.
     *
     * @param request the reservation to create, without room
     * @return the confirmed reservation in the assigned room
     * @throws InvalidReservationPeriodException if the period is invalid
     * @throws ResourceNotFoundException         if the organizer or an equipment code does not exist
     * @throws ConflictException                 {@code NO_COMPATIBLE_ROOM} if no room can host the reservation;
     *                                           nothing is created in that case
     */
    @Transactional
    public Reservation createAutomatic(AutomaticReservationRequest request) {
        Instant start = request.start().toInstant();
        Instant end = request.end().toInstant();
        periodValidator.validate(start, end);

        Organizer organizer = organizerService.findById(request.organizerId());
        Set<Equipment> requiredEquipment = equipmentService.findAllByCodes(request.requiredEquipmentCodes());

        RoomAssignment assignment = roomAllocator.allocate(roomService.findAllAvailable(),
                        reservationRepository.findConfirmedOverlapping(start, end), organizer,
                        request.numberOfParticipants(), codesOf(requiredEquipment), start, end)
                .orElseThrow(() -> new ConflictException(ErrorCode.NO_COMPATIBLE_ROOM,
                        "Aucune salle disponible ne correspond aux critères demandés"));

        return save(request.title(), assignment.room(), organizer, start, end,
                request.numberOfParticipants(), requiredEquipment);
    }

    /**
     * Returns the reservations, confirmed and cancelled, matching every given
     * filter. A {@code null} filter is ignored. With {@code from} and {@code to},
     * the reservations overlapping that period are kept.
     *
     * @param roomId      keep only the reservations of this room
     * @param organizerId keep only the reservations of this organizer
     * @param from        keep only the reservations ending strictly after this date
     * @param to          keep only the reservations starting strictly before this date
     * @return the matching reservations, sorted by start then id
     * @throws InvalidReservationPeriodException if {@code from} is not before {@code to}
     */
    @Transactional(readOnly = true)
    public List<Reservation> findAll(Long roomId, Long organizerId, Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidReservationPeriodException("Le paramètre from doit être antérieur au paramètre to");
        }
        return reservationRepository.findWithFilters(roomId, organizerId, from, to);
    }

    /**
     * @param reservationId id of the reservation
     * @return the reservation, confirmed or cancelled
     * @throws ResourceNotFoundException {@code RESERVATION_NOT_FOUND} if it does not exist
     */
    @Transactional(readOnly = true)
    public Reservation findById(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.reservation(reservationId));
    }

    /**
     * Cancels a confirmed reservation. It stays visible but no longer blocks the room.
     *
     * @param reservationId id of the reservation
     * @return the cancelled reservation
     * @throws ResourceNotFoundException {@code RESERVATION_NOT_FOUND} if it does not exist
     * @throws ConflictException         {@code RESERVATION_ALREADY_CANCELLED} if it is already cancelled
     */
    @Transactional
    public Reservation cancel(Long reservationId) {
        Reservation reservation = findById(reservationId);
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ConflictException(ErrorCode.RESERVATION_ALREADY_CANCELLED,
                    "La réservation " + reservationId + " est déjà annulée", Map.of("reservationId", reservationId));
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        return reservation;
    }

    /**
     * Refuses the reservation with the matching conflict code if the room cannot host it.
     */
    private void checkRoomCanHost(Room room, int numberOfParticipants, Set<String> requiredEquipmentCodes,
                                  Instant start, Instant end) {
        if (!roomCompatibility.isAvailable(room)) {
            throw new ConflictException(ErrorCode.ROOM_UNAVAILABLE,
                    "La salle " + room.getName() + " est en maintenance", Map.of("roomId", room.getId()));
        }
        if (!roomCompatibility.hasCapacity(room, numberOfParticipants)) {
            throw new ConflictException(ErrorCode.ROOM_CAPACITY_EXCEEDED,
                    "La capacité de la salle est insuffisante",
                    Map.of("roomId", room.getId(),
                            "roomCapacity", room.getCapacity(),
                            "numberOfParticipants", numberOfParticipants));
        }
        SortedSet<String> missingEquipment = roomCompatibility.findMissingEquipment(room, requiredEquipmentCodes);
        if (!missingEquipment.isEmpty()) {
            throw new ConflictException(ErrorCode.MISSING_REQUIRED_EQUIPMENT,
                    "La salle ne possède pas tous les équipements demandés",
                    Map.of("roomId", room.getId(), "missingEquipmentCodes", List.copyOf(missingEquipment)));
        }
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(room.getId(), start, end);
        if (!conflicts.isEmpty()) {
            throw new ConflictException(ErrorCode.ROOM_ALREADY_RESERVED,
                    "La salle " + room.getName() + " est déjà réservée sur cette période",
                    Map.of("roomId", room.getId(), "conflictingReservationId", conflicts.getFirst().getId()));
        }
    }

    /**
     * Saves a confirmed reservation once every rule has been checked.
     */
    private Reservation save(String title, Room room, Organizer organizer, Instant start, Instant end,
                             int numberOfParticipants, Set<Equipment> requiredEquipment) {
        Reservation reservation = new Reservation();
        reservation.setTitle(title);
        reservation.setRoom(room);
        reservation.setOrganizer(organizer);
        reservation.setStart(start);
        reservation.setEnd(end);
        reservation.setNumberOfParticipants(numberOfParticipants);
        reservation.setRequiredEquipment(new HashSet<>(requiredEquipment));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setCreatedAt(Instant.now(clock));
        return reservationRepository.save(reservation);
    }

    private static Set<String> codesOf(Set<Equipment> equipment) {
        return equipment.stream().map(Equipment::getCode).collect(Collectors.toSet());
    }
}
