package fr.emse.ismin.reservation.dtos;

import fr.emse.ismin.reservation.models.Organizer;

/**
 * Organizer without the email address, as nested in a reservation
 * ({@code OrganizerSummaryResponse} in the OpenAPI contract).
 *
 * @param id       organizer id
 * @param name     full name
 * @param building building where the organizer works
 * @param floor    floor of the organizer in that building
 */
public record OrganizerSummaryResponse(Long id, String name, BuildingResponse building, Integer floor) {

    /**
     * Builds the summary from the entity.
     *
     * @param organizer the organizer entity
     * @return the matching summary
     */
    public static OrganizerSummaryResponse from(Organizer organizer) {
        return new OrganizerSummaryResponse(organizer.getId(), organizer.getName(),
                BuildingResponse.from(organizer.getBuilding()), organizer.getFloor());
    }
}
