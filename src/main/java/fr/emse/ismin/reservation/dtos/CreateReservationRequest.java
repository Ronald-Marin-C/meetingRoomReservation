package fr.emse.ismin.reservation.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Body used to book a chosen room ({@code CreateReservationRequest} in the OpenAPI contract).
 *
 * @param title                  title of the meeting
 * @param roomId                 id of the chosen room
 * @param organizerId            id of the organizer
 * @param start                  start of the period, ISO 8601 with an offset
 * @param end                    end of the period, ISO 8601 with an offset
 * @param numberOfParticipants   number of people attending
 * @param requiredEquipmentCodes codes of the equipment the room must have, may be omitted
 */
public record CreateReservationRequest(
        @NotBlank(message = "est obligatoire")
        @Size(max = 200, message = "doit contenir au plus 200 caractères")
        String title,

        @NotNull(message = "est obligatoire")
        @Min(value = 1, message = "doit être un identifiant positif")
        Long roomId,

        @NotNull(message = "est obligatoire")
        @Min(value = 1, message = "doit être un identifiant positif")
        Long organizerId,

        @NotNull(message = "est obligatoire")
        OffsetDateTime start,

        @NotNull(message = "est obligatoire")
        OffsetDateTime end,

        @NotNull(message = "est obligatoire")
        @Min(value = 1, message = "doit être strictement positif")
        Integer numberOfParticipants,

        List<@Pattern(regexp = EquipmentRequest.CODE_PATTERN, message = EquipmentRequest.CODE_MESSAGE) String>
                requiredEquipmentCodes) {
}
