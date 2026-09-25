package fr.emse.ismin.reservation.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Body used to replace all the equipment of a room ({@code ReplaceRoomEquipmentRequest}
 * in the OpenAPI contract). An empty list removes every piece of equipment.
 *
 * @param equipmentCodes codes of the new equipment of the room
 */
public record ReplaceRoomEquipmentRequest(
        @NotNull(message = "est obligatoire")
        List<@Pattern(regexp = EquipmentRequest.CODE_PATTERN, message = EquipmentRequest.CODE_MESSAGE) String>
                equipmentCodes) {
}
