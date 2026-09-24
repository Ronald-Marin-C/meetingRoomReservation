package fr.emse.ismin.reservation.dtos;

import fr.emse.ismin.reservation.models.Building;

/**
 * Building as returned by the API ({@code BuildingResponse} in the OpenAPI contract).
 *
 * @param id             building id
 * @param name           building name
 * @param numberOfFloors number of floors, ground floor included
 */
public record BuildingResponse(Long id, String name, Integer numberOfFloors) {

    /**
     * Builds the response from the entity.
     *
     * @param building the building entity
     * @return the matching response
     */
    public static BuildingResponse from(Building building) {
        return new BuildingResponse(building.getId(), building.getName(), building.getNumberOfFloors());
    }
}
