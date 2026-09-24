package fr.emse.ismin.reservation.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body used to create or update a building ({@code CreateBuildingRequest} and
 * {@code UpdateBuildingRequest} in the OpenAPI contract, which are identical).
 *
 * @param name           unique name of the building, case-insensitive
 * @param numberOfFloors number of floors, ground floor included
 */
public record BuildingRequest(
        @NotBlank(message = "est obligatoire")
        @Size(max = 100, message = "doit contenir au plus 100 caractères")
        String name,

        @NotNull(message = "est obligatoire")
        @Min(value = 1, message = "doit être au moins égal à 1")
        @Max(value = 200, message = "doit être au plus égal à 200")
        Integer numberOfFloors) {
}
