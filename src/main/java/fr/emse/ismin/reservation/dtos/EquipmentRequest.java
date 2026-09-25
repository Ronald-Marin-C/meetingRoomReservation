package fr.emse.ismin.reservation.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body used to create a piece of equipment ({@code CreateEquipmentRequest} in the OpenAPI contract).
 *
 * @param code  unique code in upper case, for example {@code PROJECTOR}
 * @param label readable name, for example {@code Vidéoprojecteur}
 */
public record EquipmentRequest(
        @NotBlank(message = "est obligatoire")
        @Pattern(regexp = EquipmentRequest.CODE_PATTERN, message = EquipmentRequest.CODE_MESSAGE)
        String code,

        @NotBlank(message = "est obligatoire")
        @Size(max = 100, message = "doit contenir au plus 100 caractères")
        String label) {

    /** Format of an equipment code ({@code EquipmentCode} in the contract). */
    public static final String CODE_PATTERN = "^[A-Z][A-Z0-9_]{1,49}$";

    /** Message returned when a code does not match {@link #CODE_PATTERN}. */
    public static final String CODE_MESSAGE =
            "doit commencer par une majuscule et ne contenir que des majuscules, chiffres ou _ (2 à 50 caractères)";
}
