package fr.emse.ismin.reservation.exceptions;

import java.util.Map;

/**
 * Thrown when a requested resource does not exist (HTTP 404).
 * Instances are created through the factory methods, one per resource type.
 */
public class ResourceNotFoundException extends ApiException {

    private ResourceNotFoundException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details, Map.of());
    }

    /**
     * @param buildingId id of the missing building
     * @return the exception with code {@code BUILDING_NOT_FOUND}
     */
    public static ResourceNotFoundException building(Long buildingId) {
        return new ResourceNotFoundException(ErrorCode.BUILDING_NOT_FOUND,
                "Le bâtiment " + buildingId + " n'existe pas", Map.of("buildingId", buildingId));
    }

    /**
     * @param roomId id of the missing room
     * @return the exception with code {@code ROOM_NOT_FOUND}
     */
    public static ResourceNotFoundException room(Long roomId) {
        return new ResourceNotFoundException(ErrorCode.ROOM_NOT_FOUND,
                "La salle " + roomId + " n'existe pas", Map.of("roomId", roomId));
    }

    /**
     * @param organizerId id of the missing organizer
     * @return the exception with code {@code ORGANIZER_NOT_FOUND}
     */
    public static ResourceNotFoundException organizer(Long organizerId) {
        return new ResourceNotFoundException(ErrorCode.ORGANIZER_NOT_FOUND,
                "L'organisateur " + organizerId + " n'existe pas", Map.of("organizerId", organizerId));
    }

    /**
     * @param reservationId id of the missing reservation
     * @return the exception with code {@code RESERVATION_NOT_FOUND}
     */
    public static ResourceNotFoundException reservation(Long reservationId) {
        return new ResourceNotFoundException(ErrorCode.RESERVATION_NOT_FOUND,
                "La réservation " + reservationId + " n'existe pas", Map.of("reservationId", reservationId));
    }

    /**
     * @param equipmentCode code of the missing equipment
     * @return the exception with code {@code EQUIPMENT_NOT_FOUND}
     */
    public static ResourceNotFoundException equipment(String equipmentCode) {
        return new ResourceNotFoundException(ErrorCode.EQUIPMENT_NOT_FOUND,
                "L'équipement " + equipmentCode + " n'existe pas", Map.of("equipmentCode", equipmentCode));
    }
}
