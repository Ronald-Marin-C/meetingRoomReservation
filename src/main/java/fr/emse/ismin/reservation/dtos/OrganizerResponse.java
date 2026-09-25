package fr.emse.ismin.reservation.dtos;

import fr.emse.ismin.reservation.models.Organizer;

/**
 * Organizer as returned by the API ({@code OrganizerResponse} in the OpenAPI contract).
 *
 * @param id       organizer id
 * @param name     full name
 * @param building building where the organizer works
 * @param floor    floor of the organizer in that building
 * @param email    email address
 */
public record OrganizerResponse(Long id, String name, BuildingResponse building, Integer floor, String email) {

    /**
     * Builds the response from the entity.
     *
     * @param organizer the organizer entity
     * @return the matching response
     */
    public static OrganizerResponse from(Organizer organizer) {
        return new OrganizerResponse(organizer.getId(), organizer.getName(),
                BuildingResponse.from(organizer.getBuilding()), organizer.getFloor(), organizer.getEmail());
    }
}
