package fr.emse.ismin.reservation.exceptions;

import java.util.Map;

/**
 * Thrown when a field is well formed but invalid with regard to the stored data
 * (HTTP 400, {@code VALIDATION_ERROR}), for example a floor that does not exist
 * in the referenced building. Format checks are done by Bean Validation instead.
 */
public class InvalidFieldException extends ApiException {

    /** Message shared by every {@code VALIDATION_ERROR} response. */
    public static final String DEFAULT_MESSAGE = "La requête contient des données invalides";

    /**
     * @param field  name of the invalid field, as sent by the client
     * @param reason why the value is refused
     */
    public InvalidFieldException(String field, String reason) {
        super(ErrorCode.VALIDATION_ERROR, DEFAULT_MESSAGE,
                Map.of(), Map.of(field, reason));
    }
}
