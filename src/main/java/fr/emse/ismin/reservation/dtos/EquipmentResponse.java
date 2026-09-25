package fr.emse.ismin.reservation.dtos;

import fr.emse.ismin.reservation.models.Equipment;

/**
 * Piece of equipment as returned by the API ({@code EquipmentResponse} in the OpenAPI contract).
 *
 * @param id    equipment id
 * @param code  unique code, for example {@code PROJECTOR}
 * @param label readable name
 */
public record EquipmentResponse(Long id, String code, String label) {

    /**
     * Builds the response from the entity.
     *
     * @param equipment the equipment entity
     * @return the matching response
     */
    public static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(equipment.getId(), equipment.getCode(), equipment.getLabel());
    }
}
