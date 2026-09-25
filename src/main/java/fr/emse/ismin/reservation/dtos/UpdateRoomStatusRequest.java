package fr.emse.ismin.reservation.dtos;

import fr.emse.ismin.reservation.models.RoomStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Body used to change the status of a room ({@code UpdateRoomStatusRequest} in the OpenAPI contract).
 *
 * @param status {@code AVAILABLE} or {@code MAINTENANCE}
 */
public record UpdateRoomStatusRequest(
        @NotNull(message = "est obligatoire")
        RoomStatus status) {
}
