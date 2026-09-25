package fr.emse.ismin.reservation.dtos;

import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;

import java.util.Comparator;
import java.util.List;

/**
 * Room as returned by the API ({@code RoomResponse} in the OpenAPI contract).
 *
 * @param id        room id
 * @param name      room name
 * @param building  building of the room
 * @param floor     floor of the room
 * @param capacity  number of seats
 * @param status    {@code AVAILABLE} or {@code MAINTENANCE}
 * @param equipment equipment of the room, sorted by code
 */
public record RoomResponse(Long id, String name, BuildingResponse building, Integer floor,
                           Integer capacity, RoomStatus status, List<EquipmentResponse> equipment) {

    /**
     * Builds the response from the entity.
     *
     * @param room the room entity, with its building and equipment loaded
     * @return the matching response
     */
    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), BuildingResponse.from(room.getBuilding()),
                room.getFloor(), room.getCapacity(), room.getStatus(), sortedEquipment(room));
    }

    /**
     * Returns the equipment of a room sorted by code, so that the response
     * does not depend on the order in which the database returns it.
     */
    static List<EquipmentResponse> sortedEquipment(Room room) {
        return room.getEquipment().stream()
                .map(EquipmentResponse::from)
                .sorted(Comparator.comparing(EquipmentResponse::code))
                .toList();
    }
}
