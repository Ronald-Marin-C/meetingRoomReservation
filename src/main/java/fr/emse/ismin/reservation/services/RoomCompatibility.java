package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.models.Reservation;
import fr.emse.ismin.reservation.models.ReservationStatus;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Rules deciding whether a room can host a reservation. This class only works
 * on objects already loaded in memory, so it can be tested without a database.
 * It is shared by the availability search, the reservation of a chosen room and
 * the automatic assignment.
 */
@Component
public class RoomCompatibility {

    /**
     * @param room the room
     * @return {@code true} if the room is not in maintenance
     */
    public boolean isAvailable(Room room) {
        return room.getStatus() == RoomStatus.AVAILABLE;
    }

    /**
     * A room of 30 seats can host exactly 30 people.
     *
     * @param room                 the room
     * @param numberOfParticipants number of people to host
     * @return {@code true} if the capacity is sufficient
     */
    public boolean hasCapacity(Room room, int numberOfParticipants) {
        return numberOfParticipants <= room.getCapacity();
    }

    /**
     * Returns the requested equipment that the room lacks. Extra equipment in
     * the room does not matter.
     *
     * @param room                   the room
     * @param requiredEquipmentCodes codes of the requested equipment
     * @return the missing codes, sorted, empty if the room has everything
     */
    public SortedSet<String> findMissingEquipment(Room room, Collection<String> requiredEquipmentCodes) {
        Set<String> roomCodes = room.getEquipment().stream()
                .map(Equipment::getCode)
                .collect(Collectors.toSet());
        return requiredEquipmentCodes.stream()
                .filter(code -> !roomCodes.contains(code))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Checks every rule at once: available status, sufficient capacity, all the
     * requested equipment, and no confirmed reservation overlapping the period.
     *
     * @param room                   the room
     * @param numberOfParticipants   number of people to host
     * @param requiredEquipmentCodes codes of the requested equipment
     * @param busyRoomIds            ids of the rooms already booked on the period
     * @return {@code true} if the room can host the reservation
     */
    public boolean isCompatible(Room room, int numberOfParticipants,
                                Collection<String> requiredEquipmentCodes, Set<Long> busyRoomIds) {
        return isAvailable(room)
                && hasCapacity(room, numberOfParticipants)
                && findMissingEquipment(room, requiredEquipmentCodes).isEmpty()
                && !busyRoomIds.contains(room.getId());
    }

    /**
     * Tells whether an existing reservation blocks the period {@code [start, end[}.
     * Only confirmed reservations block a room, and consecutive periods such as
     * 10:00-11:00 and 11:00-12:00 do not overlap.
     *
     * @param existing an existing reservation
     * @param start    start of the requested period
     * @param end      end of the requested period
     * @return {@code true} if the reservation is confirmed and overlaps the period
     */
    public boolean blocks(Reservation existing, Instant start, Instant end) {
        return existing.getStatus() == ReservationStatus.CONFIRMED
                && existing.getStart().isBefore(end)
                && existing.getEnd().isAfter(start);
    }

    /**
     * @param room                 the room
     * @param numberOfParticipants number of people to host
     * @return the number of seats left empty
     */
    public int unusedCapacity(Room room, int numberOfParticipants) {
        return room.getCapacity() - numberOfParticipants;
    }
}
