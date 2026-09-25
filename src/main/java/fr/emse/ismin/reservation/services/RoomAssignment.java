package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.models.Room;

/**
 * A compatible room with the details of its score for an automatic assignment.
 *
 * @param room           the compatible room
 * @param distance       distance between the organizer and the room
 * @param unusedCapacity number of seats left empty
 * @param score          {@code distance * 10 + unusedCapacity}, the lower the better
 */
public record RoomAssignment(Room room, int distance, int unusedCapacity, long score) {
}
