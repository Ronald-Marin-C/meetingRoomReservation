package fr.emse.ismin.reservation.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body used to create a room ({@code CreateRoomRequest} in the OpenAPI contract).
 *
 * @param name           unique name of the room, case-insensitive
 * @param buildingId     id of the building of the room
 * @param floor          floor of the room in that building
 * @param capacity       number of seats
 * @param equipmentCodes codes of the equipment available in the room, may be omitted
 */
public record CreateRoomRequest(
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
        Integer capacity,

        List<@Pattern(regexp = EquipmentRequest.CODE_PATTERN, message = EquipmentRequest.CODE_MESSAGE) String>
                equipmentCodes) {
}
