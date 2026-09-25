package fr.emse.ismin.reservation.dtos;

import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;

import java.util.List;

/**
 * Room returned by the availability search ({@code AvailableRoomResponse} in the
 * OpenAPI contract): a room plus the number of seats that would stay empty.
 *
 * @param id             room id
 * @param name           room name
 * @param building       building of the room
 * @param floor          floor of the room
 * @param capacity       number of seats
 * @param status         status of the room, always {@code AVAILABLE} here
 * @param equipment      equipment of the room, sorted by code
 * @param unusedCapacity room capacity minus the requested capacity
 */
public record AvailableRoomResponse(Long id, String name, BuildingResponse building, Integer floor,
                                    Integer capacity, RoomStatus status, List<EquipmentResponse> equipment,
                                    Integer unusedCapacity) {

    /**
     * Builds the response from the entity.
     *
     * @param room              the room entity, with its building and equipment loaded
     * @param requestedCapacity number of people requested in the search
     * @return the matching response
     */
    public static AvailableRoomResponse from(Room room, int requestedCapacity) {
        return new AvailableRoomResponse(room.getId(), room.getName(), BuildingResponse.from(room.getBuilding()),
                room.getFloor(), room.getCapacity(), room.getStatus(), RoomResponse.sortedEquipment(room),
                room.getCapacity() - requestedCapacity);
    }
}
