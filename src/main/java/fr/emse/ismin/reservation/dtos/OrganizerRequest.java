package fr.emse.ismin.reservation.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body used to create an organizer ({@code CreateOrganizerRequest} in the OpenAPI contract).
 *
 * @param name       full name of the organizer
 * @param email      unique email address, case-insensitive
 * @param buildingId id of the building where the organizer works
 * @param floor      floor of the organizer in that building
 */
public record OrganizerRequest(
        @NotBlank(message = "est obligatoire")
        @Size(max = 150, message = "doit contenir au plus 150 caractères")
        String name,

        @NotBlank(message = "est obligatoire")
        @Email(message = "doit être une adresse e-mail valide")
        @Size(max = 254, message = "doit contenir au plus 254 caractères")
        String email,

        @NotNull(message = "est obligatoire")
        @Min(value = 1, message = "doit être un identifiant positif")
        Long buildingId,

        @NotNull(message = "est obligatoire")
        @Min(value = 0, message = "doit être positif ou nul")
        @Max(value = 199, message = "doit être au plus égal à 199")
        Integer floor) {
}
