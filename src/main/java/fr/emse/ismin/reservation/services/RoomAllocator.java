package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.models.Reservation;
import fr.emse.ismin.reservation.models.Room;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chooses the most suitable room for an automatic reservation.
 * <p>
 * Only the compatible rooms are kept: available status, enough seats, all the
 * requested equipment and no confirmed reservation overlapping the period.
 * Each of them gets a score, the lowest being the best:
 * <ul>
 *     <li>{@code unusedCapacity = capacity - numberOfParticipants}</li>
 *     <li>same building: {@code distance = |roomFloor - organizerFloor|}</li>
 *     <li>other building: {@code distance = 10 + |roomFloor - organizerFloor|}</li>
 *     <li>{@code score = distance * 10 + unusedCapacity}</li>
 * </ul>
 * One floor of distance is worth ten empty seats, and changing building adds a
 * penalty of 100 points. Ties are broken by room name ignoring case, then by id,
 * so the result is always the same for the same data.
 * <p>
 * This class only works on objects already loaded in memory, so it can be tested
 * without a database.
 */
@Component
@RequiredArgsConstructor
public class RoomAllocator {

    /** Distance added when the room is not in the building of the organizer. */
    public static final int OTHER_BUILDING_PENALTY = 10;

    /** Weight of one unit of distance, compared to one empty seat. */
    public static final int DISTANCE_WEIGHT = 10;

    private final RoomCompatibility roomCompatibility;

    /**
     * Returns the best room for the request, if any.
     *
     * @param rooms                  the candidate rooms
     * @param existingReservations   reservations that may block the candidate rooms
     * @param organizer              the organizer, whose location gives the distance
     * @param numberOfParticipants   number of people to host
     * @param requiredEquipmentCodes codes of the requested equipment
     * @param start                  start of the period
     * @param end                    end of the period
     * @return the best assignment, or empty if no room is compatible
     */
    public Optional<RoomAssignment> allocate(Collection<Room> rooms, Collection<Reservation> existingReservations,
                                             Organizer organizer, int numberOfParticipants,
                                             Collection<String> requiredEquipmentCodes, Instant start, Instant end) {
        return rank(rooms, existingReservations, organizer, numberOfParticipants, requiredEquipmentCodes, start, end)
                .stream()
                .findFirst();
    }

    /**
     * Returns every compatible room with its score, from the best to the worst.
     *
     * @param rooms                  the candidate rooms
     * @param existingReservations   reservations that may block the candidate rooms
     * @param organizer              the organizer, whose location gives the distance
     * @param numberOfParticipants   number of people to host
     * @param requiredEquipmentCodes codes of the requested equipment
     * @param start                  start of the period
     * @param end                    end of the period
     * @return the compatible rooms sorted by score, then name ignoring case, then id
     */
    public List<RoomAssignment> rank(Collection<Room> rooms, Collection<Reservation> existingReservations,
                                     Organizer organizer, int numberOfParticipants,
                                     Collection<String> requiredEquipmentCodes, Instant start, Instant end) {
        Set<Long> busyRoomIds = existingReservations.stream()
                .filter(reservation -> roomCompatibility.blocks(reservation, start, end))
                .map(reservation -> reservation.getRoom().getId())
                .collect(Collectors.toSet());

        return rooms.stream()
                .filter(room -> roomCompatibility.isCompatible(
                        room, numberOfParticipants, requiredEquipmentCodes, busyRoomIds))
                .map(room -> assign(room, organizer, numberOfParticipants))
                .sorted(Comparator.comparingLong(RoomAssignment::score)
                        .thenComparing(assignment -> assignment.room().getNormalizedName())
                        .thenComparing(assignment -> assignment.room().getId()))
                .toList();
    }

    /**
     * @param room      the room
     * @param organizer the organizer
     * @return the floor difference, plus {@value #OTHER_BUILDING_PENALTY} when the buildings differ
     */
    public int distance(Room room, Organizer organizer) {
        int floorDifference = Math.abs(room.getFloor() - organizer.getFloor());
        boolean sameBuilding = room.getBuilding().getId().equals(organizer.getBuilding().getId());
        return sameBuilding ? floorDifference : OTHER_BUILDING_PENALTY + floorDifference;
    }

    /**
     * @param distance       distance between the organizer and the room
     * @param unusedCapacity number of seats left empty
     * @return {@code distance * 10 + unusedCapacity}
     */
    public long score(int distance, int unusedCapacity) {
        return (long) distance * DISTANCE_WEIGHT + unusedCapacity;
    }

    private RoomAssignment assign(Room room, Organizer organizer, int numberOfParticipants) {
        int distance = distance(room, organizer);
        int unusedCapacity = roomCompatibility.unusedCapacity(room, numberOfParticipants);
        return new RoomAssignment(room, distance, unusedCapacity, score(distance, unusedCapacity));
    }
}
