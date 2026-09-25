package fr.emse.ismin.reservation.dtos;

import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;

/**
 * Room without its equipment, as nested in a reservation ({@code RoomSummaryResponse}
 * in the OpenAPI contract).
 *
 * @param id       room id
 * @param name     room name
 * @param building building of the room
 * @param floor    floor of the room
 * @param capacity number of seats
 * @param status   {@code AVAILABLE} or {@code MAINTENANCE}
 */
public record RoomSummaryResponse(Long id, String name, BuildingResponse building, Integer floor,
                                  Integer capacity, RoomStatus status) {

    /**
     * Builds the summary from the entity.
     *
     * @param room the room entity
     * @return the matching summary
     */
    public static RoomSummaryResponse from(Room room) {
        return new RoomSummaryResponse(room.getId(), room.getName(), BuildingResponse.from(room.getBuilding()),
                room.getFloor(), room.getCapacity(), room.getStatus());
    }
}
