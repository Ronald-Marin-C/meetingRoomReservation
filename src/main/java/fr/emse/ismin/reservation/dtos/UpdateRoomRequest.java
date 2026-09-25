package fr.emse.ismin.reservation.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body used to replace the information of a room ({@code UpdateRoomRequest} in
 * the OpenAPI contract). The status and the equipment are not part of it.
 *
 * @param name       unique name of the room, case-insensitive
 * @param buildingId id of the building of the room
 * @param floor      floor of the room in that building
 * @param capacity   number of seats
 */
public record UpdateRoomRequest(
        @NotBlank(message = "est obligatoire")
        @Size(max = 100, message = "doit contenir au plus 100 caractères")
        String name,

        @NotNull(message = "est obligatoire")
        @Min(value = 1, message = "doit être un identifiant positif")
        Long buildingId,

        @NotNull(message = "est obligatoire")
        @Min(value = 0, message = "doit être positif ou nul")
        @Max(value = 199, message = "doit être au plus égal à 199")
        Integer floor,

        @NotNull(message = "est obligatoire")
        @Min(value = 1, message = "doit être strictement positive")
        Integer capacity) {
}
